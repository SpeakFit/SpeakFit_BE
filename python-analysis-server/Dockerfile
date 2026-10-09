# SpeakFit 분석 서버 (FastAPI)
# 빌드:  docker build -t speakfit-analysis python-analysis-server
# 실행:  docker run --env-file python-analysis-server/.env -p 5000:5000 speakfit-analysis
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    JAVA_HOME=/usr/lib/jvm/default-java \
    HOME=/home/app

# 시스템 패키지
#  - libreoffice-impress : PPT/PPTX -> PDF 변환 (soffice)
#  - fonts-nanum         : 한글이 깨지지 않도록 (PPT 변환 시 글꼴 대체)
#  - default-jre-headless: KoNLPy(JPype) 가 쓰는 JVM
#  - libsndfile1         : librosa 음성 파일 읽기
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libreoffice-impress \
        fonts-nanum \
        default-jre-headless \
        libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --create-home --home-dir /home/app --shell /usr/sbin/nologin app

WORKDIR /srv/analysis

# 의존성을 먼저 설치해 코드만 바뀔 때 이 레이어를 재사용한다.
COPY requirements.txt .
RUN pip install -r requirements.txt

# ffmpeg(static-ffmpeg)는 첫 import 때 인터넷에서 내려받으므로, 실행 시점이 아니라 빌드 시점에 받아 둔다.
RUN python -c "import static_ffmpeg; static_ffmpeg.add_paths()"

COPY app ./app

USER app
EXPOSE 5000

# 실시간 STT 세션 상태가 프로세스 안에 있으므로 워커는 1개로 둔다.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:5000/', timeout=3)" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "5000"]
