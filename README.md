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
### 3) Create .env File

프로젝트 루트에 .env 파일 생성

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
