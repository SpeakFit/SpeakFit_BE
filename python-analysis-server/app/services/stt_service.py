import json
import time
from app.core.config import (
    ANALYSIS_STT_PROVIDER,
    GOOGLE_STT_ASYNC_TIMEOUT_SECONDS,
    GOOGLE_STT_ENCODING,
    GOOGLE_STT_INLINE_MAX_BYTES,
    GOOGLE_STT_LANGUAGE_CODE,
    GOOGLE_STT_SAMPLE_RATE,
    GOOGLE_STT_SYNC_MAX_SECONDS,
    GEMINI_API_KEY,
    model as gemini_model
)


def transcribe_audio(file_path, duration_sec=None):
    """Transcribe an audio file and return word-level timestamps."""
    # 1분(60초) 이상인 경우 Google Sync STT는 실패하므로, Gemini를 우선적으로 고려합니다.
    is_long_audio = duration_sec is not None and float(duration_sec) > 55.0
    
    if is_long_audio and GEMINI_API_KEY and gemini_model:
        print(f"[Python] Long audio detected ({duration_sec}s). Using Gemini for STT.")
        return transcribe_audio_with_gemini(file_path)

    if ANALYSIS_STT_PROVIDER == "google":
        return transcribe_audio_with_google(file_path, duration_sec)
    
    # Google 실패 시 Gemini로 다시 시도
    if GEMINI_API_KEY and gemini_model:
        return transcribe_audio_with_gemini(file_path)

    print(f"[Python] Analysis STT provider disabled or unavailable: {ANALYSIS_STT_PROVIDER}")
    return None


def transcribe_audio_with_gemini(file_path):
    """Use Gemini AI to transcribe audio with word-level timestamps."""
    try:
        import google.generativeai as genai
        
        print(f"[Python] Uploading audio to Gemini: {file_path}")
        audio_file = genai.upload_file(path=file_path)
        
        # 파일 처리가 완료될 때까지 잠시 대기
        while audio_file.state.name == "PROCESSING":
            time.sleep(1)
            audio_file = genai.get_file(audio_file.name)
            
        if audio_file.state.name == "FAILED":
            raise Exception("Gemini audio upload/processing failed")

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
        
        # confidence 값 보정 (Gemini는 개별 단어 점수를 주지 않으므로 기본값 설정)
        for w in words:
            w["confidence"] = 0.95
            w["isFinal"] = True
            
        print(f"[Python] Gemini STT completed: words={len(words)}")
        return words
    except Exception as e:
        print(f"[Python] Gemini STT failed: {e}")
        return None


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

        if should_use_async_stt(duration_sec):
            print(
                "[Python] Analysis STT async request: "
                f"durationSec={duration_sec}, timeoutSec={GOOGLE_STT_ASYNC_TIMEOUT_SECONDS}"
            )
            # 주의: GCS URI가 아니면 long_running_recognize도 실패할 가능성이 높음
            operation = client.long_running_recognize(config=config, audio=audio)
            response = operation.result(timeout=GOOGLE_STT_ASYNC_TIMEOUT_SECONDS)
        else:
            response = client.recognize(request={"config": config, "audio": audio})

        words = extract_words_from_response(response)
        print(f"[Python] Analysis STT completed: provider=google, words={len(words)}")
        return words
    except Exception as e:
        print(f"[Python] Analysis STT failed: provider=google, reason={e}")
        return None


def should_use_async_stt(duration_sec):
    return duration_sec is not None and float(duration_sec) > GOOGLE_STT_SYNC_MAX_SECONDS


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
