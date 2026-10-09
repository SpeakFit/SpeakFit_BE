package com.speakfit.backend.global.config;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * local 프로파일이 원격 DB(예: 운영 RDS)에 연결되는 사고를 막는다.
 * Flyway/Hibernate 보다 먼저 실행되도록 BeanFactoryPostProcessor 로 둔다.
 * 원격 DB 가 꼭 필요하면 APP_LOCAL_DB_ALLOW_REMOTE=true 로 명시적으로 허용한다.
 */
@Component
@Profile("local")
public class LocalDatabaseGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Pattern JDBC_HOST = Pattern.compile("^jdbc:[a-z0-9]+://(\\[[^]]+]|[^:/?]+)");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1", "host.docker.internal");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (environment.getProperty("app.local-db.allow-remote", Boolean.class, false)) {
            return;
        }
        String url = environment.getProperty("spring.datasource.url");
        if (!isLocalHost(extractHost(url))) {
            throw new IllegalStateException(
                    "local 프로파일은 로컬 DB(localhost)에만 연결할 수 있습니다. 현재 접속 대상 host: "
                            + extractHost(url)
                            + " — .env 의 DB_HOST / LOCAL_DB_URL 에 운영 DB 주소가 들어 있지 않은지 확인하세요."
                            + " (원격 DB 가 꼭 필요하면 APP_LOCAL_DB_ALLOW_REMOTE=true)");
        }
    }

    static String extractHost(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        Matcher matcher = JDBC_HOST.matcher(jdbcUrl.trim().toLowerCase());
        return matcher.find() ? matcher.group(1) : null;
    }

    static boolean isLocalHost(String host) {
        return host != null && LOCAL_HOSTS.contains(host);
    }
}
