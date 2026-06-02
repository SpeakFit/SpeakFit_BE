import librosa
import numpy as np
import os
from difflib import SequenceMatcher
# [STEP 7] 공유 임계값은 helpers.py 단일 소스에서 import
from app.utils.helpers import (
    clamp, normalize_match_text,
    MATCH_THRESHOLD, SHORT_WORD_MATCH_THRESHOLD,
)

MIN_STT_CONFIDENCE = 0.1
# MATCH_THRESHOLD, SHORT_WORD_MATCH_THRESHOLD → helpers.py로 이전 (중복 제거)
PRONUNCIATION_SCORE_THRESHOLD = 0.72
LOW_PRONUNCIATION_THRESHOLD = 0.68
ALIGNMENT_LOOKAHEAD = 8
LONG_PAUSE_GAP_MS = 700
MIN_WORD_DURATION_MS = 300
ISSUE_LIMIT = 5
LOW_CONFIDENCE_THRESHOLD = 0.7
LOW_SCORE_THRESHOLD = 70
SKIPPED_RATIO_THRESHOLD = 0.15
MISMATCH_RATIO_THRESHOLD = 0.15

# ── §7 임계값 기반 규칙 점수 상수 (STEP 5) ─────────────────────────────────
# targetWpm 없을 때 fallback 기본 WPM 범위
WPM_DEFAULT_MIN   = 100.0   # §7 하한
WPM_DEFAULT_MAX   = 180.0   # §7 상한
WPM_DEFAULT_MID   = (WPM_DEFAULT_MIN + WPM_DEFAULT_MAX) / 2.0  # 140 음절/분

# WPM 편차 허용 범위
WPM_OK_RATIO      = 0.10    # targetWpm 대비 ±10% 이내 → 만점
WPM_GUARD_RATIO   = 0.20    # targetWpm 대비 ±20% 초과 → 0점 접근

# §7 기반 FAST/SLOW 기본 임계값 (targetWpm 없을 때 fallback)
FAST_WPM_THRESHOLD = WPM_DEFAULT_MAX   # 180
SLOW_WPM_THRESHOLD = WPM_DEFAULT_MIN   # 100

# Pause 점수 상수
PAUSE_PENALTY_PER_SEC = 3.0  # 긴 휴지 1초 = 3점 감점
PAUSE_MAX_PENALTY     = 20.0 # 최대 감점 한도

