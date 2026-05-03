import json
import os
import time
from app.core.config import (
    ANALYSIS_STT_PROVIDER,
    GOOGLE_STT_ENCODING,
    GOOGLE_STT_INLINE_MAX_BYTES,
    GOOGLE_STT_LANGUAGE_CODE,
    GOOGLE_STT_SAMPLE_RATE,
    GEMINI_API_KEY,
    model as gemini_model
)


def transcribe_audio(file_path, duration_sec=None):
    """Transcribe an audio file and return word-level timestamps."""
    if ANALYSIS_STT_PROVIDER == "gemini" and GEMINI_API_KEY and gemini_model:
        words = transcribe_audio_with_gemini(file_path)
        if words is not None:
            return words
        if can_use_google_direct_stt(file_path, duration_sec):
            return transcribe_audio_with_google(file_path, duration_sec)
        return None

    if ANALYSIS_STT_PROVIDER == "google" and can_use_google_direct_stt(file_path, duration_sec):
        words = transcribe_audio_with_google(file_path, duration_sec)
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


def transcribe_audio_with_gemini(file_path):
    """Use Gemini AI to transcribe audio with word-level timestamps."""
    try:
        import google.generativeai as genai
        
        print(f"[Python] Uploading audio to Gemini: {file_path}")
        audio_file = genai.upload_file(path=file_path, mime_type="audio/webm")
        
        # 파일 처리가 완료될 때까지 잠시 대기
        while audio_file.state.name == "PROCESSING":
            time.sleep(1)
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

        try:
            response = client.recognize(request={"config": config, "audio": audio})
        except Exception as sync_error:
            if "Sync input too long" in str(sync_error) and GEMINI_API_KEY and gemini_model:
                print("[Python] Google Sync STT input too long. Retrying with Gemini.")
                return transcribe_audio_with_gemini(file_path)
            raise

        words = extract_words_from_response(response)
        print(f"[Python] Analysis STT completed: provider=google, words={len(words)}")
        return words
    except Exception as e:
        print(f"[Python] Analysis STT failed: provider=google, reason={e}")
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
