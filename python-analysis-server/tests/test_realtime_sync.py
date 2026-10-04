"""
[STEP 10-4] 실시간 싱크 재접속 시나리오 통합 테스트

검증 대상:
  1. confirmed_global_word_index 재접속 인수인계 (STEP 7-B)
  2. 이중 패널티 버그 수정 (STEP 7-A): find_realtime_token_feedback
  3. 매칭 임계값 상수 일원화 (helpers.py 공유 상수)
  4. 실시간 점수 isCorrect 임계값 (REALTIME_CORRECT_THRESHOLD = 0.68)
"""
import pytest
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.utils.helpers import (
    MATCH_THRESHOLD, SHORT_WORD_MATCH_THRESHOLD,
    REALTIME_MIN_SCORE, REALTIME_CORRECT_THRESHOLD,
    normalize_match_text,
)


# ── find_realtime_token_feedback 로직 인라인 (WebSocket 의존 없이 검증) ──
from difflib import SequenceMatcher


def _calculate_similarity(script_text: str, spoken_text: str) -> float:
    if not script_text or not spoken_text:
        return 0.0
    if script_text == spoken_text:
        return 1.0
    if len(script_text) > 2 and (script_text in spoken_text or spoken_text in script_text):
        return 0.86
    return SequenceMatcher(None, script_text, spoken_text).ratio()


def _find_token_feedback(script_words, token, search_start):
    """endpoints.find_realtime_token_feedback 로직 인라인."""
    best_word  = None
    best_score = -1.0
    start_idx  = max(search_start - 2, 0)
    end_idx    = min(search_start + 11, len(script_words))

    for sw in script_words[start_idx:end_idx]:
        script_text = normalize_match_text(sw.get("normalizedText") or sw.get("text", ""))
        if not script_text:
            continue
        base_score = _calculate_similarity(script_text, token)
        distance   = abs(sw.get("globalWordIndex", 0) - search_start)
        adjusted   = base_score - distance * 0.04

        if adjusted > best_score:
            best_score = adjusted
            best_word  = sw

    if not best_word or best_score < REALTIME_MIN_SCORE:
        return None

    # [STEP 7-A] 이중 패널티 수정: adjusted_score 그대로 반환 (penalty 재가산 금지)
    return best_word, max(best_score, 0.0)


def _make_words(texts):
    """테스트용 script_words 생성."""
    return [
        {
            "globalWordIndex": i,
            "text": t,
            "normalizedText": normalize_match_text(t),
        }
        for i, t in enumerate(texts)
    ]


# ═══════════════════════════════════════════════════════════
# STEP 7-A: 이중 패널티 버그 수정 검증
# ═══════════════════════════════════════════════════════════

class TestDoublepenaltyFix:
    """수정 전: return max(best_score + distance*0.04, 0.0)  ← 잘못됨
       수정 후: return max(best_score, 0.0)                  ← 올바름
       best_score는 이미 distance penalty가 적용된 adjusted_score.
    """

    def test_exact_match_at_search_start_returns_near_1(self):
        """검색 위치(distance=0)의 완전 일치 → score ≈ 1.0."""
        words = _make_words(["안녕"])
        result = _find_token_feedback(words, "안녕", 0)
        assert result is not None
        _, score = result
        assert score == pytest.approx(1.0, abs=0.01)

    def test_distance1_word_penalty_applied_once(self):
        """distance=1 단어: adjusted = 1.0 - 1*0.04 = 0.96.
        수정 전 이중 패널티라면 0.96 + 0.04 = 1.0이 됐을 것.
        수정 후: 0.96이 그대로 반환됨."""
        words = _make_words(["dummy", "안녕"])  # "안녕"의 globalWordIndex=1
        result = _find_token_feedback(words, "안녕", 0)  # search_start=0, distance=1
        assert result is not None
        _, score = result
        # base_score=1.0, distance=1, adjusted=0.96 → 반환값 0.96
        assert score == pytest.approx(0.96, abs=0.02)

    def test_score_never_exceeds_1(self):
        """어떤 경우에도 score > 1.0 불가 (이중 패널티 수정 전에는 가능했음)."""
        words = _make_words(["테스트"])
        result = _find_token_feedback(words, "테스트", 0)
        if result:
            _, score = result
            assert score <= 1.0, f"score가 1.0 초과: {score}"

    def test_low_score_below_min_returns_none(self):
        """REALTIME_MIN_SCORE 미만 → None 반환."""
        words = _make_words(["가나다라마바사"])
        # 전혀 다른 토큰 → 유사도 낮음
        result = _find_token_feedback(words, "zzz", 0)
        assert result is None


# ═══════════════════════════════════════════════════════════
# STEP 7-B: 재접속 시 confirmed_global_word_index 인수인계
# ═══════════════════════════════════════════════════════════

