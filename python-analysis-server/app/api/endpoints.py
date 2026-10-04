import sys
import os
import json
import asyncio
import base64
import hashlib
import hmac
import queue
import threading
import time
import shutil
import tempfile
import traceback
from pathlib import Path
from difflib import SequenceMatcher
from fastapi import APIRouter, Form, HTTPException, WebSocket, WebSocketDisconnect, UploadFile, File
from fastapi.responses import StreamingResponse

from app.schemas.models import (
    AnalyzeRequest, MarkRequest, GenerateScriptRequest,
    UpdateScriptRequest, ConvertPptRequest, ScriptWordPayload,
    FeedbackSummaryRequest,  # [STEP 9]
)
from app.services.voice_service import (
    analyze_voice_features, build_aligned_word_results,
    build_sentence_results, build_issue_results, build_skipped_word_results,
    detect_disfluencies,
)
from app.services.stt_service import transcribe_audio
from app.services.ai_service import (
    generate_ai_feedback, generate_script_ai,
    update_script_ai, mark_script_ai,
    generate_feedback_summary,  # [STEP 9]
    generate_script_ai_stream, update_script_ai_stream,  # [STEP-A] SSE streaming
)
from app.services.ppt_service import (
    ensure_within_upload_root, convert_ppt_to_pdf, render_pdf_to_images
)
from app.services.s3_service import upload_to_s3
# [STEP 7] 공유 임계값 import — voice_service.py와 동일 소스 참조
from app.utils.helpers import (
    clamp, normalize_match_text,
    MATCH_THRESHOLD, SHORT_WORD_MATCH_THRESHOLD,
    REALTIME_MIN_SCORE, REALTIME_CORRECT_THRESHOLD,
    calculate_text_similarity,      # [STEP-C] 공유 유사도 함수
    monotonic_dtw_align,            # [STEP-C] 공유 DTW 정렬 엔진
    ANCHOR_MIN_LEN,                 # [STEP-C] 앵커 단어 최소 길이
)
from app.core.config import (
    GOOGLE_STT_ENABLED, GOOGLE_STT_SAMPLE_RATE,
    GOOGLE_STT_LANGUAGE_CODE, GOOGLE_STT_ENCODING, GOOGLE_STT_INTERIM_RESULTS,
    GOOGLE_STT_STREAM_ALLOWED_ENCODINGS,
    GOOGLE_STT_STREAM_MAX_CHUNK_BYTES, GOOGLE_STT_STREAM_MAX_SECONDS,
    GOOGLE_STT_STREAM_QUEUE_SIZE,
    JWT_SECRET, UPLOAD_ROOT
)

# [TRACE] LangSmith 트레이싱 — /analyze 전체를 루트 트레이스로 묶어
# STT·librosa·피드백을 자식 런으로 중첩 표시. (asyncio.to_thread는 contextvars를
# 스레드로 복사하므로 부모 런 컨텍스트가 정상 전파됨.)
# langsmith 미설치 환경에서도 동작하도록 no-op 폴백 제공.
try:
    from langsmith import traceable
except ImportError:
    def traceable(*args, **kwargs):
        def decorator(fn):
            return fn
        return decorator if args and callable(args[0]) else decorator

router = APIRouter()

# ── [STEP-B] SpeechClient 싱글톤 ─────────────────────────────────────────────
# 매 STT 세션마다 SpeechClient()를 새로 생성하면 gRPC 채널 + 인증 로딩으로
# 수백ms~1초 콜드 스타트가 발생한다. 모듈 레벨 싱글톤으로 공유.
# (gRPC client는 스레드 세이프 — 재사용 가능)
_stt_client = None
_stt_client_lock = __import__("threading").Lock()

def get_stt_client():
    """SpeechClient 싱글톤 반환. 최초 호출 시 1회만 생성."""
    global _stt_client
    if _stt_client is not None:
        return _stt_client
    with _stt_client_lock:
        if _stt_client is None:
            try:
                from google.cloud import speech
                _stt_client = speech.SpeechClient()
                print("[Python STEP-B] SpeechClient 싱글톤 생성 완료", flush=True)
            except Exception as e:
                print(f"[Python STEP-B] SpeechClient 생성 실패: {e}", flush=True)
                _stt_client = False  # 실패 플래그
    return _stt_client


def warmup_stt_client():
    """[STEP-B] 앱 기동 시 SpeechClient를 미리 생성 (첫 세션 콜드 스타트 방지)."""
    client = get_stt_client()
    if client and client is not False:
        print("[Python STEP-B] SpeechClient 워밍업 완료", flush=True)
    else:
        print("[Python STEP-B] SpeechClient 워밍업 스킵 (비활성 또는 실패)", flush=True)


# ── [STT-REUSE] 실시간 스트리밍 STT 결과 캐시 ────────────────────────────────
# 발표 연습 중 WebSocket으로 이미 수행한 STT(is_final 단어 + 타임스탬프)를
# practice_id 기준으로 저장해 두었다가, 직후 /analyze 호출에서 재사용한다.
# 이렇게 하면 /analyze에서 전체 녹음을 다시 STT 하는 ~30초 비용을 제거할 수 있다.
#
# 신뢰성 제약(중요):
#  - 스트리밍은 시간제한(GOOGLE_STT_STREAM_MAX_SECONDS)으로 재접속(resume)될 수 있다.
#    재접속 시 각 스트림의 타임스탬프는 0부터 다시 시작하므로, 여러 세션의 결과를
#    이어 붙이면 타임스탬프가 어긋난다(문장/pause 계산이 깨짐).
#    → 재접속이 한 번이라도 발생한 연습은 trusted=False로 표시하고 재사용하지 않는다.
#  - 단일 세션으로 끝난 연습만 trusted=True → 타임스탬프를 신뢰하고 배치 STT를 생략.
#  - 어떤 경우든 캐시 미스/비신뢰 시 기존 배치 STT로 안전하게 폴백한다.
#
# 단일 uvicorn 프로세스 전제(인메모리). 멀티 워커 환경에서는 외부 캐시 필요.
_analysis_stt_cache = {}
_analysis_stt_cache_lock = threading.Lock()
# 메모리 누수 방지: 캐시 최대 항목 수 (초과 시 가장 오래된 항목 제거)
_ANALYSIS_STT_CACHE_MAX = 256


def store_streaming_stt_words(practice_id, words):
    """[STT-REUSE] 스트리밍 연습 종료 시 누적·정합된 is_final 단어 결과를 캐시에 저장.

    재접속(5분 초과)으로 여러 세그먼트가 생긴 경우, 호출부(WS 핸들러)에서
    세그먼트별 시작 오프셋(base_time_offset_ms)을 더해 단일 타임라인으로 누적한
    뒤 한 번에 전달한다. 따라서 여기서는 별도의 신뢰 강등 없이 그대로 신뢰 저장한다.

    Args:
        practice_id: 연습 ID (캐시 키)
        words: 정합 완료된 [{word, startMs, endMs, confidence, isFinal}, ...]
    """
    if practice_id is None:
        return
    try:
        key = int(practice_id)
    except (TypeError, ValueError):
        key = practice_id

    trusted = bool(words)
    with _analysis_stt_cache_lock:
        # 캐시 용량 관리 (가장 오래된 항목 제거)
        if key not in _analysis_stt_cache and len(_analysis_stt_cache) >= _ANALYSIS_STT_CACHE_MAX:
            oldest_key = min(_analysis_stt_cache, key=lambda k: _analysis_stt_cache[k]["stored_at"])
            _analysis_stt_cache.pop(oldest_key, None)

        _analysis_stt_cache[key] = {
            "words": list(words or []),
            "trusted": trusted,
            "stored_at": time.monotonic(),
        }
        print(
            f"[STT-REUSE] practice {key}: 스트리밍 STT 캐시 저장 "
            f"(words={len(words or [])}, trusted={trusted})",
            flush=True
        )


