import json
from app.core.config import model
from app.schemas.models import AnalyzeRequest
# [STEP 8] RAG Grounding 컨텍스트 모듈
from app.services.rag_context import build_rag_context

# [STEP 5-E] goalSimilarityScore: Gemini 생성 제거 → 규칙 기반 값으로 주입
# [리뷰] symbolFeedback: Gemini 생성 제거 → generate_symbol_feedback() 규칙 값으로 대체
#        _normalize_feedback_payload가 clip 처리하도록 SUMMARY_FIELDS에 유지
SUMMARY_FIELDS = {"wpmSummary", "energySummary", "goalSummary", "symbolFeedback"}
FEEDBACK_FIELDS = {"wpmFeedback", "energyFeedback", "pauseFeedback", "goalFeedback"}

def _clip_text(value, max_length):
    if not isinstance(value, str):
        return value

    text = " ".join(value.split())
    if len(text) <= max_length:
        return text

    return text[:max_length].rstrip() + "..."

def _first_sentence(value, max_length):
    if not isinstance(value, str):
        return value

    text = " ".join(value.split())
    for separator in [".", "!", "?", "다.", "요."]:
        index = text.find(separator)
        if index >= 0:
            return _clip_text(text[:index + len(separator)], max_length)

    return _clip_text(text, max_length)

def _normalize_feedback_payload(payload):
    if not isinstance(payload, dict):
        return payload

    normalized = dict(payload)
    normalized["aiSummary"] = _first_sentence(normalized.get("aiSummary"), 70)

    for field in SUMMARY_FIELDS:
        normalized[field] = _clip_text(normalized.get(field), 12)

    for field in FEEDBACK_FIELDS:
        normalized[field] = _first_sentence(normalized.get(field), 55)

    return normalized

def _parse_json_payload(text):
    clean_text = text.replace("```json", "").replace("```", "").strip()

    try:
        return json.loads(clean_text)
    except json.JSONDecodeError:
        start_index = clean_text.find("{")
        end_index = clean_text.rfind("}")

        if start_index < 0 or end_index < start_index:
            raise

        return json.loads(clean_text[start_index:end_index + 1])

def _fallback_script_response(field_name, response_text=None):
    fallback_text = (response_text or "").replace("```json", "").replace("```", "").strip()

    if fallback_text.startswith("{") and fallback_text.endswith("}"):
        fallback_text = ""

    if not fallback_text:
        fallback_text = "AI 대본 생성 중 오류가 발생했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요."

    return {field_name: fallback_text}

def generate_symbol_feedback(features: dict) -> str:
    """[STEP 5 리뷰] 낭독 기호 준수 여부를 오디오 없이 추정 가능한 규칙으로 판정.

    오디오만으로 '기호를 얼마나 읽었는지' 직접 판단은 불가능하므로,
    대신 쉼 비율(pauseRatio)을 §7 기준과 비교해 기호 활용도를 간접 추정.
    - pauseRatio > 25%: 쉼이 과도함 → 기호 과잉 적용 가능성
    - pauseRatio < 8%:  쉼이 부족함 → 기호 무시 가능성
    - 그 외:           적절한 범위로 판단
    """
    pause_pct = (features.get("pauseRatio") or 0.0) * 100.0
    if pause_pct > 25.0:
        return "쉼 과다"
    if pause_pct < 8.0:
        return "쉼 부족"
    return "쉼 적절"


