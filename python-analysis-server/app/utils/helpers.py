import re
import unicodedata
from difflib import SequenceMatcher

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


# ── [STEP-C] 공통 텍스트 유사도 함수 ──────────────────────────────────────────
# endpoints.py(실시간)와 voice_service.py(사후)에서 동일 로직 공유.
# 기존 두 파일에 각각 중복 구현되어 있던 calculate_realtime_similarity / calculate_word_similarity 통합.

def calculate_text_similarity(text1: str, text2: str) -> float:
    """두 정규화된 텍스트의 유사도를 반환 (0.0 ~ 1.0).

    완전 일치 → 1.0
    부분 포함(3자 이상) → 0.86
    SequenceMatcher ratio 사용
    """
    if not text1 or not text2:
        return 0.0
    if text1 == text2:
        return 1.0
    if len(text1) > 2 and (text1 in text2 or text2 in text1):
        return 0.86
    return SequenceMatcher(None, text1, text2).ratio()


# ── [STEP-C] 단조 증가 DTW 정렬 엔진 ─────────────────────────────────────────
# 실시간(endpoints.py)과 사후(voice_service.py) 정렬이 동일 엔진을 호출.
# "그리디 윈도우 매칭" 대체 — 전역 최적 + 단조성 보장.
#
# 알고리즘: Subsequence DTW
#   - dp[i][j] = 처음 i개 query 토큰을 ref[start_idx..start_idx+j-1]에 정렬하는 최소 비용
#   - dp[0][j] = 0 (ref 앞부분 스킵 허용 — 대본의 어느 위치에서나 시작 가능)
#   - 전환: (i-1,j-1) | (i-1,j) 토큰 스킵 | (i,j-1) ref 스킵
#   - 역추적: 마지막 행 최솟값 → 경로 복원
#   - 단조성: query_idx, ref_idx 모두 순 증가만 허용
#
# 시간복잡도: O(N × W), N=query 길이, W=max_window (실시간: ~8×20=160, 사후: ~300×500=150k)

# 앵커 기준: 한글 4자 이상 단어는 반복 확률이 낮아 절대 위치 고정에 사용
ANCHOR_MIN_LEN = 4


def monotonic_dtw_align(
    query_tokens: list,
    ref_words: list,
    start_idx: int = 0,
    max_window: int = None,
) -> list:
    """단조 증가 DTW로 query 토큰을 ref 단어 목록에 정렬.

    Args:
        query_tokens : 정규화된 문자열 리스트 (transcript 토큰)
        ref_words    : script 단어 dict 리스트. 각 dict에 "text" 또는 "normalizedText" 포함.
        start_idx    : 탐색 시작 ref 인덱스 (단조 제약 — 이 이전은 탐색 안 함)
        max_window   : start_idx 이후 탐색 최대 단어 수.
                       None이면 len(query_tokens)*3+10 사용 (적응형 윈도우).

    Returns:
        list of (query_idx: int, ref_idx: int, score: float)
        - query_idx, ref_idx 모두 단조 증가
        - score < REALTIME_MIN_SCORE 인 항목은 제외됨
        - 앵커 단어(한글 4자 이상) 매칭 시 해당 ref_idx를 'anchor' 로 표시하려면
          호출부에서 ref_words[ref_idx]["text"] 길이를 직접 확인
    """
    if not query_tokens or not ref_words:
        return []

    n = len(query_tokens)
    if max_window is None:
        max_window = n * 3 + 10

    end_idx = min(start_idx + max_window, len(ref_words))
    sub = ref_words[start_idx:end_idx]
    m = len(sub)
    if m == 0:
        return []

    INF = float("inf")

    # ─ DP 초기화 ─────────────────────────────────────────────────────────────
    # dp[i][j]: query[0..i-1] 를 sub[0..j-1] 에 정렬한 최솟값
    # (i=0): 아직 query 처리 전 → ref 앞부분 스킵 비용 0 (Subsequence DTW)
    dp   = [[INF] * (m + 1) for _ in range(n + 1)]
    from_  = [[None] * (m + 1) for _ in range(n + 1)]

    for j in range(m + 1):
        dp[0][j] = 0.0  # ref 앞부분 스킵 허용

    # ─ DP 채우기 ─────────────────────────────────────────────────────────────
    for i in range(1, n + 1):
        token = query_tokens[i - 1]
        for j in range(1, m + 1):
            sw_text = normalize_match_text(
                sub[j - 1].get("normalizedText") or sub[j - 1].get("text") or ""
            )
            cost = 1.0 - calculate_text_similarity(sw_text, token)

            options = [
                (dp[i - 1][j - 1], (i - 1, j - 1)),  # 매칭
                (dp[i - 1][j],     (i - 1, j)),        # query 토큰 스킵
                (dp[i][j - 1],     (i, j - 1)),        # ref 단어 스킵
            ]
            best_val, best_from = min(options, key=lambda x: x[0])
            dp[i][j]   = best_val + cost
            from_[i][j] = best_from

    # ─ 최적 종료 위치 탐색 (마지막 행 최솟값) ─────────────────────────────────
    best_end_j = min(range(1, m + 1), key=lambda j: dp[n][j])

    # ─ 역추적 ────────────────────────────────────────────────────────────────
    raw_path: list = []
    ci, cj = n, best_end_j
    while ci > 0 and cj > 0:
        raw_path.append((ci - 1, start_idx + cj - 1))  # (query_idx, ref_idx)
        pi, pj = from_[ci][cj]
        ci, cj = pi, pj
    raw_path.reverse()

    # ─ 후처리: 단조성 보장 + 저신뢰 필터 ────────────────────────────────────
    results: list = []
    last_ref_idx = start_idx - 1

    for query_idx, ref_idx in raw_path:
        if ref_idx <= last_ref_idx:
            continue  # 단조성 위반 제거

        sw = ref_words[ref_idx]
        sw_text = normalize_match_text(
            sw.get("normalizedText") or sw.get("text") or ""
        )
        score = calculate_text_similarity(sw_text, query_tokens[query_idx])

        if score < REALTIME_MIN_SCORE:
            continue  # 최소 신뢰 기준 미달 제거

        results.append((query_idx, ref_idx, score))
        last_ref_idx = ref_idx

    return results
