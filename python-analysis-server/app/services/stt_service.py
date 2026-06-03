import json
import mimetypes
import os
import time
from app.core.config import (
    ANALYSIS_STT_PROVIDER,
    GOOGLE_STT_ENCODING,
    GOOGLE_STT_INLINE_MAX_BYTES,
    GOOGLE_STT_LANGUAGE_CODE,
    GOOGLE_STT_SAMPLE_RATE,
    GOOGLE_STT_ASYNC_TIMEOUT_SECONDS,
    GEMINI_API_KEY,
    model as gemini_model
)

# [TRACE] LangSmith 트레이싱 — STT 전사 소요시간을 트레이스에 노출.
# langsmith 미설치 환경에서도 동작하도록 no-op 폴백 제공(ai_service와 동일 패턴).
try:
    from langsmith import traceable
except ImportError:
    def traceable(*args, **kwargs):
        def decorator(fn):
            return fn
        return decorator if args and callable(args[0]) else decorator


@traceable(run_type="tool", name="transcribe_audio")
def transcribe_audio(file_path, duration_sec=None):
    """Transcribe an audio file and return word-level timestamps."""
    if ANALYSIS_STT_PROVIDER == "gemini" and GEMINI_API_KEY and gemini_model:
        words = transcribe_audio_with_gemini(file_path)
        if words is not None:
            return words
        if can_use_google_direct_stt(file_path, duration_sec):
            return transcribe_audio_with_google(file_path, duration_sec)
        return None

    if ANALYSIS_STT_PROVIDER == "google":
        if can_use_google_direct_stt(file_path, duration_sec):
            # 60초 이하: 동기(sync) STT가 가장 빠름.
            words = transcribe_audio_with_google(file_path, duration_sec)
            if words is not None:
                return words
        else:
            # 60초 초과: Google 비동기(long-running) STT 우선.
            #   - 긴 녹음 전용 API라 단어 타임스탬프를 네이티브로 주고, Gemini보다 빠르고 정확.
            #   - 인라인 한계 초과/실패 시에만 아래 Gemini 폴백으로 내려간다.
            words = transcribe_audio_with_google_long_running(file_path)
            if words is not None:
                return words

    if GEMINI_API_KEY and gemini_model:
        print(f"[Python] Using Gemini for analysis STT (duration={duration_sec}s).")
        return transcribe_audio_with_gemini(file_path)

    if ANALYSIS_STT_PROVIDER == "google":
        print(f"[Python] Audio is too long for direct Google STT and Gemini is unavailable (duration={duration_sec}s).")
    else:
        print(f"[Python] Analysis STT provider disabled or unavailable: {ANALYSIS_STT_PROVIDER}")
    return None


def can_use_google_direct_stt(file_path, duration_sec=None):
    if duration_sec is not None and float(duration_sec) > 60:
        return False

    return os.path.getsize(file_path) <= GOOGLE_STT_INLINE_MAX_BYTES