def pop_trusted_streaming_stt_words(practice_id):
    """[STT-REUSE] 신뢰 가능한 스트리밍 STT 결과를 꺼내 반환(소비). 없으면 None.

    - trusted=False(재접속 발생)거나 캐시 미스면 None → /analyze가 배치 STT로 폴백.
    - 반환 시 캐시에서 제거(동일 연습 재분석 방지 및 메모리 회수).
    """
    if practice_id is None:
        return None
    try:
        key = int(practice_id)
    except (TypeError, ValueError):
        key = practice_id

    with _analysis_stt_cache_lock:
        entry = _analysis_stt_cache.pop(key, None)

    if not entry:
        return None
    if not entry.get("trusted"):
        print(f"[STT-REUSE] practice {key}: 캐시 존재하나 신뢰 불가 → 배치 STT 폴백", flush=True)
        return None
    words = entry.get("words") or []
    if not words:
        return None
    print(f"[STT-REUSE] practice {key}: 신뢰 캐시 적중 → 배치 STT 생략 (words={len(words)})", flush=True)
    return words


# --- STT Helpers ---
def get_google_audio_encoding(speech):
    encoding_map = {
        "LINEAR16": speech.RecognitionConfig.AudioEncoding.LINEAR16,
        "FLAC": speech.RecognitionConfig.AudioEncoding.FLAC,
        "WEBM_OPUS": speech.RecognitionConfig.AudioEncoding.WEBM_OPUS,
        "OGG_OPUS": speech.RecognitionConfig.AudioEncoding.OGG_OPUS,
        "ENCODING_UNSPECIFIED": speech.RecognitionConfig.AudioEncoding.ENCODING_UNSPECIFIED,
    }
    return encoding_map.get(resolve_stream_audio_encoding(), speech.RecognitionConfig.AudioEncoding.ENCODING_UNSPECIFIED)


def get_google_stream_audio_encoding(speech, audio_encoding):
    encoding_map = {
        "LINEAR16": speech.RecognitionConfig.AudioEncoding.LINEAR16,
        "FLAC": speech.RecognitionConfig.AudioEncoding.FLAC,
        "WEBM_OPUS": speech.RecognitionConfig.AudioEncoding.WEBM_OPUS,
        "OGG_OPUS": speech.RecognitionConfig.AudioEncoding.OGG_OPUS,
        "ENCODING_UNSPECIFIED": speech.RecognitionConfig.AudioEncoding.ENCODING_UNSPECIFIED,
    }
    return encoding_map.get(audio_encoding, speech.RecognitionConfig.AudioEncoding.ENCODING_UNSPECIFIED)


def resolve_stream_audio_encoding(value=None):
    requested_encoding = (value or GOOGLE_STT_ENCODING or "").upper()
    if requested_encoding in GOOGLE_STT_STREAM_ALLOWED_ENCODINGS:
        return requested_encoding

    return None


def requires_sample_rate(audio_encoding):
    return audio_encoding not in {"WEBM_OPUS", "OGG_OPUS"}


