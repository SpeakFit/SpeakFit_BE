"""
[STEP 8] RAG Grounding 컨텍스트 모듈

SpeakFit_Analysis README(§3~§9) 기반 학술 근거 데이터를 구조화하여
Gemini 프롬프트에 주입합니다.

데이터 출처: SpeakFit_Analysis README (GitHub)
목적: Gemini zero-shot 피드백 → 데이터·논문 근거 기반 피드백으로 전환

[리뷰 반영] 선택적 RAG: 이탈 지표만 해당 학술 근거 주입 → 토큰 절감
"""

# ────────────────────────────────────────────────────────────
# §6. 뉴스 앵커 Gold Standard (항상 주입 — 모든 피드백의 기준)
# ────────────────────────────────────────────────────────────
GOLD_STANDARD = """
[§6 Gold Standard — 뉴스 앵커 기반 전달력 마스터 지표]
분석 문장 수: 290,704개 (전문 아나운서 집단 발화 데이터셋)
성량 표준(dB SPL): 63.72 dB | ZCR 평균: 0.1108 | ZCR 표준편차: 0.1895 | 황금 휴지기: 27.15%
정상 범위: dB 45~80 (64 내외 최적) / ZCR 0.04~0.25 (0.11 내외) / Pause 10~45% (27% 내외)
활용: WPM·Pitch는 개인 Baseline 보정값, dB·ZCR·Pause는 이 Gold Standard가 절대 목표.
"""

# ────────────────────────────────────────────────────────────
# §7 + §9 지표별 학술 근거 — 이탈 지표만 선택 주입
# ────────────────────────────────────────────────────────────
_EVIDENCE_WPM = """
[§7 WPM 임계값 + 학술 근거]
권장: 130~160 WPM | 하한: 100 | 상한: 180
- [Journal of DCS] AI·인간 아나운서 준언어 비교 — 말 속도가 정보 전달력·청중 인상 형성에 직결됨.
- [Effects of Lecture Rate on Comprehension] — 이해도 낮은 청중일수록 느린 속도(100~130 WPM)에서 이해도·중요도 점수 유의미하게 높음. 180 WPM 초과 시 과속 경계.
알고리즘: 개인 Baseline × §5 보정배율로 목표 WPM, §7 가드레일 100~180 클램핑.
"""

_EVIDENCE_PITCH = """
[§7 Pitch 임계값 + 학술 근거]
권장: 개인 Baseline 대비 ±10~15% | 하한: -20% | 상한: +20%
- [Aalto Univ. Speech Processing Book] — 성인 발화 F0 물리 범위 80~450 Hz, 급격한 피치 점프 억제가 핵심.
- [Cambridge Handbook of Phonetics] — F0와 Pitch 심리음향 관계, 화자별 변화 패턴과 청취자 인지 메커니즘.
알고리즘: 개인 Baseline × §5 보정배율로 목표 Pitch, ±20% 가드레일. 급격한 Frequency Leap 제어.
"""

_EVIDENCE_DB = """
[§7 dB 임계값 + 학술 근거]
권장: 개인 Baseline 대비 +0~6 dB | 하한: -6 dB | 상한: +6 dB
- [PubMed 24645565] 대학 강의실 강사 실측 — 60~65 dB 정상 발화, 강조 시 66~71 dB 'Raised Voice'.
- [PubMed 26106071] — 배경 소음 증가 시 음량·Pitch 동반 상승하는 Lombard Effect. 과도한 볼륨 스파이크 청중 피로.
알고리즘: §6 Gold Standard(63.72 dB) 대비 Baseline +0~6 dB 범위. §7 ±6 dB 가드레일.
"""

_EVIDENCE_PAUSE = """
[§7 Pause 임계값 + 학술 근거]
권장: 전체 발화 시간의 10~20% | 하한: 8% | 상한: 25%
- [Barack Obama 연설 분석] — 목적 있는 무음 휴지(Silent Pause)가 카리스마·설득력·매력에 기여. 단순 공백과 의도된 휴지 구분.
- [Filled Pauses & Speaking Rate] — "어...", "음..." 유음 휴지(Filled Pause)는 청중 작업기억 교란 및 신뢰도 감소.
알고리즘: §6 Gold Standard(27.15%) 대비, §7 8~25% 가드레일. Filled Pause는 별도 감점.
"""

