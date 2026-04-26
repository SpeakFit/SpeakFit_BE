import librosa
import numpy as np
from difflib import SequenceMatcher
from app.utils.helpers import clamp, normalize_match_text

MIN_STT_CONFIDENCE = 0.1
MATCH_THRESHOLD = 0.72
SHORT_WORD_MATCH_THRESHOLD = 0.9
ALIGNMENT_LOOKAHEAD = 8
TARGET_WPM = 130
FAST_WPM_THRESHOLD = 170
SLOW_WPM_THRESHOLD = 80
LONG_PAUSE_GAP_MS = 700
MIN_WORD_DURATION_MS = 300

def analyze_voice_features(file_path):
    """오디오 파일에서 정량적 특징 추출 (Librosa 사용)"""
    try:
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
            "wpmDiff": 10.0,
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
            "scriptWordId": word.scriptWordId,
            "wordIndex": word.globalWordIndex,
            "globalWordIndex": word.globalWordIndex,
            "sentenceWordIndex": word.sentenceWordIndex,
            "scriptText": word.text,
            "spokenText": word.text,
            "startMs": start_ms,
            "endMs": end_ms,
            "confidence": confidence,
            "skipped": False,
            "status": "NORMAL"
        })
    return word_results

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
        order_penalty = (index - start_index) * 0.03
        score = similarity - order_penalty
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
    confidence = float(stt_word.get("confidence") or 0.0)
    status = "NORMAL" if similarity >= MATCH_THRESHOLD else "MISMATCH"
    return {
        "scriptWordId": script_word.scriptWordId,
        "wordIndex": script_word.globalWordIndex,
        "globalWordIndex": script_word.globalWordIndex,
        "sentenceWordIndex": script_word.sentenceWordIndex,
        "scriptText": script_word.text,
        "spokenText": stt_word.get("word"),
        "startMs": stt_word.get("startMs"),
        "endMs": stt_word.get("endMs"),
        "confidence": round(confidence, 2),
        "skipped": False,
        "status": status
    }

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

def resolve_sentence_status(wpm, avg_confidence, skipped_word_count, mismatch_word_count):
    if skipped_word_count > 0 or mismatch_word_count > 0 or avg_confidence < 0.7:
        return "MISMATCH"
    if wpm is None:
        return "MISMATCH"
    if wpm > FAST_WPM_THRESHOLD:
        return "FAST"
    if wpm < SLOW_WPM_THRESHOLD:
        return "SLOW"
    return "NORMAL"

def build_sentence_results(script_words, word_results, features):
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
        score = calculate_sentence_score(
            word_count=len(words),
            avg_confidence=avg_confidence,
            skipped_word_count=skipped_word_count,
            mismatch_word_count=mismatch_word_count,
            wpm=wpm,
            pause_duration_ms=pause_duration_ms,
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
            "status": resolve_sentence_status(wpm, avg_confidence, skipped_word_count, mismatch_word_count)
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

def calculate_sentence_score(word_count, avg_confidence, skipped_word_count, mismatch_word_count, wpm, pause_duration_ms):
    if word_count <= 0:
        return 0.0

    skipped_ratio = skipped_word_count / word_count
    mismatch_ratio = mismatch_word_count / word_count
    speed_penalty = min(abs(wpm - TARGET_WPM) * 0.08, 18) if wpm is not None else 18
    pause_penalty = min((pause_duration_ms or 0) / 1000 * 4, 12)

    score = (
        avg_confidence * 100
        - skipped_ratio * 45
        - mismatch_ratio * 25
        - speed_penalty
        - pause_penalty
    )
    return clamp(score, 0, 100)

def build_issue_results(sentence_results):
    issues = []
    ranked = sorted(sentence_results, key=lambda x: x.get("score", 100.0))

    summaries = {
        "SKIPPED_WORDS": "일부 단어가 누락되었습니다.",
        "TOO_FAST": "문장 발화 속도가 빠릅니다.",
        "TOO_SLOW": "문장 발화 속도가 느립니다.",
        "LONG_PAUSE": "문장 안의 쉼이 깁니다.",
        "LOW_SCORE": "문장 점수가 낮습니다.",
        "LOW_CONFIDENCE": "발화 신뢰도가 낮습니다."
    }
    feedback_map = {
        "SKIPPED_WORDS": "대본 단어를 빠뜨리지 않도록 문장 단위로 천천히 다시 읽어보세요.",
        "TOO_FAST": "핵심 단어 앞뒤에서 짧게 쉬며 속도를 낮춰보세요.",
        "TOO_SLOW": "문장 흐름이 끊기지 않도록 의미 단위로 이어 읽어보세요.",
        "LONG_PAUSE": "쉼은 유지하되 1초 안팎으로 짧게 조절해보세요.",
        "LOW_SCORE": "발음, 속도, 쉼을 함께 점검하며 다시 연습해보세요.",
        "LOW_CONFIDENCE": "입 모양을 또렷하게 하고 문장 끝을 흐리지 않게 말해보세요."
    }

    for i, s in enumerate(ranked[:5]):
        itype = "LOW_CONFIDENCE"
        wpm = s.get("wpm")
        if s.get("skippedWordCount", 0) > 0: itype = "SKIPPED_WORDS"
        elif wpm is not None and wpm > FAST_WPM_THRESHOLD: itype = "TOO_FAST"
        elif wpm is not None and wpm < SLOW_WPM_THRESHOLD: itype = "TOO_SLOW"
        elif (s.get("pauseDurationMs") or 0) > LONG_PAUSE_GAP_MS: itype = "LONG_PAUSE"
        elif s.get("score", 100.0) < 70: itype = "LOW_SCORE"

        issues.append({
            "scriptSentenceId": s.get("scriptSentenceId"),
            "sentenceIndex": s.get("sentenceIndex"),
            "startIndex": s.get("sentenceIndex"),
            "endIndex": s.get("sentenceIndex"),
            "issueType": itype,
            "issueSummary": summaries.get(itype, "개선이 필요한 문장입니다."),
            "feedbackContent": feedback_map.get(itype, "문장 단위로 다시 연습해보세요."),
            "reason": f"유형={itype}, 점수={s.get('score')}, WPM={s.get('wpm')}, 쉼={s.get('pauseDurationMs')}ms, 누락단어={s.get('skippedWordCount')}",
            "score": s.get("score"),
            "displayOrder": i,
            "wpm": s.get("wpm"),
            "intensity": s.get("avgIntensity")
        })
    return issues
