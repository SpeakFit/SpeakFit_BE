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
from pathlib import Path
from difflib import SequenceMatcher
from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect, UploadFile, File

from app.schemas.models import (
    AnalyzeRequest, MarkRequest, GenerateScriptRequest,
    UpdateScriptRequest, ConvertPptRequest, ScriptWordPayload
)
from app.services.voice_service import (
    analyze_voice_features, build_aligned_word_results,
    build_sentence_results, build_issue_results
)
from app.services.stt_service import transcribe_audio
from app.services.ai_service import (
    generate_ai_feedback, generate_script_ai,
    update_script_ai, mark_script_ai
)
from app.services.ppt_service import (
    ensure_within_upload_root, convert_ppt_to_pdf, render_pdf_to_images
)
from app.utils.helpers import clamp, normalize_match_text
from app.core.config import (
    GOOGLE_STT_ENABLED, GOOGLE_STT_SAMPLE_RATE,
    GOOGLE_STT_LANGUAGE_CODE, GOOGLE_STT_ENCODING, GOOGLE_STT_INTERIM_RESULTS,
    GOOGLE_STT_STREAM_ALLOWED_ENCODINGS,
    GOOGLE_STT_STREAM_MAX_CHUNK_BYTES, GOOGLE_STT_STREAM_MAX_SECONDS,
    GOOGLE_STT_STREAM_QUEUE_SIZE,
    JWT_SECRET, UPLOAD_ROOT
)