class GoogleStreamingSttSession:
    def __init__(self, practice_id, script_words, websocket, loop, audio_encoding=None, sample_rate_hertz=None):
        self.practice_id = practice_id
        self.script_words = normalize_script_words(script_words)
        self.websocket = websocket
        self.loop = loop
        self.audio_encoding = resolve_stream_audio_encoding(audio_encoding)
        self.sample_rate_hertz = int(sample_rate_hertz or GOOGLE_STT_SAMPLE_RATE)
        self.audio_queue = queue.Queue(maxsize=GOOGLE_STT_STREAM_QUEUE_SIZE)
        self.closed = threading.Event()
        self.confirmed_global_word_index = -1
        self.last_partial_global_word_index = -1
        self.word_results_by_index = {}
        self.final_transcript = ""
        self.started_at = time.monotonic()
        # [STEP-C] 앵커 인덱스: 고유 단어(4자 이상) 매칭 시 설정 → 누적 드리프트 방지
        self.anchor_global_index = -1
        # [STT-REUSE] is_final 결과의 word-level 타임스탬프 누적 → 사후 /analyze에서 배치 STT 대체
        self.final_stt_words = []
        # [STT-REUSE] 재접속(스트림 시간제한/재연결)으로 생성된 세션이면 True.
        self.is_resumed = False
        # [STT-REUSE] 재접속 세그먼트 정합: 이 세션의 단어 타임스탬프(0부터 시작)에 더할
        #   누적 시작 오프셋(ms). 첫 세그먼트는 0, 이후 세그먼트는 이전 세그먼트들의
        #   오디오 길이 합. 이를 더해 여러 세그먼트를 하나의 타임라인으로 정합한다.
        self.base_time_offset_ms = 0
        # 이 세그먼트가 오디오 수신을 멈춘 시각(monotonic). segment_duration_ms 계산용.
        self.audio_ended_at = None

    def start(self):
        if not self.audio_encoding:
            print("STT Error: unsupported audio encoding")
            return False

        try:
            from google.cloud import speech
            self.thread = threading.Thread(target=self._run, args=(speech,), daemon=True)
            self.thread.start()
            return True
        except Exception as e:
            print(f"STT Error: {e}")
            return False

    def add_audio(self, chunk):
        if not chunk:
            return True
        if len(chunk) > GOOGLE_STT_STREAM_MAX_CHUNK_BYTES:
            self.send_threadsafe({
                "type": "sttError",
                "practiceId": self.practice_id,
                "message": "Audio chunk is too large."
            })
            return False

        try:
            self.audio_queue.put_nowait(chunk)
            return True
        except queue.Full:
            self.send_threadsafe({
                "type": "sttError",
                "practiceId": self.practice_id,
                "message": "Audio queue is full. Reduce chunk rate or reconnect."
            })
            return False

    def stop(self):
        if self.audio_ended_at is None:
            self.audio_ended_at = time.monotonic()
        self.closed.set()
        try:
            self.audio_queue.put_nowait(None)
        except queue.Full:
            pass

    def _run(self, speech):
        try:
            # [STEP-B] 싱글톤 재사용 — 매 세션마다 SpeechClient() 신규 생성 제거
            client = get_stt_client()
            if not client or client is False:
                # 싱글톤 생성 실패 시 세션별 생성으로 폴백
                client = speech.SpeechClient()
            encoding = get_google_stream_audio_encoding(speech, self.audio_encoding)

            # WEBM_OPUS는 헤더에 샘플 레이트가 있으므로 강제로 지정하면 에러가 납니다.
            # [STT-REUSE] enable_word_time_offsets: is_final 단어별 타임스탬프 확보 →
            #   사후 /analyze에서 배치 STT를 생략하고 이 결과를 재사용하기 위함.
            if not requires_sample_rate(self.audio_encoding):
                config = speech.RecognitionConfig(
                    encoding=encoding,
                    language_code=GOOGLE_STT_LANGUAGE_CODE,
                    enable_word_time_offsets=True,
                )
            else:
                config = speech.RecognitionConfig(
                    encoding=encoding,
                    sample_rate_hertz=self.sample_rate_hertz,
                    language_code=GOOGLE_STT_LANGUAGE_CODE,
                    enable_word_time_offsets=True,
                )

            streaming_config = speech.StreamingRecognitionConfig(config=config, interim_results=GOOGLE_STT_INTERIM_RESULTS)

            def requests():
                while not self.closed.is_set():
                    if self.elapsed_seconds() >= GOOGLE_STT_STREAM_MAX_SECONDS:
                        self.audio_ended_at = time.monotonic()
                        self.closed.set()
                        self.send_threadsafe({
                            "type": "restartRequired",
                            "practiceId": self.practice_id,
                            "reason": "STREAM_TIME_LIMIT",
                            "message": "Streaming STT time limit reached. Reconnect to continue."
                        })
                        break

                    try:
                        chunk = self.audio_queue.get(timeout=0.5)
                    except queue.Empty:
                        continue

                    if chunk is None:
                        self.audio_ended_at = time.monotonic()
                        break
                    yield speech.StreamingRecognizeRequest(audio_content=chunk)

            responses = client.streaming_recognize(streaming_config, requests=requests())
            print(f"[Python] STT Stream started for practice {self.practice_id}. Waiting for responses...", flush=True)

            for response in responses:
                if self.closed.is_set(): break
                if not response.results:
                    continue

                for result in response.results:
                    if not result.alternatives: continue
                    alt = result.alternatives[0]

                    # 매우 중요한 로그: 실제 인식된 텍스트
                    print(f"[STT LIVE] Practice {self.practice_id} | Transcript: {alt.transcript} | Final: {getattr(result, 'is_final', False)}", flush=True)

                    is_final = getattr(result, "is_final", False)

                    # [STT-REUSE] is_final 결과에서만 단어별 타임스탬프를 수집.
                    #   partial은 잠정 결과라 타임스탬프가 불안정하므로 제외.
                    if is_final:
                        self._collect_final_words(alt)

                    msg = self.build_highlight_message(
                        alt.transcript,
                        is_final,
                        getattr(alt, "confidence", 0.0)
                    )
                    self.send_threadsafe(msg)
        except Exception as e:
            print(f"[Python ERROR] STT Run Error in thread: {e}", flush=True)
            import traceback
            traceback.print_exc()
            if not self.closed.is_set():
                self.send_threadsafe({
                    "type": "sttError",
                    "practiceId": self.practice_id,
                    "message": "Streaming STT failed."
                })
            self.closed.set()

    @staticmethod
    def _duration_to_millis(duration):
        """google protobuf Duration → 밀리초(int). 없으면 None."""
        if duration is None:
            return None
        if hasattr(duration, "total_seconds"):
            return int(duration.total_seconds() * 1000)
        seconds = getattr(duration, "seconds", 0) or 0
        nanos = getattr(duration, "nanos", 0) or 0
        return int(seconds * 1000 + nanos / 1_000_000)

    def _collect_final_words(self, alt):
        """[STT-REUSE] is_final alternative의 단어별 타임스탬프를 누적 저장.

        배치 STT(stt_service.extract_words_from_response)와 동일한 형태로 맞춘다:
          {word, startMs, endMs, confidence, isFinal}
        - confidence: per-word confidence가 0이면 alt.confidence, 그것도 0이면 0.9로
          폴백. voice_service.MIN_STT_CONFIDENCE(0.1) 필터를 통과시키기 위함.
          (스트리밍은 per-word confidence를 0으로 주는 경우가 흔하다.)
        - start_time/end_time이 없는 단어는 타임스탬프 신뢰성이 없으므로 건너뛴다.
        """
        try:
            alt_conf = float(getattr(alt, "confidence", 0.0) or 0.0)
            words = getattr(alt, "words", None) or []
            for w in words:
                start_ms = self._duration_to_millis(getattr(w, "start_time", None))
                end_ms = self._duration_to_millis(getattr(w, "end_time", None))
                if start_ms is None or end_ms is None:
                    continue
                conf = float(getattr(w, "confidence", 0.0) or 0.0)
                if conf <= 0.0:
                    conf = alt_conf if alt_conf > 0.0 else 0.9
                self.final_stt_words.append({
                    "word": getattr(w, "word", "") or "",
                    # [STT-REUSE] 세그먼트 시작 오프셋을 더해 멀티세그먼트 타임라인 정합.
                    "startMs": start_ms + self.base_time_offset_ms,
                    "endMs": end_ms + self.base_time_offset_ms,
                    "confidence": conf,
                    "isFinal": True,
                })
        except Exception as e:
            # 수집 실패는 치명적이지 않음 — /analyze가 배치 STT로 폴백.
            print(f"[STT-REUSE] 단어 타임스탬프 수집 실패(무시): {e}", flush=True)

    def elapsed_seconds(self):
        return time.monotonic() - self.started_at

    def segment_duration_ms(self):
        """[STT-REUSE] 이 세그먼트가 소비한 오디오 길이(ms) 근사.

        실시간 스트리밍이므로 (오디오 종료 시각 - 세션 시작 시각) ≈ 오디오 길이.
        다음 재접속 세그먼트의 base_time_offset_ms 누적에 사용한다.
        """
        end = self.audio_ended_at if self.audio_ended_at is not None else time.monotonic()
        return max(0, int((end - self.started_at) * 1000))

    def is_closed(self):
        return self.closed.is_set()

    def send_threadsafe(self, message):
        asyncio.run_coroutine_threadsafe(self.websocket.send_json(message), self.loop)

    # ── [STEP-C] 실시간 하이라이트 메시지 생성 ────────────────────────────────
    # is_final: DTW 정렬 결과를 커밋(word_results_by_index 갱신 + confirmed 갱신).
    # partial : 단조 상한(peek)만 갱신, 커밋 없음 → 시각적 튐 방지.
    def build_highlight_message(self, transcript, is_final, confidence):
        if is_final:
            # is_final → 전체 DTW 정렬 + 앵커 갱신 + 커밋
            current_index = self._align_and_commit(transcript)
            self.final_transcript = f"{self.final_transcript} {transcript}".strip()
        else:
            # partial → DTW peek (커밋 없음), 단조 상한만 갱신
            current_index = self._peek_current_index(transcript)

        matched_word = find_script_word_by_global_index(self.script_words, current_index)
        return {
            "type": "highlight",
            "practiceId": self.practice_id,
            "transcript": transcript,
            "currentGlobalWordIndex": current_index if current_index >= 0 else -1,
            "matchedWord": matched_word.get("text") if matched_word else None,
            "isFinal": bool(is_final),
            "confidence": float(confidence or 0.0),
            "wordResults": list(self.word_results_by_index.values()),
        }

    def _get_search_start(self):
        """앵커 또는 confirmed 중 큰 값을 탐색 시작점으로 반환."""
        return max(
            self.confirmed_global_word_index + 1,
            self.anchor_global_index + 1,
            0,
        )

    def _align_and_commit(self, transcript: str) -> int:
        """[STEP-C] DTW 정렬 결과를 커밋.

        1. transcript 토큰 → DTW 정렬 → word_results_by_index 갱신
        2. 앵커 단어(4자+) 발견 시 anchor_global_index 갱신
        3. confirmed_global_word_index 갱신 (단조 증가)
        Returns: 현재 커밋 후 global word index
        """
        tokens = [normalize_match_text(t) for t in transcript.split() if normalize_match_text(t)]
        if not tokens or not self.script_words:
            return self.confirmed_global_word_index

        search_start = self._get_search_start()
        alignments = monotonic_dtw_align(tokens, self.script_words, start_idx=search_start)

        if not alignments:
            return self.confirmed_global_word_index

        original_tokens = [t for t in transcript.split() if normalize_match_text(t)]
        last_committed_ref = self.confirmed_global_word_index

        # 갭 처리: 이전 커밋 이후 건너뛴 단어 마킹
        prev_ref = search_start - 1

        for query_idx, ref_idx, score in alignments:
            script_word = self.script_words[ref_idx]
            global_idx = script_word.get("globalWordIndex", ref_idx)

            # 건너뛴 단어 처리
            for skipped in range(prev_ref + 1, global_idx):
                if skipped not in self.word_results_by_index:
                    sw = self.script_words[skipped] if skipped < len(self.script_words) else {}
                    self.word_results_by_index[skipped] = {
                        "globalWordIndex": skipped,
                        "expectedWord": sw.get("text") or "",
                        "spokenWord": "(건너뜀)",
                        "matchScore": 0.0,
                        "isCorrect": False,
                    }

            spoken = original_tokens[query_idx] if query_idx < len(original_tokens) else ""
            self.word_results_by_index[global_idx] = {
                "globalWordIndex": global_idx,
                "expectedWord": script_word.get("text") or "",
                "spokenWord": spoken,
                "matchScore": round(float(score), 3),
                "isCorrect": score >= REALTIME_CORRECT_THRESHOLD,
            }

            # 앵커 갱신: 4자 이상 고유 단어 → 절대 위치 고정
            sw_text = script_word.get("text") or ""
            if len(sw_text) >= ANCHOR_MIN_LEN and score >= MATCH_THRESHOLD:
                self.anchor_global_index = max(self.anchor_global_index, global_idx)

            prev_ref = global_idx
            last_committed_ref = global_idx

        self.confirmed_global_word_index = max(self.confirmed_global_word_index, last_committed_ref)
        self.last_partial_global_word_index = self.confirmed_global_word_index
        return self.confirmed_global_word_index

    def _peek_current_index(self, transcript: str) -> int:
        """[STEP-C] Partial transcript용 — DTW peek (커밋 없음).

        단조 상한만 갱신: partial 결과는 이전 confirmed보다 뒤로 가지 않음.
        """
        tokens = [normalize_match_text(t) for t in transcript.split() if normalize_match_text(t)]
        if not tokens or not self.script_words:
            return max(self.confirmed_global_word_index, self.last_partial_global_word_index)

        search_start = self._get_search_start()
        # partial은 짧은 윈도우로 빠르게 처리
        alignments = monotonic_dtw_align(tokens[-6:], self.script_words, start_idx=search_start, max_window=20)

        if not alignments:
            return max(self.confirmed_global_word_index, self.last_partial_global_word_index)

        # 마지막 매칭 ref_idx를 현재 위치로 사용
        _, last_ref_idx, _ = alignments[-1]
        sw = self.script_words[last_ref_idx]
        peek_idx = sw.get("globalWordIndex", last_ref_idx)

        # 단조 상한: confirmed 이상으로만 갱신
        current = max(peek_idx, self.confirmed_global_word_index, self.last_partial_global_word_index)
        self.last_partial_global_word_index = current
        return current

    # ── 하위 호환 래퍼 (기존 build_word_results / estimate_current_global_word_index) ──
    def build_word_results(self, transcript):
        """[STEP-C] _align_and_commit 위임 — 기존 호출부 호환."""
        # is_final 시 build_highlight_message가 이미 _align_and_commit 호출함.
        # 별도 호출 시에는 현재 결과만 반환 (중복 커밋 방지).
        return list(self.word_results_by_index.values())

    def estimate_current_global_word_index(self, transcript):
        """[STEP-C] _peek_current_index 위임 — 기존 호출부 호환."""
        return self._peek_current_index(transcript)


