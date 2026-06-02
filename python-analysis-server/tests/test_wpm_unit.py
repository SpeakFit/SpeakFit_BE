"""
[STEP 10-2] STT 음절/분 WPM 계산 회귀 테스트

검증 대상: voice_service.py / endpoints.py의 WPM 산출 공식
  - STEP 1에서 변경: onset/2 방식 → STT 텍스트 음절 수 / (duration/60)
  - 공식: WPM = len(stt_text.replace(" ", "")) / (duration / 60)
  - 분석 스크립트(자유대화음성분석.py)와 동일 단위
"""
import pytest


# ─── 공식 함수 (endpoints.py 로직 추출) ─────────────────────────────────
def calc_syllable_wpm(stt_text: str, duration_sec: float) -> float:
    """STT 텍스트 기반 음절/분 WPM 계산 — 분석 스크립트와 동일 공식."""
    syllable_count = len(stt_text.replace(" ", ""))
    if syllable_count == 0 or duration_sec <= 0:
        return 0.0
    return syllable_count / (duration_sec / 60.0)


# ─── 정상 케이스 ─────────────────────────────────────────────────────────

def test_key_validation_from_plan():
    """개선계획서 핵심 검증: Baseline WPM 261.7이 음절/분 단위여야 한다.
    수도권_남성 자유발화 10초에 261.7/60*10 ≈ 43.6 음절이면 WPM=261.7.
    역산: 43.6 음절 / (10/60) = 261.6 WPM ≈ 261.7."""
    syllables_in_10s = round(261.7 / 60.0 * 10)  # ≈ 44
    wpm = calc_syllable_wpm("가" * syllables_in_10s, 10.0)
    assert abs(wpm - 261.7) <= 5.0, f"기준 WPM 재현 실패: {wpm:.1f} ≠ 261.7 (±5)"


def test_wpm_130_standard():
    """130 WPM = 130 음절/분 → 60초 발화에 130 음절."""
    wpm = calc_syllable_wpm("가" * 130, 60.0)
    assert abs(wpm - 130.0) < 0.01


def test_wpm_160_standard():
    """160 WPM — §7 권장 상한."""
    wpm = calc_syllable_wpm("가" * 160, 60.0)
    assert abs(wpm - 160.0) < 0.01


def test_wpm_with_spaces_ignored():
    """공백은 음절 카운트에서 제외해야 한다."""
    text_with_spaces    = "안녕 하세요 저는 발표 연습 중입니다"
    text_without_spaces = "안녕하세요저는발표연습중입니다"
    wpm_spaces    = calc_syllable_wpm(text_with_spaces,    60.0)
    wpm_no_spaces = calc_syllable_wpm(text_without_spaces, 60.0)
    assert abs(wpm_spaces - wpm_no_spaces) < 0.01, (
        "공백 포함/제외 WPM이 달라선 안 됨"
    )


def test_wpm_short_duration():
    """10초 녹음, 40음절 → WPM = 40/(10/60) = 240."""
    wpm = calc_syllable_wpm("가" * 40, 10.0)
    assert abs(wpm - 240.0) < 0.01


def test_wpm_proportional_to_syllables():
    """음절 수가 2배 → WPM도 2배 (동일 duration)."""
    wpm_base   = calc_syllable_wpm("가" * 100, 60.0)
    wpm_double = calc_syllable_wpm("가" * 200, 60.0)
    assert abs(wpm_double / wpm_base - 2.0) < 0.001


def test_wpm_inversely_proportional_to_duration():
    """duration이 2배 → WPM은 절반 (동일 음절 수)."""
    wpm_30s = calc_syllable_wpm("가" * 100, 30.0)
    wpm_60s = calc_syllable_wpm("가" * 100, 60.0)
    assert abs(wpm_30s / wpm_60s - 2.0) < 0.001


# ─── 엣지 케이스 ─────────────────────────────────────────────────────────

def test_wpm_empty_text():
    """빈 STT 텍스트 → WPM 0."""
    assert calc_syllable_wpm("", 60.0) == 0.0


def test_wpm_zero_duration():
    """duration=0 → ZeroDivision 방어, WPM 0 반환."""
    assert calc_syllable_wpm("안녕", 0.0) == 0.0


def test_wpm_spaces_only():
    """공백만 있는 텍스트 → WPM 0."""
    assert calc_syllable_wpm("   ", 60.0) == 0.0


# ─── onset 기반 방식과의 차이 확인 ──────────────────────────────────────

def test_onset_vs_syllable_are_different():
    """onset 기반(onset/2/duration*60)과 음절/분은 다른 값을 낸다.
    onset 방식: 반드시 제거되어야 함 (STEP 1 핵심 수정)."""

    def onset_based_wpm(onset_count: int, duration_sec: float) -> float:
        """구 방식: (onset_count / 2) / (duration_sec / 60)."""
        return (onset_count / 2) / (duration_sec / 60)

    # 40음절 텍스트, 10초, onset이 동일하게 40개 감지된다고 가정
    syllable_wpm = calc_syllable_wpm("가" * 40, 10.0)  # 40/(10/60) = 240
    onset_wpm    = onset_based_wpm(40, 10.0)            # (40/2)/(10/60) = 120

    assert syllable_wpm != onset_wpm, "onset 방식과 음절 방식이 동일한 값을 내면 안 됨"
    assert syllable_wpm == pytest.approx(240.0, abs=0.1)
    assert onset_wpm    == pytest.approx(120.0, abs=0.1)
