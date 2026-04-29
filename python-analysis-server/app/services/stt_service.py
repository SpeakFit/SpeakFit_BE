from app.core.config import (
    ANALYSIS_STT_PROVIDER,
    GOOGLE_STT_ASYNC_TIMEOUT_SECONDS,
    GOOGLE_STT_ENCODING,
    GOOGLE_STT_INLINE_MAX_BYTES,
    GOOGLE_STT_LANGUAGE_CODE,
    GOOGLE_STT_SAMPLE_RATE,
    GOOGLE_STT_SYNC_MAX_SECONDS,
)


def transcribe_audio(file_path, duration_sec=None):
    """Transcribe an audio file and return word-level timestamps."""
    if ANALYSIS_STT_PROVIDER == "google":
        return transcribe_audio_with_google(file_path, duration_sec)

    print(f"[Python] Analysis STT provider disabled: {ANALYSIS_STT_PROVIDER}")
    return None


def transcribe_audio_with_google(file_path, duration_sec=None):
    """Use Google Speech-to-Text for full-recording word timestamps."""
    try:
        from google.cloud import speech

        client = speech.SpeechClient()
        with open(file_path, "rb") as audio_file:
            content = audio_file.read()

        if len(content) > GOOGLE_STT_INLINE_MAX_BYTES:
            print(
                "[Python] Analysis STT failed: "
                f"audioBytes={len(content)} exceeds inline limit={GOOGLE_STT_INLINE_MAX_BYTES}. "
                "Configure GCS URI based async STT for larger files."
            )
            return None

        config = build_google_recognition_config(speech)
        audio = speech.RecognitionAudio(content=content)

        if should_use_async_stt(duration_sec):
            print(
                "[Python] Analysis STT async request: "
                f"durationSec={duration_sec}, timeoutSec={GOOGLE_STT_ASYNC_TIMEOUT_SECONDS}"
            )
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