@traceable(run_type="tool", name="transcribe_audio_with_gemini")
def transcribe_audio_with_gemini(file_path):
    """Use Gemini AI to transcribe audio with word-level timestamps."""
    try:
        import google.generativeai as genai
        
        mime_type = guess_audio_mime_type(file_path)
        print(f"[Python] Uploading audio to Gemini: {file_path}, mime_type={mime_type}")
        audio_file = genai.upload_file(path=file_path, mime_type=mime_type)

        # 파일 처리가 완료될 때까지 대기 (폴링 간격을 줄여 지연 최소화 + 최대 대기 가드)
        _poll_deadline = time.monotonic() + 120  # 최대 120초 대기 후 포기
        while audio_file.state.name == "PROCESSING":
            if time.monotonic() > _poll_deadline:
                raise Exception(f"Gemini audio processing timeout: file={audio_file.name}")
            time.sleep(0.5)
            audio_file = genai.get_file(audio_file.name)

        if audio_file.state.name == "FAILED":
            raise Exception(f"Gemini audio upload/processing failed: file={audio_file.name}, state={audio_file.state.name}")

        prompt = (
            "이 오디오 파일을 듣고 정확하게 받아쓰기(Transcription)를 해주세요. "
            "반드시 아래의 JSON 배열 형식으로만 응답하세요. "
            "[{\"word\": \"단어\", \"startMs\": 시작밀리초, \"endMs\": 종료밀리초}, ...] "
            "JSON 외의 다른 텍스트는 포함하지 마세요."
        )

        response = gemini_model.generate_content([prompt, audio_file])
        
        # 응답 텍스트에서 JSON 부분만 추출 (가끔 마크다운 형식이 섞일 수 있음)
        text = response.text.strip()
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0].strip()
        elif "```" in text:
            text = text.split("```")[1].split("```")[0].strip()
            
        words = json.loads(text)
        
        # [STEP 6] Gemini는 word-level confidence를 반환하지 않으므로 추정값(0.75) 사용.
        # 0.95로 하드코딩하면 발음 점수가 실제보다 과대 평가됨.
        # Google STT가 primary이고 Gemini는 60초 초과/폴백 전용 보조.
        for w in words:
            w.setdefault("confidence", 0.75)
            w["isFinal"] = True
            
        print(f"[Python] Gemini STT completed: words={len(words)}")
        return words
    except Exception as e:
        print(f"[Python] Gemini STT failed: {e}")
        return None


def guess_audio_mime_type(file_path):
    mime_type, _ = mimetypes.guess_type(file_path)
    if mime_type and mime_type.startswith("audio/"):
        return mime_type

    extension = os.path.splitext(file_path)[1].lower()
    if extension == ".webm":
        return "audio/webm"
    if extension == ".m4a":
        return "audio/mp4"
    if extension == ".mp4":
        return "audio/mp4"
    if extension == ".ogg":
        return "audio/ogg"
    if extension == ".wav":
        return "audio/wav"
    if extension == ".mp3":
        return "audio/mpeg"

    return "audio/webm"


@traceable(run_type="tool", name="transcribe_audio_with_google")
def transcribe_audio_with_google(file_path, duration_sec=None):
    """Use Google Speech-to-Text for full-recording word timestamps."""
    try:
        from google.cloud import speech

        client = speech.SpeechClient()
        with open(file_path, "rb") as audio_file:
            content = audio_file.read()

        # Google STT의 bytes 전송 한계는 약 1분입니다.
        if len(content) > GOOGLE_STT_INLINE_MAX_BYTES or (duration_sec and duration_sec > 60):
            print(f"[Python] Audio too long for direct Google STT (duration={duration_sec}s).")
            return None

        config = build_google_recognition_config(speech)
        audio = speech.RecognitionAudio(content=content)

        try:
            response = client.recognize(request={"config": config, "audio": audio})
        except Exception as sync_error:
            if "Sync input too long" in str(sync_error):
                # 동기 한계 초과 → Google 비동기(long-running) STT 우선, 실패 시 Gemini 폴백.
                print("[Python] Google Sync STT input too long. Retrying with long-running Google STT.")
                long_words = transcribe_audio_with_google_long_running(file_path)
                if long_words is not None:
                    return long_words
                if GEMINI_API_KEY and gemini_model:
                    print("[Python] Long-running STT unavailable. Falling back to Gemini.")
                    return transcribe_audio_with_gemini(file_path)
            raise

        words = extract_words_from_response(response)
        print(f"[Python] Analysis STT completed: provider=google, words={len(words)}")
        return words
    except Exception as e:
        print(f"[Python] Analysis STT failed: provider=google, reason={e}")
        return None


