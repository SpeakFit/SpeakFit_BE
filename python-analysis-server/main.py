import os
import time
import numpy as np
import librosa
import google.generativeai as genai
from fastapi import FastAPI, HTTPException, Body
from pydantic import BaseModel, Field
from typing import Optional, List
from dotenv import load_dotenv
import uvicorn
import json
import shutil
import subprocess
import tempfile

# 환경 설정 로드
load_dotenv(os.path.join(os.path.dirname(__file__), '../.env'))
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

# Gemini 설정
if GEMINI_API_KEY:
    try:
        genai.configure(api_key=GEMINI_API_KEY)
        # 최신 모델명으로 업데이트 (필요시 flash 등 하위 모델 사용 가능)
        model = genai.GenerativeModel('gemini-2.5-flash')
        print("[Python] Gemini AI 엔진 준비 완료")
    except Exception as e:
        print(f"[Python] Gemini 설정 실패: {e}")
        model = None
else:
    print("[Python] GEMINI_API_KEY가 없어 더미 응답 모드로 동작합니다.")
    model = None

app = FastAPI()
UPLOAD_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "uploads"))

# --- 데이터 모델 정의 ---

class ScriptWordPayload(BaseModel):
    scriptWordId: int
    scriptSentenceId: int
    sentenceIndex: int
    globalWordIndex: int
    sentenceWordIndex: int
    text: str
    normalizedText: Optional[str] = None
    startCharIndex: int
    endCharIndex: int

class AnalyzeRequest(BaseModel):
    practiceId: int
    audioUrl: str
    content: Optional[str] = None
    markedContent: str
    scriptWords: List[ScriptWordPayload] = Field(default_factory=list)
    audienceType: str
    audienceUnderstanding: str
    speechInformation: str
    styleType: str

class MarkRequest(BaseModel):
    content: str

class GenerateScriptRequest(BaseModel):
    topic: str
    time: int
    audienceAge: str
    audienceLevel: str
    speechType: str
    purpose: str
    keywords: Optional[str] = None

class UpdateScriptRequest(BaseModel):
    topic: str
    content: str
    time: int
    audienceAge: str
    audienceLevel: str
    speechType: str
    purpose: str
    keywords: Optional[str] = None

class ConvertPptRequest(BaseModel):
    pptPath: str
    outputDir: str

# --- 유틸리티 함수 ---

def clamp(value, min_value, max_value):
    """값을 지정한 범위 안으로 제한합니다."""
    return max(min_value, min(value, max_value))

def analyze_voice_features(file_path):
    """오디오 파일에서 정량적 특징 추출 (Librosa 사용)"""
    try:
        # 오디오 파일 로드
        y, sr = librosa.load(file_path, sr=None)
        duration = librosa.get_duration(y=y, sr=sr)

        # 1. 목소리 높낮이 (Pitch)
        pitches, magnitudes = librosa.piptrack(y=y, sr=sr)
        pitches = pitches[magnitudes > np.median(magnitudes)]
        avg_pitch = np.mean(pitches) if len(pitches) > 0 else 0.0

        # 2. 성량 (Intensity)
        rms = librosa.feature.rms(y=y)
        avg_intensity = np.mean(rms) * 1000

        # 3. 쉼 구간 (Pause) 탐지
        intervals = librosa.effects.split(y, top_db=25)
        pause_count = len(intervals) - 1 if len(intervals) > 0 else 0
        non_silent_duration = sum([(e - s) / sr for s, e in intervals])
        pause_ratio = (duration - non_silent_duration) / duration if duration > 0 else 0

        # 4. 발화 속도 (WPM 추정)
        onsets = librosa.onset.onset_detect(y=y, sr=sr)
        avg_wpm = (len(onsets) / 2) / (duration / 60) if duration > 0 else 0

        # 5. 발음 선명도 (ZCR)
        zcr = librosa.feature.zero_crossing_rate(y)
        avg_zcr = np.mean(zcr)

        return {
            "durationSec": float(duration),
            "avgWpm": float(avg_wpm),
            "avgPitch": float(avg_pitch),
            "avgIntensity": float(avg_intensity),
            "avgZcr": float(avg_zcr),
            "pauseRatio": float(pause_ratio),
            "wpmDiff": 10.0, # 변동성 (실제 계산 로직 필요시 추가)
            "pitchDiff": float(np.std(pitches)) if len(pitches) > 0 else 0.0,
            "intensityDiff": float(np.std(rms)) * 1000,
            "zcrDiff": float(np.std(zcr)),
            "pauseCount": int(pause_count)
        }
    except Exception as e:
        print(f"[Python] 음성 분석 중 오류: {e}")
        return None