def generate_ai_feedback(features, req: AnalyzeRequest, goal_similarity_score: float = 0.0):
    """[STEP 8] RAG Grounding + Gemini JSON mode 기반 심층 피드백 생성.

    변경 사항 (STEP 8):
    - RAG 컨텍스트 주입: §6 Gold Standard, §7 임계값+학술근거, §8 청중 매트릭스
      (SpeakFit_Analysis README 기반 — 논문 4종 근거 포함)
    - Gemini JSON mode: response_mime_type="application/json" → 마크다운 코드블록 제거 불필요
    - goalSimilarityScore: 규칙 산출값 주입 (환각 차단 유지)
    - symbolFeedback: 규칙 기반 값 주입 (유지)
    """
    symbol_feedback = generate_symbol_feedback(features)

    if not model:
        return {
            "aiSummary": "훌륭한 발표였습니다!", "wpmSummary": "적절한 속도", "wpmFeedback": "좋습니다.",
            "energySummary": "강함", "energyFeedback": "전달력 우수", "pauseFeedback": "적절",
            "symbolFeedback": symbol_feedback,
            "goalSimilarityScore": goal_similarity_score,
            "goalSummary": "목표 근접", "goalFeedback": "계속 연습하세요."
        }

    # [STEP 8] RAG 컨텍스트 빌드 (§6/§7/§8/§9 + 현재 세션 측정값)
    rag_context = build_rag_context(features, req, goal_similarity_score)

    prompt = f"""
당신은 SpeakFit 스피치 코칭 AI입니다.
아래 [SpeakFit 알고리즘 근거 데이터]는 실제 음성 데이터 분석과 학술 연구를 기반으로 수립된 정량적 기준입니다.
반드시 이 데이터를 참조하여 피드백을 작성하고, 수치 근거 없는 추상적 평가는 하지 마세요.

==================================================
[SpeakFit 알고리즘 근거 데이터]
{rag_context}
==================================================

[출력 요구사항]
다음 키를 가진 JSON 객체 하나를 출력하세요.
- aiSummary: 규칙 평가 결과(§7 임계값 판정)를 반영한 따뜻한 총평. 70자 이내 1문장.
- wpmSummary: 말하기 속도 상태 한 줄. 12자 이내.
- wpmFeedback: §7 WPM 임계값과 실측값 차이를 근거로 한 구체적 개선 조언. 55자 이내 1문장.
- energySummary: 성량 상태 한 줄. 12자 이내.
- energyFeedback: §6 Gold Standard(63.72 dB) 대비 실측 dB를 근거로 한 조언. 55자 이내 1문장.
- pauseFeedback: §7 Pause(8~25%) 임계값과 Obama 연설 근거를 반영한 쉼 피드백. 55자 이내 1문장.
- goalSummary: 목표 스타일 달성도 요약. 12자 이내.
- goalFeedback: 목표 스타일에 더 가까워지기 위한 핵심 팁. 55자 이내 1문장.

[제약]
- JSON 객체 하나만 출력. Markdown, 설명문, 코드블록 금지.
- 모든 피드백은 위 [SpeakFit 알고리즘 근거 데이터]의 수치를 직접 언급하거나 참조하여 작성.
- UI 카드 크기 제한: aiSummary 70자, Summary류 12자, Feedback류 55자.
"""

    # [STEP 8 리뷰] Response Schema — 필드명 고정으로 Java DTO 직렬화 오류 방지
    _RESPONSE_SCHEMA = {
        "type": "OBJECT",
        "properties": {
            "aiSummary":      {"type": "STRING"},
            "wpmSummary":     {"type": "STRING"},
            "wpmFeedback":    {"type": "STRING"},
            "energySummary":  {"type": "STRING"},
            "energyFeedback": {"type": "STRING"},
            "pauseFeedback":  {"type": "STRING"},
            "goalSummary":    {"type": "STRING"},
            "goalFeedback":   {"type": "STRING"},
        },
        "required": [
            "aiSummary", "wpmSummary", "wpmFeedback",
            "energySummary", "energyFeedback",
            "pauseFeedback", "goalSummary", "goalFeedback",
        ],
    }

    try:
        # [STEP 8] Gemini JSON mode + Response Schema
        response = model.generate_content(
            prompt,
            generation_config={
                "response_mime_type": "application/json",
                "response_schema": _RESPONSE_SCHEMA,
            }
        )
        payload = _normalize_feedback_payload(json.loads(response.text))
        # goalSimilarityScore / symbolFeedback: 규칙 산출값으로 덮어씀 (환각 차단)
        payload["goalSimilarityScore"] = goal_similarity_score
        payload["symbolFeedback"] = symbol_feedback
        print(f"[Python STEP8] RAG 피드백 생성 완료 — goalSimilarity={goal_similarity_score}", flush=True)
        return payload
    except Exception as e:
        print(f"[Python] Gemini RAG 피드백 생성 실패: {e}")
        return {
            "aiSummary": "분석 오류가 발생했습니다.",
            "symbolFeedback": symbol_feedback,
            "goalSimilarityScore": goal_similarity_score,
        }