# ────────────────────────────────────────────────────────────
# §8. 청중 세그먼트별 권장 범위 + 학술 근거
# ────────────────────────────────────────────────────────────
_AUDIENCE_EVIDENCE_MAP = {
    "LOW": "이해도 낮은 청중: [Effects of Lecture Rate] 느린 WPM(100~130)에서 이해도 상승. [EFL Learners] 빠른 속도 시 정보처리 초과로 이해도 급락.",
    "MID": "보통 이해도 청중: [Effects of Lecture Rate] 120~150 WPM에서 안정적 디코딩. [PubMed 24645565] 60~65 dB 정상, 66~71 dB 강조.",
    "HIGH": "높은 이해도 청중: [Effects of Lecture Rate] 130~160 WPM 고밀도 압축도 왜곡 없이 디코딩. 다채로운 Pitch 변동 허용.",
}
_ELDERLY_EVIDENCE = "노년층: [노인음성인식 연구] 청각 역치·인지속도 저하 → 느린 템포·안정적 어조 필수. [노년층 추상어 이해] 청각기억 과부하 → WPM 제한, Pause 15~25%."
_YOUTH_EVIDENCE = "어린이·청소년: [청소년기 자아중심성] Pitch 뉘앙스 변화에 민감 → Pitch ±15% 허용, Pause 타이트."
_PAUSE_COMMON_EVIDENCE = "Pause 공통: [Obama 연설] 의도된 Silent Pause → 카리스마·설득력. 이해도 낮을수록 Pause 상한 20~25%. [Filled Pause 연구] Filled Pause는 작업기억 교란."

# ────────────────────────────────────────────────────────────
# SpeakFit 알고리즘 파이프라인 요약 (항상 주입)
# ────────────────────────────────────────────────────────────
PIPELINE_SUMMARY = """
[SpeakFit 알고리즘 파이프라인]
1. 개인화 타겟: 사용자 Baseline × §5 보정배율(40개 군집)
2. Gold Standard 맵핑: §6 앵커 지표 → dB/ZCR/Pause 절대 기준
3. 임계값 가드레일: §7 인지과학 근거 하/상한선
4. 청중 세그먼트: §8 [이해도×연령] 12칸 매트릭스 오프셋
5. 규칙 기반 점수: 측정값 vs 목표치 편차 → §7 이탈 판정
"""

# ────────────────────────────────────────────────────────────
# §8 청중별 WPM 권장 범위 매핑
# ────────────────────────────────────────────────────────────
_AUDIENCE_WPM_MAP = {
    ("LOW",  "CHILD"):  "90–120",
    ("LOW",  "TEEN"):   "100–130",
    ("LOW",  "ADULT"):  "100–130",
    ("LOW",  "SENIOR"): "90–120",
    ("MID",  "CHILD"):  "110–130",
    ("MID",  "TEEN"):   "120–145",
    ("MID",  "ADULT"):  "120–150",
    ("MID",  "SENIOR"): "110–135",
    ("HIGH", "CHILD"):  "120–140",
    ("HIGH", "TEEN"):   "130–155",
    ("HIGH", "ADULT"):  "130–160",
    ("HIGH", "SENIOR"): "120–145",
}


def _get_audience_wpm_range(understanding: str, audience_type: str) -> str:
    key = (
        (understanding or "").upper().strip(),
        (audience_type or "").upper().strip(),
    )
    return _AUDIENCE_WPM_MAP.get(key, "")


# ────────────────────────────────────────────────────────────
# 선택적 RAG 헬퍼 함수
# ────────────────────────────────────────────────────────────