def analyze_voice_features(file_path, stt_text=None, gender=None, max_duration=None):
    """오디오 파일에서 정량적 특징 추출 (Librosa 사용)

    Args:
        file_path: 오디오 파일 경로
        stt_text: STT 결과 전체 텍스트 (제공 시 음절/분 방식으로 WPM 계산, 분석 스크립트 기준)
        gender: 성별 ('MALE' / 'FEMALE'), Pitch 필터 적용에 사용
        max_duration: 최대 로드 길이(초). 베이스라인 분석 시 10초 제한 적용.
    """
    try:
        file_size = os.path.getsize(file_path) if os.path.exists(file_path) else 0
        print(f"[Python] Loading voice file with librosa: path={file_path}, size={file_size} bytes", flush=True)

        # [STEP 1] sr=16000 고정 — 분석 스크립트(자유대화음성분석.py) 기준과 통일
        load_kwargs = {"sr": 16000}
        if max_duration is not None:
            load_kwargs["duration"] = max_duration

        y, sr = librosa.load(file_path, **load_kwargs)
        if y.size == 0:
            raise ValueError("Loaded audio is empty")

        duration = librosa.get_duration(y=y, sr=sr)
        print(
            f"[Python] Librosa loaded voice file: sample_rate={sr}, samples={y.size}, duration={duration:.3f}s",
            flush=True
        )

        # 1. 목소리 높낮이 (Pitch)
        # [STEP 1] piptrack mean → librosa.yin median + 성별 필터
        # 분석 스크립트: median(librosa.yin(y, fmin=65, fmax=450)) + 성별 필터
        yin_pitches = librosa.yin(y, fmin=65, fmax=450)
        yin_pitches = yin_pitches[yin_pitches > 0]  # 무성 구간(0Hz) 제거

        gender_upper = gender.upper() if gender else None
        if gender_upper == "MALE":
            filtered_pitches = yin_pitches[(yin_pitches >= 70) & (yin_pitches <= 220)]
        elif gender_upper == "FEMALE":
            filtered_pitches = yin_pitches[(yin_pitches >= 140) & (yin_pitches <= 380)]
        else:
            # gender=None (Java 미전송) 또는 알 수 없는 값 → 전체 유효 범위 fallback
            # Java 동기화 전까지 이 경로로 동작. 에러 없이 65~450Hz 범위 적용.
            if gender is not None:
                print(f"[Python WARN] 알 수 없는 gender 값: '{gender}', 전체 범위(65~450Hz) fallback 적용", flush=True)
            filtered_pitches = yin_pitches[(yin_pitches >= 65) & (yin_pitches <= 450)]

        avg_pitch = float(np.median(filtered_pitches)) if len(filtered_pitches) > 0 else 0.0
        pitch_std = float(np.std(filtered_pitches)) if len(filtered_pitches) > 0 else 0.0

        print(
            f"[Python] Pitch (yin median, gender={gender}): {avg_pitch:.2f} Hz"
            f" | valid frames={len(filtered_pitches)}",
            flush=True
        )

        # 2. 성량 (Intensity)
        # [STEP 1] np.mean(rms)*1000 → dBFS (20*log10(mean_rms))
        # 마이크 캘리브레이션 없이 절대 SPL 측정 불가 → Baseline 상대값으로 운용
        rms = librosa.feature.rms(y=y)
        # [STEP 1 리뷰] 완전 묵음 안전장치: 배열 전체에 최솟값 1e-5 보장
        # mean_rms + 1e-9만으로는 rms 개별 원소가 0일 때 log(-inf) → std(nan) → JSON 직렬화 실패 가능
        rms_safe = np.maximum(rms, 1e-5)
        mean_rms = float(np.mean(rms_safe))
        avg_intensity = float(20 * np.log10(mean_rms))           # dBFS
        intensity_std = float(np.std(20 * np.log10(rms_safe.flatten())))

        # 3. 쉼 구간 (Pause) 탐지
        intervals = librosa.effects.split(y, top_db=25)
        pause_count = len(intervals) - 1 if len(intervals) > 0 else 0
        non_silent_duration = sum([(e - s) / sr for s, e in intervals])
        pause_ratio = (duration - non_silent_duration) / duration if duration > 0 else 0

        # 4. 발화 속도 (WPM)
        # [STEP 1] 음절/분 방식 — 분석 스크립트 기준:
        #   WPM = len(stt_text.replace(" ", "")) / (duration / 60)
        # stt_text 없을 경우 onset fallback (베이스라인 STEP 2에서 대체 예정)
        if stt_text and duration > 0:
            syllable_count = len(stt_text.replace(" ", ""))
            avg_wpm = syllable_count / (duration / 60)
            print(f"[Python] WPM (음절/분): {avg_wpm:.1f} | syllables={syllable_count}, duration={duration:.2f}s", flush=True)
        else:
            onsets = librosa.onset.onset_detect(y=y, sr=sr)
            avg_wpm = (len(onsets) / 2) / (duration / 60) if duration > 0 else 0
            print(f"[Python] WPM (onset fallback): {avg_wpm:.1f} | onsets={len(onsets)}", flush=True)

        # 5. 발음 선명도 (ZCR)
        zcr = librosa.feature.zero_crossing_rate(y)
        avg_zcr = float(np.mean(zcr))

        return {
            "durationSec": float(duration),
            "avgWpm": float(avg_wpm),
            "avgPitch": float(avg_pitch),
            "avgIntensity": float(avg_intensity),   # dBFS (예: -30.0 ~ -10.0)
            "avgZcr": float(avg_zcr),
            "pauseRatio": float(pause_ratio),
            "wpmDiff": 0.0,                         # [STEP 1] 하드코딩 10.0 제거 → STEP 3에서 목표치 대비 실제 차이 계산
            "pitchDiff": pitch_std,
            "intensityDiff": float(intensity_std),  # dB 단위 표준편차
            "zcrDiff": float(np.std(zcr)),
            "pauseCount": int(pause_count)
        }
    except Exception as e:
        import traceback
        print(f"[Python] 음성 분석 중 치명적 오류 발생: {repr(e)}", flush=True)
        traceback.print_exc()
        return None

def build_word_results(script_words, duration_sec):
    """임시 단어 정렬 결과를 생성합니다."""
    if not script_words:
        return []

    return build_skipped_word_results(script_words)

