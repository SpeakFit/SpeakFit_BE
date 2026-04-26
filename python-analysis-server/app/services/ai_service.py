import json
from app.core.config import model
from app.schemas.models import AnalyzeRequest

def generate_ai_feedback(features, req: AnalyzeRequest):
    """Gemini를 사용한 심층 피드백 생성"""
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
        json_str = response.text.replace("```json", "").replace("```", "").strip()
        return json.loads(json_str)
    except Exception as e:
        print(f"[Python] Gemini 피드백 생성 실패: {e}")
        return {"aiSummary": "분석 오류가 발생했습니다.", "goalSimilarityScore": 0.0}

async def generate_script_ai(req):
    prompt = f"""
    당신은 발표 대본을 전문적으로 작성하는 스피치 코치입니다.
    주제: {req.topic}, 시간: {req.time}분, 청중: {req.audienceAge}/{req.audienceLevel}, 형식: {req.speechType}, 목적: {req.purpose}, 키워드: {req.keywords}
    
    발표자가 바로 읽을 수 있는 문장형 대본으로 JSON 형식으로 출력해주세요.
    {{ "generatedScript": "내용" }}
    """
    response = model.generate_content(prompt)
    json_str = response.text.replace("```json", "").replace("```", "").strip()
    return json.loads(json_str)

async def update_script_ai(req):
    prompt = f"""
    기존 대본을 최적화해주세요.
    기존 대본: {req.content}
    주제: {req.topic}, 시간: {req.time}분, 청중: {req.audienceAge}/{req.audienceLevel}, 형식: {req.speechType}, 목적: {req.purpose}, 키워드: {req.keywords}
    
    최적화된 대본을 JSON 형식으로 출력해주세요.
    {{ "optimizedScript": "내용" }}
    """
    response = model.generate_content(prompt)
    json_str = response.text.replace("```json", "").replace("```", "").strip()
    return json.loads(json_str)

async def mark_script_ai(content):
    prompt = f"""
    대본에 낭독 기호(/, *)를 추가해주세요.
    [대본] {content}
    규칙: 쉼표/의미단위 뒤 ' / ', 강조단어 앞뒤 '*' 추가. 원본 유지.
    """
    response = model.generate_content(prompt)
    return response.text.strip()
