import os
import google.generativeai as genai
from dotenv import load_dotenv

# 환경 설정 로드 (.env 파일이 SpeakFit_BE 루트에 있음)
load_dotenv(os.path.join(os.path.dirname(__file__), '../../../.env'))

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

# Gemini 설정
model = None
if GEMINI_API_KEY:
    try:
        genai.configure(api_key=GEMINI_API_KEY)
        model = genai.GenerativeModel('gemini-2.5-flash')
        print("[Python] Gemini AI 엔진 준비 완료")
    except Exception as e:
        print(f"[Python] Gemini 설정 실패: {e}")
else:
    print("[Python] GEMINI_API_KEY가 없어 더미 응답 모드로 동작합니다.")

# 경로 설정 (app/core/config.py 기준이므로 세 단계 위가 루트)
BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
UPLOAD_ROOT = os.path.join(BASE_DIR, "uploads")

# STT 관련 설정
GOOGLE_STT_ENABLED = os.getenv("GOOGLE_STT_ENABLED", "false").lower() == "true"
GOOGLE_STT_LANGUAGE_CODE = os.getenv("GOOGLE_STT_LANGUAGE_CODE", "ko-KR")
GOOGLE_STT_ENCODING = os.getenv("GOOGLE_STT_ENCODING", "WEBM_OPUS")
GOOGLE_STT_SAMPLE_RATE = int(os.getenv("GOOGLE_STT_SAMPLE_RATE", "48000"))
GOOGLE_STT_INTERIM_RESULTS = os.getenv("GOOGLE_STT_INTERIM_RESULTS", "true").lower() == "true"
ANALYSIS_STT_PROVIDER = os.getenv("ANALYSIS_STT_PROVIDER", "google" if GOOGLE_STT_ENABLED else "none").lower()
