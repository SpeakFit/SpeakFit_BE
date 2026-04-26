import os
import json
import asyncio
import queue
import threading
from difflib import SequenceMatcher
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
        self.script_words = normalize_script_words(script_words)
        self.websocket = websocket
        self.loop = loop
        self.audio_queue = queue.Queue()
        self.closed = threading.Event()
        self.confirmed_global_word_index = -1
        self.last_partial_global_word_index = -1
        self.final_transcript = ""

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
                    msg = self.build_highlight_message(
                        alt.transcript,
                        getattr(result, "is_final", False),
                        getattr(alt, "confidence", 0.0)
                    )
                    asyncio.run_coroutine_threadsafe(self.websocket.send_json(msg), self.loop)
        except Exception as e:
            print(f"STT Run Error: {e}")

    def build_highlight_message(self, transcript, is_final, confidence):
        current_index = self.estimate_current_global_word_index(transcript)
        if is_final:
            self.confirmed_global_word_index = max(self.confirmed_global_word_index, current_index)
            self.last_partial_global_word_index = self.confirmed_global_word_index
            self.final_transcript = f"{self.final_transcript} {transcript}".strip()
        else:
            current_index = max(current_index, self.confirmed_global_word_index, self.last_partial_global_word_index)
            self.last_partial_global_word_index = max(self.last_partial_global_word_index, current_index)

        matched_word = find_script_word_by_global_index(self.script_words, current_index)
        return {
            "type": "highlight",
            "practiceId": self.practice_id,
            "transcript": transcript,
            "currentGlobalWordIndex": current_index if current_index >= 0 else None,
            "matchedWord": matched_word.get("text") if matched_word else None,
            "isFinal": bool(is_final),
            "confidence": float(confidence or 0.0),
        }

    def estimate_current_global_word_index(self, transcript):
        if not self.script_words or not transcript:
            return self.confirmed_global_word_index

        transcript_tokens = [
            normalize_match_text(token)
            for token in transcript.split()
            if normalize_match_text(token)
        ]
        if not transcript_tokens:
            return self.confirmed_global_word_index

        search_start = max(self.confirmed_global_word_index + 1, 0)
        token_window = transcript_tokens[-8:]
        current_index = self.confirmed_global_word_index

        for token in token_window:
            match = find_realtime_token_match(self.script_words, token, search_start)
            if not match:
                continue

            current_index = match.get("globalWordIndex", current_index)
            search_start = current_index + 1

        return current_index


def normalize_script_words(script_words):
    normalized_words = []
    for index, word in enumerate(script_words or []):
        if not isinstance(word, dict):
            continue

        global_word_index = word.get("globalWordIndex", word.get("index", index))
        normalized_words.append({
            "scriptWordId": word.get("scriptWordId"),
            "globalWordIndex": global_word_index,
            "sentenceIndex": word.get("sentenceIndex"),
            "text": word.get("text") or word.get("word") or "",
            "normalizedText": word.get("normalizedText"),
        })

    return sorted(normalized_words, key=lambda word: word.get("globalWordIndex", 0))


def find_script_word_by_global_index(script_words, global_word_index):
    for word in script_words:
        if word.get("globalWordIndex") == global_word_index:
            return word

    return None


def find_realtime_token_match(script_words, token, search_start):
    best_word = None
    best_score = 0.0
    for script_word in script_words[search_start:search_start + 16]:
        script_text = normalize_match_text(script_word.get("normalizedText") or script_word.get("text"))
        if not script_text:
            continue

        score = calculate_realtime_similarity(script_text, token)
        order_penalty = max(script_word.get("globalWordIndex", 0) - search_start, 0) * 0.01
        adjusted_score = score - order_penalty
        if adjusted_score > best_score:
            best_score = adjusted_score
            best_word = script_word

    return best_word if best_score >= 0.72 else None


def calculate_realtime_similarity(script_text, spoken_text):
    if not script_text or not spoken_text:
        return 0.0
    if script_text == spoken_text:
        return 1.0
    if len(script_text) > 2 and (script_text in spoken_text or spoken_text in script_text):
        return 0.86

    return SequenceMatcher(None, script_text, spoken_text).ratio()

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
                if data.get("type") == "init" and not GOOGLE_STT_ENABLED:
                    await websocket.send_json({
                        "type": "sttDisabled",
                        "practiceId": practice_id,
                        "message": "Streaming STT is disabled."
                    })
                elif data.get("type") == "init":
                    stt_session = GoogleStreamingSttSession(practice_id, data.get("scriptWords", []), websocket, loop)
                    if not stt_session.start():
                        await websocket.send_json({
                            "type": "sttError",
                            "practiceId": practice_id,
                            "message": "Streaming STT session failed to start."
                        })
                        stt_session = None
                        continue
                    await websocket.send_json({
                        "type": "ready",
                        "practiceId": practice_id,
                        "scriptWordCount": len(stt_session.script_words)
                    })
                elif data.get("type") == "stop": break
            if msg.get("bytes") and stt_session:
                stt_session.add_audio(msg["bytes"])
    except WebSocketDisconnect: pass
    finally:
        if stt_session: stt_session.stop()