# ────────────────────────────────────────────────────────────
# [STEP 9] /feedback/summary — 기간별 피드백 요약 생성
# ────────────────────────────────────────────────────────────

# §3 스타일별 전체-지역 평균 군집 중심점 (40행 → 스타일 4종 평균)
_STYLE_CENTROIDS = {
    "열정적인":    {"wpm": 103.3, "pitch": 223.0},
    "지적인":      {"wpm":  99.9, "pitch": 171.0},
    "신중한":      {"wpm":  83.2, "pitch": 173.4},
    "전달력 있는": {"wpm":  82.2, "pitch": 202.0},
}

# WPM/Pitch 정규화 폭 (STEP 3-B와 동일 기준 — NORM_WPM=40, NORM_PITCH=200)
_NORM_WPM   = 40.0
_NORM_PITCH = 200.0


def find_most_similar_style(avg_wpm: float, avg_pitch: float) -> str:
    """§3 군집 중심점과의 정규화 유클리드 거리로 가장 유사한 스타일 반환."""
    best_style = "전달력 있는"
    best_dist = float("inf")
    for style, center in _STYLE_CENTROIDS.items():
        d_wpm   = (avg_wpm   - center["wpm"])   / _NORM_WPM
        d_pitch = (avg_pitch - center["pitch"]) / _NORM_PITCH
        dist = (d_wpm ** 2 + d_pitch ** 2) ** 0.5
        if dist < best_dist:
            best_dist = dist
            best_style = style
    return best_style


def calculate_matching_rate(avg_wpm: float, avg_intensity: float, avg_zcr: float, pause_ratio: float) -> int:
    """§6/§7 Gold Standard 대비 일치율 → 0~100 정수 반환.

    WPM 40% | dB 30% | Pause 20% | ZCR 10%
    각 지표를 §7 가드레일 폭으로 정규화.
    """
    def norm_score(actual, target, guard):
        if target == 0 or guard == 0:
            return 0.0
        deviation = abs(actual - target) / guard
        return max(0.0, 1.0 - deviation) * 100.0

    # §7 목표 (Gold Standard 절대치)
    WPM_TARGET, WPM_GUARD   = 145.0, 45.0   # 100~180 중심 ≈ 145, 폭 45
    DB_TARGET,  DB_GUARD    = 63.72, 6.0    # §6 Gold Standard
    PAUSE_TARGET_PCT        = 27.15          # §6 Gold Standard
    PAUSE_GUARD             = 17.0          # §7 8~25 폭
    ZCR_TARGET, ZCR_GUARD   = 0.1108, 0.07  # §6 Gold Standard (0.04~0.25 폭의 절반)

    pause_pct = pause_ratio * 100.0

    s_wpm   = norm_score(avg_wpm,       WPM_TARGET,   WPM_GUARD)
    s_db    = norm_score(avg_intensity, DB_TARGET,    DB_GUARD)
    s_pause = norm_score(pause_pct,     PAUSE_TARGET_PCT, PAUSE_GUARD)
    s_zcr   = norm_score(avg_zcr,       ZCR_TARGET,   ZCR_GUARD)

    raw = s_wpm * 0.40 + s_db * 0.30 + s_pause * 0.20 + s_zcr * 0.10
    return int(round(max(0.0, min(100.0, raw))))


