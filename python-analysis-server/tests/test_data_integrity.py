"""
[STEP 10-1] 데이터 정합성 테스트 — §4×§5 ≈ §3 교차검증

검증 공식: Baseline(§4) × 보정배율(§5) ≈ 군집 중심점(§3)
대표 예시: 수도권_남성 Baseline WPM 261.7 × 열정적인 배율 0.40 = 104.68 ≈ 104.00 (§3)

허용 오차: WPM·Pitch 각 3% 이내 (K-means 반올림 + 배율 소수점 절사 감안)
검증 범위: 40행 전체 (5지역 × 2성별 × 4스타일)
"""
import pytest

# ── §4. 자유대화 베이스라인 (지역×성별, 10행) ─────────────────────────────
# key: (지역코드, 성별)  value: {wpm, pitch}
BASELINES = {
    ("수도권", "남성"): {"wpm": 261.7,  "pitch": 173.3},
    ("수도권", "여성"): {"wpm": 252.3,  "pitch": 292.0},
    ("경상",   "남성"): {"wpm": 269.4,  "pitch": 175.4},
    ("경상",   "여성"): {"wpm": 246.9,  "pitch": 293.6},
    ("충청",   "남성"): {"wpm": 253.1,  "pitch": 173.9},
    ("충청",   "여성"): {"wpm": 254.4,  "pitch": 295.2},
    ("강원",   "남성"): {"wpm": 265.4,  "pitch": 181.1},
    ("강원",   "여성"): {"wpm": 241.7,  "pitch": 299.6},
    ("전라",   "남성"): {"wpm": 272.2,  "pitch": 171.2},
    ("전라",   "여성"): {"wpm": 241.3,  "pitch": 310.4},
}

# ── §5. 보정 배율 (40행) ─────────────────────────────────────────────────
# key: (지역코드, 성별, 스타일)  value: {pitch_ratio, wpm_ratio}
RATIOS = {
    ("수도권", "남성", "신중한"):      {"pitch_ratio": 0.80, "wpm_ratio": 0.32},
    ("수도권", "남성", "열정적인"):    {"pitch_ratio": 1.03, "wpm_ratio": 0.40},
    ("수도권", "남성", "전달력 있는"): {"pitch_ratio": 0.93, "wpm_ratio": 0.32},
    ("수도권", "남성", "지적인"):      {"pitch_ratio": 0.79, "wpm_ratio": 0.38},
    ("수도권", "여성", "신중한"):      {"pitch_ratio": 0.71, "wpm_ratio": 0.33},
    ("수도권", "여성", "열정적인"):    {"pitch_ratio": 0.91, "wpm_ratio": 0.41},
    ("수도권", "여성", "전달력 있는"): {"pitch_ratio": 0.82, "wpm_ratio": 0.32},
    ("수도권", "여성", "지적인"):      {"pitch_ratio": 0.70, "wpm_ratio": 0.39},
    ("경상",   "남성", "신중한"):      {"pitch_ratio": 0.83, "wpm_ratio": 0.31},
    ("경상",   "남성", "열정적인"):    {"pitch_ratio": 1.07, "wpm_ratio": 0.39},
    ("경상",   "남성", "전달력 있는"): {"pitch_ratio": 0.97, "wpm_ratio": 0.31},
    ("경상",   "남성", "지적인"):      {"pitch_ratio": 0.82, "wpm_ratio": 0.38},
    ("경상",   "여성", "신중한"):      {"pitch_ratio": 0.70, "wpm_ratio": 0.34},
    ("경상",   "여성", "열정적인"):    {"pitch_ratio": 0.90, "wpm_ratio": 0.42},
    ("경상",   "여성", "전달력 있는"): {"pitch_ratio": 0.82, "wpm_ratio": 0.33},
    ("경상",   "여성", "지적인"):      {"pitch_ratio": 0.69, "wpm_ratio": 0.40},
    ("충청",   "남성", "신중한"):      {"pitch_ratio": 0.87, "wpm_ratio": 0.32},
    ("충청",   "남성", "열정적인"):    {"pitch_ratio": 1.11, "wpm_ratio": 0.39},
    ("충청",   "남성", "전달력 있는"): {"pitch_ratio": 1.01, "wpm_ratio": 0.31},
    ("충청",   "남성", "지적인"):      {"pitch_ratio": 0.85, "wpm_ratio": 0.38},
    ("충청",   "여성", "신중한"):      {"pitch_ratio": 0.73, "wpm_ratio": 0.32},
    ("충청",   "여성", "열정적인"):    {"pitch_ratio": 0.94, "wpm_ratio": 0.40},
    ("충청",   "여성", "전달력 있는"): {"pitch_ratio": 0.85, "wpm_ratio": 0.32},
    ("충청",   "여성", "지적인"):      {"pitch_ratio": 0.72, "wpm_ratio": 0.39},
    ("강원",   "남성", "신중한"):      {"pitch_ratio": 0.70, "wpm_ratio": 0.32},
    ("강원",   "남성", "열정적인"):    {"pitch_ratio": 0.90, "wpm_ratio": 0.39},
    ("강원",   "남성", "전달력 있는"): {"pitch_ratio": 0.82, "wpm_ratio": 0.31},
    ("강원",   "남성", "지적인"):      {"pitch_ratio": 0.69, "wpm_ratio": 0.38},
    ("강원",   "여성", "신중한"):      {"pitch_ratio": 0.63, "wpm_ratio": 0.36},
    ("강원",   "여성", "열정적인"):    {"pitch_ratio": 0.82, "wpm_ratio": 0.44},
    ("강원",   "여성", "전달력 있는"): {"pitch_ratio": 0.74, "wpm_ratio": 0.35},
    ("강원",   "여성", "지적인"):      {"pitch_ratio": 0.63, "wpm_ratio": 0.43},
    ("전라",   "남성", "신중한"):      {"pitch_ratio": 0.84, "wpm_ratio": 0.29},
    ("전라",   "남성", "열정적인"):    {"pitch_ratio": 1.08, "wpm_ratio": 0.36},
    ("전라",   "남성", "전달력 있는"): {"pitch_ratio": 0.98, "wpm_ratio": 0.29},
    ("전라",   "남성", "지적인"):      {"pitch_ratio": 0.83, "wpm_ratio": 0.35},
    ("전라",   "여성", "신중한"):      {"pitch_ratio": 0.67, "wpm_ratio": 0.36},
    ("전라",   "여성", "열정적인"):    {"pitch_ratio": 0.86, "wpm_ratio": 0.45},
    ("전라",   "여성", "전달력 있는"): {"pitch_ratio": 0.78, "wpm_ratio": 0.36},
    ("전라",   "여성", "지적인"):      {"pitch_ratio": 0.66, "wpm_ratio": 0.43},
}

