from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.endpoints import router
import uvicorn
from static_ffmpeg import add_paths

# FFmpeg 경로 자동 설정
add_paths()

app = FastAPI(title="SpeakFit Analysis Server")

# CORS 설정 추가
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# API 라우터 등록
app.include_router(router)

@app.get("/")
async def root():
    return {"message": "SpeakFit Analysis Server is running", "version": "2.0.0"}

if __name__ == "__main__":
    # 포트는 스프링 설정에 맞춰 5000번 유지
    uvicorn.run("app.main:app", host="0.0.0.0", port=5000, reload=True)
