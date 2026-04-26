import os
import json
import asyncio
import queue
import threading
from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect
from app.schemas.models import (
    AnalyzeRequest, MarkRequest, GenerateScriptRequest, 
    UpdateScriptRequest, ConvertPptRequest, ScriptWordPayload
)
from app.services.voice_service import (
    analyze_voice_features, build_aligned_word_results, build_word_results, 
    build_sentence_results, build_issue_results
)
from app.services.stt_service import transcribe_audio
from app.services.ai_service import (
    generate_ai_feedback, generate_script_ai, 
    update_script_ai, mark_script_ai
)
from app.services.ppt_service import (
    ensure_within_upload_root, convert_ppt_to_pdf, render_pdf_to_images
)
from app.utils.helpers import clamp, normalize_match_text
from app.core.config import (
    GOOGLE_STT_ENABLED, GOOGLE_STT_SAMPLE_RATE, 
    GOOGLE_STT_LANGUAGE_CODE, GOOGLE_STT_ENCODING, GOOGLE_STT_INTERIM_RESULTS
)

router = APIRouter()

# --- STT Helpers ---
def get_google_audio_encoding(speech):
    encoding_map = {
        "LINEAR16": speech.RecognitionConfig.AudioEncoding.LINEAR16,
        "FLAC": speech.RecognitionConfig.AudioEncoding.FLAC,
        "WEBM_OPUS": speech.RecognitionConfig.AudioEncoding.WEBM_OPUS,
    }
    return encoding_map.get(GOOGLE_STT_ENCODING.upper(), speech.RecognitionConfig.AudioEncoding.WEBM_OPUS)

class GoogleStreamingSttSession:
    def __init__(self, practice_id, script_words, websocket, loop):
        self.practice_id = practice_id
        self.script_words = script_words
        self.websocket = websocket
        self.loop = loop
        self.audio_queue = queue.Queue()
        self.closed = threading.Event()

    def start(self):
        try:
            from google.cloud import speech
            self.thread = threading.Thread(target=self._run, args=(speech,), daemon=True)
            self.thread.start()
            return True
        except Exception as e:
            print(f"STT Error: {e}")
            return False

    def add_audio(self, chunk):
        if chunk: self.audio_queue.put(chunk)

    def stop(self):
        self.closed.set()
        self.audio_queue.put(None)

    def _run(self, speech):
        try:
            client = speech.SpeechClient()
            config = speech.RecognitionConfig(
                encoding=get_google_audio_encoding(speech),
                sample_rate_hertz=GOOGLE_STT_SAMPLE_RATE,
                language_code=GOOGLE_STT_LANGUAGE_CODE,
            )
            streaming_config = speech.StreamingRecognitionConfig(config=config, interim_results=GOOGLE_STT_INTERIM_RESULTS)
            
            def requests():
                yield speech.StreamingRecognizeRequest(streaming_config=streaming_config)
                while not self.closed.is_set():
                    chunk = self.audio_queue.get()
                    if chunk is None: break
                    yield speech.StreamingRecognizeRequest(audio_content=chunk)

            responses = client.streaming_recognize(requests())
            for response in responses:
                if self.closed.is_set(): break
                for result in response.results:
                    if not result.alternatives: continue
                    alt = result.alternatives[0]
                    # 단순화된 로직 (필요시 estimate_word_index_from_transcript 유지)
                    msg = {"type": "highlight", "practiceId": self.practice_id, "transcript": alt.transcript}
                    asyncio.run_coroutine_threadsafe(self.websocket.send_json(msg), self.loop)
        except Exception as e:
            print(f"STT Run Error: {e}")

# --- Endpoints ---

@router.post("/analyze")
async def run_analysis(req: AnalyzeRequest):
    if not os.path.exists(req.audioUrl):
        raise HTTPException(status_code=404, detail="Audio file not found")
    
    features = analyze_voice_features(req.audioUrl)
    if not features:
        raise HTTPException(status_code=500, detail="Analysis failed")

    stt_words = transcribe_audio(req.audioUrl)
    if stt_words:
        print(f"[Python] 분석 STT 단어 타임스탬프 사용 가능: words={len(stt_words)}")
    else:
        print("[Python] 분석 STT 단어 타임스탬프 없음: duration 기반 fallback 사용")

    word_results = build_aligned_word_results(req.scriptWords, stt_words) if stt_words else build_word_results(req.scriptWords, features.get("durationSec", 0.0))
    sentence_results = build_sentence_results(req.scriptWords, word_results, features)
    issue_results = build_issue_results(sentence_results)
    ai_feedback = generate_ai_feedback(features, req)

    return {**features, **ai_feedback, "wordResults": word_results, "sentenceResults": sentence_results, "issues": issue_results}

@router.post("/scripts/mark")
async def mark_script(req: MarkRequest):
    marked = await mark_script_ai(req.content)
    return {"markedContent": marked}

@router.post("/scripts/generate")
async def generate_script(req: GenerateScriptRequest):
    return await generate_script_ai(req)

@router.post("/scripts/update")
async def update_script(req: UpdateScriptRequest):
    return await update_script_ai(req)

@router.post("/ppt/convert")
async def convert_ppt(req: ConvertPptRequest):
    ppt_path = ensure_within_upload_root(req.pptPath, must_exist=True)
    output_dir = ensure_within_upload_root(req.outputDir)
    pdf_path = convert_ppt_to_pdf(ppt_path, output_dir)
    slides = render_pdf_to_images(pdf_path, output_dir)
    if os.path.exists(pdf_path): os.remove(pdf_path)
    return {"sourcePptUrl": ppt_path, "totalSlides": len(slides), "slides": slides}

@router.websocket("/ws/practice/{practice_id}")
async def practice_websocket(websocket: WebSocket, practice_id: int):
    await websocket.accept()
    stt_session = None
    loop = asyncio.get_running_loop()
    try:
        while True:
            msg = await websocket.receive()
            if msg.get("type") == "websocket.disconnect": break
            if msg.get("text"):
                data = json.loads(msg["text"])
                if data.get("type") == "init" and GOOGLE_STT_ENABLED:
                    stt_session = GoogleStreamingSttSession(practice_id, [], websocket, loop)
                    stt_session.start()
                elif data.get("type") == "stop": break
            if msg.get("bytes") and stt_session:
                stt_session.add_audio(msg["bytes"])
    except WebSocketDisconnect: pass
    finally:
        if stt_session: stt_session.stop()
