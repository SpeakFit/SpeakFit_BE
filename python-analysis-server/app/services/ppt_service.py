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

def ensure_within_upload_root(path, must_exist=False):
    absolute_path = os.path.abspath(path)
    try:
        common_path = os.path.commonpath([UPLOAD_ROOT, absolute_path])
        if common_path != UPLOAD_ROOT:
            raise HTTPException(status_code=400, detail="Path is outside the allowed uploads directory")
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid path")

    if must_exist and not os.path.exists(absolute_path):
        raise HTTPException(status_code=404, detail="Requested file not found")
    return absolute_path

def convert_ppt_to_pdf(ppt_path, output_dir):
    libreoffice_path = find_libreoffice()
    if not libreoffice_path:
        raise HTTPException(status_code=503, detail="LibreOffice is not installed")

    os.makedirs(output_dir, exist_ok=True)
    profile_dir = tempfile.mkdtemp(prefix="libreoffice-profile-")
    command = [
        libreoffice_path, f"-env:UserInstallation={to_file_uri(profile_dir)}",
        "--headless", "--nologo", "--norestore", "--convert-to", "pdf",
        "--outdir", output_dir, ppt_path,
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
    return os.path.join(output_dir, base_name + ".pdf")

def render_pdf_to_images(pdf_path, output_dir):
    import fitz
    slides_dir = os.path.join(output_dir, "slides")
    os.makedirs(slides_dir, exist_ok=True)
    document = fitz.open(pdf_path)
    slides = []
    try:
        for i in range(document.page_count):
            page = document.load_page(i)
            pix = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
            img_path = os.path.join(slides_dir, f"{i + 1}.png")
            pix.save(img_path)
            slides.append({"page": i + 1, "imageUrl": img_path})
    finally:
        document.close()
    return slides
