import json
import re
import threading
from app.core.config import model, script_model
from app.schemas.models import AnalyzeRequest
from app.services.rag_context import build_rag_context, build_session_detail_context

try:
    from langsmith import traceable
except ImportError:
    def traceable(*args, **kwargs):
        def decorator(fn):
            return fn
        return decorator if args and callable(args[0]) else decorator

# ── [STEP-B] 로컬 낭독기호 마킹 엔진 ───────────────────────────────────────
_okt = None
_okt_lock = threading.Lock()

def _get_okt():
    global _okt
    if _okt is not None:
        return _okt
    with _okt_lock:
        if _okt is None:
            try:
                from konlpy.tag import Okt
                _okt = Okt()
                print("[Python STEP-B] KoNLPy Okt 태거 초기화 완료", flush=True)
            except Exception as e:
                print(f"[Python STEP-B] KoNLPy 사용 불가, 정규식 전용 모드: {e}", flush=True)
                _okt = False
    return _okt

def _mark_pauses(text: str) -> str:
    return re.sub(r'([.!?,。…])\s+', r'\1 / ', text)

def _mark_emphasis_regex(text: str) -> str:
    def emphasize(m):
        word = m.group(0)
        return f"*{word}*" if len(word) >= 2 else word
    return re.sub(r'[가-힣]+', emphasize, text)

def _mark_emphasis_okt(text: str, okt) -> str:
    try:
        morphs = okt.pos(text, norm=True, stem=False)
        offset = 0
        insertions = []
        for word, tag in morphs:
            idx = text.find(word, offset)
            if idx == -1: continue
            if tag in ("NNG", "NNP") and len(word) >= 2:
                insertions.append((idx, idx + len(word)))
            offset = idx + len(word)
        marked = text
        for start, end in reversed(insertions):
            marked = marked[:start] + "*" + marked[start:end] + "*" + marked[end:]
        return marked
    except Exception:
        return _mark_emphasis_regex(text)

def mark_script_local(content: str) -> str:
    try:
        text = content.strip()
        paused = _mark_pauses(text)
        okt = _get_okt()
        result = _mark_emphasis_okt(paused, okt) if (okt and okt is not False) else _mark_emphasis_regex(paused)
        return result
    except Exception as e:
        print(f"[Python STEP-B] 마킹 예외: {e}")
        return content

def warmup_marking_engine():
    okt = _get_okt()
    if okt and okt is not False:
        try: okt.pos("예열")
        except: pass

# ── [STEP-A] AI 대본 생성/최적화 스트리밍 ───────────────────────────────────

def _build_generate_prompt(req) -> str:
    target_chars = int(req.time) * 600
    return f"주제: {req.topic}\n시간: {req.time}분\n청중: {req.audienceAge}/{req.audienceLevel}\n형식: {req.speechType}\n목적: {req.purpose}\n키워드: {req.keywords}\n\n자연스러운 대본을 {target_chars}자 이내로 작성해주세요. 대본만 출력하세요."

def _build_update_prompt(req) -> str:
    target_chars = int(req.time) * 600
    return f"기존 대본: {req.content}\n주제: {req.topic}\n시간: {req.time}분\n키워드: {req.keywords}\n\n위 대본을 최적화해주세요. 대본만 출력하세요."

@traceable(run_type="llm", name="generate_script_ai_stream")
async def generate_script_ai_stream(req):
    _model = script_model or model
    sent_any_chunk = False
    if not _model:
        yield f"data: {json.dumps({'error': 'AI 모델 미설정'}, ensure_ascii=False)}\n\n"
        return

    try:
        import asyncio
        response_stream = await asyncio.to_thread(_model.generate_content, _build_generate_prompt(req), stream=True)
        for chunk in response_stream:
            try:
                if chunk.text:
                    yield f"data: {json.dumps({'chunk': chunk.text}, ensure_ascii=False)}\n\n"
                    sent_any_chunk = True
            except: continue
        yield "data: [DONE]\n\n"
    except Exception as e:
        if not sent_any_chunk:
            yield f"data: {json.dumps({'error': '대본 생성 실패'}, ensure_ascii=False)}\n\n"

