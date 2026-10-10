import os
import boto3
from botocore.exceptions import BotoCoreError, ClientError
from fastapi import HTTPException
from app.core.config import S3_BUCKET_NAME, S3_REGION, AWS_ACCESS_KEY_ID_VAL, AWS_SECRET_ACCESS_KEY_VAL


def get_s3_client():
    """S3 클라이언트. 액세스 키가 둘 다 설정되면 그 키를 쓰고(로컬 개발),
    아니면 boto3 기본 체인(환경변수, 프로파일, EC2 의 IAM 역할)으로 자격 증명을 찾는다."""
    kwargs = {"region_name": S3_REGION}
    if AWS_ACCESS_KEY_ID_VAL and AWS_SECRET_ACCESS_KEY_VAL:
        kwargs["aws_access_key_id"] = AWS_ACCESS_KEY_ID_VAL
        kwargs["aws_secret_access_key"] = AWS_SECRET_ACCESS_KEY_VAL
    return boto3.client("s3", **kwargs)


def upload_to_s3(local_path: str, s3_key: str, content_type: str = "image/png") -> str:
    """로컬 파일을 S3에 업로드하고 퍼블릭 URL을 반환합니다."""
    if not S3_BUCKET_NAME:
        raise HTTPException(status_code=500, detail="S3 bucket name is not configured")

    try:
        client = get_s3_client()
        with open(local_path, "rb") as f:
            client.put_object(
                Bucket=S3_BUCKET_NAME,
                Key=s3_key,
                Body=f,
                ContentType=content_type,
            )
        return f"https://{S3_BUCKET_NAME}.s3.{S3_REGION}.amazonaws.com/{s3_key}"
    except (BotoCoreError, ClientError) as e:
        print(f"[Python ERROR] S3 upload failed - key: {s3_key}, error: {e}")
        raise HTTPException(status_code=500, detail=f"S3 upload failed: {str(e)}")


def download_from_s3(s3_key: str, local_path: str) -> None:
    """S3 오브젝트를 로컬 경로로 내려받는다. (스프링과 파이썬이 서로 다른 서버여도 파일을 주고받기 위함)"""
    if not S3_BUCKET_NAME:
        raise HTTPException(status_code=500, detail="S3 bucket name is not configured")

    try:
        get_s3_client().download_file(S3_BUCKET_NAME, s3_key, local_path)
    except (BotoCoreError, ClientError) as e:
        print(f"[Python ERROR] S3 download failed - key: {s3_key}, error: {e}")
        raise HTTPException(status_code=502, detail=f"Failed to download file from S3: {str(e)}")