def _detect_deviations(actual_wpm, actual_pitch, actual_db, actual_pause_pct, tm) -> dict:
    """§7 임계값 기준 이탈 여부 판정 → {wpm, pitch, db, pause} bool dict."""
    dev = {"wpm": False, "pitch": False, "db": False, "pause": False}

    # WPM: 절대 가드레일 100~180
    if actual_wpm < 100 or actual_wpm > 180:
        dev["wpm"] = True

    # Pitch: 개인 Baseline 대비 ±20%
    if tm and getattr(tm, "targetPitch", None) and tm.targetPitch > 0:
        ratio = actual_pitch / tm.targetPitch
        if ratio < 0.80 or ratio > 1.20:
            dev["pitch"] = True

    # dB: 개인 Baseline 대비 ±6 dB, Baseline 없으면 Gold Standard 63.72 기준
    if tm and getattr(tm, "targetIntensity", None) and tm.targetIntensity != 0:
        if abs(actual_db - tm.targetIntensity) > 6:
            dev["db"] = True
    else:
        if abs(actual_db - 63.72) > 6:
            dev["db"] = True

    # Pause: 8~25% 가드레일
    if actual_pause_pct < 8 or actual_pause_pct > 25:
        dev["pause"] = True

    return dev


def _build_selective_evidence(deviations: dict) -> str:
    """이탈 지표에 해당하는 §7+§9 학술 근거만 선별 조합."""
    parts = []
    if deviations["wpm"]:
        parts.append(_EVIDENCE_WPM)
    if deviations["pitch"]:
        parts.append(_EVIDENCE_PITCH)
    if deviations["db"]:
        parts.append(_EVIDENCE_DB)
    if deviations["pause"]:
        parts.append(_EVIDENCE_PAUSE)
    return "\n".join(parts)


def _build_audience_context(req, deviations: dict) -> str:
    """§8 청중 정보 요약 + 이탈 지표와 관련된 청중별 학술 근거."""
    audience_type = (getattr(req, "audienceType", None) or "미설정").upper().strip()
    understanding = (getattr(req, "audienceUnderstanding", None) or "미설정").upper().strip()
    speech_info = getattr(req, "speechInformation", None) or "미설정"
    style = getattr(req, "styleType", None) or "미선택"

    lines = [
        "[§8 청중 컨텍스트]",
        f"- 발표 유형: {speech_info}",
        f"- 청중 연령대: {audience_type}",
        f"- 청중 이해도: {understanding}",
        f"- 목표 스타일: {style}",
    ]

    wpm_range = _get_audience_wpm_range(understanding, audience_type)
    if wpm_range:
        lines.append(f"- §8 해당 청중 권장 WPM: {wpm_range}")

    # WPM 또는 Pause 이탈 시에만 청중별 학술 근거 주입
    if deviations["wpm"] or deviations["pause"]:
        evidence = _AUDIENCE_EVIDENCE_MAP.get(understanding, "")
        if evidence:
            lines.append(evidence)
        if audience_type == "SENIOR":
            lines.append(_ELDERLY_EVIDENCE)
        elif audience_type in ("CHILD", "TEEN"):
            lines.append(_YOUTH_EVIDENCE)
        lines.append(_PAUSE_COMMON_EVIDENCE)

    return "\n".join(lines)


def _build_measurement_summary(features: dict, tm, goal_similarity_score: float, deviations: dict) -> str:
    """실측값 vs 목표치 비교 + §7 임계값 판정 플래그."""
    actual_wpm = features.get("avgWpm", 0.0)
    actual_pitch = features.get("avgPitch", 0.0)
    actual_db = features.get("avgIntensity", 0.0)
    actual_pause_pct = (features.get("pauseRatio") or 0.0) * 100.0

    lines = [
        f"실측 WPM: {actual_wpm:.1f}",
        f"실측 Pitch: {actual_pitch:.1f} Hz",
        f"실측 dB: {actual_db:.1f} dBFS",
        f"실측 Pause: {actual_pause_pct:.1f}%",
    ]

    if tm:
        if getattr(tm, "targetWpm", None):
            lines.append(f"목표 WPM: {tm.targetWpm:.1f} (편차: {actual_wpm - tm.targetWpm:+.1f})")
        if getattr(tm, "targetPitch", None):
            lines.append(f"목표 Pitch: {tm.targetPitch:.1f} Hz (편차: {actual_pitch - tm.targetPitch:+.1f})")
        if getattr(tm, "targetPauseRatio", None) is not None:
            lines.append(f"목표 Pause: {tm.targetPauseRatio:.1f}% (편차: {actual_pause_pct - tm.targetPauseRatio:+.1f}%)")

    lines.append(f"목표 스타일 유사도 (규칙 산출): {goal_similarity_score:.1f}/100")

    # deviations 재활용 — 이미 판정된 결과를 텍스트로 변환
    flags = [
        "WPM 이탈" if deviations["wpm"] else "WPM 정상",
        "Pitch 이탈" if deviations["pitch"] else "Pitch 정상",
        "dB 이탈" if deviations["db"] else "dB 정상",
        "Pause 이탈" if deviations["pause"] else "Pause 정상",
    ]
    lines.append("§7 임계값 판정: " + " | ".join(flags))

    return "\n".join(lines)


