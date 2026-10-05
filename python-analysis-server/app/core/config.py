import os
import google.generativeai as genai
from dotenv import load_dotenv
from langsmith import Client as LangSmithClient

# 환경 설정 로드 (로컬 개발: SpeakFit_BE 루트의 .env, 컨테이너/운영: 프로세스 환경변수). 예시는 .env.python.example 참고.
env_path = os.path.join(os.path.dirname(__file__), '../../../.env')
load_dotenv(env_path)

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

# LangSmith 트레이싱 설정
LANGSMITH_API_KEY = os.getenv("LANGSMITH_API_KEY")
LANGSMITH_PROJECT = os.getenv("LANGSMITH_PROJECT", "speakfit")
LANGSMITH_TRACING = os.getenv("LANGSMITH_TRACING", "false").lower() == "true"

if LANGSMITH_API_KEY:
    # LangSmith SDK가 직접 읽는 환경변수 (LANGSMITH_* prefix)
    os.environ["LANGSMITH_TRACING"] = "true" if LANGSMITH_TRACING else "false"
    os.environ["LANGSMITH_API_KEY"] = LANGSMITH_API_KEY
    os.environ["LANGSMITH_PROJECT"] = LANGSMITH_PROJECT
    try:
        _ls_client = LangSmithClient(api_key=LANGSMITH_API_KEY)
        print(f"[Python] LangSmith 트레이싱 활성화 — project={LANGSMITH_PROJECT}", flush=True)
    except Exception as e:
        print(f"[Python] LangSmith 초기화 실패: {e}", flush=True)
else:
    print("[Python] LANGSMITH_API_KEY 없음 — 트레이싱 비활성화", flush=True)

# Gemini 설정
GEMINI_MODEL_NAME = "gemini-3.1-flash-lite"

model = None          # 피드백/낭독기호 등에 쓰는 기본 모델 (feedback_model 과 동일 인스턴스)
script_model = None   # 대본 초안 생성용 모델
feedback_model = None # 발표 피드백/요약용 모델

if GEMINI_API_KEY:
    try:
        genai.configure(api_key=GEMINI_API_KEY)
        # 대본 초안/최적화
        script_model = genai.GenerativeModel(GEMINI_MODEL_NAME)
        # 피드백/요약/낭독기호
        feedback_model = genai.GenerativeModel(GEMINI_MODEL_NAME)
        # 기존 코드 호환성 유지 — feedback_model과 동일 인스턴스
        model = feedback_model
        print(f"[Python] Gemini AI 엔진 준비 완료 (model={GEMINI_MODEL_NAME})")
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
GOOGLE_STT_SAMPLE_RATE = int(os.getenv("GOOGLE_STT_SAMPLE_RATE", "16000"))
GOOGLE_STT_INTERIM_RESULTS = os.getenv("GOOGLE_STT_INTERIM_RESULTS", "true").lower() == "true"
ANALYSIS_STT_PROVIDER = os.getenv("ANALYSIS_STT_PROVIDER", "google" if GOOGLE_STT_ENABLED else "none").lower()
JWT_SECRET = os.getenv("JWT_SECRET")
GOOGLE_STT_SYNC_MAX_SECONDS = float(os.getenv("GOOGLE_STT_SYNC_MAX_SECONDS", "55"))
GOOGLE_STT_ASYNC_TIMEOUT_SECONDS = int(os.getenv("GOOGLE_STT_ASYNC_TIMEOUT_SECONDS", "600"))
GOOGLE_STT_INLINE_MAX_BYTES = int(os.getenv("GOOGLE_STT_INLINE_MAX_BYTES", str(10 * 1024 * 1024)))
GOOGLE_STT_STREAM_MAX_SECONDS = float(os.getenv("GOOGLE_STT_STREAM_MAX_SECONDS", "290"))
GOOGLE_STT_STREAM_MAX_CHUNK_BYTES = int(os.getenv("GOOGLE_STT_STREAM_MAX_CHUNK_BYTES", str(25 * 1024)))
GOOGLE_STT_STREAM_QUEUE_SIZE = int(os.getenv("GOOGLE_STT_STREAM_QUEUE_SIZE", "200"))
GOOGLE_STT_STREAM_ALLOWED_ENCODINGS = [
    encoding.strip().upper()
    for encoding in os.getenv("GOOGLE_STT_STREAM_ALLOWED_ENCODINGS", "WEBM_OPUS,OGG_OPUS,LINEAR16,FLAC").split(",")
    if encoding.strip()
]

# 음성 분석 성능 설정
# [PERF] librosa.yin(피치 추출)은 분석 시간을 지배하며 길이에 비례해 느려진다.
#   yin 프레임 수를 이 값으로 상한 처리해 긴 녹음에서도 분석시간을 일정하게 묶는다.
#   짧은 녹음(약 192초 이하)은 기본 hop(512)과 동일하게 동작해 결과가 변하지 않는다.
VOICE_PITCH_MAX_FRAMES = int(os.getenv("VOICE_PITCH_MAX_FRAMES", "6000"))

# S3 설정 (Spring Boot 와 동일한 환경변수 이름 사용)
S3_BUCKET_NAME = os.getenv("AWS_S3_BUCKET", "")
S3_REGION = os.getenv("AWS_REGION", "ap-northeast-2")
AWS_ACCESS_KEY_ID_VAL = os.getenv("AWS_ACCESS_KEY", "")
AWS_SECRET_ACCESS_KEY_VAL = os.getenv("AWS_SECRET_KEY", "")