router = APIRouter()

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
        self.closed.set()
        try:
            self.audio_queue.put_nowait(None)
        except queue.Full:
            pass

    def _run(self, speech):
        try:
            client = speech.SpeechClient()
            encoding = get_google_stream_audio_encoding(speech, self.audio_encoding)

            # WEBM_OPUS는 헤더에 샘플 레이트가 있으므로 강제로 지정하면 에러가 납니다.
            if not requires_sample_rate(self.audio_encoding):
                config = speech.RecognitionConfig(
                    encoding=encoding,
                    language_code=GOOGLE_STT_LANGUAGE_CODE,
                )
            else:
                config = speech.RecognitionConfig(
                    encoding=encoding,
                    sample_rate_hertz=self.sample_rate_hertz,
                    language_code=GOOGLE_STT_LANGUAGE_CODE,
                )

            streaming_config = speech.StreamingRecognitionConfig(config=config, interim_results=GOOGLE_STT_INTERIM_RESULTS)

            def requests():
                while not self.closed.is_set():
                    if self.elapsed_seconds() >= GOOGLE_STT_STREAM_MAX_SECONDS:
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

                    if chunk is None: break
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

                    msg = self.build_highlight_message(
                        alt.transcript,
                        getattr(result, "is_final", False),
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

    def elapsed_seconds(self):
        return time.monotonic() - self.started_at

    def is_closed(self):
        return self.closed.is_set()

    def send_threadsafe(self, message):
        asyncio.run_coroutine_threadsafe(self.websocket.send_json(message), self.loop)

    def build_highlight_message(self, transcript, is_final, confidence):
        current_index = self.estimate_current_global_word_index(transcript)
        word_results = self.build_word_results(transcript)
        if is_final:
            self.confirmed_global_word_index = max(self.confirmed_global_word_index, current_index)
            self.last_partial_global_word_index = self.confirmed_global_word_index
            self.final_transcript = f"{self.final_transcript} {transcript}".strip()
        else:
            current_index = max(current_index, self.confirmed_global_word_index, self.last_partial_global_word_index)
            self.last_partial_global_word_index = max(self.last_partial_global_word_index, current_index)

        matched_word = find_script_word_by_global_index(self.script_words, current_index)
        return {
            "type": "highlight",
            "practiceId": self.practice_id,
            "transcript": transcript,
            "currentGlobalWordIndex": current_index if current_index >= 0 else -1,
            "matchedWord": matched_word.get("text") if matched_word else None,
            "isFinal": bool(is_final),
            "confidence": float(confidence or 0.0),
            "wordResults": word_results,
        }

    def build_word_results(self, transcript):
        if not self.script_words or not transcript:
            return list(self.word_results_by_index.values())

        transcript_tokens = [
            (token, normalize_match_text(token))
            for token in transcript.split()
            if normalize_match_text(token)
        ]
        if not transcript_tokens:
            return list(self.word_results_by_index.values())

        # 현재 분석 중인 세그먼트 내에서의 검색 시작 위치
        search_start = max(self.confirmed_global_word_index + 1, 0)
        # 이번 루프에서 마지막으로 매칭된 단어의 인덱스를 추적하여 간격을 찾습니다.
        last_matched_in_loop = search_start - 1

        for spoken_text, normalized_token in transcript_tokens[-8:]:
            match = find_realtime_token_feedback(self.script_words, normalized_token, search_start)
            if not match:
                continue

            script_word, score = match
            global_word_index = script_word.get("globalWordIndex")
            if global_word_index is None:
                continue

            # --- 간격(Gap) 처리: 건너뛴 단어 감지 ---
            # 만약 현재 매칭된 단어가 예상 위치보다 뒤에 있다면, 그 사이의 단어들을 오답(건너뜀)으로 처리합니다.
            for skipped_idx in range(last_matched_in_loop + 1, global_word_index):
                if skipped_idx not in self.word_results_by_index:
                    skipped_word = self.script_words[skipped_idx]
                    self.word_results_by_index[skipped_idx] = {
                        "globalWordIndex": skipped_idx,
                        "expectedWord": skipped_word.get("text") or "",
                        "spokenWord": "(건너뜀)",
                        "matchScore": 0.0,
                        "isCorrect": False,
                    }

            # 현재 단어 결과 기록 (점수가 낮아도 포함시켜서 빨간색으로 표시되게 함)
            word_result = {
                "globalWordIndex": global_word_index,
                "expectedWord": script_word.get("text") or "",
                "spokenWord": spoken_text,
                "matchScore": round(float(score), 3),
                "isCorrect": score >= 0.75,
            }
            self.word_results_by_index[global_word_index] = word_result
            
            # 다음 토큰은 현재 매칭된 단어 이후부터 검색하여 순서를 보장합니다.
            last_matched_in_loop = global_word_index
            search_start = global_word_index + 1

        return list(self.word_results_by_index.values())

    def estimate_current_global_word_index(self, transcript):
        if not self.script_words or not transcript:
            return self.confirmed_global_word_index

        transcript_tokens = [
            normalize_match_text(token)
            for token in transcript.split()
            if normalize_match_text(token)
        ]
        if not transcript_tokens:
            return self.confirmed_global_word_index

        search_start = max(self.confirmed_global_word_index + 1, 0)
        token_window = transcript_tokens[-8:]
        current_index = self.confirmed_global_word_index

        for token in token_window:
            match = find_realtime_token_match(self.script_words, token, search_start)
            if not match:
                continue

            current_index = match.get("globalWordIndex", current_index)
            search_start = current_index + 1

        return current_index


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
    return script_word if score >= 0.72 else None


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

    if not best_word or best_score < 0.4: # 최소 일치 기준 미달 시 무시
        return None

    # 반환할 때는 감점되지 않은 원본 유사도 점수를 기반으로 한 피드백 정보를 넘깁니다.
    # 단, 결과 판단은 adjusted_score로 수행하여 '위치 적합성'을 보장했습니다.
    return best_word, max(best_score + (abs(best_word.get("globalWordIndex", 0) - search_start) * 0.04), 0.0)


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


# --- Endpoints ---

@router.post("/analyze")
async def run_analysis(req: AnalyzeRequest):
    # Spring 서버에서 전달받은 audioUrl은 로컬 파일 경로입니다.
    # 상대 경로인 경우 UPLOAD_ROOT를 기준으로 절대 경로를 만듭니다.
    audio_path = req.audioUrl
    if not os.path.isabs(audio_path):
        audio_path = os.path.join(UPLOAD_ROOT, audio_path.replace("uploads/", ""))

    print(f"[Python] Starting analysis for file: {audio_path}")
    
    if not os.path.exists(audio_path):
        print(f"[Python ERROR] Audio file not found at: {audio_path}")
        raise HTTPException(status_code=404, detail=f"Audio file not found: {audio_path}")
    
    try:
        # 1. 음성 특징 분석 (Librosa 등 사용)
        features = analyze_voice_features(audio_path)
        if not features:
            raise HTTPException(status_code=500, detail="Voice feature analysis failed")

        # 2. STT 수행 (Gemini 폴백이 적용된 새로운 transcribe_audio 사용)
        duration = features.get("durationSec")
        stt_words = transcribe_audio(audio_path, duration)
        
        if stt_words is None:
            print("[Python ERROR] STT process failed completely.")
            raise HTTPException(status_code=502, detail="STT processing failed")

        # 3. 결과 조립
        word_results = build_aligned_word_results(req.scriptWords, stt_words)
        sentence_results = build_sentence_results(req.scriptWords, word_results, features)
        issue_results = build_issue_results(sentence_results)
        ai_feedback = generate_ai_feedback(features, req)

        print(f"[Python] Analysis successful for practice {req.practiceId}")
        return {
            **features, 
            **ai_feedback, 
            "wordResults": word_results, 
            "sentenceResults": sentence_results, 
            "issues": issue_results
        }
    except Exception as e:
        print(f"[Python ERROR] Unexpected error during analysis: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/scripts/mark")
async def mark_script(req: MarkRequest):
    marked = await mark_script_ai(req.content)
    return {"markedContent": marked}

@router.post("/scripts/generate")
async def generate_script(req: GenerateScriptRequest):
    return await generate_script_ai(req)

@router.post("/scripts/update")
async def update_script(req: UpdateScriptRequest):
    return await update_script_ai(req)

@router.post("/ppt/convert")
async def convert_ppt(req: ConvertPptRequest):
    ppt_path = ensure_within_upload_root(req.pptPath, must_exist=True)
    output_dir = ensure_within_upload_root(req.outputDir)
    pdf_path = convert_ppt_to_pdf(ppt_path, output_dir)
    slides = render_pdf_to_images(pdf_path, output_dir)
    if os.path.exists(pdf_path): os.remove(pdf_path)
    return {"sourcePptUrl": ppt_path, "totalSlides": len(slides), "slides": slides}

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

@router.post("/voice-analysis")
async def analyze_voice_api(voiceFile: UploadFile = File(...)):
    """
    사용자 음색 분석 요청 API

    요청된 예문 녹음 파일을 임시 저장하고, Librosa 기반의 음성 특징 분석 함수를 호출하여
    평균 피치(Pitch)와 발화 속도(WPM) 등의 분석 결과를 반환합니다.

    - Content-Type: multipart/form-data
    - Request Body: voiceFile (File, 필수)
    - Response:
        - 성공 시 (200 OK): 분석 결과 및 상태 반환
        - 실패 시 (422 Unprocessable Entity): 목소리 미감지 시 에러 메시지 반환
        - 실패 시 (400 Bad Request): 데이터 부족 또는 예외 발생 시 에러 메시지 반환
    """
    temp_path = None
    try:
        # 클라이언트의 파일 이름으로부터 확장자를 추출
        suffix = Path(voiceFile.filename or "").suffix

        # 1. 안전한 임시 파일 생성
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as buffer:
            temp_path = buffer.name
            shutil.copyfileobj(voiceFile.file, buffer)

        # 2. 기존 음성 분석 함수 호출
        features = analyze_voice_features(temp_path)

        if not features:
            raise HTTPException(
                status_code=422,
                detail="목소리가 감지되지 않았습니다. 조용한 곳에서 다시 녹음해주세요."
            )

        # 3. 래퍼를 제거하고 flat한 JSON (snake_case) 형태로 반환
        return {
            "analysis_id": 123,
            "avg_pitch": features.get("avgPitch", 0.0),
            "avg_wpm": features.get("avgWpm", 0.0),
            "status": "COMPLETED"
        }
    except HTTPException:
        # 4. 예외 처리 방식: 422 에러가 400으로 덮어씌워지지 않도록 방지
        raise
    except Exception as err:
        raise HTTPException(
            status_code=400,
            detail="분석을 위한 음성 데이터가 부족합니다. 세 문장을 모두 읽어주세요."
        ) from err
    finally:
        # 5. 사용한 임시 파일 정리 (서버 공간 확보 및 보안)
        if temp_path and os.path.exists(temp_path):
            os.remove(temp_path)