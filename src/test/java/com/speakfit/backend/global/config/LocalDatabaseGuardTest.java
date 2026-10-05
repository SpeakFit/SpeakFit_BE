package com.speakfit.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LocalDatabaseGuardTest {

    private static LocalDatabaseGuard guard(String url, String allowRemote) {
        MockEnvironment env = new MockEnvironment();
        if (url != null) {
            env.setProperty("spring.datasource.url", url);
        }
        if (allowRemote != null) {
            env.setProperty("app.local-db.allow-remote", allowRemote);
        }
        LocalDatabaseGuard guard = new LocalDatabaseGuard();
        guard.setEnvironment(env);
        return guard;
    }

    @Test
    void 로컬_호스트는_통과한다() {
        assertThatCode(() -> guard("jdbc:mysql://localhost:3306/speakfit_local?x=1", null).postProcessBeanFactory(null))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard("jdbc:mysql://127.0.0.1/speakfit", null).postProcessBeanFactory(null))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard("jdbc:mysql://[::1]:3306/speakfit", null).postProcessBeanFactory(null))
                .doesNotThrowAnyException();
    }

    @Test
    void 원격_호스트는_기동을_막는다() {
        assertThatThrownBy(() -> guard(
                "jdbc:mysql://speakfit-db.abc.ap-northeast-2.rds.amazonaws.com:3306/speakfit", null)
                .postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rds.amazonaws.com");
    }

    @Test
    void 호스트를_알_수_없으면_막는다() {
        assertThatThrownBy(() -> guard(null, null).postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 명시적으로_허용하면_원격도_통과한다() {
        assertThatCode(() -> guard("jdbc:mysql://db.example.com:3306/x", "true").postProcessBeanFactory(null))
                .doesNotThrowAnyException();
    }

    @Test
    void 호스트_추출() {
        assertThat(LocalDatabaseGuard.extractHost("jdbc:mysql://LocalHost:3306/db")).isEqualTo("localhost");
        assertThat(LocalDatabaseGuard.extractHost("garbage")).isNull();
    }
}