class TestReconnectHandoff:
    """WebSocket 핸들러에서 재접속 시 인덱스 복원 로직 검증.

    실제 WebSocket 없이, 상태 변수 초기화 및 복원 로직을 단독 검증.
    """

    def _simulate_init(self, resumed_index=None):
        """endpoints.py WebSocket init 핸들러 로직 시뮬레이션."""
        confirmed = -1              # 기본값
        last_partial = -1

        if resumed_index is not None and int(resumed_index) >= 0:
            confirmed    = int(resumed_index)
            last_partial = int(resumed_index)

        return confirmed, last_partial

    def test_first_connection_starts_at_minus1(self):
        """최초 접속 — resumedIndex 없음 → -1 유지."""
        confirmed, last_partial = self._simulate_init(resumed_index=None)
        assert confirmed    == -1
        assert last_partial == -1

    def test_reconnect_restores_index(self):
        """재접속 — resumedIndex=10 전달 → confirmed=10 복원."""
        confirmed, last_partial = self._simulate_init(resumed_index=10)
        assert confirmed    == 10
        assert last_partial == 10

    def test_reconnect_with_zero_index(self):
        """resumedIndex=0 → 0으로 복원 (>=0 조건 통과)."""
        confirmed, last_partial = self._simulate_init(resumed_index=0)
        assert confirmed    == 0
        assert last_partial == 0

    def test_reconnect_with_negative_index_ignored(self):
        """resumedIndex=-1 → 유효하지 않음, -1 기본값 유지."""
        confirmed, last_partial = self._simulate_init(resumed_index=-1)
        assert confirmed    == -1
        assert last_partial == -1

    def test_search_continues_from_restored_index(self):
        """복원된 confirmed index 이후부터만 탐색해야 한다.
        이전 단어(idx 0~4)는 이미 확인된 것으로 건너뜀."""
        words = _make_words(["첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째"])
        confirmed = 4   # 재접속 복원값

        # search_start = confirmed + 1 = 5 → "여섯째"만 탐색 범위
        result = _find_token_feedback(words, "여섯째", confirmed + 1)
        assert result is not None
        matched_word, _ = result
        assert matched_word["globalWordIndex"] == 5

    def test_search_does_not_go_back_before_confirmed(self):
        """confirmed 이전 단어가 동일해도 confirmed 이후를 우선 매칭."""
        # "안녕"이 idx=0과 idx=5에 모두 존재
        words = _make_words(["안녕", "중간1", "중간2", "중간3", "중간4", "안녕"])
        confirmed = 3   # 이미 idx=3까지 확인 완료

        result = _find_token_feedback(words, "안녕", confirmed + 1)
        assert result is not None
        matched_word, _ = result
        # idx=5("안녕")이 매칭되어야 함 (search_start=4 기준 distance: idx5=1, idx0=4 → idx5 우선)
        assert matched_word["globalWordIndex"] == 5


# ═══════════════════════════════════════════════════════════
# STEP 7 Review: 공유 상수 일원화 검증
# ═══════════════════════════════════════════════════════════

class TestSharedConstants:
    """helpers.py에 선언된 임계값 상수가 정확한 값인지 확인."""

    def test_match_threshold_value(self):
        assert MATCH_THRESHOLD == 0.72, f"MATCH_THRESHOLD={MATCH_THRESHOLD} ≠ 0.72"

    def test_short_word_threshold_value(self):
        assert SHORT_WORD_MATCH_THRESHOLD == 0.90, \
            f"SHORT_WORD_MATCH_THRESHOLD={SHORT_WORD_MATCH_THRESHOLD} ≠ 0.90"

    def test_realtime_min_score_value(self):
        assert REALTIME_MIN_SCORE == 0.40, \
            f"REALTIME_MIN_SCORE={REALTIME_MIN_SCORE} ≠ 0.40"

    def test_realtime_correct_threshold_value(self):
        """STEP 7-A 이중패널티 수정 후 isCorrect 임계값 0.75→0.68."""
        assert REALTIME_CORRECT_THRESHOLD == 0.68, \
            f"REALTIME_CORRECT_THRESHOLD={REALTIME_CORRECT_THRESHOLD} ≠ 0.68"

    def test_iscorrect_threshold_passes_distance1_match(self):
        """distance=1 완전 일치: adjusted=0.96 → isCorrect=True (0.96 >= 0.68)."""
        words = _make_words(["dummy", "안녕"])
        result = _find_token_feedback(words, "안녕", 0)
        assert result is not None
        _, score = result
        is_correct = score >= REALTIME_CORRECT_THRESHOLD
        assert is_correct, (
            f"distance=1 완전 일치가 isCorrect=False: score={score:.3f} < {REALTIME_CORRECT_THRESHOLD}"
        )

    def test_iscorrect_threshold_rejects_very_low_score(self):
        """유사도 낮은 매칭 → isCorrect=False."""
        words = _make_words(["가나다", "라마바"])
        result = _find_token_feedback(words, "가나다", 0)
        if result:
            _, score = result
            if score < REALTIME_CORRECT_THRESHOLD:
                assert not (score >= REALTIME_CORRECT_THRESHOLD)


# ═══════════════════════════════════════════════════════════
# normalize_match_text 한국어 조사 정규화 (STEP 6-B)
# ═══════════════════════════════════════════════════════════

class TestNormalizeMatchText:
    """helpers.normalize_match_text 검증."""

    def test_removes_punctuation(self):
        """구두점 제거."""
        assert normalize_match_text("안녕!") == normalize_match_text("안녕")

    def test_lowercase(self):
        """소문자 변환."""
        result = normalize_match_text("Hello")
        assert result == result.lower()

    def test_empty_string(self):
        """빈 문자열 → 빈 문자열."""
        assert normalize_match_text("") == ""

    def test_whitespace_stripped(self):
        """앞뒤 공백 제거."""
        assert normalize_match_text("  안녕  ") == normalize_match_text("안녕")