def build_word_results(script_words, duration_sec):
    """임시 단어 정렬 결과를 생성합니다."""
    if not script_words:
        return []

    duration_ms = max(int(duration_sec * 1000), len(script_words) * 200)
    slot_ms = max(int(duration_ms / len(script_words)), 1)
    word_results = []

    for index, word in enumerate(script_words):
        start_ms = index * slot_ms
        end_ms = duration_ms if index == len(script_words) - 1 else min((index + 1) * slot_ms, duration_ms)
        confidence = round(clamp(0.92 - (index % 7) * 0.03, 0.65, 0.95), 2)
        word_results.append({
            "wordIndex": word.globalWordIndex,
            "globalWordIndex": word.globalWordIndex,
            "sentenceWordIndex": word.sentenceWordIndex,
            "startMs": start_ms,
            "endMs": end_ms,
            "confidence": confidence,
            "skipped": False,
            "status": "NORMAL"
        })

    return word_results

def group_words_by_sentence(script_words):
    """대본 단어를 문장 단위로 묶습니다."""
    sentence_map = {}
    for word in script_words:
        sentence = sentence_map.setdefault(word.sentenceIndex, {
            "scriptSentenceId": word.scriptSentenceId,
            "sentenceIndex": word.sentenceIndex,
            "words": []
        })
        sentence["words"].append(word)

    return [
        sentence_map[key]
        for key in sorted(sentence_map.keys())
    ]

def build_sentence_results(script_words, word_results, features):
    """임시 문장 단위 분석 결과를 생성합니다."""
    if not script_words or not word_results:
        return []

    word_result_map = {
        result["globalWordIndex"]: result
        for result in word_results
    }
    sentence_results = []

    for sentence in group_words_by_sentence(script_words):
        words = sentence["words"]
        related_results = [
            word_result_map[word.globalWordIndex]
            for word in words
            if word.globalWordIndex in word_result_map
        ]
        if not related_results:
            continue

        start_ms = min(result["startMs"] for result in related_results)
        end_ms = max(result["endMs"] for result in related_results)
        duration_min = max((end_ms - start_ms) / 60000, 0.001)
        word_count = len(words)
        skipped_word_count = sum(1 for result in related_results if result["skipped"])
        wpm = word_count / duration_min
        avg_confidence = sum(result["confidence"] for result in related_results) / len(related_results)
        score = clamp(avg_confidence * 100 - abs(wpm - 130) * 0.08 - skipped_word_count * 8, 0, 100)
        pause_duration_ms = int(features.get("pauseRatio", 0.0) * (end_ms - start_ms))

        sentence_results.append({
            "scriptSentenceId": sentence["scriptSentenceId"],
            "sentenceIndex": sentence["sentenceIndex"],
            "startMs": start_ms,
            "endMs": end_ms,
            "wordCount": word_count,
            "skippedWordCount": skipped_word_count,
            "wpm": round(float(wpm), 2),
            "pauseDurationMs": pause_duration_ms,
            "avgPitch": features.get("avgPitch", 0.0),
            "avgIntensity": features.get("avgIntensity", 0.0),
            "score": round(float(score), 2),
            "status": resolve_sentence_status(wpm, avg_confidence, skipped_word_count)
        })

    return sentence_results

def resolve_sentence_status(wpm, avg_confidence, skipped_word_count):
    """문장 결과 상태를 결정합니다."""
    if skipped_word_count > 0 or avg_confidence < 0.7:
        return "MISMATCH"
    if wpm > 170:
        return "FAST"
    if wpm < 80:
        return "SLOW"
    return "NORMAL"

def build_issue_results(sentence_results):
    """문제 구간 Top 5 결과를 생성합니다."""
    issues = []
    ranked_sentences = sorted(sentence_results, key=lambda result: result.get("score", 100.0))

    for display_order, sentence in enumerate(ranked_sentences[:5]):
        issue_type = resolve_issue_type(sentence)
        issues.append({
            "scriptSentenceId": sentence.get("scriptSentenceId"),
            "sentenceIndex": sentence.get("sentenceIndex"),
            "startIndex": sentence.get("sentenceIndex"),
            "endIndex": sentence.get("sentenceIndex"),
            "issueType": issue_type,
            "issueSummary": build_issue_summary(issue_type),
            "feedbackContent": build_issue_feedback(issue_type),
            "reason": build_issue_reason(sentence, issue_type),
            "score": sentence.get("score"),
            "displayOrder": display_order,
            "wpm": sentence.get("wpm"),
            "intensity": sentence.get("avgIntensity")
        })

    return issues

