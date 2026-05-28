import os
import boto3
from botocore.exceptions import BotoCoreError, ClientError
from fastapi import HTTPException
from app.core.config import S3_BUCKET_NAME, S3_REGION, AWS_ACCESS_KEY_ID_VAL, AWS_SECRET_ACCESS_KEY_VAL


def get_s3_client():
    return boto3.client(
        "s3",
        region_name=S3_REGION,
        aws_access_key_id=AWS_ACCESS_KEY_ID_VAL,
        aws_secret_access_key=AWS_SECRET_ACCESS_KEY_VAL,
    )


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
