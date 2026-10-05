-- V2: 분석 기준 데이터 (SpeakFit_AI_개선계획.md "데이터 분석 지표" 원본 값)
-- 스키마 변경 없이 값만 바꿀 때도 새 마이그레이션(V{n})으로 추가한다.

-- §3 발표 스타일 군집 중심점 (40행)
-- speech_style_cluster INSERT
-- §3 대학 강의 데이터 — 발표 스타일 군집 중심점 (K-means K=4, 40행)
-- region 형식: "지역_성별" (GuideServiceImpl 필터 기준)
-- gender 컬럼은 region에서 분리한 값으로 동일하게 적재

INSERT INTO speech_style_cluster (region, gender, style_name, base_pitch, base_wpm) VALUES
-- 표준어_남성
('표준어_남성', '남성', '열정적인', 178.7223, 104.0016),
('표준어_남성', '남성', '지적인',   137.0491, 100.6109),
('표준어_남성', '남성', '신중한',   139.0339,  83.7733),
('표준어_남성', '남성', '전달력 있는', 161.9460, 82.7749),

-- 표준어_여성
('표준어_여성', '여성', '열정적인', 265.0538, 102.5846),
('표준어_여성', '여성', '지적인',   203.2504,  99.2401),
('표준어_여성', '여성', '신중한',   206.1939,  82.6319),
('표준어_여성', '여성', '전달력 있는', 240.1737, 81.6471),

-- 경상도_남성
('경상도_남성', '남성', '열정적인', 187.7469, 105.1174),
('경상도_남성', '남성', '지적인',   143.9694, 101.6904),
('경상도_남성', '남성', '신중한',   146.0544,  84.6720),
('경상도_남성', '남성', '전달력 있는', 170.1235, 83.6630),

-- 경상도_여성
('경상도_여성', '여성', '열정적인', 265.4412, 102.8144),
('경상도_여성', '여성', '지적인',   203.5474,  99.4625),
('경상도_여성', '여성', '신중한',   206.4952,  82.8170),
('경상도_여성', '여성', '전달력 있는', 240.5247, 81.8301),

-- 전라도_남성
('전라도_남성', '남성', '열정적인', 184.2337,  97.9045),
('전라도_남성', '남성', '지적인',   141.2754,  94.7126),
('전라도_남성', '남성', '신중한',   143.3213,  78.8621),
('전라도_남성', '남성', '전달력 있는', 166.9400, 77.9222),

-- 전라도_여성
('전라도_여성', '여성', '열정적인', 268.0942, 108.2841),
('전라도_여성', '여성', '지적인',   205.5819, 104.7539),
('전라도_여성', '여성', '신중한',   208.5591,  87.2228),
('전라도_여성', '여성', '전달력 있는', 242.9287, 86.1834),

-- 강원도_남성
('강원도_남성', '남성', '열정적인', 163.6374, 104.4559),
('강원도_남성', '남성', '지적인',   125.4816, 101.0504),
('강원도_남성', '남성', '신중한',   127.2988,  84.1392),
('강원도_남성', '남성', '전달력 있는', 148.2771, 83.1365),

-- 강원도_여성
('강원도_여성', '여성', '열정적인', 246.2662, 106.6758),
('강원도_여성', '여성', '지적인',   188.8435, 103.1979),
('강원도_여성', '여성', '신중한',   191.5783,  85.9273),
('강원도_여성', '여성', '전달력 있는', 223.1496, 84.9033),

-- 충청도_남성
('충청도_남성', '남성', '열정적인', 193.8592,  99.3274),
('충청도_남성', '남성', '지적인',   148.6564,  96.0891),
('충청도_남성', '남성', '신중한',   150.8093,  80.0082),
('충청도_남성', '남성', '전달력 있는', 175.6620, 79.0547),

-- 충청도_여성
('충청도_여성', '여성', '열정적인', 276.4879, 101.5473),
('충청도_여성', '여성', '지적인',   212.0184,  98.2366),
('충청도_여성', '여성', '신중한',   215.0888,  81.7963),
('충청도_여성', '여성', '전달력 있는', 250.5345, 80.8215);

