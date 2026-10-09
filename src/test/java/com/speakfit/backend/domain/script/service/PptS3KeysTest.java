package com.speakfit.backend.domain.script.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PptS3KeysTest {

    @Test
    void 변환_시도_prefix_는_대본_prefix_아래에_만들어진다() {
        String prefix = PptS3Keys.newAttemptPrefix(10L);

        assertThat(prefix).startsWith("ppt/10/attempts/");
        assertThat(PptS3Keys.scriptPrefix(10L)).isEqualTo("ppt/10");
        assertThat(PptS3Keys.newAttemptPrefix(10L)).isNotEqualTo(prefix);
    }

    @Test
    void 객체_키에서_변환_시도_prefix_를_뽑는다() {
        assertThat(PptS3Keys.attemptPrefixOf("ppt/10/attempts/abc-1/uuid_slide.pptx"))
                .isEqualTo("ppt/10/attempts/abc-1");
        // 예전 파이썬이 만들던 키(file.pptx)도 같은 구조
        assertThat(PptS3Keys.attemptPrefixOf("ppt/7/attempts/abc/file.pdf")).isEqualTo("ppt/7/attempts/abc");
    }

    @Test
    void 규칙에_맞지_않는_키는_null_이다() {
        assertThat(PptS3Keys.attemptPrefixOf(null)).isNull();
        assertThat(PptS3Keys.attemptPrefixOf("file.pptx")).isNull();
        assertThat(PptS3Keys.attemptPrefixOf("voice/baseline/1/a.webm")).isNull();
        assertThat(PptS3Keys.attemptPrefixOf("ppt/10/file.pptx")).isNull();
        assertThat(PptS3Keys.attemptPrefixOf("ppt/abc/attempts/x/file.pptx")).isNull();
    }
}
