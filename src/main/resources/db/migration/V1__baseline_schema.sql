-- V1: 스키마 기준선 (JPA 엔티티 기준 Hibernate 7 / MySQL 8 DDL 에서 생성)
-- 이후 스키마 변경은 새 V{n}__*.sql 로만 추가한다. (이 파일은 수정 금지)

create table ai_analysis_result (
        goal_similarity_score float(53),
        created_at datetime(6) not null,
        record_id bigint not null,
        ai_summary TEXT,
        content_feedback TEXT,
        content_summary varchar(255),
        energy_feedback TEXT,
        energy_summary varchar(255),
        goal_feedback TEXT,
        goal_summary varchar(255),
        improvements TEXT,
        pause_feedback TEXT,
        practice_tip TEXT,
        strengths TEXT,
        symbol_feedback TEXT,
        wpm_feedback TEXT,
        wpm_summary varchar(255),
        primary key (record_id)
    ) engine=InnoDB default charset=utf8mb4;

create table analysis_result (
        avg_intensity float(53),
        avg_pitch float(53),
        avg_wpm float(53),
        avg_zcr float(53),
        intensity_diff float(53),
        matching_rate integer,
        pause_count integer,
        pause_ratio float(53),
        pitch_diff float(53),
        volume_score integer,
        wpm_diff float(53),
        zcr_diff float(53),
        created_at datetime(6) not null,
        record_id bigint not null,
        most_similar_style varchar(255),
        voice_style_description varchar(255),
        volume_level varchar(255),
        primary key (record_id)
    ) engine=InnoDB default charset=utf8mb4;

