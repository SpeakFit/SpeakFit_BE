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