# ────────────────────────────────────────────────────────────
# [STEP 10] 세션 상세 컨텍스트 — 문장별 결과·이슈·전사 주입
# 집계 지표만으로는 "일반론" 피드백에 그치므로, 이미 계산된 문장 단위 결과와
# 우선순위 이슈, 실제 전사 텍스트를 LLM에 함께 제공해 "특정 문장 근거" 피드백을
# 생성하게 한다. (점수·판정은 코드 산출값 그대로 — LLM은 서술만 담당)
# ────────────────────────────────────────────────────────────

# 문장 상세는 토큰 절감을 위해 문제 문장 위주로 상한을 둔다.
_MAX_PROBLEM_SENTENCES = 8
_MAX_ISSUES = 5
_MAX_TRANSCRIPT_CHARS = 1200

_SENTENCE_STATUS_LABEL = {
    "FAST": "빠름",
    "SLOW": "느림",
    "MISMATCH": "대본불일치/누락",
    "NORMAL": "정상",
}


def _summarize_sentence_results(sentence_results) -> str:
    """문장별 결과 요약 — 문제 문장(FAST/SLOW/MISMATCH) 우선, 정상은 개수만."""
    if not sentence_results:
        return ""

    problem = [s for s in sentence_results if s.get("status") in ("FAST", "SLOW", "MISMATCH")]
    normal_count = sum(1 for s in sentence_results if s.get("status") == "NORMAL")
    total = len(sentence_results)

    lines = [f"[문장별 분석] 전체 {total}문장 중 정상 {normal_count}문장, 문제 {len(problem)}문장"]
    for s in problem[:_MAX_PROBLEM_SENTENCES]:
        idx = s.get("sentenceIndex")
        status = _SENTENCE_STATUS_LABEL.get(s.get("status"), s.get("status") or "")
        wpm = s.get("wpm")
        pause = s.get("pauseDurationMs")
        skipped = s.get("skippedWordCount") or 0
        mismatch = s.get("mismatchWordCount") or 0
        # 사용자 표기는 1-based가 직관적이므로 +1 (FE 표기와 일치 가정)
        human_idx = (idx + 1) if isinstance(idx, int) else idx
        detail = []
        if wpm is not None:
            detail.append(f"WPM {wpm:.0f}")
        if pause:
            detail.append(f"문장내 쉼 {pause}ms")
        if skipped:
            detail.append(f"누락 {skipped}단어")
        if mismatch:
            detail.append(f"불일치 {mismatch}단어")
        lines.append(f"- {human_idx}번 문장: {status} ({', '.join(detail)})")
    if len(problem) > _MAX_PROBLEM_SENTENCES:
        lines.append(f"- 외 {len(problem) - _MAX_PROBLEM_SENTENCES}개 문제 문장 생략")
    return "\n".join(lines)


def _summarize_issues(issues) -> str:
    """우선순위 이슈 목록 요약 — 규칙 엔진이 산출한 상위 이슈."""
    if not issues:
        return ""
    lines = ["[우선순위 이슈] 규칙 엔진이 심각도 순으로 선별"]
    for it in issues[:_MAX_ISSUES]:
        idx = it.get("sentenceIndex")
        human_idx = (idx + 1) if isinstance(idx, int) else idx
        summary = it.get("summary") or ""
        issue_type = it.get("issueType") or ""
        where = f"{human_idx}번 문장" if human_idx is not None else "전체"
        lines.append(f"- {where} · {issue_type}: {summary}")
    return "\n".join(lines)


def _build_transcript_context(transcript: str, script_text: str) -> str:
    """실제 전사 + (선택) 대본 일부 — 내용·표현 근거 제공."""
    parts = []
    if transcript:
        t = transcript.strip()
        if len(t) > _MAX_TRANSCRIPT_CHARS:
            t = t[:_MAX_TRANSCRIPT_CHARS] + " …(이하 생략)"
        parts.append(f"[실제 발화 전사]\n{t}")
    return "\n".join(parts)