-- §4 자유대화 일반 발화 베이스라인 (10행)
-- [STEP 2-C] §4 자유대화 일반 발화 베이스라인 (지역×성별, 10행)
-- gender: 'MALE' | 'FEMALE'
-- dialect: 'STANDARD'=수도권, 'GYEONGSANG'=경상, 'CHUNGCHEONG'=충청, 'GANGWON'=강원, 'JEOLLA'=전라
-- avg_pitch: Hz / avg_wpm: 음절/분 (SpeakFit_Analysis README §4 원본값)

INSERT INTO baseline_regional_metric (gender, dialect, avg_pitch, avg_wpm) VALUES
('MALE',   'STANDARD',    173.3, 261.7),
('FEMALE', 'STANDARD',    292.0, 252.3),
('MALE',   'GYEONGSANG',  175.4, 269.4),
('FEMALE', 'GYEONGSANG',  293.6, 246.9),
('MALE',   'CHUNGCHEONG', 173.9, 253.1),
('FEMALE', 'CHUNGCHEONG', 295.2, 254.4),
('MALE',   'GANGWON',     181.1, 265.4),
('FEMALE', 'GANGWON',     299.6, 241.7),
('MALE',   'JEOLLA',      171.2, 272.2),
('FEMALE', 'JEOLLA',      310.4, 241.3);

-- §5 스피치 스타일 보정 배율 (40행) : 목표치 = 사용자 Baseline × 배율
INSERT INTO speech_style_matrix (dialect, gender, style_type, pitch_ratio, wpm_ratio) VALUES
('STANDARD', 'MALE', 'CALM_LOW_TONE', 0.80, 0.32),
('STANDARD', 'MALE', 'ENERGETIC_FAST', 1.03, 0.40),
('STANDARD', 'MALE', 'DELIVERY', 0.93, 0.32),
('STANDARD', 'MALE', 'STANDARD_LECTURE', 0.79, 0.38),
('STANDARD', 'FEMALE', 'CALM_LOW_TONE', 0.71, 0.33),
('STANDARD', 'FEMALE', 'ENERGETIC_FAST', 0.91, 0.41),
('STANDARD', 'FEMALE', 'DELIVERY', 0.82, 0.32),
('STANDARD', 'FEMALE', 'STANDARD_LECTURE', 0.70, 0.39),
('GYEONGSANG', 'MALE', 'CALM_LOW_TONE', 0.83, 0.31),
('GYEONGSANG', 'MALE', 'ENERGETIC_FAST', 1.07, 0.39),
('GYEONGSANG', 'MALE', 'DELIVERY', 0.97, 0.31),
('GYEONGSANG', 'MALE', 'STANDARD_LECTURE', 0.82, 0.38),
('GYEONGSANG', 'FEMALE', 'CALM_LOW_TONE', 0.70, 0.34),
('GYEONGSANG', 'FEMALE', 'ENERGETIC_FAST', 0.90, 0.42),
('GYEONGSANG', 'FEMALE', 'DELIVERY', 0.82, 0.33),
('GYEONGSANG', 'FEMALE', 'STANDARD_LECTURE', 0.69, 0.40),
('CHUNGCHEONG', 'MALE', 'CALM_LOW_TONE', 0.87, 0.32),
('CHUNGCHEONG', 'MALE', 'ENERGETIC_FAST', 1.11, 0.39),
('CHUNGCHEONG', 'MALE', 'DELIVERY', 1.01, 0.31),
('CHUNGCHEONG', 'MALE', 'STANDARD_LECTURE', 0.85, 0.38),
('CHUNGCHEONG', 'FEMALE', 'CALM_LOW_TONE', 0.73, 0.32),
('CHUNGCHEONG', 'FEMALE', 'ENERGETIC_FAST', 0.94, 0.40),
('CHUNGCHEONG', 'FEMALE', 'DELIVERY', 0.85, 0.32),
('CHUNGCHEONG', 'FEMALE', 'STANDARD_LECTURE', 0.72, 0.39),
('GANGWON', 'MALE', 'CALM_LOW_TONE', 0.70, 0.32),
('GANGWON', 'MALE', 'ENERGETIC_FAST', 0.90, 0.39),
('GANGWON', 'MALE', 'DELIVERY', 0.82, 0.31),
('GANGWON', 'MALE', 'STANDARD_LECTURE', 0.69, 0.38),
('GANGWON', 'FEMALE', 'CALM_LOW_TONE', 0.63, 0.36),
('GANGWON', 'FEMALE', 'ENERGETIC_FAST', 0.82, 0.44),
('GANGWON', 'FEMALE', 'DELIVERY', 0.74, 0.35),
('GANGWON', 'FEMALE', 'STANDARD_LECTURE', 0.63, 0.43),
('JEOLLA', 'MALE', 'CALM_LOW_TONE', 0.84, 0.29),
('JEOLLA', 'MALE', 'ENERGETIC_FAST', 1.08, 0.36),
('JEOLLA', 'MALE', 'DELIVERY', 0.98, 0.29),
('JEOLLA', 'MALE', 'STANDARD_LECTURE', 0.83, 0.35),
('JEOLLA', 'FEMALE', 'CALM_LOW_TONE', 0.67, 0.36),
('JEOLLA', 'FEMALE', 'ENERGETIC_FAST', 0.86, 0.45),
('JEOLLA', 'FEMALE', 'DELIVERY', 0.78, 0.36),
('JEOLLA', 'FEMALE', 'STANDARD_LECTURE', 0.66, 0.43);