def build_aligned_word_results(script_words, stt_words):
    """STT 단어 타임스탬프를 대본 단어 순서에 맞춰 정렬합니다."""
    if not script_words:
        return []

    usable_stt_words = [
        word for word in stt_words
        if normalize_match_text(word.get("word")) and float(word.get("confidence") or 0.0) >= MIN_STT_CONFIDENCE
    ]
    if not usable_stt_words:
        return build_skipped_word_results(script_words)

    word_results = []
    stt_index = 0
    matched_count = 0

    for script_word in script_words:
        match = find_best_stt_match(script_word, usable_stt_words, stt_index)
        if match:
            matched_word_index, matched_word, similarity = match
            word_results.append(to_matched_word_result(script_word, matched_word, similarity))
            stt_index = matched_word_index + 1
            matched_count += 1
        else:
            word_results.append(to_skipped_word_result(script_word))

    print(
        "[Python] 단어 alignment 완료: "
        f"scriptWords={len(script_words)}, sttWords={len(usable_stt_words)}, matched={matched_count}"
    )
    return word_results

def build_skipped_word_results(script_words):
    return [to_skipped_word_result(word) for word in script_words]

def find_best_stt_match(script_word, stt_words, start_index):
    script_text = normalize_match_text(script_word.normalizedText or script_word.text)
    if not script_text:
        return None

    end_index = min(len(stt_words), start_index + ALIGNMENT_LOOKAHEAD)
    best_match = None
    best_score = 0.0

    for index in range(start_index, end_index):
        spoken_text = normalize_match_text(stt_words[index].get("word"))
        similarity = calculate_word_similarity(script_text, spoken_text)
        confidence = normalize_stt_confidence(stt_words[index].get("confidence"))
        order_penalty = (index - start_index) * 0.03
        score = similarity * 0.8 + confidence * 0.2 - order_penalty
        if score > best_score:
            best_score = score
            best_match = (index, stt_words[index], similarity)

    threshold = SHORT_WORD_MATCH_THRESHOLD if len(script_text) <= 2 else MATCH_THRESHOLD
    if best_match and best_match[2] >= threshold:
        return best_match

    return None

def calculate_word_similarity(script_text, spoken_text):
    if not script_text or not spoken_text:
        return 0.0
    if script_text == spoken_text:
        return 1.0
    if len(script_text) > 2 and (script_text in spoken_text or spoken_text in script_text):
        return 0.86

    return SequenceMatcher(None, script_text, spoken_text).ratio()

def to_matched_word_result(script_word, stt_word, similarity):
    stt_confidence = normalize_stt_confidence(stt_word.get("confidence"))
    pronunciation_score = calculate_pronunciation_score(similarity, stt_confidence)
    status = resolve_word_status(script_word, similarity, pronunciation_score)
    return {
        "scriptWordId": script_word.scriptWordId,
        "wordIndex": script_word.globalWordIndex,
        "globalWordIndex": script_word.globalWordIndex,
        "sentenceWordIndex": script_word.sentenceWordIndex,
        "scriptText": script_word.text,
        "spokenText": stt_word.get("word"),
        "startMs": stt_word.get("startMs"),
        "endMs": stt_word.get("endMs"),
        "confidence": round(pronunciation_score, 2),
        "skipped": False,
        "status": status
    }

def calculate_pronunciation_score(similarity, stt_confidence):
    """Estimate word quality from script similarity and STT confidence."""
    return clamp(similarity * 0.65 + stt_confidence * 0.35, 0.0, 1.0)

def normalize_stt_confidence(confidence):
    confidence = float(confidence or 0.0)
    if confidence <= 0:
        return 0.75

    return clamp(confidence, 0.0, 1.0)

def resolve_word_status(script_word, similarity, pronunciation_score):
    script_text = normalize_match_text(script_word.normalizedText or script_word.text)
    similarity_threshold = SHORT_WORD_MATCH_THRESHOLD if len(script_text) <= 2 else MATCH_THRESHOLD
    if similarity < similarity_threshold:
        return "MISMATCH"
    if pronunciation_score < PRONUNCIATION_SCORE_THRESHOLD:
        return "MISMATCH"

    return "NORMAL"

def to_skipped_word_result(script_word):
    return {
        "scriptWordId": script_word.scriptWordId,
        "wordIndex": script_word.globalWordIndex,
        "globalWordIndex": script_word.globalWordIndex,
        "sentenceWordIndex": script_word.sentenceWordIndex,
        "scriptText": script_word.text,
        "spokenText": None,
        "startMs": None,
        "endMs": None,
        "confidence": 0.0,
        "skipped": True,
        "status": "MISMATCH"
    }

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
    return [sentence_map[key] for key in sorted(sentence_map.keys())]