# ── §3. 군집 중심점 (40행) — §5 지역 코드로 통일 ─────────────────────────
# key: (지역코드, 성별, 스타일)  value: {wpm, pitch}
# §3 원본의 "표준어"=수도권, "경상도"=경상, "전라도"=전라, "강원도"=강원, "충청도"=충청
CLUSTER_CENTERS = {
    ("수도권", "남성", "열정적인"):    {"wpm": 104.0016, "pitch": 178.7223},
    ("수도권", "남성", "지적인"):      {"wpm": 100.6109, "pitch": 137.0491},
    ("수도권", "남성", "신중한"):      {"wpm":  83.7733, "pitch": 139.0339},
    ("수도권", "남성", "전달력 있는"): {"wpm":  82.7749, "pitch": 161.9460},
    ("수도권", "여성", "열정적인"):    {"wpm": 102.5846, "pitch": 265.0538},
    ("수도권", "여성", "지적인"):      {"wpm":  99.2401, "pitch": 203.2504},
    ("수도권", "여성", "신중한"):      {"wpm":  82.6319, "pitch": 206.1939},
    ("수도권", "여성", "전달력 있는"): {"wpm":  81.6471, "pitch": 240.1737},
    ("경상",   "남성", "열정적인"):    {"wpm": 105.1174, "pitch": 187.7469},
    ("경상",   "남성", "지적인"):      {"wpm": 101.6904, "pitch": 143.9694},
    ("경상",   "남성", "신중한"):      {"wpm":  84.6720, "pitch": 146.0544},
    ("경상",   "남성", "전달력 있는"): {"wpm":  83.6630, "pitch": 170.1235},
    ("경상",   "여성", "열정적인"):    {"wpm": 102.8144, "pitch": 265.4412},
    ("경상",   "여성", "지적인"):      {"wpm":  99.4625, "pitch": 203.5474},
    ("경상",   "여성", "신중한"):      {"wpm":  82.8170, "pitch": 206.4952},
    ("경상",   "여성", "전달력 있는"): {"wpm":  81.8301, "pitch": 240.5247},
    ("전라",   "남성", "열정적인"):    {"wpm":  97.9045, "pitch": 184.2337},
    ("전라",   "남성", "지적인"):      {"wpm":  94.7126, "pitch": 141.2754},
    ("전라",   "남성", "신중한"):      {"wpm":  78.8621, "pitch": 143.3213},
    ("전라",   "남성", "전달력 있는"): {"wpm":  77.9222, "pitch": 166.9400},
    ("전라",   "여성", "열정적인"):    {"wpm": 108.2841, "pitch": 268.0942},
    ("전라",   "여성", "지적인"):      {"wpm": 104.7539, "pitch": 205.5819},
    ("전라",   "여성", "신중한"):      {"wpm":  87.2228, "pitch": 208.5591},
    ("전라",   "여성", "전달력 있는"): {"wpm":  86.1834, "pitch": 242.9287},
    ("강원",   "남성", "열정적인"):    {"wpm": 104.4559, "pitch": 163.6374},
    ("강원",   "남성", "지적인"):      {"wpm": 101.0504, "pitch": 125.4816},
    ("강원",   "남성", "신중한"):      {"wpm":  84.1392, "pitch": 127.2988},
    ("강원",   "남성", "전달력 있는"): {"wpm":  83.1365, "pitch": 148.2771},
    ("강원",   "여성", "열정적인"):    {"wpm": 106.6758, "pitch": 246.2662},
    ("강원",   "여성", "지적인"):      {"wpm": 103.1979, "pitch": 188.8435},
    ("강원",   "여성", "신중한"):      {"wpm":  85.9273, "pitch": 191.5783},
    ("강원",   "여성", "전달력 있는"): {"wpm":  84.9033, "pitch": 223.1496},
    ("충청",   "남성", "열정적인"):    {"wpm":  99.3274, "pitch": 193.8592},
    ("충청",   "남성", "지적인"):      {"wpm":  96.0891, "pitch": 148.6564},
    ("충청",   "남성", "신중한"):      {"wpm":  80.0082, "pitch": 150.8093},
    ("충청",   "남성", "전달력 있는"): {"wpm":  79.0547, "pitch": 175.6620},
    ("충청",   "여성", "열정적인"):    {"wpm": 101.5473, "pitch": 276.4879},
    ("충청",   "여성", "지적인"):      {"wpm":  98.2366, "pitch": 212.0184},
    ("충청",   "여성", "신중한"):      {"wpm":  81.7963, "pitch": 215.0888},
    ("충청",   "여성", "전달력 있는"): {"wpm":  80.8215, "pitch": 250.5345},
}

