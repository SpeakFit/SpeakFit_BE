"""
PPT 변환 요청 검증 — 스프링이 넘긴 S3 URL/prefix 가 ppt/ 아래인지 확인한다.
(원본 파일을 로컬 경로가 아닌 S3 로 주고받으므로, 버킷의 다른 오브젝트를 읽지 못하게 막는다.)
"""
import pytest
from fastapi import HTTPException

from app.services.ppt_service import resolve_ppt_s3_key, normalize_ppt_output_prefix

BUCKET = "speakfit-prod-1"


def test_virtual_hosted_url_to_key():
    url = "https://speakfit-prod-1.s3.ap-northeast-2.amazonaws.com/ppt/10/attempts/abc/1d_slide.pptx"
    assert resolve_ppt_s3_key(url, BUCKET) == "ppt/10/attempts/abc/1d_slide.pptx"


def test_path_style_url_to_key():
    url = "https://s3.ap-northeast-2.amazonaws.com/speakfit-prod-1/ppt/10/attempts/abc/file.pdf"
    assert resolve_ppt_s3_key(url, BUCKET) == "ppt/10/attempts/abc/file.pdf"


def test_url_encoded_key_is_decoded():
    url = "https://speakfit-prod-1.s3.ap-northeast-2.amazonaws.com/ppt/1/attempts/a/x%20y.pptx"
    assert resolve_ppt_s3_key(url, BUCKET) == "ppt/1/attempts/a/x y.pptx"


@pytest.mark.parametrize("url", [
    "https://speakfit-prod-1.s3.ap-northeast-2.amazonaws.com/voice/baseline/1/a.webm",
    "https://speakfit-prod-1.s3.ap-northeast-2.amazonaws.com/ppt/../voice/a.webm",
    "https://speakfit-prod-1.s3.ap-northeast-2.amazonaws.com/ppt/10/",
    "",
])
def test_key_outside_ppt_is_rejected(url):
    with pytest.raises(HTTPException) as e:
        resolve_ppt_s3_key(url, BUCKET)
    assert e.value.status_code == 400


def test_output_prefix_is_normalized():
    assert normalize_ppt_output_prefix("/ppt/10/attempts/abc/") == "ppt/10/attempts/abc"


@pytest.mark.parametrize("prefix", ["voice/baseline", "ppt/../x", "", "uploads/ppt/1"])
def test_output_prefix_outside_ppt_is_rejected(prefix):
    with pytest.raises(HTTPException) as e:
        normalize_ppt_output_prefix(prefix)
    assert e.value.status_code == 400