def normalize_script_words(script_words):
    normalized_words = []
    for index, word in enumerate(script_words or []):
        if not isinstance(word, dict):
            continue

        global_word_index = word.get("globalWordIndex", word.get("index", index))
        normalized_words.append({
            "scriptWordId": word.get("scriptWordId"),
            "globalWordIndex": global_word_index,
            "sentenceIndex": word.get("sentenceIndex"),
            "text": word.get("text") or word.get("word") or "",
            "normalizedText": word.get("normalizedText"),
        })

    return sorted(normalized_words, key=lambda word: word.get("globalWordIndex", 0))


def find_script_word_by_global_index(script_words, global_word_index):
    for word in script_words:
        if word.get("globalWordIndex") == global_word_index:
            return word

    return None


def find_realtime_token_match(script_words, token, search_start):
    match = find_realtime_token_feedback(script_words, token, search_start)
    if not match:
        return None

    script_word, score = match
    return script_word if score >= MATCH_THRESHOLD else None


def find_realtime_token_feedback(script_words, token, search_start):
    best_word = None
    best_score = -1.0
    
    # [설계 반영] 탐색 범위를 앞뒤로 확장 (과거 2단어 ~ 미래 10단어)
    # 지연된 인식(STT Lag)이나 짧은 건너뛰기를 모두 수용할 수 있는 범위입니다.
    start_idx = max(search_start - 2, 0)
    end_idx = min(search_start + 11, len(script_words))
    
    for script_word in script_words[start_idx:end_idx]:
        script_text = normalize_match_text(script_word.get("normalizedText") or script_word.get("text"))
        if not script_text:
            continue

        # 기본 유사도 점수 계산
        base_score = calculate_realtime_similarity(script_text, token)
        
        # [설계 반영] 거리 기반 페널티 부여
        # 현재 예상 위치(search_start)에서 멀어질수록 감점하여, 
        # 비슷한 단어가 여러 개일 경우 가장 가까운 단어를 우선 매칭합니다.
        distance = abs(script_word.get("globalWordIndex", 0) - search_start)
        distance_penalty = distance * 0.04
        
        adjusted_score = base_score - distance_penalty
        
        if adjusted_score > best_score:
            best_score = adjusted_score
            best_word = script_word

    if not best_word or best_score < REALTIME_MIN_SCORE: # 최소 일치 기준 미달 시 무시
        return None

    # [STEP 7-A] 이중 패널티 버그 수정:
    # adjusted_score = base_score - distance_penalty 로 비교했고 best_word는 그 결과.
    # 반환 시 다시 distance_penalty를 더하면 패널티가 상쇄되어 원본 base_score에 근접.
    # 올바른 반환값은 adjusted_score (이미 패널티 적용된 값) 그대로.
    # 단, 최소 0을 보장.
    return best_word, max(best_score, 0.0)


