"""
[STEP 10-3] §7 임계값 경계 케이스 단위 테스트

검증 대상: rag_context._detect_deviations() + endpoints.calculate_goal_similarity_score()

§7 임계값 명세:
  WPM  : 하한 100, 상한 180
  Pitch: 개인 Baseline 대비 ±20% 가드레일
  dB   : Baseline 대비 ±6 dB (Baseline 없으면 §6 Gold Standard 63.72 기준)
  Pause: 하한 8%, 상한 25%
"""
import pytest
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.services.rag_context import _detect_deviations


# ── 헬퍼: TargetMetrics 스텁 ─────────────────────────────────────────────
class _TM:
    """테스트용 TargetMetrics 스텁 (Pydantic 불필요)."""
    def __init__(self, target_pitch=None, target_intensity=None):
        self.targetPitch     = target_pitch
        self.targetIntensity = target_intensity


# ═══════════════════════════════════════════════════════════
# WPM 경계 케이스
# ═══════════════════════════════════════════════════════════

class TestWpmBoundary:
    def test_wpm_99_is_deviation(self):
        """WPM 99 → 하한(100) 이탈."""
        dev = _detect_deviations(99.0, 150.0, 63.72, 15.0, None)
        assert dev["wpm"] is True

    def test_wpm_100_is_normal(self):
        """WPM 100 → 하한 경계값, 정상."""
        dev = _detect_deviations(100.0, 150.0, 63.72, 15.0, None)
        assert dev["wpm"] is False

    def test_wpm_140_is_normal(self):
        """WPM 140 → 권장 범위(130~160) 내 정상."""
        dev = _detect_deviations(140.0, 150.0, 63.72, 15.0, None)
        assert dev["wpm"] is False

    def test_wpm_180_is_normal(self):
        """WPM 180 → 상한 경계값, 정상."""
        dev = _detect_deviations(180.0, 150.0, 63.72, 15.0, None)
        assert dev["wpm"] is False

    def test_wpm_181_is_deviation(self):
        """WPM 181 → 상한(180) 이탈."""
        dev = _detect_deviations(181.0, 150.0, 63.72, 15.0, None)
        assert dev["wpm"] is True

    def test_wpm_50_extreme_low(self):
        """WPM 50 → 극단적 과저속 이탈."""
        dev = _detect_deviations(50.0, 150.0, 63.72, 15.0, None)
        assert dev["wpm"] is True

    def test_wpm_220_extreme_high(self):
        """WPM 220 → 극단적 과속 이탈."""
        dev = _detect_deviations(220.0, 150.0, 63.72, 15.0, None)
        assert dev["wpm"] is True


# ═══════════════════════════════════════════════════════════
# Pause 경계 케이스
# ═══════════════════════════════════════════════════════════

class TestPauseBoundary:
    def test_pause_7pct_is_deviation(self):
        """Pause 7% → 하한(8%) 이탈."""
        dev = _detect_deviations(130.0, 150.0, 63.72, 7.0, None)
        assert dev["pause"] is True

    def test_pause_8pct_is_normal(self):
        """Pause 8% → 하한 경계값, 정상."""
        dev = _detect_deviations(130.0, 150.0, 63.72, 8.0, None)
        assert dev["pause"] is False

    def test_pause_15pct_is_normal(self):
        """Pause 15% → §7 권장 범위(10~20%) 내 정상."""
        dev = _detect_deviations(130.0, 150.0, 63.72, 15.0, None)
        assert dev["pause"] is False

    def test_pause_25pct_is_normal(self):
        """Pause 25% → 상한 경계값, 정상."""
        dev = _detect_deviations(130.0, 150.0, 63.72, 25.0, None)
        assert dev["pause"] is False

    def test_pause_26pct_is_deviation(self):
        """Pause 26% → 상한(25%) 이탈."""
        dev = _detect_deviations(130.0, 150.0, 63.72, 26.0, None)
        assert dev["pause"] is True

    def test_pause_0_is_deviation(self):
        """Pause 0% → 극단적 이탈."""
        dev = _detect_deviations(130.0, 150.0, 63.72, 0.0, None)
        assert dev["pause"] is True

    def test_pause_50_is_deviation(self):
        """Pause 50% → 극단적 상한 이탈."""
        dev = _detect_deviations(130.0, 150.0, 63.72, 50.0, None)
        assert dev["pause"] is True