def resolve_sentence_status(wpm, avg_confidence, skipped_word_count, mismatch_word_count, target_wpm=None):
    if skipped_word_count > 0 or mismatch_word_count > 0 or avg_confidence < 0.7:
        return "MISMATCH"
    if wpm is None:
        return "MISMATCH"
    # [STEP 5-C] target_wpm 기반 임계값 — 없으면 §7 fallback
    fast_threshold = target_wpm * (1.0 + WPM_GUARD_RATIO) if target_wpm and target_wpm > 0 else FAST_WPM_THRESHOLD
    slow_threshold = target_wpm * (1.0 - WPM_GUARD_RATIO) if target_wpm and target_wpm > 0 else SLOW_WPM_THRESHOLD
    if wpm > fast_threshold:
        return "FAST"
    if wpm < slow_threshold:
        return "SLOW"
    return "NORMAL"

def build_sentence_results(script_words, word_results, features, target_metrics=None):
    """문장별 분석 결과를 생성합니다.

    Args:
        script_words: 대본 단어 목록
        word_results: 단어 정렬 결과
        features: analyze_voice_features() 반환값 (전체 피처)
        target_metrics: [STEP 4-C] STEP 3 목표치 dict (targetWpm/targetPitch/targetIntensity/targetZcr/targetPauseRatio).
                        현재는 수신만 하고, STEP 5에서 규칙 기반 점수 계산에 실제 사용됩니다.
    """
    if not script_words or not word_results:
        return []

    word_result_map = {r["globalWordIndex"]: r for r in word_results}
    sentence_results = []

    for sentence in group_words_by_sentence(script_words):
        words = sentence["words"]
        related_results = [word_result_map[w.globalWordIndex] for w in words if w.globalWordIndex in word_result_map]
        if not related_results: continue

        timed_results = get_timed_word_results(related_results)
        start_ms, end_ms = resolve_sentence_time_range(timed_results)
        wpm = calculate_sentence_wpm(len(words), start_ms, end_ms)
        pause_duration_ms = calculate_sentence_pause_duration(timed_results)
        avg_confidence = calculate_average_confidence(related_results)
        skipped_word_count = sum(1 for r in related_results if r["skipped"])
        mismatch_word_count = sum(1 for r in related_results if r.get("status") == "MISMATCH" and not r["skipped"])
        # [STEP 5-C] target_wpm 추출 — STEP 4에서 전달된 목표치 사용
        target_wpm = target_metrics.get("targetWpm") if target_metrics else None
        score = calculate_sentence_score(
            word_count=len(words),
            avg_confidence=avg_confidence,
            skipped_word_count=skipped_word_count,
            mismatch_word_count=mismatch_word_count,
            wpm=wpm,
            pause_duration_ms=pause_duration_ms,
            target_wpm=target_wpm,
        )

        sentence_results.append({
            "scriptSentenceId": sentence["scriptSentenceId"],
            "sentenceIndex": sentence["sentenceIndex"],
            "startMs": start_ms,
            "endMs": end_ms,
            "wordCount": len(words),
            "skippedWordCount": skipped_word_count,
            "mismatchWordCount": mismatch_word_count,
            "wpm": round(float(wpm), 2) if wpm is not None else None,
            "pauseDurationMs": pause_duration_ms,
            "avgPitch": features.get("avgPitch", 0.0),
            "avgIntensity": features.get("avgIntensity", 0.0),
            "score": round(float(score), 2),
            "status": resolve_sentence_status(wpm, avg_confidence, skipped_word_count, mismatch_word_count, target_wpm)
        })
    return sentence_results

def get_timed_word_results(word_results):
    return sorted(
        [r for r in word_results if r.get("startMs") is not None and r.get("endMs") is not None],
        key=lambda r: r["startMs"]
    )

def resolve_sentence_time_range(timed_results):
    if not timed_results:
        return None, None

    return timed_results[0]["startMs"], max(r["endMs"] for r in timed_results)

def calculate_sentence_wpm(word_count, start_ms, end_ms):
    if start_ms is None or end_ms is None or end_ms <= start_ms:
        return None

    min_duration_ms = max(word_count, 1) * MIN_WORD_DURATION_MS
    duration_ms = max(end_ms - start_ms, min_duration_ms)
    duration_min = duration_ms / 60000
    return word_count / duration_min