TOLERANCE = 0.03  # 3% 허용 오차


def _percent_error(computed, expected):
    """백분율 오차 계산 (expected ≠ 0 보장)."""
    return abs(computed - expected) / abs(expected)


# ── 파라미터 생성: 40행 전체 ─────────────────────────────────────────────
_CASES = [
    pytest.param(region, gender, style, id=f"{region}_{gender}_{style}")
    for (region, gender, style) in CLUSTER_CENTERS
]


@pytest.mark.parametrize("region,gender,style", _CASES)
def test_wpm_integrity(region, gender, style):
    """§4 Baseline WPM × §5 WPM 배율 ≈ §3 군집 중심점 WPM (오차 3% 이내)."""
    baseline = BASELINES[(region, gender)]
    ratio    = RATIOS[(region, gender, style)]
    center   = CLUSTER_CENTERS[(region, gender, style)]

    computed_wpm = baseline["wpm"] * ratio["wpm_ratio"]
    err = _percent_error(computed_wpm, center["wpm"])

    assert err <= TOLERANCE, (
        f"[WPM 정합성 실패] {region}_{gender}_{style}: "
        f"Baseline({baseline['wpm']}) × 배율({ratio['wpm_ratio']}) "
        f"= {computed_wpm:.4f} ≠ §3 중심점({center['wpm']:.4f}), "
        f"오차 {err*100:.2f}% > {TOLERANCE*100}%"
    )


@pytest.mark.parametrize("region,gender,style", _CASES)
def test_pitch_integrity(region, gender, style):
    """§4 Baseline Pitch × §5 Pitch 배율 ≈ §3 군집 중심점 Pitch (오차 3% 이내)."""
    baseline = BASELINES[(region, gender)]
    ratio    = RATIOS[(region, gender, style)]
    center   = CLUSTER_CENTERS[(region, gender, style)]

    computed_pitch = baseline["pitch"] * ratio["pitch_ratio"]
    err = _percent_error(computed_pitch, center["pitch"])

    assert err <= TOLERANCE, (
        f"[Pitch 정합성 실패] {region}_{gender}_{style}: "
        f"Baseline({baseline['pitch']}) × 배율({ratio['pitch_ratio']}) "
        f"= {computed_pitch:.4f} ≠ §3 중심점({center['pitch']:.4f}), "
        f"오차 {err*100:.2f}% > {TOLERANCE*100}%"
    )


def test_key_example_from_plan():
    """개선계획서 대표 예시 검증: 수도권_남성 WPM 261.7 × 0.40 = 104.7 ≈ 104.0."""
    computed = 261.7 * 0.40   # = 104.68
    expected = 104.0016
    err = _percent_error(computed, expected)
    assert err <= TOLERANCE, (
        f"대표 예시 검증 실패: {computed:.2f} ≠ {expected:.4f}, 오차 {err*100:.2f}%"
    )


def test_all_40_rows_present():
    """§3/§4/§5 데이터가 각각 40행 / 10행 / 40행인지 확인."""
    assert len(CLUSTER_CENTERS) == 40, f"§3 군집 중심점: {len(CLUSTER_CENTERS)}행 (40행 필요)"
    assert len(BASELINES)        == 10, f"§4 베이스라인: {len(BASELINES)}행 (10행 필요)"
    assert len(RATIOS)           == 40, f"§5 보정배율: {len(RATIOS)}행 (40행 필요)"
