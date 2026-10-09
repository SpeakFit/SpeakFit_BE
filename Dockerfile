# SpeakFit 백엔드 (Spring Boot)
# 빌드:  docker build -t speakfit-backend .
# 실행:  docker run --env-file .env -e SPRING_PROFILES_ACTIVE=prod -p 8080:8080 speakfit-backend

# ---- 1단계: 빌드 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 해석 결과를 캐시하기 위해 빌드 설정 파일을 먼저 복사한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
# 테스트는 CI 에서 먼저 실행하므로 이미지 빌드에서는 건너뛴다.
RUN ./gradlew --no-daemon bootJar -x test

# ---- 2단계: 실행 ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# 헬스체크용 curl, 비루트 실행 사용자
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --shell /usr/sbin/nologin app

COPY --from=build /workspace/build/libs/*.jar app.jar

USER app
EXPOSE 8080

# 컨테이너 메모리에 맞춰 힙을 조정한다. 필요하면 JAVA_OPTS 로 덮어쓴다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
