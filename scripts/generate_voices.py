#!/usr/bin/env python3
"""Generate the game's offline Mandarin voice pack with Gemini's dedicated TTS API.

Requires GEMINI_API_KEY in the environment. No key, header, or response audio is logged.
The standard-library implementation keeps generation reproducible without an SDK install.
"""
from __future__ import annotations

import argparse
import array
import base64
import concurrent.futures
import datetime
import hashlib
import io
import json
import math
import os
from pathlib import Path
import random
import sys
import time
import urllib.error
import urllib.request
import wave

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/voices"
MODEL = "gemini-3.8-flash-lite-tts"
RATE = 24000
VOICES = {
    0: {"name": "Achird", "label": "温暖男声", "presentation": "male", "style": "成年男性，温暖亲切的中音，声音圆润放松，轻微笑意。"},
    1: {"name": "Orus", "label": "沉稳男声", "presentation": "male", "style": "成年男性，沉稳醇厚的偏低中音，笃定平和，自然舒展。"},
    2: {"name": "Leda", "label": "清亮女声", "presentation": "female", "style": "年轻成年女性，清亮柔和的中音，自然亲切，轻微笑意，不卖萌。"},
}
COMMON_STYLE = (
    "标准中国大陆普通话，像与朋友面对面打斗地主，真实自然的人声。"
    "语速轻快但吐字清楚，节奏紧凑，不拖长尾音，句末干净利落。"
    "报牌时简洁清晰，亲切从容，不用播音腔，不夸张表演，不喊叫。"
    "只说指定台词，不额外发声，不笑、不唱，不加背景音乐或音效。"
    "字母 K 按英语字母 K 读。"
)

RANKS = dict(zip(
    ["3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k", "a", "2", "sj", "bj"],
    ["三", "四", "五", "六", "七", "八", "九", "十", "勾", "圈", "K", "尖", "二", "小王", "大王"],
))
PHRASES = {"single_" + key: text for key, text in RANKS.items()}
PHRASES.update({"pair_" + key: "对" + text for key, text in list(RANKS.items())[:13]})
PHRASES.update({"triple_" + key: "三个" + text for key, text in list(RANKS.items())[:13]})
PHRASES.update({
    "triple_single": "三带一", "triple_pair": "三带一对",
    "straight": "顺子", "pair_straight": "连对", "plane": "飞机",
    "plane_singles": "飞机带单", "plane_pairs": "飞机带对",
    "four_two_singles": "四带二", "four_two_pairs": "四带两对",
    "bomb": "炸弹", "rocket": "王炸",
    "call": "叫地主", "no_call": "不叫", "rob": "抢地主", "no_rob": "不抢",
    "double": "加倍", "no_double": "不加倍",
    "pass_0": "不要", "pass_1": "要不起", "pass_2": "过",
    "alert_one": "我只剩一张牌啦", "alert_two": "我只剩两张牌啦",
    "landlord": "我来当地主", "win": "赢啦", "lose": "下把再来",
    "spring": "春天", "anti_spring": "反春天", "redeal": "没人叫地主，重新发牌",
    "trustee_on": "交给我吧", "trustee_off": "回来啦",
    "error_empty": "先选好要出的牌", "error_combo": "这手牌型不对哦",
    "error_small": "这手牌还不够大", "turn": "轮到你出牌啦", "relief": "救济金到账啦",
})
# Chinese card-room J is spoken gōu. The unambiguous homophone 钩 avoids
# occasional letter-K pronunciation on isolated pair callouts.
PHONETIC_TRANSCRIPTS = {"pair_j": "对钩"}


def samples_from_response(data: dict) -> tuple[array.array, int]:
    parts = data.get("candidates", [{}])[0].get("content", {}).get("parts", [])
    inline = next((p["inlineData"] for p in parts if "inlineData" in p), None)
    if not inline:
        raise ValueError("TTS response has no audio")
    raw = base64.b64decode(inline["data"])
    rate = RATE
    if raw.startswith(b"RIFF"):
        with wave.open(io.BytesIO(raw), "rb") as wav:
            if wav.getnchannels() != 1 or wav.getsampwidth() != 2:
                raise ValueError("Expected mono PCM16")
            rate, raw = wav.getframerate(), wav.readframes(wav.getnframes())
    elif not inline.get("mimeType", "").lower().startswith("audio/l16"):
        raise ValueError("Expected WAV or raw PCM16 audio")
    samples = array.array("h", raw)
    if sys.byteorder != "little":
        samples.byteswap()
    return samples, rate


def rms(samples) -> float:
    return math.sqrt(sum(int(x) * int(x) for x in samples) / max(1, len(samples)))


