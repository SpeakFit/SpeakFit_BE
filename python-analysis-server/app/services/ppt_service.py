import os
import shutil
import subprocess
import tempfile
from fastapi import HTTPException

def find_libreoffice():
    env_path = os.getenv("LIBREOFFICE_PATH")
    if env_path and os.path.exists(env_path): return env_path
    command_path = shutil.which("soffice") or shutil.which("libreoffice")
    if command_path: return command_path
    candidates = [
        r"C:\Program Files\LibreOffice\program\soffice.exe",
        r"C:\Program Files (x86)\LibreOffice\program\soffice.exe",
    ]
    for candidate in candidates:
        if os.path.exists(candidate): return candidate
    return None

def to_file_uri(path):
    normalized_path = os.path.abspath(path).replace("\\", "/")
    return "file://" + (normalized_path if normalized_path.startswith("/") else "/" + normalized_path)

PPT_S3_ROOT = "ppt/"


def resolve_ppt_s3_key(ppt_url: str, bucket_name: str) -> str:
    """원본 PPT 의 S3 URL 에서 오브젝트 키를 뽑고, ppt/ 아래 파일만 허용한다."""
    from urllib.parse import unquote, urlparse

    key = unquote(urlparse(ppt_url or "").path.lstrip("/"))
    if bucket_name and key.startswith(f"{bucket_name}/"):
        key = key[len(bucket_name) + 1:]
    if not key.startswith(PPT_S3_ROOT) or ".." in key.split("/") or key.endswith("/"):
        raise HTTPException(status_code=400, detail="pptUrl must point to an object under ppt/")
    return key


def normalize_ppt_output_prefix(output_prefix: str) -> str:
    """변환 결과를 저장할 S3 prefix 를 검증하고 끝의 / 를 제거한다."""
    prefix = (output_prefix or "").strip().strip("/")
    if not prefix.startswith(PPT_S3_ROOT) or ".." in prefix.split("/"):
        raise HTTPException(status_code=400, detail="outputPrefix must be under ppt/")
    return prefix


def render_pdf_to_images(pdf_path: str, s3_key_prefix: str) -> list:
    """
    PDF를 슬라이드 이미지로 변환한 후 S3에 업로드합니다.
    각 슬라이드의 S3 URL 목록을 반환합니다.
    """
    import fitz
    from app.services.s3_service import upload_to_s3

    slides_temp_dir = tempfile.mkdtemp(prefix="speakfit-slides-")
    document = fitz.open(pdf_path)
    slides = []
    try:
        for i in range(document.page_count):
            page = document.load_page(i)
            pix = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
            temp_img_path = os.path.join(slides_temp_dir, f"{i + 1}.png")
            pix.save(temp_img_path)

            s3_key = f"{s3_key_prefix}/{i + 1}.png"
            s3_url = upload_to_s3(temp_img_path, s3_key, content_type="image/png")
            slides.append({"page": i + 1, "imageUrl": s3_url})
    finally:
        document.close()
        shutil.rmtree(slides_temp_dir, ignore_errors=True)
    return slides
