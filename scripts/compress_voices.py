#!/usr/bin/env python3
"""Encode audited WAVs to compact AAC-LC, verify gapless decoding, update catalog.

Uses macOS afconvert/afinfo. Their system codec services may require running this
command outside a restricted process sandbox. Source WAVs are copied to --archive
before removal from application assets.
"""
import argparse
import array
import hashlib
import json
import math
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile

from generate_voices import OUTPUT, PHRASES, RATE, ROOT, write_wav


def read_pcm(path):
    """Read mono PCM16 including afconvert's WAVE_FORMAT_EXTENSIBLE header."""
    data = path.read_bytes()
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError("Expected RIFF WAV")
    offset = 12
    fmt = pcm = None
    while offset + 8 <= len(data):
        tag, length = struct.unpack_from("<4sI", data, offset)
        chunk = data[offset + 8:offset + 8 + length]
        if tag == b"fmt ":
            fmt = struct.unpack_from("<HHIIHH", chunk)
        elif tag == b"data":
            pcm = chunk
        offset += 8 + length + length % 2
    if not fmt or pcm is None or fmt[0] not in (1, 65534) or fmt[1] != 1 or fmt[2] != RATE or fmt[5] != 16:
        raise ValueError("Expected 24 kHz mono PCM16")
    return array.array("h", pcm)


def run(arguments):
    result = subprocess.run(arguments, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"Audio conversion failed ({result.returncode}): {result.stderr[:300]}")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=OUTPUT)
    parser.add_argument("--archive", type=Path, default=Path("/private/tmp/doudizhu-voice-source/clips"))
    parser.add_argument("--audit", type=Path, default=Path("/private/tmp/doudizhu-voice-audit.json"))
    args = parser.parse_args()
    catalog_path = args.source / "catalog.json"
    catalog = json.loads(catalog_path.read_text())
    audit = json.loads(args.audit.read_text())
    reviews = {(row["seat"], row["key"]): row for row in audit["clips"]}
    expected = [(seat, key) for seat in range(3) for key in PHRASES]
    if any(not reviews.get(pair, {}).get("accepted", False) for pair in expected):
        raise ValueError("Every clip must have an accepted independent transcript audit before publication")
    encoded_bytes = 0
    round_trip = []
    for seat, key in expected:
        original = args.source / str(seat) / f"{key}.wav"
        archived = args.archive / str(seat) / f"{key}.wav"
        if not original.exists():
            original = archived
        if hashlib.sha256(original.read_bytes()).hexdigest() != reviews[(seat, key)]["sha256"]:
            raise ValueError(f"Audit does not match source PCM: {seat}/{key}")
        destination = args.source / str(seat) / f"{key}.m4a"
        temporary = destination.with_suffix(".tmp.m4a")
        for bitrate in (48000, 64000, 80000, 96000):
            temporary.unlink(missing_ok=True)
            run(["/usr/bin/afconvert", str(original), str(temporary), "-f", "m4af", "-d", "aac", "-b", str(bitrate), "-c", "1", "-q", "127", "--no-filler"])
            info = run(["/usr/bin/afinfo", str(temporary)])
            if "aac" not in info or "1 ch,  24000 Hz" not in info:
                raise ValueError(f"Unexpected encoded format: {seat}/{key}")
            with tempfile.TemporaryDirectory(prefix="ddz-aac-") as temporary_dir:
                decoded = Path(temporary_dir) / "decoded.wav"
                run(["/usr/bin/afconvert", str(temporary), str(decoded), "-f", "WAVE", "-d", "LEI16"])
                input_pcm, output_pcm = read_pcm(original), read_pcm(decoded)
            if len(input_pcm) != len(output_pcm):
                raise ValueError(f"AAC lost or padded frames: {seat}/{key} {len(input_pcm)} != {len(output_pcm)}")
            noise = sum((int(x) - int(y)) ** 2 for x, y in zip(input_pcm, output_pcm))
            power = sum(int(x) ** 2 for x in input_pcm)
            snr = round(10 * math.log10(power / max(1, noise)), 2)
            if snr >= 12:
                break
        if snr < 12:
            raise ValueError(f"AAC round-trip SNR too low: {seat}/{key} {snr} dB")
        temporary.replace(destination)
        archived.parent.mkdir(parents=True, exist_ok=True)
        if original != archived:
            shutil.copy2(original, archived)
        metadata = catalog["voices"][str(seat)][key]
        metadata.update({"path": f"voices/{seat}/{key}.m4a", "codec": "AAC-LC", "bitrate": bitrate,
                         "durationMs": round(len(output_pcm) * 1000 / RATE),
                         "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
                         "pcmSha256": hashlib.sha256(archived.read_bytes()).hexdigest(),
                         "roundTripSnrDb": snr, "transcriptVerified": True})
        encoded_bytes += destination.stat().st_size
        round_trip.append(snr)
    catalog["mastering"]["format"] = "AAC-LC mono M4A"
    catalog["mastering"]["bitrate"] = 48000
    catalog["mastering"]["higherBitratesWhenRequired"] = [64000, 80000, 96000]
    catalog["batchGeneration"]["transcriptAuditComplete"] = True
    catalog["validation"] = {"transcriptAuditModel": audit["model"], "auditedClips": len(expected),
                              "allTranscriptsAccepted": True, "allRoundTripFrameCountsMatch": True,
                              "minimumRoundTripSnrDb": min(round_trip), "encodedBytes": encoded_bytes}
    temporary_catalog = catalog_path.with_suffix(".tmp.json")
    temporary_catalog.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
    temporary_catalog.replace(catalog_path)
    for seat, key in expected:
        (args.source / str(seat) / f"{key}.wav").unlink(missing_ok=True)
    previews = ROOT / "docs/audio"
    previews.mkdir(parents=True, exist_ok=True)
    for seat in range(3):
        pcm = array.array("h")
        for index, key in enumerate(("call", "pair_3", "rocket")):
            if index:
                pcm.extend([0] * (RATE // 5))
            phrase = read_pcm(args.archive / str(seat) / f"{key}.wav")
            # Internal preview boundaries already have 90/150 ms silent handles.
            # Remove those preserved handles to keep the actual inter-phrase gap
            # near 200 ms without touching spoken content.
            if index:
                phrase = phrase[int(RATE * 0.09):]
            if index < 2:
                phrase = phrase[:-int(RATE * 0.15)]
            pcm.extend(phrase)
        preview_source = args.archive.parent / f"voice-preview-{seat}.wav"
        write_wav(preview_source, pcm, RATE)
        run(["/usr/bin/afconvert", str(preview_source), str(previews / f"voice-preview-{seat}.m4a"),
             "-f", "m4af", "-d", "aac", "-b", "48000", "-c", "1", "-q", "127", "--no-filler"])
    print(json.dumps(catalog["validation"]))


if __name__ == "__main__":
    main()