def calculate_realtime_similarity(script_text, spoken_text):
    if not script_text or not spoken_text:
        return 0.0
    if script_text == spoken_text:
        return 1.0
    if len(script_text) > 2 and (script_text in spoken_text or spoken_text in script_text):
        return 0.86

    return SequenceMatcher(None, script_text, spoken_text).ratio()


def validate_practice_websocket_token(token, practice_id):
    if not JWT_SECRET or not token:
        return None

    try:
        header_b64, payload_b64, signature_b64 = token.split(".")
        signing_input = f"{header_b64}.{payload_b64}".encode("utf-8")
        expected_signature = hmac.new(
            JWT_SECRET.encode("utf-8"),
            signing_input,
            hashlib.sha256
        ).digest()
        actual_signature = decode_base64url(signature_b64)
        if not hmac.compare_digest(expected_signature, actual_signature):
            return None

        header = json.loads(decode_base64url(header_b64))
        if header.get("alg") != "HS256":
            return None

        payload = json.loads(decode_base64url(payload_b64))
        if payload.get("type") != "ws_practice":
            return None
        if int(payload.get("practiceId", -1)) != int(practice_id):
            return None

        exp = payload.get("exp")
        if exp is None or float(exp) < time.time():
            return None

        return payload
    except Exception:
        return None


def decode_base64url(value):
    padded = value + "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(padded.encode("utf-8"))


def extract_s3_key_from_url(audio_url, bucket_name):
    from urllib.parse import unquote, urlparse

    parsed = urlparse(audio_url)
    key = unquote(parsed.path.lstrip("/"))
    if bucket_name and key.startswith(f"{bucket_name}/"):
        key = key[len(bucket_name) + 1:]

    return key


# --- Goal Similarity Score (STEP 5-D) ---

def calculate_goal_similarity_score(features: dict, target_metrics: dict) -> float:
    """규칙 기반 목표 스타일 유사도 점수 산출 (0~100).

    가중치: WPM 40% | Pitch 30% | Pause 20% | Intensity 10%
    각 항목 편차율을 §7 Guard 범위로 정규화하여 개별 점수 산출.
    targetMetrics가 없거나 항목이 null이면 해당 항목을 건너뛰고 나머지 가중치로 정규화.
    """
    weights = {
        "wpm":       0.40,
        "pitch":     0.30,
        "pause":     0.20,
        "intensity": 0.10,
    }

    def item_score(actual, target, guard_ratio):
        """편차율 → 개별 항목 점수 (0~100). guard_ratio 이상 편차 시 0점."""
        if actual is None or target is None or target == 0:
            return None
        deviation = abs(actual - target) / abs(target)
        return max(0.0, 1.0 - deviation / guard_ratio) * 100.0

    WPM_GUARD    = 0.20  # §7
    PITCH_GUARD  = 0.20  # §7
    DB_GUARD     = 6.0   # §7 절댓값 dB
    PAUSE_GUARD  = 17.0  # §7 pause 범위 폭(25-8)

    scores = {}
    scores["wpm"]   = item_score(features.get("avgWpm"),       target_metrics.get("targetWpm"),   WPM_GUARD)
    scores["pitch"] = item_score(features.get("avgPitch"),     target_metrics.get("targetPitch"), PITCH_GUARD)

    # Pause: pauseRatio (0~1) vs targetPauseRatio (%) 단위 통일 필요
    actual_pause_pct  = (features.get("pauseRatio") or 0.0) * 100.0
    target_pause_pct  = target_metrics.get("targetPauseRatio")
    if target_pause_pct is not None and PAUSE_GUARD > 0:
        scores["pause"] = max(0.0, 1.0 - abs(actual_pause_pct - target_pause_pct) / PAUSE_GUARD) * 100.0
    else:
        scores["pause"] = None

    # Intensity: dBFS 절댓값 편차 vs guard dB
    actual_db  = features.get("avgIntensity")
    target_db  = target_metrics.get("targetIntensity")
    if actual_db is not None and target_db is not None and DB_GUARD > 0:
        scores["intensity"] = max(0.0, 1.0 - abs(actual_db - target_db) / DB_GUARD) * 100.0
    else:
        scores["intensity"] = None

    # 가중 평균 (null 항목은 제외 후 가중치 재정규화)
    total_weight = sum(weights[k] for k, v in scores.items() if v is not None)
    if total_weight == 0:
        return 0.0

    weighted_sum = sum(weights[k] * v for k, v in scores.items() if v is not None)
    raw = weighted_sum / total_weight
    # [리뷰] 부동소수점 오차 방어 — 명시적 0~100 클램핑
    result = round(clamp(raw, 0.0, 100.0) * 10) / 10

    print(
        f"[Python STEP5-D] goalSimilarityScore={result} "
        f"| wpm={scores['wpm']}, pitch={scores['pitch']}, pause={scores['pause']}, intensity={scores['intensity']}",
        flush=True
    )
    return result


# --- Helpers ---

def _probe_duration_seconds(path):
    """ffprobe로 오디오 길이를 빠르게 선조회 (헤더만 읽음, ~수십 ms).

    STT 라우팅(60초 초과 → Gemini 폴백) 판단에 사용한다.
    static_ffmpeg.add_paths()로 ffprobe가 PATH에 등록되어 있다.
    실패 시 None 반환 → transcribe_audio가 파일 크기/런타임 폴백으로 처리.
    """
    try:
        import subprocess
        out = subprocess.run(
            ["ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", path],
            capture_output=True, text=True, timeout=10,
        )
        info = json.loads(out.stdout or "{}")
        dur = info.get("format", {}).get("duration")
        return float(dur) if dur else None
    except Exception as e:
        print(f"[Python] duration 선조회 실패(무시): {e}", flush=True)
        return None


# --- Endpoints ---