def generate_feedback_summary(
    avg_wpm: float, avg_pitch: float, avg_intensity: float,
    avg_zcr: float, pause_ratio: float,
    start_date: str, end_date: str, feedback_id=None
) -> dict:
    """[STEP 9] 기간별 피드백 요약 — 규칙 판정 + Gemini RAG 설명 생성.

    Java PythonFeedbackRes 스키마 8개 필드 + matchingRate 반환.
    """
    most_similar_style = find_most_similar_style(avg_wpm, avg_pitch)
    matching_rate = calculate_matching_rate(avg_wpm, avg_intensity, avg_zcr, pause_ratio)
    pause_pct = pause_ratio * 100.0

    # §7 이탈 판정 (Gold Standard 기준)
    wpm_flag   = "이탈(과저속)" if avg_wpm < 100 else ("이탈(과속)" if avg_wpm > 180 else "정상")
    db_flag    = "이탈" if abs(avg_intensity - 63.72) > 6 else "정상"
    pause_flag = "이탈(부족)" if pause_pct < 8 else ("이탈(과다)" if pause_pct > 25 else "정상")
    zcr_flag   = "이탈" if avg_zcr < 0.04 or avg_zcr > 0.25 else "정상"

    print(
        f"[Python STEP9] feedbackId={feedback_id} | "
        f"mostSimilarStyle={most_similar_style} | matchingRate={matching_rate} | "
        f"WPM={avg_wpm:.1f}({wpm_flag}), dB={avg_intensity:.1f}({db_flag}), "
        f"Pause={pause_pct:.1f}%({pause_flag}), ZCR={avg_zcr:.4f}({zcr_flag})",
        flush=True
    )

    if not model:
        return _fallback_feedback_summary(most_similar_style, matching_rate)

    prompt = f"""
당신은 SpeakFit 스피치 코칭 AI입니다.
아래는 {start_date} ~ {end_date} 기간 동안의 발표 연습 통합 분석 결과입니다.

[기간별 평균 측정값]
- 평균 WPM: {avg_wpm:.1f} (§7 판정: {wpm_flag} / 정상 100~180)
- 평균 Pitch: {avg_pitch:.1f} Hz
- 평균 dB: {avg_intensity:.1f} dBFS (§6 Gold Standard 63.72 dB, 판정: {db_flag})
- 평균 Pause: {pause_pct:.1f}% (§7 판정: {pause_flag} / 정상 8~25%)
- ZCR: {avg_zcr:.4f} (§6 Gold Standard 0.1108, 판정: {zcr_flag})
- 가장 유사한 발표 스타일: {most_similar_style}
- 기간 종합 점수(matchingRate): {matching_rate}/100

[§6 Gold Standard 참고]
뉴스 앵커 290,704개 문장 분석 기준: dB 63.72 | ZCR 0.1108 | Pause 27.15%

[출력 요구사항]
다음 키를 가진 JSON 객체 하나를 출력하세요.
- styleDescription: '{most_similar_style}' 스타일의 특징 설명 + 이 기간 발표에서 나타난 해당 스타일 경향. 30자 이내.
- positiveTitle: 이 기간에서 가장 잘한 발화 지표 1가지 제목. 10자 이내.
- positiveDescription: positiveTitle 근거 — §6/§7 수치를 직접 인용한 구체적 칭찬. 50자 이내.
- improvementTitle: 가장 우선 개선이 필요한 지표 1가지 제목. 10자 이내.
- improvementDescription: improvementTitle 근거 — §7 임계값 대비 실측값 차이를 명시한 개선 조언. 50자 이내.
- guideSummary: 다음 연습 단계 한 줄 요약. 20자 이내.
- guideNextStep: 다음 연습에서 집중할 구체적 행동 지침. 50자 이내.

[제약]
- JSON 객체 하나만 출력. Markdown, 코드블록 금지.
- 모든 피드백은 위 수치를 직접 참조.
"""

    _SUMMARY_SCHEMA = {
        "type": "OBJECT",
        "properties": {
            "styleDescription":      {"type": "STRING"},
            "positiveTitle":         {"type": "STRING"},
            "positiveDescription":   {"type": "STRING"},
            "improvementTitle":      {"type": "STRING"},
            "improvementDescription":{"type": "STRING"},
            "guideSummary":          {"type": "STRING"},
            "guideNextStep":         {"type": "STRING"},
        },
        "required": [
            "styleDescription", "positiveTitle", "positiveDescription",
            "improvementTitle", "improvementDescription",
            "guideSummary", "guideNextStep",
        ],
    }

    try:
        response = model.generate_content(
            prompt,
            generation_config={
                "response_mime_type": "application/json",
                "response_schema": _SUMMARY_SCHEMA,
            }
        )
        payload = json.loads(response.text)
        payload["mostSimilarStyle"] = most_similar_style
        payload["matchingRate"]     = matching_rate
        print(f"[Python STEP9] 피드백 요약 생성 완료 — matchingRate={matching_rate}", flush=True)
        return payload
    except Exception as e:
        print(f"[Python STEP9] Gemini 피드백 요약 생성 실패: {e}")
        return _fallback_feedback_summary(most_similar_style, matching_rate)