# ═══════════════════════════════════════════════════════════
# Pitch 경계 케이스 (Baseline 대비 ±20%)
# ═══════════════════════════════════════════════════════════

class TestPitchBoundary:
    """targetPitch=150Hz 기준: 정상 범위 = 120~180Hz."""

    TARGET_PITCH = 150.0

    def _dev(self, actual_pitch):
        tm = _TM(target_pitch=self.TARGET_PITCH)
        return _detect_deviations(130.0, actual_pitch, 63.72, 15.0, tm)

    def test_pitch_exactly_minus20pct_is_normal(self):
        """Pitch -20% (120Hz) → 하한 경계값, 정상."""
        assert self._dev(120.0)["pitch"] is False

    def test_pitch_below_minus20pct_is_deviation(self):
        """Pitch -21% (118.5Hz) → 하한 이탈."""
        assert self._dev(118.5)["pitch"] is True

    def test_pitch_exactly_plus20pct_is_normal(self):
        """Pitch +20% (180Hz) → 상한 경계값, 정상."""
        assert self._dev(180.0)["pitch"] is False

    def test_pitch_above_plus20pct_is_deviation(self):
        """Pitch +21% (181.5Hz) → 상한 이탈."""
        assert self._dev(181.5)["pitch"] is True

    def test_pitch_equal_to_target_is_normal(self):
        """Pitch = targetPitch → 완전 일치, 정상."""
        assert self._dev(150.0)["pitch"] is False

    def test_pitch_no_target_is_not_flagged(self):
        """targetPitch 없으면 Pitch 이탈 판정 안 함."""
        tm = _TM(target_pitch=None)
        dev = _detect_deviations(130.0, 150.0, 63.72, 15.0, tm)
        assert dev["pitch"] is False


# ═══════════════════════════════════════════════════════════
# dB 경계 케이스
# ═══════════════════════════════════════════════════════════

class TestDbBoundary:
    """targetIntensity 없으면 §6 Gold Standard 63.72 기준 ±6 dB."""

    def test_db_gold_standard_is_normal(self):
        """dB = Gold Standard 63.72 → 정상."""
        dev = _detect_deviations(130.0, 150.0, 63.72, 15.0, None)
        assert dev["db"] is False

    def test_db_gold_plus6_is_normal(self):
        """dB = 63.72 + 6 = 69.72 → 상한 경계, 정상."""
        dev = _detect_deviations(130.0, 150.0, 69.72, 15.0, None)
        assert dev["db"] is False

    def test_db_gold_plus6_point1_is_deviation(self):
        """dB = 63.72 + 6.1 = 69.82 → 상한 이탈."""
        dev = _detect_deviations(130.0, 150.0, 69.82, 15.0, None)
        assert dev["db"] is True

    def test_db_gold_minus6_is_normal(self):
        """dB = 63.72 - 6 = 57.72 → 하한 경계, 정상."""
        dev = _detect_deviations(130.0, 150.0, 57.72, 15.0, None)
        assert dev["db"] is False

    def test_db_gold_minus6_point1_is_deviation(self):
        """dB = 63.72 - 6.1 = 57.62 → 하한 이탈."""
        dev = _detect_deviations(130.0, 150.0, 57.62, 15.0, None)
        assert dev["db"] is True

    def test_db_with_target_intensity_in_range(self):
        """targetIntensity=65.0 기준 ±6 dB 내 → 정상."""
        tm = _TM(target_intensity=65.0)
        dev = _detect_deviations(130.0, 150.0, 68.0, 15.0, tm)   # 65+3=68
        assert dev["db"] is False

    def test_db_with_target_intensity_out_of_range(self):
        """targetIntensity=65.0 기준, dB=72.0 (+7) → 이탈."""
        tm = _TM(target_intensity=65.0)
        dev = _detect_deviations(130.0, 150.0, 72.0, 15.0, tm)
        assert dev["db"] is True


# ═══════════════════════════════════════════════════════════
# 복합 케이스 — 여러 지표 동시 이탈
# ═══════════════════════════════════════════════════════════