def calculate_sentence_pause_duration(timed_results):
    if len(timed_results) < 2:
        return 0 if timed_results else None

    pause_duration_ms = 0
    previous_end_ms = timed_results[0]["endMs"]
    for result in timed_results[1:]:
        gap_ms = result["startMs"] - previous_end_ms
        if gap_ms > LONG_PAUSE_GAP_MS:
            pause_duration_ms += gap_ms
        previous_end_ms = max(previous_end_ms, result["endMs"])

    return pause_duration_ms

def calculate_average_confidence(word_results):
    if not word_results:
        return 0.0

    return sum(float(r.get("confidence") or 0.0) for r in word_results) / len(word_results)

def calculate_sentence_score(word_count, avg_confidence, skipped_word_count, mismatch_word_count, wpm, pause_duration_ms, target_wpm=None):
    """[STEP 5-B] 규칙 기반 문장 점수 계산 (총 100점)

    - 발음 정확도 50점: avg_confidence(신뢰도) - skipped/mismatch 비율 감점
    - WPM 속도   30점: targetWpm(§3 목표치) 대비 편차율 → §7 임계값으로 선형 감점
    - Pause 여유 20점: 긴 휴지 1초당 PAUSE_PENALTY_PER_SEC 감점, 최대 PAUSE_MAX_PENALTY
    """
    if word_count <= 0:
        return 0.0

    skipped_ratio = skipped_word_count / word_count
    mismatch_ratio = mismatch_word_count / word_count

    # 발음 정확도 (50점)
    pronunciation_score = clamp(avg_confidence * 50 - skipped_ratio * 25 - mismatch_ratio * 25, 0.0, 50.0)

    # WPM 점수 (30점) — target_wpm 기준, 없으면 §7 중앙값(140) fallback
    effective_target = target_wpm if target_wpm and target_wpm > 0 else WPM_DEFAULT_MID
    if wpm is not None:
        deviation_ratio = abs(wpm - effective_target) / effective_target
        if deviation_ratio <= WPM_OK_RATIO:
            wpm_score = 30.0
        elif deviation_ratio >= WPM_GUARD_RATIO:
            wpm_score = 0.0
        else:
            wpm_score = 30.0 * (1.0 - (deviation_ratio - WPM_OK_RATIO) / (WPM_GUARD_RATIO - WPM_OK_RATIO))
    else:
        wpm_score = 0.0

    # Pause 점수 (20점) — 긴 휴지 초당 감점
    pause_penalty = min(PAUSE_PENALTY_PER_SEC * ((pause_duration_ms or 0) / 1000), PAUSE_MAX_PENALTY)
    pause_score = clamp(20.0 - pause_penalty, 0.0, 20.0)

    return clamp(pronunciation_score + wpm_score + pause_score, 0.0, 100.0)

def build_issue_results(sentence_results):
    candidates = []
    for sentence in sentence_results:
        candidates.extend(build_sentence_issue_candidates(sentence))

    ranked_candidates = sorted(
        candidates,
        key=lambda issue: (-issue["severity"], issue.get("score") if issue.get("score") is not None else 100.0)
    )

    issues = []
    seen_keys = set()
    for candidate in ranked_candidates:
        dedupe_key = (candidate.get("sentenceIndex"), candidate.get("issueType"))
        if dedupe_key in seen_keys:
            continue

        candidate["displayOrder"] = len(issues)
        issues.append(candidate)
        seen_keys.add(dedupe_key)
        if len(issues) >= ISSUE_LIMIT:
            break

    return [{key: value for key, value in issue.items() if key != "severity"} for issue in issues]