def resolve_issue_type(sentence):
    """문제 유형을 결정합니다."""
    if sentence.get("skippedWordCount", 0) > 0:
        return "SKIPPED_WORDS"
    if sentence.get("wpm", 0.0) > 170:
        return "TOO_FAST"
    if sentence.get("wpm", 0.0) < 80:
        return "TOO_SLOW"
    if sentence.get("pauseDurationMs", 0) > 1200:
        return "LONG_PAUSE"
    if sentence.get("score", 100.0) < 70:
        return "LOW_SCORE"
    return "LOW_CONFIDENCE"

def build_issue_summary(issue_type):
    """문제 유형 요약을 생성합니다."""
    summaries = {
        "SKIPPED_WORDS": "일부 단어가 누락되었습니다.",
        "TOO_FAST": "문장 발화 속도가 빠릅니다.",
        "TOO_SLOW": "문장 발화 속도가 느립니다.",
        "LONG_PAUSE": "문장 안의 쉼이 깁니다.",
        "LOW_SCORE": "문장 점수가 낮습니다.",
        "LOW_CONFIDENCE": "발화 신뢰도가 낮습니다."
    }
    return summaries.get(issue_type, "개선이 필요한 문장입니다.")

def build_issue_feedback(issue_type):
    """문제 유형별 피드백을 생성합니다."""
    feedback = {
        "SKIPPED_WORDS": "대본 단어를 빠뜨리지 않도록 문장 단위로 천천히 다시 읽어보세요.",
        "TOO_FAST": "핵심 단어 앞뒤에서 짧게 쉬며 속도를 낮춰보세요.",
        "TOO_SLOW": "문장 흐름이 끊기지 않도록 의미 단위로 이어 읽어보세요.",
        "LONG_PAUSE": "쉼은 유지하되 1초 안팎으로 짧게 조절해보세요.",
        "LOW_SCORE": "발음, 속도, 쉼을 함께 점검하며 다시 연습해보세요.",
        "LOW_CONFIDENCE": "입 모양을 또렷하게 하고 문장 끝을 흐리지 않게 말해보세요."
    }
    return feedback.get(issue_type, "문장 단위로 다시 연습해보세요.")

def build_issue_reason(sentence, issue_type):
    """문제 구간 사유를 생성합니다."""
    return (
        f"유형={issue_type}, "
        f"점수={sentence.get('score')}, "
        f"WPM={sentence.get('wpm')}, "
        f"쉼={sentence.get('pauseDurationMs')}ms, "
        f"누락단어={sentence.get('skippedWordCount')}"
    )

def generate_ai_feedback(features, req: AnalyzeRequest):
    """Gemini를 사용한 심층 피드백 생성 (상황 컨텍스트 활용)"""
    if not model:
        return {
            "aiSummary": "훌륭한 발표였습니다!", "wpmSummary": "적절한 속도", "wpmFeedback": "좋습니다.",
            "energySummary": "강함", "energyFeedback": "전달력 우수", "pauseFeedback": "적절",
            "symbolFeedback": "잘 지킴", "goalSimilarityScore": 85.0,
            "goalSummary": "목표 근접", "goalFeedback": "계속 연습하세요."
        }

    prompt = f"""
    당신은 세계 최고의 스피치 트레이너입니다. 다음 발표 데이터와 상황 정보를 분석하여 상세 피드백을 JSON 형식으로 작성해 주세요.

    [발표 상황 정보]
    - 청중: {req.audienceType} (이해도: {req.audienceUnderstanding})
    - 발표 종류: {req.speechInformation}
    - 목표 스타일: {req.styleType}
    - 낭독 기호 대본: {req.markedContent}

    [음성 분석 데이터]
    - 속도: {features['avgWpm']:.1f} WPM
    - 높낮이: {features['avgPitch']:.1f} Hz
    - 쉼 비율: {features['pauseRatio']*100:.1f}%
    - 쉼 횟수: {features['pauseCount']}회

    [출력 요구사항]
    반드시 다음 키를 가진 JSON 객체 하나만 출력해 주세요 (Markdown 등 다른 텍스트 금지).
    - aiSummary: 전체적인 따뜻한 총평 (2~3문장)
    - wpmSummary: 말하기 속도에 대한 한 줄 요약
    - wpmFeedback: 속도 개선을 위한 구체적 조언
    - energySummary: 성량/에너지에 대한 한 줄 요약
    - energyFeedback: 성량 조절에 대한 조언
    - pauseFeedback: 쉼 구간 활용에 대한 피드백
    - symbolFeedback: 대본의 낭독 기호(/, *)를 얼마나 잘 지켰는지에 대한 피드백
    - goalSimilarityScore: 목표 스타일과의 유사도 점수 (0~100 사이의 실수)
    - goalSummary: 목표 스타일 달성 정도 요약
    - goalFeedback: 목표 스타일에 더 가까워지기 위한 핵심 팁
    """
    try:
        response = model.generate_content(prompt)
        # JSON 문자열만 추출 (코드 블록 제거 등)
        json_str = response.text.replace("```json", "").replace("```", "").strip()
        return json.loads(json_str)
    except Exception as e:
        print(f"[Python] Gemini 피드백 생성 실패: {e}")
        return {
            "aiSummary": "분석 오류가 발생했습니다.", "goalSimilarityScore": 0.0
        }