def master(samples: array.array, rate: int) -> tuple[array.array, dict]:
    """Conservatively trim exterior silence, level active speech, preserve consonants."""
    if not samples:
        raise ValueError("Empty PCM")
    window = max(1, rate // 100)  # 10 ms
    levels = [rms(samples[i:i + window]) for i in range(0, len(samples), window)]
    threshold = max(50.0, max(levels) * 0.012)
    active = [i for i, value in enumerate(levels) if value >= threshold]
    if not active:
        raise ValueError("Silent PCM")
    start = max(0, active[0] * window - int(rate * 0.09))
    end = min(len(samples), (active[-1] + 1) * window + int(rate * 0.15))
    trimmed = samples[start:end]
    speech = [value for value in trimmed if abs(value) >= threshold]
    peak = max(abs(value) for value in trimmed)
    gain = min((32767 * 10 ** (-20 / 20)) / rms(speech), (32767 * 10 ** (-1 / 20)) / peak, 3.0)
    result = array.array("h", (round(value * gain) for value in trimmed))
    # Fade only the outer 4 ms, safely inside the preserved silent handles.
    fade = min(rate // 250, len(result) // 2)
    for i in range(fade):
        result[i] = round(result[i] * i / fade)
        result[-i - 1] = round(result[-i - 1] * i / fade)
    return result, {"leadingTrimMs": round(start * 1000 / rate),
                    "trailingTrimMs": round((len(samples) - end) * 1000 / rate),
                    "gainDb": round(20 * math.log10(gain), 2),
                    "peakDbfs": round(20 * math.log10(max(abs(x) for x in result) / 32767), 2)}


def write_wav(path: Path, samples: array.array, rate: int):
    path.parent.mkdir(parents=True, exist_ok=True)
    output = array.array("h", samples)
    if sys.byteorder != "little":
        output.byteswap()
    temporary = path.with_suffix(".tmp.wav")
    with wave.open(str(temporary), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(rate)
        wav.writeframes(output.tobytes())
    temporary.replace(path)


def request_audio(seat: int, key: str, model: str, api_key: str, direct: bool, transcript=None):
    style = VOICES[seat]["style"] + COMMON_STYLE
    if key == "pair_j":
        style += "钩读汉语拼音 gōu，第一声。完整台词只有对钩两个字。不要念英文字母 K，不添加任何解释。"
    if transcript is not None:
        style += "逐句独立录音。每句之间严格停顿一秒半的完全安静，句内不停顿。不要连读相邻两句。"
    payload = {"contents": [{"role": "user", "parts": [
        {"text": transcript if transcript is not None else PHONETIC_TRANSCRIPTS.get(key, PHRASES[key]), "speech_metadata": {"style": style}}
    ]}], "generationConfig": {"responseModalities": ["AUDIO"],
                              "speechConfig": {"voiceConfig": {"voice": VOICES[seat]["name"]}}}}
    request = urllib.request.Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        data=json.dumps(payload, ensure_ascii=False).encode(),
        headers={"x-goog-api-key": api_key, "Content-Type": "application/json"},
    )
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({})) if direct else urllib.request.build_opener()
    for attempt in range(5):
        try:
            with opener.open(request, timeout=240) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            # Never print full upstream bodies: they can contain request information.
            body = json.loads(error.read()).get("error", {})
            violations = [v for detail in body.get("details", []) for v in detail.get("violations", [])]
            if any("PerDay" in v.get("quotaId", "") for v in violations):
                raise RuntimeError("Daily TTS request quota exhausted; resume after reset or choose another available dedicated TTS model") from None
            if error.code not in (429, 500, 502, 503, 504) or attempt == 4:
                raise RuntimeError(f"TTS HTTP {error.code}") from None
            wait = min(45, 4 * 2 ** attempt) + random.uniform(0, 2)
            print(f"retry seat={seat} key={key} status={error.code} wait={wait:.1f}s", flush=True)
            time.sleep(wait)
        except (TimeoutError, urllib.error.URLError):
            if attempt == 4:
                raise RuntimeError("TTS network failure after retries") from None
            time.sleep(min(30, 4 * 2 ** attempt))


def split_batch(samples: array.array, rate: int, expected: int, merge_gaps=()):
    """Split only verified long exterior pauses, never sentence-internal short pauses."""
    window = rate // 100
    levels = [rms(samples[i:i + window]) for i in range(0, len(samples), window)]
    threshold = max(50.0, max(levels) * 0.012)
    run = None
    gaps = []
    for index, value in enumerate(levels + [threshold * 2]):
        if value < threshold:
            if run is None:
                run = index
        elif run is not None:
            if index - run >= 50 and run > 0 and index < len(levels):
                gaps.append((run * window, index * window))
            run = None
    if merge_gaps:
        gaps = [gap for index, gap in enumerate(gaps) if index not in merge_gaps]
    if len(gaps) != expected - 1:
        raise ValueError(f"Batch needs {expected - 1} unambiguous phrase gaps; detected {len(gaps)}. Do not publish until reviewed.")
    boundaries = [0] + [(start + end) // 2 for start, end in gaps] + [len(samples)]
    return [(samples[boundaries[i]:boundaries[i + 1]], boundaries[i], boundaries[i + 1]) for i in range(expected)]


def describe(path: Path, seat: int, key: str) -> dict:
    with wave.open(str(path), "rb") as wav:
        channels, width, rate, frames = wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getnframes()
        pcm = array.array("h", wav.readframes(frames))
    if channels != 1 or width != 2 or rate != RATE or frames < RATE * 0.15:
        raise ValueError(f"Invalid clip {seat}/{key}")
    if sys.byteorder != "little":
        pcm.byteswap()
    peak = max(abs(x) for x in pcm)
    if peak < 300 or peak >= 32767:
        raise ValueError(f"Silent or clipped audio {seat}/{key}")
    duration = round(frames * 1000 / rate)
    # Long tails or accidental repeated speech need deliberate review.
    if duration > max(3500, len(PHRASES[key]) * 480 + 600):
        raise ValueError(f"Suspiciously long clip {seat}/{key}: {duration} ms")
    return {"path": f"voices/{seat}/{key}.wav", "text": PHRASES[key],
            "durationMs": duration, "sampleRate": rate, "channels": channels,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "peakDbfs": round(20 * math.log10(peak / 32767), 2)}


def build_catalog(output: Path, model: str):
    existing = output / "catalog.json"
    catalog = json.loads(existing.read_text()) if existing.exists() else {}
    voices = {}
    for seat in VOICES:
        voices[str(seat)] = {}
        for key in PHRASES:
            path = output / str(seat) / f"{key}.wav"
            if path.exists():
                item = describe(path, seat, key)
                previous = catalog.get("voices", {}).get(str(seat), {}).get(key, {})
                for preserved in ("mastering", "generationMode", "spokenText"):
                    if preserved in previous:
                        item[preserved] = previous[preserved]
                voices[str(seat)][key] = item
    catalog.update({"version": 1, "model": model, "language": "zh-CN", "source": "Google Gemini dedicated speech generation",
                    "generatedAt": catalog.get("generatedAt", datetime.datetime.now(datetime.timezone.utc).isoformat()),
                    "expectedKeys": list(PHRASES), "voices": voices,
                    "voiceMetadata": {str(seat): dict(profile, style=profile["style"] + COMMON_STYLE) for seat, profile in VOICES.items()},
                    "mastering": {"format": "PCM16 mono WAV", "sampleRate": RATE, "targetActiveRmsDbfs": -20,
                                  "peakCeilingDbfs": -1, "leadingHandleMs": 90, "trailingHandleMs": 150,
                                  "fadeMs": 4, "maximumGainDb": 9.54}})
    output.mkdir(parents=True, exist_ok=True)
    temporary = output / "catalog.tmp.json"
    temporary.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(existing)
    return catalog


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--seats", default="0,1,2")
    parser.add_argument("--keys", help="Comma-separated subset; default all 76 lines")
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--direct", action="store_true", help="Bypass configured HTTP proxies")
    parser.add_argument("--replace", action="store_true", help="Regenerate selected existing clips")
    parser.add_argument("--batch", action="store_true", help="One recording per seat with long pauses, strict segmentation, then mandatory transcript audit")
    parser.add_argument("--merge-gap", action="append", default=[], help="After independent transcription only: remove a sentence-internal gap, seat:index (zero-based gap index)")
    parser.add_argument("--sources", type=Path, default=Path("/private/tmp/doudizhu-voice-source"), help="Keep uncompressed source recordings outside the APK")
    parser.add_argument("--catalog-only", action="store_true", help="Validate existing WAVs and rebuild manifest without API requests")
    args = parser.parse_args()
    if args.catalog_only:
        catalog = build_catalog(args.output, args.model)
        counts = {seat: len(clips) for seat, clips in catalog["voices"].items()}
        print(json.dumps({"clipCounts": counts, "expectedPerSeat": len(PHRASES)}))
        return
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        parser.error("Set GEMINI_API_KEY in the environment")
    seats = [int(x) for x in args.seats.split(",")]
    keys = args.keys.split(",") if args.keys else list(PHRASES)
    if not all(seat in VOICES for seat in seats) or not all(key in PHRASES for key in keys):
        parser.error("Unknown seat or phrase key")
    if args.batch:
        mastering = {}
        sources = []
        merge_gaps = {}
        for override in args.merge_gap:
            seat, index = map(int, override.split(":"))
            merge_gaps.setdefault(seat, []).append(index)
        transcript = "。 <long pause> ".join(PHONETIC_TRANSCRIPTS.get(key, PHRASES[key]) for key in keys) + "。"
        for seat in seats:
            source = args.sources / f"seat-{seat}-{args.model}.wav"
            sidecar = source.with_suffix(".json")
            if source.exists() and sidecar.exists() and not args.replace:
                with wave.open(str(source), "rb") as wav:
                    rate = wav.getframerate()
                    samples = array.array("h", wav.readframes(wav.getnframes()))
                source_metadata = json.loads(sidecar.read_text())
                if source_metadata["keys"] != keys:
                    raise ValueError("Existing batch transcript differs; use a new source directory")
            else:
                print(f"Generating seat={seat} phrases={len(keys)} model={args.model}", flush=True)
                response = request_audio(seat, "batch", args.model, api_key, args.direct, transcript)
                samples, rate = samples_from_response(response)
                write_wav(source, samples, rate)
                source_metadata = {"seat": seat, "model": args.model, "keys": keys, "transcript": transcript,
                                   "style": VOICES[seat]["style"] + COMMON_STYLE + "逐句独立录音。每句之间严格停顿一秒半的完全安静，句内不停顿。不要连读相邻两句。",
                                   "voiceName": VOICES[seat]["name"], "usage": response.get("usageMetadata", {}),
                                   "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest()}
                sidecar.write_text(json.dumps(source_metadata, ensure_ascii=False, indent=2) + "\n")
            segments = split_batch(samples, rate, len(keys), merge_gaps.get(seat, []))
            for key, (clip, start, end) in zip(keys, segments):
                processed, report = master(clip, rate)
                path = args.output / str(seat) / f"{key}.wav"
                write_wav(path, processed, rate)
                metadata = describe(path, seat, key)
                report.update({"sourceStartMs": round(start * 1000 / rate), "sourceEndMs": round(end * 1000 / rate)})
                mastering[(seat, key)] = report
                print(f"seat={seat} key={key} durationMs={metadata['durationMs']}", flush=True)
            sources.append(source_metadata)
        catalog = build_catalog(args.output, args.model)
        for (seat, key), report in mastering.items():
            catalog["voices"][str(seat)][key]["mastering"] = report
        catalog["batchGeneration"] = {"pauseTag": "<long pause>", "minimumSplitSilenceMs": 500,
                                       "expectedPerSeat": len(keys), "recordings": sources,
                                       "reviewedInternalGaps": args.merge_gap,
                                       "transcriptAuditRequired": True}
        (args.output / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
        print(f"Generated and segmented {len(mastering)} clips; transcript audit required", flush=True)
        return
    tasks = [(seat, key) for seat in seats for key in keys
             if args.replace or not (args.output / str(seat) / f"{key}.wav").exists()]
    mastering = {}

    def generate(task):
        seat, key = task
        response = request_audio(seat, key, args.model, api_key, args.direct)
        samples, rate = samples_from_response(response)
        if rate != RATE:
            raise ValueError("Unexpected TTS sample rate")
        processed, report = master(samples, rate)
        path = args.output / str(seat) / f"{key}.wav"
        write_wav(path, processed, rate)
        metadata = describe(path, seat, key)
        return seat, key, metadata["durationMs"], report, response.get("usageMetadata", {})

    failures = []
    usage = {"promptTokenCount": 0, "candidatesTokenCount": 0, "totalTokenCount": 0}
    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, min(4, args.workers))) as executor:
        futures = {executor.submit(generate, task): task for task in tasks}
        for completed, future in enumerate(concurrent.futures.as_completed(futures), start=1):
            seat, key = futures[future]
            try:
                seat, key, duration, report, tokens = future.result()
                mastering[(seat, key)] = report
                for name in usage:
                    usage[name] += tokens.get(name, 0)
                print(f"{completed}/{len(tasks)} seat={seat} key={key} durationMs={duration}", flush=True)
            except Exception as error:
                failures.append({"seat": seat, "key": key, "error": str(error).replace(api_key, "[redacted]")})
                print(f"FAILED seat={seat} key={key}: {failures[-1]['error']}", flush=True)
    catalog = build_catalog(args.output, args.model)
    for (seat, key), report in mastering.items():
        catalog["voices"][str(seat)][key]["mastering"] = report
        catalog["voices"][str(seat)][key]["generationMode"] = "single"
        catalog["voices"][str(seat)][key]["spokenText"] = PHONETIC_TRANSCRIPTS.get(key, PHRASES[key])
    catalog["generationRuns"] = catalog.get("generationRuns", []) + [
        {"completedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "model": args.model,
         "requestedClips": len(tasks), "generatedClips": len(mastering), "usage": usage, "failures": failures,
         "clips": [{"seat": seat, "key": key, "spokenText": PHONETIC_TRANSCRIPTS.get(key, PHRASES[key])} for seat, key in tasks]}]
    (args.output / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"generated": len(mastering), "failed": len(failures), "usage": usage}), flush=True)
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