def _summarize_disfluency(disfluency) -> str:
    """[STAGE 2] 말버릇(필러)/반복어 탐지 결과 요약.

    탐지 결과가 비어 있으면 빈 문자열 반환(섹션 생략).
    주의: STT는 순수 필러를 자주 누락하므로 수치는 하한선이며,
    LLM에는 '관찰된 군더더기/반복' 정도로만 활용하도록 안내한다.
    """
    if not disfluency:
        return ""

    filler_count = disfluency.get("fillerCount") or 0
    breakdown = disfluency.get("fillerBreakdown") or {}
    repeated = disfluency.get("repeatedSequences") or []

    if filler_count == 0 and not repeated:
        return ""

    lines = ["[말버릇·반복 관찰] STT 전사 기반(순수 필러는 누락될 수 있어 실제보다 적게 잡힘)"]
    if filler_count > 0:
        detail = ", ".join(f"'{w}' {c}회" for w, c in breakdown.items())
        lines.append(f"- 군더더기 표현 총 {filler_count}회" + (f" ({detail})" if detail else ""))
    if repeated:
        rep = ", ".join(f"'{r.get('word')}' {r.get('count')}회 연속" for r in repeated)
        lines.append(f"- 단어 반복: {rep}")
    return "\n".join(lines)


def build_session_detail_context(sentence_results=None, issues=None, transcript=None,
                                 script_text=None, disfluency=None) -> str:
    """[STEP 10] 문장별 결과·이슈·전사를 합친 세션 상세 컨텍스트.

    각 요소가 없으면 해당 섹션을 생략한다(하위 호환·토큰 절감).
    """
    sections = [
        _summarize_sentence_results(sentence_results),
        _summarize_issues(issues),
        _summarize_disfluency(disfluency),
        _build_transcript_context(transcript or "", script_text or ""),
    ]
    return "\n\n".join(s for s in sections if s)


# ────────────────────────────────────────────────────────────
# 공개 API
# ────────────────────────────────────────────────────────────

try:
    from langsmith import traceable as _traceable
    _rag_traceable = _traceable(run_type="retriever", name="build_rag_context")
except ImportError:
    def _rag_traceable(fn):
        return fn


@_rag_traceable
def build_rag_context(features: dict, req, goal_similarity_score: float) -> str:
    """[STEP 8 리뷰] 선택적 RAG 컨텍스트 빌드.

    항상 포함: PIPELINE_SUMMARY, §6 Gold Standard, 청중·측정값 요약
    조건부 포함: 이탈 지표에 해당하는 §7+§9 학술 근거만 선별 주입
      - WPM 이탈 → _EVIDENCE_WPM
      - Pitch 이탈 → _EVIDENCE_PITCH
      - dB 이탈 → _EVIDENCE_DB
      - Pause 이탈 → _EVIDENCE_PAUSE
    미이탈 지표는 해당 근거 섹션 생략 → 토큰 절감
    """
    tm = req.targetMetrics
    actual_wpm = features.get("avgWpm", 0.0)
    actual_pitch = features.get("avgPitch", 0.0)
    actual_db = features.get("avgIntensity", 0.0)
    actual_pause_pct = (features.get("pauseRatio") or 0.0) * 100.0

    deviations = _detect_deviations(actual_wpm, actual_pitch, actual_db, actual_pause_pct, tm)
    selective_evidence = _build_selective_evidence(deviations)
    audience_context = _build_audience_context(req, deviations)
    measurement_summary = _build_measurement_summary(features, tm, goal_similarity_score, deviations)

    injected_count = sum(deviations.values())
    print(
        f"[Python STEP8] 선택적 RAG 주입 — 이탈 지표 {injected_count}개: "
        f"WPM={deviations['wpm']}, Pitch={deviations['pitch']}, "
        f"dB={deviations['db']}, Pause={deviations['pause']}",
        flush=True
    )

    return f"""{PIPELINE_SUMMARY}
{GOLD_STANDARD}
{selective_evidence}
{audience_context}

[현재 발표 세션 규칙 평가 결과]
{measurement_summary}
"""
