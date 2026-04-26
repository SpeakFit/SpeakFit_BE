from app.core.config import (
    ANALYSIS_STT_PROVIDER,
    GOOGLE_STT_ENCODING,
    GOOGLE_STT_LANGUAGE_CODE,
    GOOGLE_STT_SAMPLE_RATE,
)


def transcribe_audio(file_path):
    """전체 음성 파일을 STT provider로 변환하고 표준 단어 타임스탬프 목록을 반환합니다."""
    if ANALYSIS_STT_PROVIDER == "google":
        return transcribe_audio_with_google(file_path)

    print(f"[Python] 분석 STT provider 비활성화: {ANALYSIS_STT_PROVIDER}")
    return []


def transcribe_audio_with_google(file_path):
    """Google Speech-to-Text로 전체 음성 파일의 단어 타임스탬프를 추출합니다."""
    try:
        from google.cloud import speech

        client = speech.SpeechClient()
        with open(file_path, "rb") as audio_file:
            content = audio_file.read()

        config = speech.RecognitionConfig(
            encoding=get_google_audio_encoding(speech),
            sample_rate_hertz=GOOGLE_STT_SAMPLE_RATE,
            language_code=GOOGLE_STT_LANGUAGE_CODE,
            enable_word_time_offsets=True,
            enable_word_confidence=True,
        )
        audio = speech.RecognitionAudio(content=content)
        response = client.recognize(config=config, audio=audio)

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

        print(f"[Python] 분석 STT 완료: provider=google, words={len(words)}")
        return words
    except Exception as e:
        print(f"[Python] 분석 STT 실패: provider=google, reason={e}")
        return []


def get_google_audio_encoding(speech):
    encoding_map = {
        "LINEAR16": speech.RecognitionConfig.AudioEncoding.LINEAR16,
        "FLAC": speech.RecognitionConfig.AudioEncoding.FLAC,
        "WEBM_OPUS": speech.RecognitionConfig.AudioEncoding.WEBM_OPUS,
        "MP3": speech.RecognitionConfig.AudioEncoding.MP3,
        "OGG_OPUS": speech.RecognitionConfig.AudioEncoding.OGG_OPUS,
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