@router.post("/analyze")
@traceable(run_type="chain", name="run_analysis")
async def run_analysis(req: AnalyzeRequest):
    # audioUrl이 S3 URL이면 임시 파일로 다운로드, 로컬 경로면 그대로 사용
    audio_path = req.audioUrl
    is_temp_audio = False

    if audio_path.startswith("http://") or audio_path.startswith("https://"):
        # S3 URL → boto3로 임시 파일 다운로드 (퍼블릭 액세스 불필요, credentials 사용)
        from app.services.s3_service import get_s3_client
        from app.core.config import S3_BUCKET_NAME
        s3_key = extract_s3_key_from_url(audio_path, S3_BUCKET_NAME)
        suffix = os.path.splitext(s3_key)[-1] or ".webm"
        tmp_file = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
        tmp_file.close()
        try:
            get_s3_client().download_file(S3_BUCKET_NAME, s3_key, tmp_file.name)
        except Exception as e:
            os.remove(tmp_file.name)
            raise HTTPException(status_code=502, detail=f"Failed to download audio from S3: {e}")
        audio_path = tmp_file.name
        is_temp_audio = True
    elif not os.path.isabs(audio_path):
        audio_path = os.path.join(UPLOAD_ROOT, audio_path.replace("uploads/", ""))

    print(f"[Python] Starting analysis for file: {audio_path}")

    # [STEP 4-C] targetMetrics 수신 확인 로그 (STEP 5 규칙 평가에서 실제 사용)
    if req.targetMetrics:
        tm = req.targetMetrics
        print(
            f"[Python STEP4] targetMetrics 수신 완료 — "
            f"WPM={tm.targetWpm}, Pitch={tm.targetPitch}, "
            f"Intensity={tm.targetIntensity}, ZCR={tm.targetZcr}, Pause={tm.targetPauseRatio}",
            flush=True
        )
    else:
        print("[Python STEP4] targetMetrics 없음 — styleType 미설정 또는 베이스라인 부재", flush=True)

    if not os.path.exists(audio_path):
        print(f"[Python ERROR] Audio file not found at: {audio_path}")
        raise HTTPException(status_code=404, detail=f"Audio file not found: {audio_path}")

    try:
        # [STT-REUSE] 발표 연습 중 WebSocket으로 이미 수행한 실시간 STT 결과가
        #   신뢰 가능하게 캐시되어 있으면 배치 STT(~30초)를 생략하고 재사용한다.
        #   캐시 미스/재접속(비신뢰) 시 cached_stt_words=None → 아래에서 배치 STT 수행.
        cached_stt_words = pop_trusted_streaming_stt_words(req.practiceId)

        # [성능] 음성 특징 분석(librosa, CPU bound)과 STT(network/Gemini, IO bound)는
        # 서로 독립적이므로 동시 실행한다. 또한 run_analysis는 async 엔드포인트이므로
        # 블로킹 함수를 asyncio.to_thread로 오프로드해 이벤트 루프 차단을 방지한다.
        # STT 라우팅(60초 초과 시 Gemini 폴백)에 필요한 duration은 ffprobe로 선조회.
        if cached_stt_words is not None:
            # STT 재사용: 음성 특징 분석만 수행하면 된다.
            features = await asyncio.to_thread(analyze_voice_features, audio_path, gender=req.gender)
            stt_words = cached_stt_words
        else:
            duration_hint = await asyncio.to_thread(_probe_duration_seconds, audio_path)

            # 1. 음성 특징 분석 + STT 동시 수행
            # [STEP 1] gender 파라미터 전달로 Pitch 성별 필터 적용
            # [STEP 1] Gemini 폴백이 적용된 transcribe_audio 사용
            features, stt_words = await asyncio.gather(
                asyncio.to_thread(analyze_voice_features, audio_path, gender=req.gender),
                asyncio.to_thread(transcribe_audio, audio_path, duration_hint),
            )

        if not features:
            raise HTTPException(status_code=500, detail="Voice feature analysis failed")

        # 2. WPM 재계산 기준 duration은 분석 결과의 정확한 값을 사용
        duration = features.get("durationSec")

        if stt_words is None:
            print("[Python WARN] STT process failed. Continuing analysis with skipped word results.")
            stt_words = []

        # [STEP 1] STT 텍스트로 WPM 재계산 (음절/분 — 분석 스크립트 기준)
        # onset 기반 fallback을 STT 결과로 덮어씀
        if stt_words and duration and duration > 0:
            full_stt_text = " ".join(w.get("word", "") for w in stt_words if w.get("word"))
            syllable_count = len(full_stt_text.replace(" ", ""))
            if syllable_count > 0:
                features["avgWpm"] = float(syllable_count / (duration / 60))
                print(
                    f"[Python] WPM 재계산 완료 (음절/분): {features['avgWpm']:.1f}"
                    f" | syllables={syllable_count}, duration={duration:.2f}s",
                    flush=True
                )

        # 3. 결과 조립
        # [STEP 4-C] target_metrics를 dict로 변환하여 하위 함수에 전달 준비
        # STEP 5에서 build_sentence_results가 실제 규칙 평가에 사용
        target_metrics_dict = req.targetMetrics.model_dump() if req.targetMetrics else {}

        word_results = build_aligned_word_results(req.scriptWords, stt_words) if stt_words else build_skipped_word_results(req.scriptWords)
        sentence_results = build_sentence_results(req.scriptWords, word_results, features, target_metrics_dict)
        issue_results = build_issue_results(sentence_results)

        # [STEP 5-D] 규칙 기반 goalSimilarityScore 산출 (Gemini 환각 제거)
        goal_similarity_score = calculate_goal_similarity_score(features, target_metrics_dict)
        # [STEP 10] 문장별 결과·우선순위 이슈·실제 전사를 LLM 피드백에 함께 전달 →
        #   "일반론" 대신 특정 문장을 근거로 한 구체적 피드백 생성.
        transcript_text = " ".join(w.get("word", "") for w in stt_words if w.get("word")) if stt_words else ""
        # [STAGE 2] 말버릇(필러)/반복어 탐지 → LLM 프롬프트에 주입(표시 지표는 3단계에서).
        disfluency = detect_disfluencies(stt_words)
        # [성능] Gemini 피드백 호출도 블로킹이므로 스레드로 오프로드 (이벤트 루프 보호)
        ai_feedback = await asyncio.to_thread(
            generate_ai_feedback, features, req, goal_similarity_score,
            sentence_results, issue_results, transcript_text, disfluency,
        )

        print(f"[Python] Analysis successful for practice {req.practiceId}")
        return {
            **features,
            **ai_feedback,
            "wordResults": word_results,
            "sentenceResults": sentence_results,
            "issues": issue_results
        }
    except HTTPException:
        raise
    except Exception as e:
        print(f"[Python ERROR] Unexpected error during analysis: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        # S3에서 다운로드한 임시 오디오 파일 삭제
        if is_temp_audio and os.path.exists(audio_path):
            os.remove(audio_path)

@router.post("/scripts/mark")
async def mark_script(req: MarkRequest):
    marked = await mark_script_ai(req.content)
    return {"markedContent": marked}

@router.post("/scripts/generate")
async def generate_script(req: GenerateScriptRequest):
    """[STEP-A] SSE 스트리밍 대본 초안 생성.

    Accept 헤더 없이도 항상 SSE 스트림을 반환한다.
    각 이벤트: data: {"chunk": "..."}\n\n
    종료 이벤트: data: [DONE]\n\n
    """
    return StreamingResponse(
        generate_script_ai_stream(req),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",  # nginx proxy buffering 비활성화
        },
    )

@router.post("/scripts/update")
async def update_script(req: UpdateScriptRequest):
    """[STEP-A] SSE 스트리밍 대본 최적화.

    각 이벤트: data: {"chunk": "..."}\n\n
    종료 이벤트: data: [DONE]\n\n
    """
    return StreamingResponse(
        update_script_ai_stream(req),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )

@router.post("/ppt/convert")
async def convert_ppt(req: ConvertPptRequest):
    return await asyncio.to_thread(convert_ppt_sync, req)


