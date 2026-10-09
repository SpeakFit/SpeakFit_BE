# 컨테이너 배포 (Spring 서버 / 파이썬 서버 분리)

```text
브라우저 ──https 443──▶ [EC2 #1 Spring 서버]
                          nginx(호스트) ──▶ backend 컨테이너 127.0.0.1:8080
                          nginx /ws/  ──사설 IP:5000──▶ [EC2 #2 파이썬 서버] analysis 컨테이너
                          backend ──사설 IP:5000──▶ [EC2 #2]            (PPT 변환/분석 호출)
                          backend ──3306──▶ [MySQL EC2, 사설 서브넷]
```

## 1. 서버별 구성 파일

| 서버 | compose 파일 | 환경변수 파일 | 이미지 |
|---|---|---|---|
| EC2 #1 Spring | `docker-compose.yml` | `.env` (`.env.spring.example` 참고) | `speakfit-backend` |
| EC2 #2 파이썬 | `docker-compose.python.yml` | `.env.python` (`.env.python.example` 참고) | `speakfit-analysis` |

- 두 이미지는 `docker build` 로 서버에서 만들 수도 있고, ECR 에서 받을 수도 있다.
- 서버에는 소스 전체가 필요 없다. compose 파일과 환경변수 파일만 두면 된다. (ECR 이미지를 쓰는 경우)

## 2. 로컬에서 이미지 만들기

```bash
docker build -t speakfit-backend .
docker build -t speakfit-analysis python-analysis-server
```

## 3. 실행

```bash
# Spring 서버 (EC2 #1)
docker compose up -d

# 파이썬 서버 (EC2 #2)
docker compose -f docker-compose.python.yml up -d

# ECR 이미지를 쓸 때
BACKEND_IMAGE=<계정>.dkr.ecr.ap-northeast-2.amazonaws.com/speakfit-backend:<태그> docker compose up -d
ANALYSIS_IMAGE=<계정>.dkr.ecr.ap-northeast-2.amazonaws.com/speakfit-analysis:<태그> \
  docker compose -f docker-compose.python.yml up -d
```

## 4. 두 서버를 연결하는 환경변수

| 위치 | 변수 | 값 |
|---|---|---|
| Spring `.env` | `AI_BASE_URL` | `http://<파이썬 서버 사설 IP>:5000` |
| Spring `.env` | `WS_BASE_URL` | `wss://api.speakfit.org/ws/practice` (브라우저가 접속하는 주소, nginx 가 파이썬 서버로 중계) |
| 양쪽 | `JWT_SECRET` | **같은 값** (실시간 WebSocket 토큰을 파이썬이 검증) |
| 양쪽 | `AWS_S3_BUCKET`, `AWS_REGION` | 같은 버킷 (PPT/음성 파일을 S3 로 주고받음) |

## 5. 보안 그룹

| 대상 | 인바운드 |
|---|---|
| Spring 서버 | 80, 443 ← 인터넷 / 22 ← 관리자 IP 만. 8080 은 열지 않는다. (compose 가 루프백에만 바인딩) |
| 파이썬 서버 | **5000 ← Spring 서버의 보안 그룹 만.** 인터넷에 열지 않는다. 22 ← 관리자 IP 만 |
| MySQL 서버 | 3306 ← Spring 서버의 보안 그룹 만 |

## 6. 헬스체크

```bash
docker ps                                   # STATUS 에 (healthy) 가 보이는지
curl -s http://127.0.0.1:8080/actuator/health   # Spring: {"status":"UP",...}
curl -s http://127.0.0.1:5000/                  # 파이썬: {"message":"SpeakFit Analysis Server is running",...}
```

## 7. Google STT 키(JSON)

이미지에 넣지 않는다. 파이썬 서버의 호스트에 파일로 두고(`chmod 600`) `docker-compose.python.yml` 의
주석 처리된 `volumes` 줄을 풀어 읽기 전용으로 마운트한다. 키를 받는 방법은 별도 안내 문서 참고.

## 8. 아직 남은 것

- Spring 이 AWS 키를 환경변수(`AWS_ACCESS_KEY`/`AWS_SECRET_KEY`)로 받는다. EC2 IAM 역할로 바꾸는 작업은 `feat/aws-iam-role-credentials`.
- 이미지 자동 빌드와 ECR 업로드는 `feat/ci-cd-ecr`.
