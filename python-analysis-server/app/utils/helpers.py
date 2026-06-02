import re
import unicodedata

def clamp(value, min_value, max_value):
    return max(min_value, min(value, max_value))


# ── [STEP 7] 실시간/사후 매칭 공유 임계값 상수 ─────────────────────────────
# voice_service.py(사후 분석)와 endpoints.py(실시간)가 동일 값을 참조.
# 단일 소스로 관리하여 한쪽만 바꾸는 실수 방지.
MATCH_THRESHOLD            = 0.72   # 일반 단어 매칭 통과 기준
SHORT_WORD_MATCH_THRESHOLD = 0.90   # 2자 이하 짧은 단어 매칭 기준
REALTIME_MIN_SCORE         = 0.40   # find_realtime_token_feedback 내부 필터 하한
REALTIME_CORRECT_THRESHOLD = 0.68   # isCorrect 판정 기준 (distance penalty 적용 후)


# [STEP 6] 한국어 조사 패턴 — 매칭 전 어미 제거로 인식률 개선.
# 2음절 우선 → 1음절 순으로 적용해 과잉 제거 방지.
_JOSA_PATTERN = re.compile(
    r"(에서|으로|에게|한테|부터|까지|처럼|보다|께서|이라|이다"
    r"|와|과|로|을|를|은|는|이|가|의|에|도|만|다)$"
)


def strip_josa(text: str) -> str:
    """단어 끝 조사를 제거해 매칭 정확도를 높입니다.
    결과가 빈 문자열이 되면 원본을 반환합니다.
    """
    if len(text) <= 1:
        return text
    stripped = _JOSA_PATTERN.sub("", text)
    return stripped if stripped else text


def normalize_match_text(text):
    if not text:
        return ""

    # 1. Unicode NFC 정규화 (자모 분리 방지)
    text = unicodedata.normalize("NFC", text)
    # 2. 특수문자 제거 (한글/영숫자만 유지)
    cleaned = re.sub(r"[^0-9a-zA-Z가-힣]+", "", text).lower()
    # 3. 조사 제거
    return strip_josa(cleaned)