create table baseline_regional_metric (
        avg_pitch float(53) not null,
        avg_wpm float(53) not null,
        id bigint not null auto_increment,
        gender varchar(20) not null,
        dialect varchar(30) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table baseline_voice (
        avg_intensity float(53),
        avg_pitch float(53),
        avg_wpm float(53),
        avg_zcr float(53),
        is_active bit not null,
        pause_ratio float(53),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        user_id bigint not null,
        analysis_raw_json TEXT,
        audio_url TEXT,
        status enum ('COMPLETED','FAILED','PENDING') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table feedback (
        end_date date not null,
        matching_rate integer,
        start_date date not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        user_id bigint not null,
        guide_next_step TEXT,
        guide_summary TEXT,
        improvement_description TEXT,
        improvement_title varchar(255),
        most_similar_style varchar(255),
        positive_description TEXT,
        positive_title varchar(255),
        style_description TEXT,
        status enum ('COMPLETED','FAILED','GENERATING') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table master_delivery_metric (
        master_intensity float(53) not null,
        master_pause_ratio float(53) not null,
        master_zcr float(53) not null,
        master_zcr_std float(53) not null,
        id bigint not null auto_increment,
        total_analyzed_sentences bigint not null,
        description varchar(255),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table metric_threshold (
        max_value float(53) not null,
        min_value float(53) not null,
        id bigint not null auto_increment,
        unit varchar(30) not null,
        description varchar(500),
        metric_type enum ('INTENSITY_DELTA','PAUSE_RATIO','PITCH_RATIO','WPM','ZCR') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table ppt_slide (
        slide_index integer,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        script_id bigint not null,
        image_url TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table practice_detail (
        confidence float(53),
        end_time float(53),
        start_time float(53),
        word_index integer,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        record_id bigint not null,
        status enum ('FAST','MISMATCH','NORMAL','SLOW'),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table practice_issue (
        display_order integer,
        end_index integer,
        intensity float(53),
        score float(53),
        sentence_index integer,
        start_index integer,
        wpm float(53),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        record_id bigint not null,
        script_sentence_id bigint,
        feedback_content TEXT,
        issue_summary varchar(255),
        reason TEXT,
        issue_type enum ('LONG_PAUSE','LOW_CONFIDENCE','LOW_SCORE','PITCH_UNSTABLE','SKIPPED_WORDS','TOO_FAST','TOO_SLOW','VOLUME_DROP'),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table practice_record (
        time float(53),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        script_id bigint not null,
        style_id bigint,
        user_id bigint not null,
        audio_url TEXT,
        audience_type enum ('ADULT','CHILD','SENIOR','YOUTH'),
        audience_understanding enum ('HIGH','LOW','MIDDLE'),
        speech_information enum ('DISCUSSION','FEEDBACKPRACTICE','INTERVIEW','LECTURE','PRESENTATION'),
        status enum ('ANALYZED','ANALYZING','COMPLETED','FAILED','READY','RECORDING') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table practice_sentence_result (
        avg_intensity float(53),
        avg_pitch float(53),
        score float(53),
        sentence_index integer not null,
        skipped_word_count integer,
        word_count integer,
        wpm float(53),
        created_at datetime(6) not null,
        end_ms bigint,
        id bigint not null auto_increment,
        pause_duration_ms bigint,
        record_id bigint not null,
        script_sentence_id bigint,
        start_ms bigint,
        status enum ('FAST','MISMATCH','NORMAL','SLOW'),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table practice_word_result (
        confidence float(53),
        global_word_index integer not null,
        sentence_word_index integer,
        skipped bit not null,
        created_at datetime(6) not null,
        end_ms bigint,
        id bigint not null auto_increment,
        record_id bigint not null,
        script_word_id bigint,
        start_ms bigint,
        status enum ('FAST','MISMATCH','NORMAL','SLOW'),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table refresh_tokens (
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        id bigint not null auto_increment,
        user_id bigint not null,
        token varchar(2000) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table script (
        total_slides integer,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        user_id bigint not null,
        content TEXT,
        marked_content TEXT,
        ppt_error_message TEXT,
        ppt_url TEXT,
        title varchar(255) not null,
        ppt_status enum ('COMPLETED','FAILED','NONE','PROCESSING'),
        script_type enum ('PPT','TEXT'),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table script_sentence (
        end_char_index integer not null,
        sentence_index integer not null,
        start_char_index integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        script_id bigint not null,
        normalized_text TEXT,
        original_text TEXT not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table script_word (
        end_char_index integer not null,
        global_word_index integer not null,
        sentence_word_index integer not null,
        start_char_index integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        script_sentence_id bigint not null,
        normalized_text TEXT,
        text TEXT not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table speech_guide (
        master_zcr_mean float(53),
        master_zcr_std float(53),
        target_db float(53),
        target_pause_ratio float(53),
        target_pitch float(53),
        target_wpm float(53),
        baseline_voice_id bigint not null,
        id bigint not null auto_increment,
        user_id bigint not null,
        pitch_guide_message TEXT,
        selected_style varchar(255) not null,
        wpm_guide_message TEXT,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table speech_style (
        sort_order integer not null,
        id bigint not null auto_increment,
        display_name varchar(50) not null,
        sample_audio_url_female varchar(500) not null,
        sample_audio_url_male varchar(500) not null,
        description varchar(255) not null,
        style_type enum ('CALM_LOW_TONE','DELIVERY','ENERGETIC_FAST','STANDARD_LECTURE') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table speech_style_cluster (
        base_pitch float(53) not null,
        base_wpm float(53) not null,
        id bigint not null auto_increment,
        gender varchar(255) not null,
        region varchar(255) not null,
        style_name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table speech_style_matrix (
        pitch_ratio float(53) not null,
        wpm_ratio float(53) not null,
        id bigint not null auto_increment,
        dialect enum ('CHUNGCHEONG','GANGWON','GYEONGSANG','JEOLLA','STANDARD') not null,
        gender enum ('FEMALE','MALE') not null,
        style_type enum ('CALM_LOW_TONE','DELIVERY','ENERGETIC_FAST','STANDARD_LECTURE') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table target_audience_metric (
        max_intensity_delta float(53) not null,
        max_pause_ratio float(53) not null,
        max_pitch_ratio float(53) not null,
        max_wpm float(53) not null,
        min_intensity_delta float(53) not null,
        min_pause_ratio float(53) not null,
        min_pitch_ratio float(53) not null,
        min_wpm float(53) not null,
        id bigint not null auto_increment,
        audience_type enum ('ADULT','CHILD','SENIOR','YOUTH') not null,
        audience_understanding enum ('HIGH','LOW','MIDDLE') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table terms (
        required bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        title varchar(100) not null,
        content TEXT not null,
        term_type enum ('PRIVACY','SERVICE') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table users (
        default_pitch float(53),
        default_wpm float(53),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        nickname varchar(20) not null,
        birthday varchar(255) not null,
        email varchar(255) not null,
        password varchar(255) not null,
        dialect enum ('CHUNGCHEONG','GANGWON','GYEONGSANG','JEOLLA','STANDARD') default 'STANDARD' not null,
        gender enum ('FEMALE','MALE') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

create table users_terms (
        agreed bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        term_id bigint not null,
        user_id bigint not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4;

alter table baseline_regional_metric
       add constraint uk_brm_gender_dialect unique (gender, dialect);

alter table metric_threshold
       add constraint uk_metric_threshold_type unique (metric_type);

alter table refresh_tokens
       add constraint uk_refresh_token_user unique (user_id);

alter table speech_style
       add constraint uk_speech_style_type unique (style_type);

alter table speech_style_matrix
       add constraint uk_speech_style_matrix_group_style unique (dialect, gender, style_type);

alter table target_audience_metric
       add constraint uk_target_audience_metric_condition unique (audience_understanding, audience_type);

alter table terms
       add constraint uk_terms_type unique (term_type);

alter table users
       add constraint uk_user_email unique (email);

alter table users
       add constraint uk_user_nickname unique (nickname);

alter table users_terms
       add constraint uk_users_terms unique (user_id, term_id);

alter table ai_analysis_result
       add constraint FKbtgffs3mlyodwumb1lap1forf
       foreign key (record_id)
       references practice_record (id);

alter table analysis_result
       add constraint FKe5djq3jwnfje6r6hoi8euvec3
       foreign key (record_id)
       references practice_record (id);

alter table baseline_voice
       add constraint FKg8ckpy7nmbihpnka04kb7hnjm
       foreign key (user_id)
       references users (id);

alter table feedback
       add constraint FKpwwmhguqianghvi1wohmtsm8l
       foreign key (user_id)
       references users (id);

alter table ppt_slide
       add constraint FKik192gg8i244jufly382lc72p
       foreign key (script_id)
       references script (id);

alter table practice_detail
       add constraint FKiwqyniqexkvu3rpeqn75ddeje
       foreign key (record_id)
       references practice_record (id);

alter table practice_issue
       add constraint FK82j7adkv8648uxk33njm2l6n3
       foreign key (record_id)
       references practice_record (id);

alter table practice_issue
       add constraint FKmnkhuau40hkaj62hk281c0043
       foreign key (script_sentence_id)
       references script_sentence (id);

alter table practice_record
       add constraint FKm80tpxggdo2djfkic4xm9tphg
       foreign key (script_id)
       references script (id);

alter table practice_record
       add constraint FKhtbgyx7o6af6dbt7cwkvlxiqe
       foreign key (style_id)
       references speech_style (id);

alter table practice_record
       add constraint FKce6xhjwtsnhtcveg21no6ghd7
       foreign key (user_id)
       references users (id);

alter table practice_sentence_result
       add constraint FK3hhp75m5eig8233c5rh80el19
       foreign key (record_id)
       references practice_record (id);

alter table practice_sentence_result
       add constraint FKcj3dy5p26lsg7n64lf19f9fad
       foreign key (script_sentence_id)
       references script_sentence (id);

alter table practice_word_result
       add constraint FKp49qd8mxjaovpy4uglxwliwuw
       foreign key (record_id)
       references practice_record (id);

alter table practice_word_result
       add constraint FKknmn3dr3jpdnck5656q4igxlo
       foreign key (script_word_id)
       references script_word (id);

alter table refresh_tokens
       add constraint FK1lih5y2npsf8u5o3vhdb9y0os
       foreign key (user_id)
       references users (id);

alter table script
       add constraint FK6dimqdme5nhcjdp46dspl9wnm
       foreign key (user_id)
       references users (id);

alter table script_sentence
       add constraint FKa4cbn0bv2bbywh6cbi0tb3ivj
       foreign key (script_id)
       references script (id);

alter table script_word
       add constraint FKjvpcpivv8iu1i149sx76tma1e
       foreign key (script_sentence_id)
       references script_sentence (id);

alter table speech_guide
       add constraint FK7ls9cb4me4brxyn0e9si748pc
       foreign key (user_id)
       references users (id);

alter table users_terms
       add constraint FKf8s5bmsb2w3yolb13bqf2vd6j
       foreign key (term_id)
       references terms (id);

alter table users_terms
       add constraint FK3nrr9r63d79wqgh6cno8acr37
       foreign key (user_id)
       references users (id);