# --- 엔드포인트 구현 ---

@app.post("/analyze")
async def run_analysis(req: AnalyzeRequest):
    print(f"[ID: {req.practiceId}] 분석 요청 처리 중...")

    # 1. 파일 경로 확인 (로컬 경로 또는 URL 처리)
    if not os.path.exists(req.audioUrl):
        # 만약 실제 S3 URL로 온다면 여기서 다운로드 로직이 필요할 수 있음
        # 현재는 로컬 경로로 가정
        print(f"[Python] 파일을 찾을 수 없음: {req.audioUrl}")
        raise HTTPException(status_code=404, detail="Audio file not found")

    # 2. 음성 특징 추출
    features = analyze_voice_features(req.audioUrl)
    if not features:
        raise HTTPException(status_code=500, detail="Audio analysis failed")

    # 3. 단어/문장/문제 구간 결과 생성
    word_results = build_word_results(req.scriptWords, features.get("durationSec", 0.0))
    sentence_results = build_sentence_results(req.scriptWords, word_results, features)
    issue_results = build_issue_results(sentence_results)

    # 4. AI 상세 피드백 생성
    ai_feedback = generate_ai_feedback(features, req)
    
    # 5. 결과 병합
    result = {
        **features,
        **ai_feedback,
        "wordResults": word_results,
        "sentenceResults": sentence_results,
        "issues": issue_results
    }
    
    print(f"[ID: {req.practiceId}] 분석 완료")
    return result

@app.post("/scripts/mark")
async def mark_script(req: MarkRequest):
    print(f"[Python] 대본 기호 생성 요청 수신")
    
    if not model:
        return {"markedContent": req.content}

    prompt = f"""
    당신은 스피치 전문가입니다. 다음 대본을 분석하여 사용자가 낭독할 때 참고할 수 있는 기호를 삽입해 주세요.
    
    [원본 대본]
    {req.content}
    
    [규칙]
    1. 끊어 읽어야 할 부분(쉼표, 의미 단위) 뒤에 ' / '를 넣어주세요.
    2. 매우 강조해서 읽어야 할 단어 앞뒤에 '*'를 붙여주세요 (예: *중요한*).
    3. 원본 텍스트의 내용을 절대 변경하지 마세요. 오직 기호만 추가하세요.
    4. 결과물은 오직 기호가 추가된 텍스트만 출력하세요.
    """
    try:
        response = model.generate_content(prompt)
        marked_text = response.text.strip()
        return {"markedContent": marked_text}
    except Exception as e:
        print(f"[Python] 대본 기호 생성 실패: {e}")
        return {"markedContent": req.content}