@traceable(run_type="tool", name="transcribe_audio_with_google_long_running")
def transcribe_audio_with_google_long_running(file_path):
    """Google 비동기(long-running) STT — 60초 초과 긴 녹음용.

    동기 recognize의 ~1분 한계를 넘는 오디오를 처리한다. 단어별 타임스탬프를
    네이티브로 제공하므로 Gemini 폴백(업로드+폴링+생성, ~39초)보다 빠르고 정확하다.
    인라인 content 한계(GOOGLE_STT_INLINE_MAX_BYTES)를 초과하면 None을 반환해
    상위 호출부가 Gemini로 폴백하도록 한다(예: GCS 업로드 미구성 환경 안전장치).
    """
    try:
        from google.cloud import speech

        client = speech.SpeechClient()
        with open(file_path, "rb") as audio_file:
            content = audio_file.read()

        # 인라인 전송 한계 초과 시 비동기 inline도 거부될 수 있어 안전하게 폴백.
        if len(content) > GOOGLE_STT_INLINE_MAX_BYTES:
            print(f"[Python] Audio exceeds inline limit for long-running STT ({len(content)} bytes).")
            return None

        config = build_google_recognition_config(speech)
        audio = speech.RecognitionAudio(content=content)

        operation = client.long_running_recognize(config=config, audio=audio)
        response = operation.result(timeout=GOOGLE_STT_ASYNC_TIMEOUT_SECONDS)

        words = extract_words_from_response(response)
        print(f"[Python] Analysis STT completed: provider=google-async, words={len(words)}")
        return words
    except Exception as e:
        print(f"[Python] Long-running Google STT failed: {e}")
        return None


def build_google_recognition_config(speech):
    encoding = get_google_audio_encoding(speech)
    kwargs = {
        "encoding": encoding,
        "language_code": GOOGLE_STT_LANGUAGE_CODE,
        "enable_word_time_offsets": True,
        "enable_word_confidence": True,
    }

    if encoding not in {
        speech.RecognitionConfig.AudioEncoding.WEBM_OPUS,
        speech.RecognitionConfig.AudioEncoding.OGG_OPUS,
    }:
        kwargs["sample_rate_hertz"] = GOOGLE_STT_SAMPLE_RATE

    return speech.RecognitionConfig(**kwargs)


def extract_words_from_response(response):
    words = []
    for result in response.results:
        if not result.alternatives:
            continue

        alternative = result.alternatives[0]
        for word_info in alternative.words:
            words.append({
                "word": word_info.word,
                "startMs": duration_to_millis(word_info.start_time),
                "endMs": duration_to_millis(word_info.end_time),
                "confidence": resolve_word_confidence(word_info, alternative),
                "isFinal": True,
            })

    return words


def get_google_audio_encoding(speech):
    encoding_map = {
        "LINEAR16": speech.RecognitionConfig.AudioEncoding.LINEAR16,
        "FLAC": speech.RecognitionConfig.AudioEncoding.FLAC,
        "WEBM_OPUS": speech.RecognitionConfig.AudioEncoding.WEBM_OPUS,
        "MP3": speech.RecognitionConfig.AudioEncoding.MP3,
        "OGG_OPUS": speech.RecognitionConfig.AudioEncoding.OGG_OPUS,
        "ENCODING_UNSPECIFIED": speech.RecognitionConfig.AudioEncoding.ENCODING_UNSPECIFIED,
    }
    return encoding_map.get(GOOGLE_STT_ENCODING.upper(), speech.RecognitionConfig.AudioEncoding.WEBM_OPUS)


def duration_to_millis(duration):
    if hasattr(duration, "total_seconds"):
        return int(duration.total_seconds() * 1000)

    seconds = getattr(duration, "seconds", 0) or 0
    nanos = getattr(duration, "nanos", 0) or 0
    return int(seconds * 1000 + nanos / 1_000_000)


def resolve_word_confidence(word_info, alternative):
    confidence = getattr(word_info, "confidence", 0.0) or 0.0
    if confidence > 0:
        return float(confidence)

    return float(getattr(alternative, "confidence", 0.0) or 0.0)
