import re


def clamp(value, min_value, max_value):
    return max(min_value, min(value, max_value))


def normalize_match_text(text):
    if not text:
        return ""

    return re.sub(r"[^0-9a-zA-Z가-힣]+", "", text).lower()