@traceable(run_type="llm", name="update_script_ai_stream")
async def update_script_ai_stream(req):
    _model = script_model or model
    sent_any_chunk = False
    if not _model:
        yield f"data: {json.dumps({'error': 'AI 모델 미설정'}, ensure_ascii=False)}\n\n"
        return

    try:
        import asyncio
        response_stream = await asyncio.to_thread(_model.generate_content, _build_update_prompt(req), stream=True)
        for chunk in response_stream:
            try:
                if chunk.text:
                    yield f"data: {json.dumps({'chunk': chunk.text}, ensure_ascii=False)}\n\n"
                    sent_any_chunk = True
            except: continue
        yield "data: [DONE]\n\n"
    except Exception as e:
        if not sent_any_chunk:
            yield f"data: {json.dumps({'error': '대본 최적화 실패'}, ensure_ascii=False)}\n\n"

async def mark_script_ai(content):
    return mark_script_local(content)

# ── [하위 호환] 비스트리밍 함수 ───────────────────────────────────────────

async def generate_script_ai(req):
    _model = script_model or model
    if not _model: return {"generatedScript": "AI 모델 미설정"}
    try:
        response = _model.generate_content(_build_generate_prompt(req))
        return {"generatedScript": response.text.strip()}
    except: return {"generatedScript": "생성 실패"}

async def update_script_ai(req):
    _model = script_model or model
    if not _model: return {"optimizedScript": "AI 모델 미설정"}
    try:
        response = _model.generate_content(_build_update_prompt(req))
        return {"optimizedScript": response.text.strip()}
    except: return {"optimizedScript": "최적화 실패"}

# ── [STEP 5/8/9] 피드백 및 요약 엔진 ──────────────────────────────────────

def generate_symbol_feedback(features: dict) -> str:
    pause_pct = (features.get("pauseRatio") or 0.0) * 100.0
    if pause_pct > 25.0: return "쉼 과다"
    if pause_pct < 8.0: return "쉼 부족"
    return "쉼 적절"

