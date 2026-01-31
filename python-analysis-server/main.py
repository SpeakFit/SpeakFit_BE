import os
import time
import numpy as np
import librosa
import google.generativeai as genai
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv
import uvicorn

# 환경 설정 로드 (프로젝트 루트의 .env 파일을 읽음)
load_dotenv(os.path.join(os.path.dirname(__file__), '../.env'))
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

# Gemini 설정
if GEMINI_API_KEY:
    try:
        genai.configure(api_key=GEMINI_API_KEY)
        model = genai.GenerativeModel('gemini-2.5-flash')
        print("[Python] Gemini AI 엔진 준비 완료")
    except Exception as e:
        print(f"[Python] Gemini 설정 실패: {e}")
        model = None
else:
    print("[Python] GEMINI_API_KEY가 없어 더미 응답 모드로 동작합니다.")
    model = None

app = FastAPI()

class AnalyzeRequest(BaseModel):
    practiceId: int
    audioUrl: str

def analyze_voice_features(file_path):
    """오디오 파일에서 정량적 특징 추출 (Librosa 사용)"""
    try:
        # 오디오 파일 로드
        y, sr = librosa.load(file_path, sr=None)
        duration = librosa.get_duration(y=y, sr=sr)

        # 1. 목소리 높낮이 (Pitch) - 기본 주파수
        pitches, magnitudes = librosa.piptrack(y=y, sr=sr)
        pitches = pitches[magnitudes > np.median(magnitudes)]
        avg_pitch = np.mean(pitches) if len(pitches) > 0 else 0.0

        # 2. 성량 (Intensity) - RMS 에너지
        rms = librosa.feature.rms(y=y)
        avg_intensity = np.mean(rms) * 1000

        # 3. 쉼 구간 (Pause) 탐지
        # 20dB 이하를 무음으로 간주하여 분할
        intervals = librosa.effects.split(y, top_db=20)
        pause_count = len(intervals) - 1 if len(intervals) > 0 else 0
        non_silent_duration = sum([(e - s) / sr for s, e in intervals])
        pause_ratio = (duration - non_silent_duration) / duration if duration > 0 else 0

        # 4. 발화 속도 (WPM 추정)
        # Onset(소리의 시작점)을 음절로 간주하여 약식 계산
        onsets = librosa.onset.onset_detect(y=y, sr=sr)
        avg_wpm = (len(onsets) / 2) / (duration / 60) if duration > 0 else 0

        # 5. 발음 선명도 (ZCR)
        zcr = librosa.feature.zero_crossing_rate(y)
        avg_zcr = np.mean(zcr)

        return {
            "avgWpm": float(avg_wpm),
            "avgPitch": float(avg_pitch),
            "avgIntensity": float(avg_intensity),
            "avgZcr": float(avg_zcr),
            "pauseRatio": float(pause_ratio),
            "wpmDiff": 10.0,
            "pitchDiff": float(np.std(pitches)) if len(pitches) > 0 else 0.0,
            "intensityDiff": float(np.std(rms)) * 1000,
            "zcrDiff": float(np.std(zcr)),
            "pauseCount": int(pause_count)
        }
    except Exception as e:
        print(f"[Python] 음성 분석 중 오류: {e}")
        return None

def generate_ai_summary(data):
    """Gemini를 사용한 텍스트 총평 생성"""
    if not model:
        return "훌륭한 발표였습니다! 속도와 톤이 안정적입니다. (더미 피드백)"

    prompt = f"""
    당신은 전문 스피치 트레이너입니다. 다음 발표 데이터를 분석하여 학생에게 따뜻한 조언을 해주세요.

    [데이터]
    - 말하기 속도: {data['avgWpm']:.1f} WPM (적정: 100~130)
    - 목소리 높낮이: {data['avgPitch']:.1f} Hz
    - 쉼 비율: {data['pauseRatio']*100:.1f}%
    - 쉼 횟수: {data['pauseCount']}회

    [요청]
    데이터의 수치를 구체적으로 언급하며, 칭찬 1문장, 개선점 1문장, 응원 1문장으로 총 3문장의 한국어 총평을 작성해 주세요.
    """
    try:
        response = model.generate_content(prompt)
        return response.text
    except Exception as e:
        print(f"[Python] Gemini 호출 실패: {e}")
        return "AI 분석이 지연되고 있습니다. 잠시 후 다시 확인해주세요."

@app.post("/analyze")
def run_analysis(req: AnalyzeRequest):
    print(f"[ID: {req.practiceId}] 분석 요청 처리 중...")

    # 1. 파일 경로 확인
    if not os.path.exists(req.audioUrl):
        print(f"[Python] 파일을 찾을 수 없음: {req.audioUrl}")
        raise HTTPException(status_code=404, detail="Audio file not found")

    # 2. 정량 특징 추출
    features = analyze_voice_features(req.audioUrl)

    if not features:
        raise HTTPException(status_code=500, detail="Audio analysis failed")

    # 3. AI 총평 생성
    features["aiSummary"] = generate_ai_summary(features)

    print(f"[ID: {req.practiceId}] 분석 완료")
    return features

if __name__ == "__main__":
    # 포트 5000에서 실행
    uvicorn.run(app, host="0.0.0.0", port=5000)