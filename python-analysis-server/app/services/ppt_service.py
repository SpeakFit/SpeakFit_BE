import os
import shutil
import subprocess
import tempfile
from fastapi import HTTPException
from app.core.config import UPLOAD_ROOT

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

def translate_to_host_path(path):
    """
    Docker 컨테이너 경로(/app/uploads/...)를 호스트 경로(UPLOAD_ROOT/...)로 변환.
    경로에서 '/uploads/' 이후 상대 경로를 추출해 UPLOAD_ROOT와 합침.
    이미 호스트 경로이거나 상대 경로인 경우에도 안전하게 처리.
    """
    normalized = path.replace("\\", "/")
    uploads_marker = "/uploads/"
    idx = normalized.find(uploads_marker)
    if idx >= 0:
        relative = normalized[idx + len(uploads_marker):]
        return os.path.normpath(os.path.join(UPLOAD_ROOT, relative))
    return path

def ensure_within_upload_root(path, must_exist=False):
    # Docker 경로 → 호스트 경로 변환 후 검증
    absolute_path = translate_to_host_path(path)
    try:
        common_path = os.path.commonpath([UPLOAD_ROOT, absolute_path])
        if common_path != UPLOAD_ROOT:
            raise HTTPException(status_code=400, detail="Path is outside the allowed uploads directory")
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid path")

    if must_exist and not os.path.exists(absolute_path):
        raise HTTPException(status_code=404, detail="Requested file not found")
    return absolute_path

def convert_ppt_to_pdf(ppt_path: str):
    """
    PPT/PPTX를 PDF로 변환합니다.
    임시 디렉토리에 PDF를 생성하고 (pdf_path, temp_dir) 튜플을 반환합니다.
    호출 측에서 작업 완료 후 temp_dir을 반드시 삭제해야 합니다.
    """
    libreoffice_path = find_libreoffice()
    if not libreoffice_path:
        raise HTTPException(status_code=503, detail="LibreOffice is not installed")

    temp_dir = tempfile.mkdtemp(prefix="speakfit-ppt-")
    profile_dir = tempfile.mkdtemp(prefix="libreoffice-profile-")
    command = [
        libreoffice_path, f"-env:UserInstallation={to_file_uri(profile_dir)}",
        "--headless", "--nologo", "--norestore", "--convert-to", "pdf",
        "--outdir", temp_dir, ppt_path,
    ]
    try:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        stdout, stderr = process.communicate(timeout=120)
        if process.returncode != 0:
            raise HTTPException(status_code=500, detail="Conversion failed")
    except subprocess.TimeoutExpired:
        process.kill()
        raise HTTPException(status_code=504, detail="Conversion timed out")
    finally:
        shutil.rmtree(profile_dir, ignore_errors=True)

    base_name = os.path.splitext(os.path.basename(ppt_path))[0]
    pdf_path = os.path.join(temp_dir, base_name + ".pdf")
    return pdf_path, temp_dir


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