@app.post("/scripts/generate")
async def generate_script(req: GenerateScriptRequest):
    print("[Python] AI script generation request received")

    if not model:
        raise HTTPException(status_code=503, detail="AI model is not configured")

    prompt = f"""
    당신은 발표 대본을 전문적으로 작성하는 스피치 코치입니다.

    다음 발표 상황에 맞춰 실제 발표자가 그대로 읽을 수 있는 한국어 발표 대본을 작성해주세요.
    발표자는 "{req.topic}"을 주제로 {req.time}분 동안 발표합니다.
    청중은 "{req.audienceAge}" 연령대이며, 해당 주제에 대한 지식수준은 "{req.audienceLevel}"입니다.
    발표 형식은 "{req.speechType}"이고, 발표 목적은 "{req.purpose}"입니다.
    발표에서 특히 강조해야 할 키워드는 "{req.keywords or "없음"}"입니다.

    작성 규칙:
    1. 청중 연령대와 지식수준을 함께 고려해서 설명 난이도, 예시, 어휘를 조절해주세요.
    2. 발표 형식에 맞게 말투와 구성을 바꿔주세요. 예를 들어 강의는 설명 중심, 면접은 답변 중심, 토론은 쟁점 중심으로 작성해주세요.
    3. 발표 목적이 분명히 드러나도록 도입부에서 문제의식이나 목표를 제시해주세요.
    4. 강조 키워드가 있으면 억지로 나열하지 말고 문맥 안에 자연스럽게 녹여주세요.
    5. 도입, 본론, 결론 흐름이 자연스럽게 이어지도록 작성해주세요.
    6. 발표자가 바로 읽을 수 있는 문장형 대본으로만 작성하고, 목차나 설명문처럼 쓰지 마세요.
    7. Markdown 코드블록 없이 JSON 객체 하나만 출력해주세요.

    출력 형식:
    {{
      "generatedScript": "발표 대본 전체 내용"
    }}
    """

    try:
        response = model.generate_content(prompt)
        json_str = response.text.replace("```json", "").replace("```", "").strip()
        result = json.loads(json_str)
        generated_script = result.get("generatedScript")

        if not generated_script:
            raise HTTPException(status_code=502, detail="AI response does not contain generatedScript")

        return {
            "generatedScript": generated_script
        }
    except HTTPException:
        raise
    except Exception as e:
        print(f"[Python] AI script generation failed: {e}")
        raise HTTPException(status_code=500, detail="AI script generation failed") from e

@app.post("/scripts/update")
async def update_script(req: UpdateScriptRequest):
    print("[Python] AI script update request received")

    if not model:
        raise HTTPException(status_code=503, detail="AI model is not configured")

    prompt = f"""
    당신은 발표 대본을 개선하는 전문 스피치 코치입니다.

    아래 기존 대본을 발표 조건에 맞게 최적화해주세요.
    발표자는 "{req.topic}"을 주제로 {req.time}분 동안 발표합니다.
    청중은 "{req.audienceAge}" 연령대이며, 해당 주제에 대한 지식수준은 "{req.audienceLevel}"입니다.
    발표 형식은 "{req.speechType}"이고, 발표 목적은 "{req.purpose}"입니다.
    발표에서 특히 강조해야 할 키워드는 "{req.keywords or "없음"}"입니다.

    기존 대본:
    {req.content}

    최적화 규칙:
    1. 기존 대본의 핵심 의미와 발표 주제는 유지해주세요.
    2. 발표 시간이 {req.time}분에 맞도록 문장 길이와 정보량을 조절해주세요.
    3. 청중 연령대와 지식수준에 맞게 설명 난이도, 예시, 어휘를 조정해주세요.
    4. 발표 형식에 맞게 말투와 구성 방식을 다듬어주세요.
    5. 발표 목적이 더 분명하게 드러나도록 도입과 결론을 보강해주세요.
    6. 강조 키워드가 있으면 문맥 안에 자연스럽게 반영해주세요.
    7. 발표자가 바로 읽을 수 있는 문장형 대본으로만 작성하고, 수정 이유나 설명은 쓰지 마세요.
    8. Markdown 코드블록 없이 JSON 객체 하나만 출력해주세요.

    출력 형식:
    {{
      "optimizedScript": "최적화된 발표 대본 전체 내용"
    }}
    """

    try:
        response = model.generate_content(prompt)
        json_str = response.text.replace("```json", "").replace("```", "").strip()
        result = json.loads(json_str)
        optimized_script = result.get("optimizedScript")

        if not optimized_script:
            raise HTTPException(status_code=502, detail="AI response does not contain optimizedScript")

        return {
            "optimizedScript": optimized_script
        }
    except HTTPException:
        raise
    except Exception as e:
        print(f"[Python] AI script update failed: {e}")
        raise HTTPException(status_code=500, detail="AI script update failed") from e

def find_libreoffice():
    """LibreOffice 실행 파일 경로를 찾습니다."""
    env_path = os.getenv("LIBREOFFICE_PATH")
    if env_path and os.path.exists(env_path):
        return env_path

    command_path = shutil.which("soffice") or shutil.which("libreoffice")
    if command_path:
        return command_path

    candidates = [
        r"C:\Program Files\LibreOffice\program\soffice.exe",
        r"C:\Program Files (x86)\LibreOffice\program\soffice.exe",
    ]
    for candidate in candidates:
        if os.path.exists(candidate):
            return candidate

    return None