def _fallback_feedback_summary(most_similar_style: str, matching_rate: int) -> dict:
    """Gemini 실패 시 규칙 기반 최소 응답."""
    return {
        "mostSimilarStyle":       most_similar_style,
        "styleDescription":       f"{most_similar_style} 발표 스타일",
        "positiveTitle":          "꾸준한 연습",
        "positiveDescription":    "지속적인 발표 연습으로 스피치 역량을 향상시키고 있습니다.",
        "improvementTitle":       "세부 지표 개선",
        "improvementDescription": "§7 임계값을 기준으로 WPM·Pause 균형을 맞춰 주세요.",
        "guideSummary":           "다음 단계 연습 진행",
        "guideNextStep":          "목표 WPM 범위(100~180)를 유지하며 의도적인 Pause를 연습하세요.",
        "matchingRate":           matching_rate,
    }


async def generate_script_ai(req):
    prompt = f"""
    당신은 발표 대본을 전문적으로 작성하는 스피치 코치입니다.
    주제: {req.topic}, 시간: {req.time}분, 청중: {req.audienceAge}/{req.audienceLevel}, 형식: {req.speechType}, 목적: {req.purpose}, 키워드: {req.keywords}
    
    발표자가 바로 읽을 수 있는 문장형 대본으로 JSON 형식으로 출력해주세요.
    {{ "generatedScript": "내용" }}
    """
    try:
        response = model.generate_content(prompt, generation_config={"response_mime_type": "application/json"})
        payload = _parse_json_payload(response.text)

        if not isinstance(payload, dict) or not payload.get("generatedScript"):
            return _fallback_script_response("generatedScript", response.text)

        return payload
    except Exception as e:
        print(f"[Python] Gemini 대본 생성 실패: {e}")
        return _fallback_script_response("generatedScript")

async def update_script_ai(req):
    prompt = f"""
    기존 대본을 최적화해주세요.
    기존 대본: {req.content}
    주제: {req.topic}, 시간: {req.time}분, 청중: {req.audienceAge}/{req.audienceLevel}, 형식: {req.speechType}, 목적: {req.purpose}, 키워드: {req.keywords}
    
    최적화된 대본을 JSON 형식으로 출력해주세요.
    {{ "optimizedScript": "내용" }}
    """
    try:
        response = model.generate_content(prompt, generation_config={"response_mime_type": "application/json"})
        payload = _parse_json_payload(response.text)

        if not isinstance(payload, dict) or not payload.get("optimizedScript"):
            return _fallback_script_response("optimizedScript", response.text)

        return payload
    except Exception as e:
        print(f"[Python] Gemini 대본 최적화 실패: {e}")
        return _fallback_script_response("optimizedScript")

async def mark_script_ai(content):
    prompt = f"""
    대본에 낭독 기호(/, *)를 추가해주세요.
    [대본] {content}
    규칙: 쉼표/의미단위 뒤 ' / ', 강조단어 앞뒤 '*' 추가. 원본 유지.
    """
    response = model.generate_content(prompt)
    return response.text.strip()