def build_sentence_issue_candidates(sentence):
    candidates = []
    word_count = max(sentence.get("wordCount") or 0, 1)
    skipped_count = sentence.get("skippedWordCount") or 0
    mismatch_count = sentence.get("mismatchWordCount") or 0
    skipped_ratio = skipped_count / word_count
    mismatch_ratio = mismatch_count / word_count
    score = sentence.get("score")
    wpm = sentence.get("wpm")
    pause_duration_ms = sentence.get("pauseDurationMs") or 0
    avg_confidence = calculate_sentence_confidence_from_score(sentence)

    if skipped_count > 0 and skipped_ratio >= SKIPPED_RATIO_THRESHOLD:
        candidates.append(to_issue_result(
            sentence, "SKIPPED_WORDS",
            severity=90 + skipped_ratio * 20,
            summary="일부 단어가 누락되었습니다.",
            feedback="대본 단어를 건너뛰지 않도록 문장 단위로 천천히 다시 읽어보세요.",
            reason=f"skippedWordCount={skipped_count}, wordCount={word_count}, skippedRatio={skipped_ratio:.2f}"
        ))

    if mismatch_count > 0 and mismatch_ratio >= MISMATCH_RATIO_THRESHOLD:
        candidates.append(to_issue_result(
            sentence, "LOW_CONFIDENCE",
            severity=75 + mismatch_ratio * 15,
            summary="대본과 다르게 인식된 단어가 있습니다.",
            feedback="단어 끝을 흐리지 말고 대본 표현과 같은 순서로 또렷하게 말해보세요.",
            reason=f"mismatchWordCount={mismatch_count}, wordCount={word_count}, mismatchRatio={mismatch_ratio:.2f}"
        ))

    if wpm is not None and wpm > FAST_WPM_THRESHOLD:
        candidates.append(to_issue_result(
            sentence, "TOO_FAST",
            severity=min(88, 60 + (wpm - FAST_WPM_THRESHOLD) * 0.3),
            summary="문장 발화 속도가 빠릅니다.",
            feedback="핵심 단어 앞뒤에서 짧게 쉬면서 속도를 낮춰보세요.",
            reason=f"wpm={wpm}, threshold={FAST_WPM_THRESHOLD}"
        ))

    if wpm is not None and wpm < SLOW_WPM_THRESHOLD:
        candidates.append(to_issue_result(
            sentence, "TOO_SLOW",
            severity=min(82, 55 + (SLOW_WPM_THRESHOLD - wpm) * 0.25),
            summary="문장 발화 속도가 느립니다.",
            feedback="단어 사이 간격을 줄이고 문장 단위로 자연스럽게 이어서 말해보세요.",
            reason=f"wpm={wpm}, threshold={SLOW_WPM_THRESHOLD}"
        ))

    if pause_duration_ms > LONG_PAUSE_GAP_MS:
        candidates.append(to_issue_result(
            sentence, "LONG_PAUSE",
            severity=min(80, 50 + pause_duration_ms / 100),
            summary="문장 안의 쉼이 깁니다.",
            feedback="긴 멈춤은 줄이고 의미 단위에서만 짧게 쉬어보세요.",
            reason=f"pauseDurationMs={pause_duration_ms}, threshold={LONG_PAUSE_GAP_MS}"
        ))

    if avg_confidence < LOW_CONFIDENCE_THRESHOLD and skipped_count == 0 and mismatch_count == 0:
        candidates.append(to_issue_result(
            sentence, "LOW_CONFIDENCE",
            severity=70 + (LOW_CONFIDENCE_THRESHOLD - avg_confidence) * 20,
            summary="발화 신뢰도가 낮습니다.",
            feedback="입 모양을 분명히 하고 문장 끝을 흐리지 않게 말해보세요.",
            reason=f"avgConfidence={avg_confidence:.2f}, threshold={LOW_CONFIDENCE_THRESHOLD}"
        ))

    if score is not None and score < LOW_SCORE_THRESHOLD:
        candidates.append(to_issue_result(
            sentence, "LOW_SCORE",
            severity=65 + (LOW_SCORE_THRESHOLD - score) * 0.2,
            summary="문장 점수가 낮습니다.",
            feedback="누락, 속도, 쉼을 함께 점검하며 같은 문장을 다시 연습해보세요.",
            reason=f"score={score}, threshold={LOW_SCORE_THRESHOLD}"
        ))

    return candidates

def calculate_sentence_confidence_from_score(sentence):
    score = sentence.get("score")
    if score is None:
        return 0.0

    skipped_count = sentence.get("skippedWordCount") or 0
    mismatch_count = sentence.get("mismatchWordCount") or 0
    if skipped_count > 0 or mismatch_count > 0:
        return min(score / 100, 0.69)

    return score / 100

def to_issue_result(sentence, issue_type, severity, summary, feedback, reason):
    return {
        "scriptSentenceId": sentence.get("scriptSentenceId"),
        "sentenceIndex": sentence.get("sentenceIndex"),
        "startIndex": sentence.get("sentenceIndex"),
        "endIndex": sentence.get("sentenceIndex"),
        "issueType": issue_type,
        "issueSummary": summary,
        "feedbackContent": feedback,
        "reason": reason,
        "score": sentence.get("score"),
        "displayOrder": 0,
        "wpm": sentence.get("wpm"),
        "intensity": sentence.get("avgIntensity"),
        "severity": severity
    }