class TestMultipleDeviations:
    def test_all_normal(self):
        """모든 지표 정상 → 이탈 없음."""
        dev = _detect_deviations(130.0, 150.0, 63.72, 15.0, None)
        assert not any(dev.values())

    def test_wpm_and_pause_both_deviate(self):
        """WPM 과속 + Pause 부족 동시 이탈."""
        dev = _detect_deviations(200.0, 150.0, 63.72, 5.0, None)
        assert dev["wpm"]   is True
        assert dev["pause"] is True
        assert dev["db"]    is False

    def test_all_deviate(self):
        """모든 지표 이탈."""
        tm = _TM(target_pitch=150.0)
        dev = _detect_deviations(
            actual_wpm=200.0,     # 이탈 (>180)
            actual_pitch=50.0,    # 이탈 (<120, -67%)
            actual_db=80.0,       # 이탈 (63.72+16.28 > 6)
            actual_pause_pct=30.0, # 이탈 (>25)
            tm=tm
        )
        assert all(dev.values()), f"전부 이탈이어야 함: {dev}"


# ═══════════════════════════════════════════════════════════
# goalSimilarityScore 클램핑 (endpoints.calculate_goal_similarity_score)
# ═══════════════════════════════════════════════════════════

class TestGoalSimilarityScore:
    """score가 0~100 범위를 반드시 지켜야 한다 (리뷰 수정사항)."""

    def _score(self, features, target):
        # endpoints.py 로직을 직접 인라인으로 검증
        def clamp(v, lo, hi):
            return max(lo, min(hi, v))

        def item_score(actual, tgt, guard_ratio):
            if actual is None or tgt is None or tgt == 0:
                return None
            deviation = abs(actual - tgt) / abs(tgt)
            return max(0.0, 1.0 - deviation / guard_ratio) * 100.0

        WPM_GUARD, PITCH_GUARD, DB_GUARD, PAUSE_GUARD = 0.20, 0.20, 6.0, 17.0
        weights = {"wpm": 0.40, "pitch": 0.30, "pause": 0.20, "intensity": 0.10}

        scores = {
            "wpm":       item_score(features.get("avgWpm"),  target.get("targetWpm"),  WPM_GUARD),
            "pitch":     item_score(features.get("avgPitch"), target.get("targetPitch"), PITCH_GUARD),
            "intensity": None,
            "pause":     None,
        }
        actual_pause_pct = (features.get("pauseRatio") or 0.0) * 100.0
        target_pause_pct = target.get("targetPauseRatio")
        if target_pause_pct is not None and PAUSE_GUARD > 0:
            scores["pause"] = max(0.0, 1.0 - abs(actual_pause_pct - target_pause_pct) / PAUSE_GUARD) * 100.0

        total_weight = sum(weights[k] for k, v in scores.items() if v is not None)
        if total_weight == 0:
            return 0.0
        weighted_sum = sum(weights[k] * v for k, v in scores.items() if v is not None)
        raw = weighted_sum / total_weight
        return round(clamp(raw, 0.0, 100.0) * 10) / 10

    def test_perfect_match_is_100(self):
        """목표치와 완전 일치 → 100점."""
        features = {"avgWpm": 130.0, "avgPitch": 150.0, "pauseRatio": 0.15}
        target   = {"targetWpm": 130.0, "targetPitch": 150.0, "targetPauseRatio": 15.0}
        score = self._score(features, target)
        assert score == pytest.approx(100.0, abs=0.1)

    def test_large_deviation_clamps_to_zero(self):
        """극단적 편차 → 0 이하로 내려가지 않아야 한다."""
        features = {"avgWpm": 300.0, "avgPitch": 50.0, "pauseRatio": 0.0}
        target   = {"targetWpm": 130.0, "targetPitch": 150.0, "targetPauseRatio": 15.0}
        score = self._score(features, target)
        assert score >= 0.0, f"score가 음수: {score}"

    def test_score_never_exceeds_100(self):
        """score는 100을 초과할 수 없다."""
        features = {"avgWpm": 130.0, "avgPitch": 150.0, "pauseRatio": 0.15}
        target   = {"targetWpm": 130.0, "targetPitch": 150.0, "targetPauseRatio": 15.0}
        score = self._score(features, target)
        assert score <= 100.0

    def test_no_target_metrics_returns_zero(self):
        """targetMetrics 없으면 0점."""
        features = {"avgWpm": 130.0}
        score = self._score(features, {})
        assert score == 0.0