-- §8 청중 조건별 권장 범위 (12행)
-- wpm: 음절/분, pitch_ratio: Baseline 대비 변화율(±), intensity_delta: Baseline 대비 dB, pause_ratio: %
INSERT INTO target_audience_metric
    (audience_understanding, audience_type, min_wpm, max_wpm, min_pitch_ratio, max_pitch_ratio,
     min_intensity_delta, max_intensity_delta, min_pause_ratio, max_pause_ratio) VALUES
('LOW', 'CHILD', 90, 120, -0.08, 0.08, 0, 3, 15, 25),
('LOW', 'YOUTH', 100, 130, -0.1, 0.1, 0, 3, 12, 22),
('LOW', 'ADULT', 100, 130, -0.1, 0.1, 0, 3, 10, 20),
('LOW', 'SENIOR', 90, 120, -0.08, 0.08, 0, 3, 15, 25),
('MIDDLE', 'CHILD', 110, 130, -0.1, 0.1, 0, 4, 10, 20),
('MIDDLE', 'YOUTH', 120, 145, -0.12, 0.12, 0, 5, 8, 18),
('MIDDLE', 'ADULT', 120, 150, -0.12, 0.12, 0, 5, 8, 16),
('MIDDLE', 'SENIOR', 110, 135, -0.1, 0.1, 0, 4, 10, 20),
('HIGH', 'CHILD', 120, 140, -0.12, 0.12, 0, 6, 8, 16),
('HIGH', 'YOUTH', 130, 155, -0.15, 0.15, 0, 6, 6, 14),
('HIGH', 'ADULT', 130, 160, -0.15, 0.15, 0, 6, 6, 12),
('HIGH', 'SENIOR', 120, 145, -0.12, 0.12, 0, 6, 8, 16);

-- §6 뉴스 앵커 Gold Standard (1행)
INSERT INTO master_delivery_metric
    (master_intensity, master_zcr, master_zcr_std, master_pause_ratio, total_analyzed_sentences, description)
VALUES (63.72, 0.1108, 0.1895, 27.15, 290704, '뉴스 앵커 Gold Standard (dB SPL / ZCR / Pause %)');

-- §7 평가 임계값 (목표치 최종 min/max 가드레일) — 5종 모두 필수 (누락 시 목표치 계산이 실패한다)
INSERT INTO metric_threshold (metric_type, min_value, max_value, unit, description) VALUES
('WPM',             100,   180,  'syllable/min', '100 부근 이해도 최적, 180 초과 과속 경계'),
('PITCH_RATIO',     -0.20, 0.20, 'ratio',        'Baseline 대비 ±20% 이내 (급격한 점프 억제)'),
('INTENSITY_DELTA', -6,    6,    'dB',           'Baseline 대비 ±6dB'),
('PAUSE_RATIO',     8,     25,   '%',            '8% 미만 숨가쁨, 25% 초과 흐름 단절'),
('ZCR',             0.04,  0.25, 'ratio',        '발음 선명도 정상 범위 (§6)');
