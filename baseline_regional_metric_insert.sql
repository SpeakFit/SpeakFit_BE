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
