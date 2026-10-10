#!/usr/bin/env bash
# ECR 에 올라간 이미지를 서버에서 받아 컨테이너를 교체한다.
#
# 사용법 (서버에서, compose 파일과 .env / .env.python 이 있는 폴더에서 실행)
#   ./deploy.sh spring            # Spring 서버(EC2 #1): speakfit-backend:latest
#   ./deploy.sh python            # 파이썬 서버(EC2 #2): speakfit-analysis:latest
#   ./deploy.sh spring <태그>     # 특정 태그(커밋 해시)로 배포 / 롤백
#
# 필요: docker + compose 플러그인, aws CLI, 서버 IAM 역할에 ECR 읽기 권한 (docs/aws-ci-cd-setup.md)
set -euo pipefail

role="${1:?사용법: ./deploy.sh <spring|python> [태그]}"
tag="${2:-latest}"
region="${AWS_REGION:-ap-northeast-2}"

account="$(aws sts get-caller-identity --query Account --output text)"
registry="${account}.dkr.ecr.${region}.amazonaws.com"

aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$registry"

case "$role" in
  spring)
    export BACKEND_IMAGE="${registry}/speakfit-backend:${tag}"
    compose=(docker compose -f docker-compose.yml)
    health="http://127.0.0.1:8080/actuator/health"
    ;;
  python)
    export ANALYSIS_IMAGE="${registry}/speakfit-analysis:${tag}"
    compose=(docker compose -f docker-compose.python.yml)
    health="http://127.0.0.1:5000/"
    ;;
  *)
    echo "알 수 없는 대상: $role (spring 또는 python)" >&2
    exit 1
    ;;
esac

echo "==> 이미지 받기: ${BACKEND_IMAGE:-$ANALYSIS_IMAGE}"
"${compose[@]}" pull

echo "==> 컨테이너 교체"
"${compose[@]}" up -d --no-build --remove-orphans

echo "==> 헬스체크 대기 (최대 120초)"
for _ in $(seq 1 24); do
  if curl -fsS "$health" > /dev/null 2>&1; then
    echo "정상: $health"
    docker image prune -f > /dev/null
    exit 0
  fi
  sleep 5
done

echo "헬스체크 실패: $health — 'docker compose logs --tail=100' 으로 확인하세요." >&2
exit 1