def _build_feedback_prompt(rag_context: str, symbol_feedback: str, goal_similarity_score: float,
                           session_detail: str = "") -> str:
    """[STEP 8/10] RAG Grounding 프롬프트.

    build_rag_context()가 만든 학술 근거·Gold Standard·청중·실측/규칙 평가 결과와,
    [STEP 10] build_session_detail_context()가 만든 문장별 결과·우선순위 이슈·실제 전사를
    함께 주입한다. Gemini가 그 근거에만 기반해 — 특히 문제가 된 구체적 문장을 짚어 —
    한국어 JSON 피드백을 작성하도록 지시한다.
    출력 키는 Java PythonAnalysisRes 스키마와 1:1로 일치시킨다.
    """
    # [STEP 10] 세션 상세가 있으면 별도 섹션으로 추가 (없으면 하위 호환 유지).
    session_block = f"\n[이번 발표 상세 — 문장별 결과·이슈·전사]\n{session_detail}\n" if session_detail else ""

    return f"""당신은 SpeakFit의 친절한 스피치 코치입니다.
이 피드백을 읽는 사람은 발표 전문가가 아니라 평범한 학생·청소년·성인입니다.
그래서 어려운 전문 용어 대신 누구나 아는 쉬운 말로, 잘한 점을 먼저 칭찬한 뒤
다음에 해볼 점을 구체적으로 알려주는 따뜻한 코치처럼 작성하세요.

아래 [근거 컨텍스트]는 학술 논문·뉴스 앵커 기준·청중 분석·이번 발표의 실측값/평가 결과입니다.
[이번 발표 상세]에는 문장별 분석 결과, 우선순위 이슈, 실제 발화 전사가 들어 있습니다.
반드시 이 근거에 담긴 수치와 판정 결과에만 기반해 피드백을 작성하고, 근거에 없는 수치를 지어내지 마세요.
다만 **근거의 수치는 반드시 쉬운 말로 풀어서** 전달하세요. 다음 용어는 사용자 문구에 그대로 노출하지 말고
아래 표현으로 바꿔 쓰세요:
- WPM → "말 빠르기"(예: "1분에 약 N단어 정도")
- dB·intensity → "목소리 크기" / pitch → "목소리 높낮이"
- pause ratio·가드레일 → "문장 사이 쉬는 시간"
- goal similarity → "목표 스타일과 비슷한 정도"
- ZCR·Gold Standard·§7 임계값·이탈/정상 → 문구에 노출 금지(해석에만 활용, "권장 범위" 정도로만 표현)

가능하면 일반론 대신 **어떤 문장에서 무엇을 해보면 좋은지**를 짚어 주세요
(예: "3번 문장은 조금 빨랐어요. 다음엔 한 박자 천천히 말해보세요"). 잘된 점도 1가지는 반드시 언급하세요.
모든 문장은 한국어로, 발표자에게 직접 말하듯("~하셨어요 / ~해보세요") 따뜻하고 실행 가능하게 작성하세요.

[근거 컨텍스트]
{rag_context}
{session_block}
[추가 참고]
- 낭독 쉼 규칙 판정: {symbol_feedback}
- 목표 스타일 유사도(규칙 산출): {goal_similarity_score:.1f}/100

반드시 아래 JSON 형식으로만 응답하세요 (마크다운·설명 없이 JSON 객체만):
{{
  "aiSummary": "이번 발표 전반에 대한 2~3문장 종합 평가. 잘한 점 1가지를 먼저 칭찬하고, 다음에 해볼 점을 쉬운 말로. 전문 용어 금지.",
  "wpmSummary": "말 빠르기 한 줄 요약 (15자 이내, 쉬운 말)",
  "wpmFeedback": "말 빠르기 피드백 2~3문장. WPM 대신 '1분에 약 N단어'처럼 쉽게 풀고, 빨랐던/느렸던 문장을 짚어 '다음엔 ~해보세요'로 제안.",
  "energySummary": "목소리 크기·높낮이 한 줄 요약 (15자 이내, 쉬운 말)",
  "energyFeedback": "목소리 크기·높낮이 피드백 2~3문장. dB·전문어 노출 금지, '목소리가 ~한 편'처럼 쉽게 설명하고 어떤 문장에서 어떻게 해볼지 제안.",
  "pauseFeedback": "문장 사이 쉬는 시간 피드백 2~3문장. 비율·가드레일 같은 용어 대신 '쉼이 길었어요/짧았어요'로 쉽게. [말버릇·반복 관찰]이 있으면 군더더기/반복 표현도 쉬운 말로 짚어 개선법을 덧붙이세요(없으면 생략).",
  "goalSummary": "목표 스타일과 비슷한 정도 한 줄 요약 (15자 이내, 쉬운 말)",
  "goalFeedback": "목표 스타일에 더 가까워지려면 다음에 무엇을 해볼지 2~3문장으로 쉽게 안내. 추상적 점수 대신 구체적 행동 중심.",
  "strengths": "이번 발표에서 잘한 점 2~3가지를 각 줄 앞에 '- '를 붙여 줄바꿈(\\n)으로 구분해 작성. 쉬운 말로 격려하듯, 근거는 풀어서.",
  "improvements": "다음에 해보면 좋은 점 3가지를 효과가 큰 순서로, 각 줄 앞에 '1. ' '2. ' '3. ' 번호를 붙여 줄바꿈(\\n)으로 구분. 비난이 아니라 제안 톤으로 구체적으로.",
  "practiceTip": "다음 연습 때 바로 따라 할 수 있는 쉬운 연습 방법 1가지를 1~2문장으로 제안.",
  "contentSummary": "내용·구성 한 줄 요약 (15자 이내, 쉬운 말)",
  "contentFeedback": "실제 발화 전사와 대본을 근거로 한 내용·구성 피드백 2~3문장. '도입에서 주제를 먼저 말해주면…'처럼 흐름·핵심 메시지를 쉬운 말로 짚어 개선점을 제안. 전사가 없으면 빈 문자열."
}}"""


