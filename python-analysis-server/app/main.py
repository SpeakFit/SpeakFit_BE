import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.endpoints import router
import asyncio
import uvicorn
from static_ffmpeg import add_paths

# FFmpeg 경로 자동 설정
add_paths()

app = FastAPI(title="SpeakFit Analysis Server")

# CORS: 이 서버는 Spring 백엔드가 서버 간으로 호출하므로 기본적으로 브라우저 교차 출처 요청을 허용하지 않는다.
# 브라우저에서 직접 HTTP 요청을 보내야 할 때만 CORS_ALLOWED_ORIGINS(쉼표 구분)로 명시한다.
# (WebSocket 은 CORS 대상이 아니며 JWT 토큰으로 인증한다.)
_cors_allowed_origins = [
    origin.strip()
    for origin in os.getenv("CORS_ALLOWED_ORIGINS", "").split(",")
    if origin.strip()
]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_cors_allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# API 라우터 등록
app.include_router(router)


@app.on_event("startup")
async def startup_warmup():
    """[STEP-B] 앱 기동 시 콜드 스타트 유발 리소스를 미리 초기화.

    1. SpeechClient 싱글톤 생성 → 첫 STT 세션의 gRPC 채널 지연 제거
    2. KoNLPy Okt 태거 초기화 → 첫 낭독기호 마킹 JVM 지연 제거
    백그라운드 스레드에서 실행해 서버 기동 블로킹을 방지.
    """
    loop = asyncio.get_event_loop()

    async def _warm():
        from app.api.endpoints import warmup_stt_client
        from app.services.ai_service import warmup_marking_engine
        # SpeechClient — gRPC 채널 사전 확보
        await loop.run_in_executor(None, warmup_stt_client)
        # KoNLPy Okt — JVM + 사전 로딩
        await loop.run_in_executor(None, warmup_marking_engine)
        print("[Python STEP-B] 전체 워밍업 완료", flush=True)

    asyncio.ensure_future(_warm())


@app.get("/")
async def root():
    return {"message": "SpeakFit Analysis Server is running", "version": "2.0.0"}

if __name__ == "__main__":
    # 포트는 스프링 설정에 맞춰 5000번 유지
    uvicorn.run("app.main:app", host="0.0.0.0", port=5000, reload=True)
