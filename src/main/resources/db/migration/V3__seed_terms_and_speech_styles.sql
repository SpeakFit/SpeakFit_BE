-- V3: 약관 / 스피치 스타일 초기 데이터
-- ※ 아래 값은 서비스 가동에 필요한 최소 골격이다. 실제 운영 문구/음원으로 교체하려면 새 마이그레이션(V{n})으로 UPDATE 한다.
--   - terms.content : 법무 검토를 거친 실제 약관 전문으로 교체 필요
--   - speech_style.sample_audio_url_* : S3 오브젝트 키 (버킷에 샘플 음원 업로드 필요)

-- 가입 화면이 termId 1(서비스 이용약관), 2(개인정보처리방침)을 고정 사용하므로 id 순서를 유지한다.
INSERT INTO terms (id, term_type, title, content, required, created_at) VALUES
(1, 'SERVICE', '서비스 이용약관',     '(서비스 이용약관 전문을 등록해 주세요.)',     b'1', NOW(6)),
(2, 'PRIVACY', '개인정보 처리방침',   '(개인정보 처리방침 전문을 등록해 주세요.)',   b'1', NOW(6));

INSERT INTO speech_style
    (id, style_type, display_name, description, sample_audio_url_male, sample_audio_url_female, sort_order) VALUES
(1, 'CALM_LOW_TONE',   '신중한',       '중저음의 신중하고 차분한 스타일',    'samples/styles/calm_low_tone_male.mp3',   'samples/styles/calm_low_tone_female.mp3',   1),
(2, 'STANDARD_LECTURE','지적인',       '안정적인 톤의 표준 강의 스타일',     'samples/styles/standard_lecture_male.mp3','samples/styles/standard_lecture_female.mp3',2),
(3, 'ENERGETIC_FAST',  '열정적인',     '에너지 넘치는 고음/빠른 스타일',     'samples/styles/energetic_fast_male.mp3',  'samples/styles/energetic_fast_female.mp3',  3),
(4, 'DELIVERY',        '전달력 있는',  '전달력 있는 스타일',                 'samples/styles/delivery_male.mp3',        'samples/styles/delivery_female.mp3',        4);