def convert_ppt_sync(req: ConvertPptRequest):
    # 로컬 PPTX 경로 (Docker 경로 → 호스트 경로 변환)
    ppt_path = ensure_within_upload_root(req.pptPath, must_exist=True)

    # S3 키 prefix 생성: pptPath에서 /uploads/ 이후 부모 경로 추출
    # 예) /app/uploads/ppt/10/attempts/uuid/file.pptx → ppt/10/attempts/uuid
    normalized_ppt = req.pptPath.replace("\\", "/")
    idx = normalized_ppt.find("/uploads/")
    if idx >= 0:
        relative = normalized_ppt[idx + len("/uploads/"):]        # ppt/10/attempts/uuid/file.pptx
        s3_parent = "/".join(relative.split("/")[:-1])             # ppt/10/attempts/uuid
    else:
        import uuid as _uuid
        s3_parent = f"ppt/unknown/{_uuid.uuid4()}"

    s3_slides_prefix = f"{s3_parent}/slides"

    # PPT 파일 S3 업로드 (PPTX → S3 원본 보관)
    ext = os.path.splitext(ppt_path)[-1].lower()
    content_type_map = {
        ".pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        ".ppt":  "application/vnd.ms-powerpoint",
        ".pdf":  "application/pdf",
    }
    ppt_content_type = content_type_map.get(ext, "application/octet-stream")
    ppt_s3_key = f"{s3_parent}/file{ext}"
    ppt_s3_url = upload_to_s3(ppt_path, ppt_s3_key, content_type=ppt_content_type)

    # 슬라이드 변환 및 S3 업로드
    if ppt_path.lower().endswith(".pdf"):
        slides = render_pdf_to_images(ppt_path, s3_slides_prefix)
    else:
        pdf_path, temp_dir = convert_ppt_to_pdf(ppt_path)
        try:
            slides = render_pdf_to_images(pdf_path, s3_slides_prefix)
        finally:
            shutil.rmtree(temp_dir, ignore_errors=True)

    return {
        "sourcePptUrl": ppt_s3_url,
        "totalSlides": len(slides),
        "slides": slides,   # imageUrl이 이미 S3 URL
    }

@router.websocket("/ws/practice/{practice_id}")
async def practice_websocket(websocket: WebSocket, practice_id: int):
    token = websocket.query_params.get("token")
    claims = validate_practice_websocket_token(token, practice_id)
    if not claims:
        await websocket.close(code=1008)
        print(f"[WS] Unauthorized connection rejected: practice_id={practice_id}")
        return

    await websocket.accept()
    print(f"[WS] Client connected: practice_id={practice_id}, user_id={claims.get('sub')}")
    stt_session = None
    loop = asyncio.get_running_loop()
    # [STT-REUSE] 재접속(5분 초과)으로 세션이 여러 번 생성돼도 단어를 누적하고,
    #   세그먼트별 시작 오프셋을 더해 단일 타임라인으로 정합한다.
    accumulated_stt_words = []
    accumulated_offset_ms = 0

    def flush_session_words(sess):
        """현재 세그먼트의 (오프셋 적용된) 단어를 누적하고 다음 오프셋을 갱신."""
        nonlocal accumulated_offset_ms
        if sess is None:
            return
        accumulated_stt_words.extend(sess.final_stt_words)
        accumulated_offset_ms += sess.segment_duration_ms()

    try:
        while True:
            msg = await websocket.receive()
            # print(f"DEBUG: Received message type: {msg.get('type')}", flush=True)

            if msg.get("type") == "websocket.disconnect":
                print(f"[WS] Client disconnected: practice_id={practice_id}", flush=True)
                break

            if msg.get("text"):
                data = json.loads(msg["text"])
                print(f"[WS] Received text: {data.get('type')}", flush=True)

                if data.get("type") == "init":
                    print(f"[WS] Initializing STT session for {practice_id}", flush=True)
                    # [STT-REUSE] 재접속 init: 이전 세그먼트의 단어를 먼저 누적·정합하고
                    #   다음 세그먼트의 시작 오프셋을 확정한다.
                    if stt_session is not None:
                        stt_session.stop()
                        flush_session_words(stt_session)
                        stt_session = None
                    if not GOOGLE_STT_ENABLED:
                        print("[WS] STT is disabled", flush=True)
                        await websocket.send_json({
                            "type": "sttDisabled",
                            "practiceId": practice_id,
                            "message": "Streaming STT is disabled."
                        })
                    else:
                        audio_encoding = data.get("audioEncoding")
                        sample_rate_hertz = data.get("sampleRateHertz")
                        stt_session = GoogleStreamingSttSession(
                            practice_id,
                            data.get("scriptWords", []),
                            websocket,
                            loop,
                            audio_encoding=audio_encoding,
                            sample_rate_hertz=sample_rate_hertz
                        )
                        # [STT-REUSE] 이 세그먼트의 단어 타임스탬프에 더할 누적 시작 오프셋.
                        #   첫 세그먼트는 0, 재접속 세그먼트는 이전 세그먼트 길이의 합.
                        stt_session.base_time_offset_ms = accumulated_offset_ms
                        # [STEP 7-B] 재접속 시 클라이언트가 마지막 confirmed index를 전달하면 복원.
                        # 첫 접속이면 -1(기본값) 유지, 재접속이면 해당 위치부터 이어서 진행.
                        resumed_index = data.get("confirmedGlobalWordIndex")
                        if resumed_index is not None and int(resumed_index) >= 0:
                            stt_session.confirmed_global_word_index = int(resumed_index)
                            stt_session.last_partial_global_word_index = int(resumed_index)
                            stt_session.is_resumed = True
                            print(
                                f"[WS STEP7] 재접속 confirmed index 복원: {resumed_index}, "
                                f"offset={accumulated_offset_ms}ms",
                                flush=True
                            )
                        if not stt_session.start():
                            print("[WS] Failed to start Google STT session", flush=True)
                            await websocket.send_json({
                                "type": "sttError",
                                "practiceId": practice_id,
                                "message": "Streaming STT session failed to start.",
                                "supportedAudioEncodings": GOOGLE_STT_STREAM_ALLOWED_ENCODINGS
                            })
                            stt_session = None
                        else:
                            print("[WS] Google STT session started successfully", flush=True)
                            await websocket.send_json({
                                "type": "ready",
                                "practiceId": practice_id,
                                "scriptWordCount": len(stt_session.script_words),
                                "audioEncoding": stt_session.audio_encoding,
                                "sampleRateHertz": stt_session.sample_rate_hertz,
                                "supportedAudioEncodings": GOOGLE_STT_STREAM_ALLOWED_ENCODINGS
                            })
                elif data.get("type") == "stop":
                    print("[WS] Received stop message", flush=True)
                    break

            if msg.get("bytes"):
                audio_data = msg["bytes"]
                # 0.5초마다 데이터가 오는지 확인할 수 있는 최소한의 로그
                # print(".", end="", flush=True)
                if stt_session:
                    if not stt_session.add_audio(audio_data):
                        print(f"\n[WS] Failed to add audio for {practice_id}", flush=True)
                        stt_session.stop()
                        stt_session = None
                else:
                    print(f"\n[WS] Received bytes but no active session for {practice_id}", flush=True)
    except WebSocketDisconnect:
        print(f"[WS] WebSocketDisconnect: practice_id={practice_id}")
    except Exception as e:
        print(f"[WS] Error in websocket loop: {e}")
    finally:
        if stt_session:
            print("[WS] Stopping STT session")
            stt_session.stop()
            flush_session_words(stt_session)
        # [STT-REUSE] 연습 종료 시 모든 세그먼트를 누적·정합한 단어를 캐시에 저장.
        #   직후 /analyze가 이 결과를 재사용해 배치 STT(~30초)를 생략한다.
        #   재접속이 있었어도 세그먼트 오프셋을 정합했으므로 신뢰 가능.
        store_streaming_stt_words(practice_id, accumulated_stt_words)

