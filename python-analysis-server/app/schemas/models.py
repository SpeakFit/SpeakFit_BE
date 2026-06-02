from pydantic import BaseModel, Field
from typing import Optional, List

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

# [STEP 4-A] Java GuideServiceImpl(STEP 3)이 계산한 목표치 수신 모델
# TargetMetricCalculator.calculate() → buildTargetMetricsPayload() → Python /analyze
class TargetMetrics(BaseModel):
    targetWpm: Optional[float] = None        # Baseline × §5 WPM 배율 (§7 guard 적용)
    targetPitch: Optional[float] = None      # Baseline × §5 Pitch 배율 (§7 guard 적용)
    targetIntensity: Optional[float] = None  # §6 Gold Standard dB + §8 청중 offset
    targetZcr: Optional[float] = None        # §6 Gold Standard ZCR
    targetPauseRatio: Optional[float] = None # §6 Gold Standard Pause + §8 청중 clamp

class AnalyzeRequest(BaseModel):
    practiceId: int
    audioUrl: str
    content: Optional[str] = None
    markedContent: Optional[str] = None
    scriptWords: List[ScriptWordPayload] = Field(default_factory=list)
    audienceType: str
    audienceUnderstanding: str
    speechInformation: str
    styleType: Optional[str] = None
    # [STEP 1] Pitch 성별 필터 적용용 — 'MALE' / 'FEMALE'
    gender: Optional[str] = None
    # [STEP 4-A] STEP 3 계산 목표치 — 이전에는 Pydantic이 조용히 폐기하던 필드
    targetMetrics: Optional[TargetMetrics] = None

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