def to_file_uri(path):
    normalized_path = os.path.abspath(path).replace("\\", "/")
    if normalized_path.startswith("/"):
        return "file://" + normalized_path
    return "file:///" + normalized_path

def ensure_within_upload_root(path, must_exist=False):
    absolute_path = os.path.abspath(path)
    try:
        common_path = os.path.commonpath([UPLOAD_ROOT, absolute_path])
    except ValueError:
        raise HTTPException(status_code=400, detail="Path is outside the allowed uploads directory")

    if common_path != UPLOAD_ROOT:
        raise HTTPException(status_code=400, detail="Path is outside the allowed uploads directory")

    if must_exist and not os.path.exists(absolute_path):
        raise HTTPException(status_code=404, detail="Requested file not found")

    return absolute_path

def convert_ppt_to_pdf(ppt_path, output_dir):
    """LibreOffice를 사용해 PPT/PPTX 파일을 PDF로 변환합니다."""
    libreoffice_path = find_libreoffice()
    if not libreoffice_path:
        raise HTTPException(status_code=503, detail="LibreOffice is not installed or configured")

    os.makedirs(output_dir, exist_ok=True)
    profile_dir = tempfile.mkdtemp(prefix="libreoffice-profile-")
    command = [
        libreoffice_path,
        f"-env:UserInstallation={to_file_uri(profile_dir)}",
        "--headless",
        "--nologo",
        "--norestore",
        "--convert-to",
        "pdf",
        "--outdir",
        output_dir,
        ppt_path,
    ]
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        stdout, stderr = process.communicate(timeout=120)
    except subprocess.TimeoutExpired:
        process.kill()
        stdout, stderr = process.communicate()
        print(f"[Python] LibreOffice conversion timed out: {stdout} {stderr}")
        raise HTTPException(status_code=504, detail="PPT to PDF conversion timed out")
    finally:
        shutil.rmtree(profile_dir, ignore_errors=True)

    if process.returncode != 0:
        print(f"[Python] LibreOffice conversion failed: {stderr}")
        raise HTTPException(status_code=500, detail="PPT to PDF conversion failed")

    base_name = os.path.splitext(os.path.basename(ppt_path))[0]
    pdf_path = os.path.join(output_dir, base_name + ".pdf")
    if not os.path.exists(pdf_path):
        raise HTTPException(status_code=500, detail="Converted PDF file not found")

    return pdf_path

def render_pdf_to_images(pdf_path, output_dir):
    """PDF 각 페이지를 PNG 이미지로 렌더링합니다."""
    try:
        import fitz
    except ImportError as e:
        raise HTTPException(status_code=503, detail="PyMuPDF is not installed") from e

    slides_dir = os.path.join(output_dir, "slides")
    os.makedirs(slides_dir, exist_ok=True)

    document = fitz.open(pdf_path)
    slides = []
    try:
        for page_index in range(document.page_count):
            page = document.load_page(page_index)
            pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
            image_path = os.path.join(slides_dir, f"{page_index + 1}.png")
            pixmap.save(image_path)
            slides.append({
                "page": page_index + 1,
                "imageUrl": image_path
            })
    finally:
        document.close()

    return slides

@app.post("/ppt/convert")
async def convert_ppt(req: ConvertPptRequest):
    print("[Python] PPT conversion request received")

    ppt_path = ensure_within_upload_root(req.pptPath, must_exist=True)
    output_dir = ensure_within_upload_root(req.outputDir, must_exist=False)

    extension = os.path.splitext(ppt_path)[1].lower()
    if extension not in [".ppt", ".pptx"]:
        raise HTTPException(status_code=400, detail="Only PPT or PPTX files are supported")

    pdf_path = None
    try:
        pdf_path = convert_ppt_to_pdf(ppt_path, output_dir)
        slides = render_pdf_to_images(pdf_path, output_dir)

        return {
            "sourcePptUrl": ppt_path,
            "totalSlides": len(slides),
            "slides": slides
        }
    except HTTPException:
        raise
    except Exception as e:
        print(f"[Python] PPT conversion failed: {e}")
        raise HTTPException(status_code=500, detail="PPT conversion failed") from e
    finally:
        if pdf_path and os.path.exists(pdf_path):
            try:
                os.remove(pdf_path)
            except Exception as cleanup_error:
                print(f"[Python] PDF cleanup failed: {cleanup_error}")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)
