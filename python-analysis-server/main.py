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
class FeedbackRequest(BaseModel):
    feedbackId: int
    avgWpm: float
    avgPitch: float
    avgIntensity: float
    avgZcr: float
    pauseRatio: float
    startDate: str
    endDate: str

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

def generate_comprehensive_feedback(data):
    """Gemini를 사용한 종합 피드백 생성"""
    if not model:
        return "AI 모델 연결 불가: 더미 피드백입니다. 꾸준함이 돋보입니다!"

    prompt = f"""
    당신은 스피치 전문 코치입니다. 다음은 학생의 {data.startDate}부터 {data.endDate}까지의 연습 평균 데이터입니다.

    [평균 데이터]
    - 말하기 속도: {data.avgWpm:.1f} WPM (적정: 100~130)
    - 목소리 높낮이: {data.avgPitch:.1f} Hz
    - 목소리 크기: {data.avgIntensity:.1f} dB
    - 쉼 비율: {data.pauseRatio*100:.1f}%
    - 발음 정확도: {data.avgZcr:.4f}

    [요청]
    위 데이터를 바탕으로 학생의 전반적인 스피치 스타일을 진단하고, 앞으로 어떤 점을 보완하면 좋을지 따뜻하고 구체적인 조언을 3~4문장으로 작성해주세요.
    격려하는 어조로 한국어로 작성해 주세요.
    """
    try:
        response = model.generate_content(prompt)
        return response.text
    except Exception as e:
        print(f"[Python] Gemini 피드백 생성 실패: {e}")
        return "AI 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."

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

@app.post("/feedback/summary")
def create_feedback_summary(req: FeedbackRequest):
    print(f"[Feedback ID: {req.feedbackId}] 종합 피드백 요청 수신")

    # 1. 전달받은 데이터를 바탕으로 Gemini 종합 피드백 생성
    ai_feedback = generate_comprehensive_feedback(req)

    print(f"[Feedback ID: {req.feedbackId}] 피드백 생성 완료")

    # 2. 생성된 텍스트 리포트 반환
    return {"aiFeedback": ai_feedback}

if __name__ == "__main__":
    # 포트 5000에서 실행
    uvicorn.run(app, host="0.0.0.0", port=5000)