#!/usr/bin/env python3
"""Independently transcribe each generated clip before shipping a batch voice pack."""
import argparse
import base64
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.request

from generate_voices import OUTPUT, PHRASES

MODEL = "gemini-3.1-flash-lite"


def normalize(text):
    text = re.sub(r"[\W_]", "", text).lower()
    return text.replace("钩", "勾").replace("沟", "勾").replace("凯", "k").replace("开", "k").replace("q", "圈").replace("a", "尖")


def accepted_variant(expected, transcript):
    expected, transcript = normalize(expected), normalize(transcript)
    if expected == transcript:
        return "exact or homophone spelling"
    if expected.replace("牌型", "排型") == transcript.replace("牌型", "排型"):
        return "homophone spelling: 牌型/排型"
    if expected[-1:] in ("啦", "了", "哦", "哟") and transcript[-1:] in ("啦", "了", "哦", "哟") and expected[:-1] == transcript[:-1]:
        return "expressive terminal particle; complete game phrase unchanged"
    return None


def audit(batch, source, api_key):
    instruction = (
        "You are auditing independently generated Mandarin Chinese game voice clips. "
        "Listen to EACH audio separately, in the exact provided order. The numeric IDs are arbitrary. "
        "Transcribe only words actually audible, in Chinese characters except English letter K. "
        "Do not infer or add words. Report every missing initial/final syllable, repeated word, "
        "extra neighboring phrase, or audible generation artifact. Return the exact transcript; "
        "clean means the voice is natural and complete, with no clipped syllable, repetition, or synthetic artifact. "
        "These clips include card ranks, game actions, instructions, and arbitrary system messages; "
        "do not reject a phrase based on its meaning. A single-syllable rank name is intentional. "
        "Return exactly ONE object per labeled audio file, even if the file contains two clauses or an internal pause. "
        f"There are exactly {len(batch)} audio files, with IDs 0 through {len(batch) - 1}. "
        "Use an empty issues string when nothing is wrong. Return JSON only."
    )
    parts = [{"text": instruction}]
    for index, (seat, key) in enumerate(batch):
        path = source / str(seat) / f"{key}.wav"
        parts.extend([{"text": f"Audio id {index}:"},
                      {"inlineData": {"mimeType": "audio/wav", "data": base64.b64encode(path.read_bytes()).decode()}}])
    payload = {"contents": [{"role": "user", "parts": parts}],
               "generationConfig": {"responseMimeType": "application/json", "temperature": 0,
                                    "responseSchema": {"type": "ARRAY", "items": {"type": "OBJECT", "properties": {
                                        "id": {"type": "INTEGER"}, "transcript": {"type": "STRING"},
                                        "clean": {"type": "BOOLEAN"}, "issues": {"type": "STRING"}},
                                        "required": ["id", "transcript", "clean", "issues"]}}}}
    if MODEL in ("gemini-3.8-flash", "gemini-3.7-flash"):
        payload["generationConfig"].pop("temperature")
        payload["generationConfig"]["thinkingConfig"] = {"thinkingLevel": "low"}
    request = urllib.request.Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent",
        data=json.dumps(payload).encode(), headers={"x-goog-api-key": api_key, "Content-Type": "application/json"})
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for attempt in range(4):
        try:
            data = json.load(opener.open(request, timeout=180))
            response = json.loads("".join(p.get("text", "") for p in data["candidates"][0]["content"]["parts"]))
            if len(response) != len(batch) or {row["id"] for row in response} != set(range(len(batch))):
                raise ValueError("Audit response omitted clip IDs")
            by_id = {row["id"]: row for row in response}
            rows = []
            for index, (seat, key) in enumerate(batch):
                row = by_id[index]
                expected = PHRASES[key]
                row.update({"seat": seat, "key": key, "expected": expected,
                            "auditModel": MODEL,
                            "transcriptMatch": normalize(expected) == normalize(row["transcript"]),
                            "sha256": hashlib.sha256((source / str(seat) / f"{key}.wav").read_bytes()).hexdigest()})
                reason = accepted_variant(expected, row["transcript"])
                row["accepted"] = row["clean"] and bool(reason)
                row["acceptanceReason"] = reason if row["accepted"] else None
                rows.append(row)
            return rows, data.get("usageMetadata", {})
        except urllib.error.HTTPError as error:
            body = json.loads(error.read()).get("error", {})
            violations = [v for detail in body.get("details", []) for v in detail.get("violations", [])]
            if any("PerDay" in v.get("quotaId", "") for v in violations):
                raise RuntimeError("Daily audio-audit quota exhausted; use another available audio-understanding model") from None
            if error.code not in (429, 500, 502, 503, 504) or attempt == 3:
                raise RuntimeError(f"Audit HTTP {error.code}") from None
            time.sleep(8 * (attempt + 1))
        except ValueError:
            if attempt == 3:
                raise
            time.sleep(1)


def main():
    global MODEL
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=OUTPUT)
    parser.add_argument("--report", type=Path, default=Path("/private/tmp/doudizhu-voice-audit.json"))
    parser.add_argument("--seats", default="0,1,2")
    parser.add_argument("--batch-size", type=int, default=12)
    parser.add_argument("--workers", type=int, default=2)
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--keys", help="Comma-separated subset to audit")
    parser.add_argument("--recheck", action="store_true", help="Re-audit selected existing clips")
    args = parser.parse_args()
    MODEL = args.model
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        parser.error("Set GEMINI_API_KEY in the environment")
    previous = json.loads(args.report.read_text()) if args.report.exists() else {"model": MODEL, "clips": [], "runs": []}
    reviewed = {(row["seat"], row["key"]): row for row in previous["clips"]}
    tasks = []
    for seat in map(int, args.seats.split(",")):
        for key in PHRASES:
            if args.keys and key not in args.keys.split(","):
                continue
            path = args.source / str(seat) / f"{key}.wav"
            if not path.exists():
                continue
            if args.recheck or reviewed.get((seat, key), {}).get("sha256") != hashlib.sha256(path.read_bytes()).hexdigest():
                tasks.append((seat, key))
    batches = [tasks[index:index + args.batch_size] for index in range(0, len(tasks), args.batch_size)]
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {executor.submit(audit, batch, args.source, api_key): batch for batch in batches}
        for future in concurrent.futures.as_completed(futures):
            rows, usage = future.result()
            previous["runs"].append({"clips": len(rows), "usage": usage})
            for row in rows:
                if (row["seat"], row["key"]) in reviewed and args.recheck:
                    row["previousPass"] = reviewed[(row["seat"], row["key"])]
                reviewed[(row["seat"], row["key"])] = row
                print(json.dumps({k: row[k] for k in ["seat", "key", "transcript", "clean", "transcriptMatch", "issues"]}, ensure_ascii=False), flush=True)
            previous["clips"] = sorted(reviewed.values(), key=lambda row: (row["seat"], list(PHRASES).index(row["key"])))
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(previous, ensure_ascii=False, indent=2) + "\n")
    suspects = [row for row in reviewed.values() if not row["clean"] or not row["transcriptMatch"]]
    print(json.dumps({"auditedClips": len(reviewed), "suspectCount": len(suspects),
                      "suspects": [{"seat": r["seat"], "key": r["key"], "heard": r["transcript"]} for r in suspects]}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
