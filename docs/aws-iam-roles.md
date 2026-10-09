# EC2 에서 AWS 키 없이 S3 쓰기 (IAM 역할)

운영 서버(EC2)에서는 액세스 키를 `.env` 에 넣지 않고, **EC2 인스턴스에 IAM 역할을 붙여** S3 에 접근한다.
키가 파일/환경변수에 없으므로 유출될 키가 없고, 자격 증명은 AWS 가 자동으로 갱신한다.

## 동작 방식
- Spring 과 파이썬 서버 모두 `AWS_ACCESS_KEY`/`AWS_SECRET_KEY` 가 **비어 있으면** AWS 기본 자격 증명 체인
  (환경변수 → 프로파일 → **EC2 인스턴스 역할**)을 사용한다.
- 두 값이 **모두** 있으면 그 키를 사용한다. (로컬 개발용)
- 운영 `.env` / `.env.python` 에서는 `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` 줄을 **삭제**한다. (`AWS_S3_BUCKET`, `AWS_REGION` 은 유지)

## 1. 정책 만들기 (IAM → 정책 → 정책 생성 → JSON)

서버마다 역할을 따로 만드는 것을 권장한다.

**Spring 서버용** `speakfit-spring-s3` (업로드, 삭제, 서명 URL 생성)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::speakfit-prod-1/*"
    },
    {
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::speakfit-prod-1"
    }
  ]
}
```

**파이썬 서버용** `speakfit-analysis-s3` (녹음/PPT 내려받기, 슬라이드 올리기)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::speakfit-prod-1/*"
    },
    {
      "Effect": "Allow",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::speakfit-prod-1/ppt/*"
    }
  ]
}
```

## 2. 역할 만들기 (IAM → 역할 → 역할 생성)
1. 신뢰할 수 있는 엔터티: **AWS 서비스 → EC2**
2. 위 정책 연결 → 역할 이름 `speakfit-spring-ec2`, `speakfit-analysis-ec2`

## 3. EC2 에 붙이기
EC2 콘솔 → 인스턴스 선택 → **작업 → 보안 → IAM 역할 수정** → 해당 역할 선택 → 저장.
(역할을 붙이면 인스턴스 재시작은 필요 없다.)

## 4. 컨테이너에서 쓰려면 IMDS hop limit 를 2 로 (중요)

컨테이너(Docker 브리지 네트워크)는 EC2 인스턴스 메타데이터(IMDS)에 호스트보다 한 단계 더 멀리 있다.
인스턴스의 기본 hop limit(1)로는 **컨테이너가 역할 자격 증명을 받지 못해** `Unable to load credentials` 오류가 난다.

EC2 콘솔 → 인스턴스 → **작업 → 인스턴스 설정 → 인스턴스 메타데이터 옵션 수정** → **PUT 응답 홉 제한 = 2** (IMDSv2 필수 유지).
또는 CLI:
```bash
aws ec2 modify-instance-metadata-options --instance-id <i-xxxx> \
  --http-tokens required --http-put-response-hop-limit 2
```

## 5. 확인

서버에서 컨테이너가 역할을 인식하는지:
```bash
docker exec speakfit-backend sh -c 'curl -s -H "X-aws-ec2-metadata-token: $(curl -s -X PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 60")" http://169.254.169.254/latest/meta-data/iam/security-credentials/'
```
역할 이름이 출력되면 정상이다. 앱에서는 PPT 업로드/슬라이드 표시가 되는지로 확인한다.

## 참고
- **서명 URL 유효 시간**: 역할 자격 증명은 주기적으로 갱신되므로, 서명 URL 은 설정한 시간(`S3_PRESIGN_EXPIRE_SECONDS`)보다 먼저 만료될 수 있다. 화면이 403 을 받으면 같은 API 를 다시 호출해 새 URL 을 받으면 된다.
- 로컬 개발은 기존처럼 `.env` 에 키를 넣어도 된다. 로컬 키는 위 `speakfit-s3` IAM 사용자의 최소 권한 키만 사용한다.
- Google Cloud STT 키(JSON)는 AWS 와 무관하며, 이 방식으로 대체되지 않는다.