@traceable(run_type="llm", name="generate_ai_feedback")
def generate_ai_feedback(features, req: AnalyzeRequest, goal_similarity_score: float = 0.0,
                         sentence_results=None, issues=None, transcript=None,
                         disfluency=None):
    symbol_feedback = generate_symbol_feedback(features)

    # Java PythonAnalysisRes 스키마와 동일한 키 — 누락 시 null 저장 방지용 기본값
    fallback = {
        "aiSummary": "발표 데이터를 분석했습니다.",
        "wpmSummary": "", "wpmFeedback": "",
        "energySummary": "", "energyFeedback": "",
        "pauseFeedback": "",
        "goalSummary": "", "goalFeedback": "",
        "symbolFeedback": symbol_feedback,
        "goalSimilarityScore": goal_similarity_score,
        # [STAGE 3] 출력 구조 확장 — 강점/핵심 개선 액션/연습 팁
        "strengths": "", "improvements": "", "practiceTip": "",
        # [STAGE 4] 내용·구성 피드백 (전사+대본 기반)
        "contentSummary": "", "contentFeedback": "",
    }

    if not model:
        fallback["aiSummary"] = "모델 미설정"
        return fallback

    # [STEP 8] 선택적 RAG 컨텍스트 빌드 → 근거 기반 프롬프트 주입
    rag_context = build_rag_context(features, req, goal_similarity_score)
    # [STEP 10] 문장별 결과·우선순위 이슈·실제 전사를 합친 세션 상세 컨텍스트.
    #   집계 지표만으로는 일반론에 그치므로, 구체적 문장 근거를 LLM에 함께 제공.
    script_text = req.content or ""
    session_detail = build_session_detail_context(
        sentence_results=sentence_results,
        issues=issues,
        transcript=transcript,
        script_text=script_text,
        disfluency=disfluency,
    )
    prompt = _build_feedback_prompt(rag_context, symbol_feedback, goal_similarity_score, session_detail)

    try:
        response = model.generate_content(
            prompt,
            generation_config={"response_mime_type": "application/json"},
        )
        payload = json.loads(response.text)
        # 모델이 채운 값만 채택하고, 누락/None 필드는 기본값으로 보정
        result = {**fallback, **{k: v for k, v in payload.items() if v is not None}}
        # 규칙 산출값은 항상 코드 기준으로 덮어씀 (모델 환각 방지)
        result["goalSimilarityScore"] = goal_similarity_score
        result["symbolFeedback"] = symbol_feedback
        return result
    except Exception as e:
        print(f"[Python STEP8] generate_ai_feedback 실패: {e}", flush=True)
        fallback["aiSummary"] = "피드백 생성 중 오류"
        return fallback

@traceable(run_type="llm", name="generate_feedback_summary")
def generate_feedback_summary(avg_wpm, avg_pitch, avg_intensity, avg_zcr, pause_ratio, start_date, end_date, feedback_id=None):
    """[STEP 9] 기간별 발표 스타일 요약 — Gemini 실제 호출."""
    if not model:
        return {"styleDescription": "모델 미설정"}

    pause_pct = (pause_ratio or 0.0) * 100.0

    prompt = f"""당신은 발표 코칭 전문가입니다. 아래 기간({start_date} ~ {end_date}) 동안의 평균 발표 지표를 분석하여 JSON으로 응답하세요.

[분석 지표]
- 평균 WPM: {avg_wpm:.1f}
- 평균 Pitch: {avg_pitch:.1f} Hz
- 평균 음량(dBFS): {avg_intensity:.1f}
- 평균 ZCR: {avg_zcr:.4f}
- 평균 휴지 비율: {pause_pct:.1f}%

[Gold Standard 기준]
- 권장 WPM: 130~160 | 권장 Pause: 10~25% | 권장 dB: 60~70 dBFS

반드시 아래 JSON 형식으로만 응답하세요 (설명 없이):
{{
  "mostSimilarStyle": "발표 스타일 한 단어 (예: 안정적인, 열정적인, 차분한, 전달력 있는)",
  "styleDescription": "전반적인 발표 스타일 2~3문장 설명",
  "positiveTitle": "잘한 점 제목 (10자 이내)",
  "positiveDescription": "잘한 점 구체적 설명 1~2문장",
  "improvementTitle": "개선할 점 제목 (10자 이내)",
  "improvementDescription": "개선할 점 구체적 설명 1~2문장",
  "guideSummary": "다음 연습 방향 제목 (10자 이내)",
  "guideNextStep": "다음 연습을 위한 구체적 행동 가이드 1~2문장",
  "matchingRate": 목표 스타일 달성률 정수 (0~100)
}}"""

    try:
        response = model.generate_content(
            prompt,
            generation_config={"response_mime_type": "application/json"}
        )
        payload = json.loads(response.text)
        # 필수 필드 보정
        payload.setdefault("matchingRate", 70)
        payload.setdefault("mostSimilarStyle", "분석 중")
        return payload
    except Exception as e:
        print(f"[Python STEP9] generate_feedback_summary 실패: {e}", flush=True)
        return {
            "styleDescription": "발표 데이터를 분석했습니다.",
            "positiveTitle": "꾸준한 연습",
            "positiveDescription": "성실하게 연습을 이어오셨습니다.",
            "improvementTitle": "균형 잡힌 발화",
            "improvementDescription": "속도와 휴지의 균형을 조금 더 맞춰보세요.",
            "guideSummary": "다음 목표",
            "guideNextStep": "자연스러운 발화 흐름을 목표로 연습해보세요.",
            "mostSimilarStyle": "성실한",
            "matchingRate": 70
        }
