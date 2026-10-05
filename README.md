# SpeakFit Backend (SpeakFit_BE)
SpeakFit Backend API Server (Spring Boot)

Local 개발 환경은 local profile + .env 기반으로 동작합니다.

---
## Tech Stack

- Java 21
- Spring Boot 4.0.1 (Gradle)
- Spring Web (WebMVC)
- Spring Data JPA
- Spring Security (초기 설정/확장 예정)
- Validation
- MySQL
- Swagger (springdoc-openapi)
---
## Project Structure

```text
speakfit-backend
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com/speakfit/backend
│   │   │       ├── global
│   │   │       │   ├── api              # 공통 응답/에러 포맷, 코드 정의
│   │   │       │   ├── config           # Swagger / Security 등 설정
│   │   │       │   ├── entity           # BaseEntity 등 공통 엔티티
│   │   │       │   └── validation       # 커스텀 검증 (선택)
│   │   │       └── domain               # 도메인별 패키지 (추후 추가)
│   │   └── resources
│   │       ├── application.yaml         # 공통 설정
│   │       └── application-local.yaml   # 로컬 전용 설정
│   └── test
├── .env                                 # 로컬 환경변수 (Git 제외)
├── build.gradle
└── README.md
```
---
## Branch Strategy
- main : 배포용(릴리즈)
- develop : 개발 통합 브랜치(PR merge 대상)
- feat/* : 기능 개발 브랜치

PR 규칙:
- feat/* → develop (기능 개발)
- develop → main (릴리즈/배포)
---
## Local Setup
### 1) Prerequisites
- Java 21 설치
- MySQL 설치 및 실행
- IntelliJ IDEA/Ultimate
### 2) Create Local DB & User (MySQL)

```sql
CREATE DATABASE speakfit CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

- 테이블은 첫 기동 시 `ddl-auto: update` 로 자동 생성됩니다.
- 시드 데이터(`baseline_regional_metric_insert.sql`, `speech_style_cluster_insert.sql` 등)는 테이블 생성 후 직접 넣어야 합니다.
- `local` 프로파일의 DB 기본값은 `localhost:3306/speakfit`, 사용자 `root`, 비밀번호 `password` 입니다. 다르면 아래 `.env` 의 `DB_*` 로 지정합니다.

### 3) Create .env File

`.env.spring.example` 을 프로젝트 루트에 `.env` 로 복사해서 값을 채웁니다. (`.env` 는 Git 에 올라가지 않습니다.)

```bash
cp .env.spring.example .env
```

Python 분석 서버의 환경변수는 `.env.python.example` 을 참고합니다. `JWT_SECRET` 은 두 서버가 **같은 값**이어야 합니다.

| 구분 | 환경변수 |
|---|---|
| DB | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` |
| JWT | `JWT_SECRET`(32바이트 이상), `JWT_ACCESS_EXP_SECONDS`, `JWT_REFRESH_EXP_SECONDS` |
| AWS | `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_S3_BUCKET`, `AWS_REGION` |
| 주소 | `CORS_ALLOWED_ORIGINS`, `AI_BASE_URL`(백엔드→Python), `WS_BASE_URL`(브라우저→WebSocket) |

> 기존 `LOCAL_DB_URL`, `LOCAL_DB_USERNAME`, `LOCAL_DB_PASSWORD` 는 `DB_*` 로 대체되었습니다. (local 프로파일에서만 한동안 호환)
> `JWT_ACCESS_TOKEN_EXP_SECONDS`, `JWT_REFRESH_TOKEN_EXP_SECONDS` 는 `JWT_ACCESS_EXP_SECONDS`, `JWT_REFRESH_EXP_SECONDS` 로 통일되었습니다.

`prod` 프로파일은 `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `AI_BASE_URL`, `WS_BASE_URL` 이 없으면 **기동에 실패**합니다. (잘못된 기본값으로 조용히 동작하는 것을 막기 위함)

`CORS_ALLOWED_ORIGINS` 를 지정하면 기본값(`http://localhost:5173,http://localhost:3000`)을 대체하므로 필요한 주소를 모두 적습니다.

헬스체크: `GET /actuator/health` (인증 없이 접근 가능, DB 연결 상태 포함). `prod` 에서는 Swagger 가 비활성화됩니다.

### 4) IntelliJ Run Configuration (local profile)

Run / Debug Configurations → SpeakfitBackendApplication

Active profiles: local

EnvFile 플러그인 사용 시:

✅ Enable EnvFile 체크

.env 파일 추가

또는 플러그인 없이 진행하려면 Environment variables에 직접 입력해도 됩니다.

### 5) Run
```json
./gradlew bootRun
```
---
## Swagger
서버 실행 후 아래에서 확인:

Swagger UI:

http://localhost:8080/swagger-ui/index.html

OpenAPI Docs (JSON):

http://localhost:8080/v3/api-docs

---
## API Response Convention
### Success
```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {}
}
```
### Failure
```json
{
  "isSuccess": false,
  "code": "COMMON400",
  "message": "잘못된 요청입니다.",
  "result": {
    "reason": "validation failed",
    "fieldErrors": {
      "username": "must not be blank"
    }
  }
}
```

---
## Git Ignore
- .env (환경 변수)
- build/, .gradle/, IDE 설정 파일 등

---
## Commit Message Convention (Recommended)
- Feat: ... 기능 추가
- Fix: ... 버그 수정
- Refactor: ... 리팩터링
- Chore: ... 설정/기타 작업
- Docs: ... 문서
```json
feat: add health check api
chore: add swagger and local env setup
```
---
## 👥 Contributors
<a href="https://github.com/SpeakFit/SpeakFit_BE/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=SpeakFit/SpeakFit_BE" />
</a>