# ────────────────────────────────────────────────────────────
# [STEP 9] /feedback/summary — 기간별 종합 피드백 요약
# Java AiFeedbackService.processFeedbackAsync() → POST /feedback/summary
# ────────────────────────────────────────────────────────────

@router.post("/feedback/summary")
async def feedback_summary(req: FeedbackSummaryRequest):
    """기간별 피드백 종합 요약 엔드포인트.

    Java AiFeedbackService가 집계된 기간 평균 피처를 POST하면,
    STEP 5 규칙(§6/§7) + STEP 8 RAG Gemini 설명으로 응답.

    응답 형식: Java PythonFeedbackRes 스키마와 1:1 대응
    - mostSimilarStyle, styleDescription
    - positiveTitle, positiveDescription
    - improvementTitle, improvementDescription
    - guideSummary, guideNextStep
    - matchingRate (0~100 Integer)
    """
    print(
        f"[Python STEP9] /feedback/summary 수신 — "
        f"feedbackId={req.feedbackId}, "
        f"WPM={req.avgWpm:.1f}, Pitch={req.avgPitch:.1f}, "
        f"dB={req.avgIntensity:.1f}, ZCR={req.avgZcr:.4f}, "
        f"Pause={req.pauseRatio:.3f}, "
        f"기간={req.startDate}~{req.endDate}",
        flush=True
    )
    try:
        result = generate_feedback_summary(
            avg_wpm=req.avgWpm,
            avg_pitch=req.avgPitch,
            avg_intensity=req.avgIntensity,
            avg_zcr=req.avgZcr,
            pause_ratio=req.pauseRatio,
            start_date=req.startDate or "",
            end_date=req.endDate or "",
            feedback_id=req.feedbackId,
        )
        return result
    except Exception as e:
        print(f"[Python STEP9 ERROR] /feedback/summary 처리 실패: {e}", flush=True)
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/voice-analysis")
async def analyze_voice_api(
    voiceFile: UploadFile = File(...),
    gender: str = Form(None)   # [STEP 2] Java에서 user.getGender().name() 전달 ('MALE'/'FEMALE')
):
    """
    사용자 음색 분석 요청 API (베이스라인 산출)

    요청된 자유발화 녹음 파일을 분석해 개인 Baseline(WPM, Pitch) 값을 반환합니다.
    - [STEP 1] sr=16000, 10초 로드 기준
    - [STEP 2] STT로 음절/분 WPM 산출 + gender 기반 Pitch 필터 적용

    - Content-Type: multipart/form-data
    - Request Body: voiceFile (File, 필수), gender (str, 선택: 'MALE'/'FEMALE')
    - Response:
        - 성공 시 (200 OK): 분석 결과 및 상태 반환
        - 실패 시 (422 Unprocessable Entity): 목소리 미감지 시 에러 메시지 반환
        - 실패 시 (400 Bad Request): 데이터 부족 또는 예외 발생 시 에러 메시지 반환
    """
    temp_path = None
    try:
        suffix = Path(voiceFile.filename or "").suffix
        print(
            "[Python] Voice analysis upload received: "
            f"filename={voiceFile.filename}, content_type={voiceFile.content_type}, "
            f"suffix={suffix}, gender={gender}",
            flush=True
        )

        # 1. 안전한 임시 파일 생성
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as buffer:
            temp_path = buffer.name
            shutil.copyfileobj(voiceFile.file, buffer)

        temp_size = os.path.getsize(temp_path)
        print(
            "[Python] Voice analysis temp file saved: "
            f"path={temp_path}, size={temp_size} bytes",
            flush=True
        )

        # 2. [STEP 1] 1차 특징 추출 — sr=16000, 10초 로드, gender로 Pitch 필터 적용
        # onset fallback WPM은 STT 후 재계산 예정
        features = analyze_voice_features(temp_path, gender=gender, max_duration=10)

        if not features:
            print(
                "[Python ERROR] Voice feature analysis returned no result: "
                f"filename={voiceFile.filename}, content_type={voiceFile.content_type}, size={temp_size} bytes",
                flush=True
            )
            raise HTTPException(
                status_code=422,
                detail="목소리가 감지되지 않았습니다. 조용한 곳에서 다시 녹음해주세요."
            )

        # 3. [STEP 2] STT로 음절/분 WPM 재계산
        # 자유발화이므로 script 없이 STT 텍스트만 추출해 음절 수 산정
        duration = features.get("durationSec", 0)
        try:
            stt_words = transcribe_audio(temp_path, duration)
            if stt_words and duration > 0:
                full_stt_text = " ".join(w.get("word", "") for w in stt_words if w.get("word"))
                syllable_count = len(full_stt_text.replace(" ", ""))
                if syllable_count > 0:
                    features["avgWpm"] = float(syllable_count / (duration / 60))
                    print(
                        f"[Python] Baseline WPM 재계산 (음절/분): {features['avgWpm']:.1f}"
                        f" | syllables={syllable_count}, duration={duration:.2f}s",
                        flush=True
                    )
                else:
                    print("[Python WARN] STT 결과 음절 없음 — onset fallback WPM 유지", flush=True)
            else:
                print("[Python WARN] STT 결과 없음 — onset fallback WPM 유지", flush=True)
        except Exception as stt_err:
            # STT 실패는 치명적이지 않음 — onset fallback WPM으로 진행
            print(f"[Python WARN] Baseline STT 실패, fallback 유지: {stt_err}", flush=True)

        # 4. Spring이 baseline id를 관리하므로 Python은 분석값만 반환
        return {
            "avg_pitch": features.get("avgPitch", 0.0),
            "avg_wpm": features.get("avgWpm", 0.0),
            "avg_intensity": features.get("avgIntensity", 0.0),
            "avg_zcr": features.get("avgZcr", 0.0),
            "pause_ratio": features.get("pauseRatio", 0.0),
            "status": "COMPLETED"
        }
    except HTTPException:
        # 4. 예외 처리 방식: 422 에러가 400으로 덮어씌워지지 않도록 방지
        raise
    except Exception as err:
        print(
            "[Python ERROR] Voice analysis request failed: "
            f"filename={voiceFile.filename}, content_type={voiceFile.content_type}, error={repr(err)}",
            flush=True
        )
        traceback.print_exc()
        raise HTTPException(
            status_code=400,
            detail="분석을 위한 음성 데이터가 부족합니다. 세 문장을 모두 읽어주세요."
        ) from err
    finally:
        # 5. 사용한 임시 파일 정리 (서버 공간 확보 및 보안)
        if temp_path and os.path.exists(temp_path):
            os.remove(temp_path)
