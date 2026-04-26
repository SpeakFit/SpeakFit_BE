import re

def clamp(value, min_value, max_value):
    """값을 지정한 범위 안으로 제한합니다."""
    return max(min_value, min(value, max_value))

def normalize_match_text(text):
    """단어 매칭용 텍스트를 정규화합니다."""
    if not text:
        return ""
    return re.sub(r"[^0-9a-zA-Z가-힣]+", "", text).lower()
