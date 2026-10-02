#!/usr/bin/env python3
"""Render the original offline music loop; requires Python 3.9+ and NumPy.

No recordings, downloaded samples, model output, or existing melodies are used.
The score and synthesizers below are the complete editable source. Encode with
macOS afconvert or ffmpeg; use --no-encode to render and inspect PCM only.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import wave

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/music/table_theme.m4a"
RATE = 44100
BPM = 114
BARS = 24
BEATS = BARS * 4
FRAMES = round(BEATS * 60 / BPM * RATE)
BEAT = FRAMES / (BEATS * RATE)
SEED = 20261003

# Each tuple is (beat in bar, MIDI pitch, note length in beats). Six original
# four-bar phrases bounce through a D-major pentatonic motif over
# D6/Bm7/Gadd9/Asus2, with short notes, upward skips, and offbeat answers.
PHRASES = [
    [
        [(0.10, 62, 0.35), (0.60, 66, 0.35), (1.10, 69, 0.55), (1.85, 71, 0.40), (2.60, 69, 0.35), (3.10, 66, 0.60)],
        [(0.10, 66, 0.35), (0.60, 71, 0.35), (1.10, 69, 0.65), (2.10, 66, 0.35), (2.60, 64, 0.35), (3.10, 62, 0.65)],
        [(0.10, 64, 0.40), (0.85, 66, 0.35), (1.35, 69, 0.55), (2.10, 71, 0.35), (2.60, 69, 0.40), (3.35, 66, 0.40)],
        [(0.10, 64, 0.35), (0.60, 69, 0.35), (1.10, 71, 0.65), (2.10, 69, 0.35), (2.60, 66, 0.40), (3.35, 64, 0.40)],
    ],
    [
        [(0.10, 69, 0.40), (0.85, 71, 0.35), (1.35, 74, 0.55), (2.10, 71, 0.35), (2.60, 74, 0.35), (3.10, 69, 0.65)],
        [(0.10, 71, 0.55), (0.85, 69, 0.40), (1.60, 66, 0.35), (2.10, 69, 0.35), (2.60, 71, 0.65)],
        [(0.10, 74, 0.40), (0.85, 71, 0.35), (1.35, 69, 0.55), (2.10, 66, 0.35), (2.60, 64, 0.35), (3.10, 66, 0.65)],
        [(0.10, 69, 0.40), (0.85, 66, 0.40), (1.60, 64, 0.35), (2.10, 66, 0.35), (2.60, 69, 0.65)],
    ],
    [
        [(0.10, 66, 0.35), (0.60, 69, 0.35), (1.10, 74, 0.55), (1.85, 76, 0.35), (2.35, 74, 0.35), (2.85, 69, 0.60)],
        [(0.10, 71, 0.35), (0.60, 74, 0.35), (1.10, 76, 0.55), (1.85, 74, 0.40), (2.60, 71, 0.35), (3.10, 69, 0.60)],
        [(0.10, 74, 0.40), (0.85, 71, 0.35), (1.35, 69, 0.55), (2.10, 71, 0.35), (2.60, 69, 0.35), (3.10, 66, 0.60)],
        [(0.10, 64, 0.35), (0.60, 66, 0.35), (1.10, 69, 0.55), (1.85, 71, 0.40), (2.60, 69, 0.65)],
    ],
    [
        [(0.10, 62, 0.35), (0.60, 66, 0.35), (1.10, 69, 0.55), (1.85, 74, 0.40), (2.60, 71, 0.35), (3.10, 69, 0.60)],
        [(0.10, 66, 0.40), (0.85, 71, 0.35), (1.35, 69, 0.55), (2.10, 66, 0.35), (2.60, 64, 0.35), (3.10, 66, 0.60)],
        [(0.10, 64, 0.35), (0.60, 66, 0.35), (1.10, 69, 0.55), (1.85, 71, 0.40), (2.60, 74, 0.35), (3.10, 71, 0.60)],
        [(0.10, 69, 0.40), (0.85, 71, 0.35), (1.35, 69, 0.55), (2.10, 66, 0.35), (2.60, 64, 0.65)],
    ],
    [
        [(0.10, 74, 0.40), (0.85, 71, 0.35), (1.35, 69, 0.55), (2.10, 66, 0.35), (2.60, 69, 0.35), (3.10, 74, 0.60)],
        [(0.10, 71, 0.40), (0.85, 74, 0.35), (1.35, 71, 0.55), (2.10, 69, 0.35), (2.60, 66, 0.65)],
        [(0.10, 69, 0.35), (0.60, 71, 0.35), (1.10, 74, 0.55), (1.85, 71, 0.40), (2.60, 69, 0.35), (3.10, 66, 0.60)],
        [(0.10, 64, 0.40), (0.85, 66, 0.35), (1.35, 69, 0.55), (2.10, 71, 0.35), (2.60, 69, 0.65)],
    ],
    [
        [(0.10, 69, 0.35), (0.60, 74, 0.35), (1.10, 76, 0.55), (1.85, 74, 0.40), (2.60, 71, 0.35), (3.10, 69, 0.60)],
        [(0.10, 71, 0.40), (0.85, 69, 0.35), (1.35, 66, 0.55), (2.10, 64, 0.35), (2.60, 66, 0.65)],
        [(0.10, 69, 0.35), (0.60, 66, 0.35), (1.10, 64, 0.55), (1.85, 66, 0.40), (2.60, 69, 0.35), (3.10, 71, 0.60)],
        [(0.10, 69, 0.40), (0.85, 66, 0.35), (1.35, 64, 0.55), (2.10, 66, 0.35), (2.60, 69, 0.55), (3.35, 64, 0.40)],
    ],
]
CHORDS = [(50, [57, 62, 66, 71]), (47, [54, 57, 62, 66]),
          (43, [55, 59, 62, 69]), (45, [57, 59, 64, 66])]


def hz(midi: int) -> float:
    return 440 * 2 ** ((midi - 69) / 12)


def envelope(t: np.ndarray, attack: float, decay: float) -> np.ndarray:
    # Every event starts and ends smoothly; long tails are folded into the loop.
    result = (1 - np.exp(-t / attack)) * np.exp(-t / decay)
    release = min(len(t), round(0.035 * RATE))
    result[-release:] *= np.linspace(1, 0, release) ** 2
    return result


def pluck(midi: int, length: float) -> np.ndarray:
    decay = 0.25 + length * 0.15
    t = np.arange(round((decay * 7 + 0.04) * RATE)) / RATE
    f = hz(midi)
    sound = np.zeros_like(t)
    # A short, bright string/wood-bar blend; the softened attack avoids sharp
    # clicks and the fast upper-harmonic decay keeps the melody comfortable.
    for harmonic, weight in [(1, 1.0), (2, 0.30), (3, 0.095), (4, 0.060)]:
        damping = np.exp(-t * (harmonic - 1) / 0.34)
        sound += weight * damping * np.sin(2 * np.pi * f * harmonic * t)
    sound += 0.028 * np.sin(2 * np.pi * f * 4.03 * t) * np.exp(-t / 0.050)
    sound += 0.025 * np.sin(2 * np.pi * f * 1.0018 * t) * np.exp(-t / 0.35)
    return sound * envelope(t, 0.006, decay)


def key(midi: int) -> np.ndarray:
    t = np.arange(round(2.7 * RATE)) / RATE
    f = hz(midi)
    sound = (np.sin(2 * np.pi * f * t) +
             0.12 * np.sin(2 * np.pi * f * 2 * t) * np.exp(-t / 0.9) +
             0.045 * np.sin(2 * np.pi * f * 3 * t) * np.exp(-t / 0.4))
    return sound * envelope(t, 0.012, 0.38)


def bass(midi: int, length: float = 1.0) -> np.ndarray:
    t = np.arange(round(2.4 * RATE)) / RATE
    f = hz(midi)
    sound = np.sin(2 * np.pi * f * t) + 0.13 * np.sin(2 * np.pi * f * 2 * t)
    return sound * envelope(t, 0.012, 0.20 + 0.10 * length)


def tap() -> np.ndarray:
    t = np.arange(round(0.35 * RATE)) / RATE
    phase = 2 * np.pi * (106 * t + 7 * 0.020 * (1 - np.exp(-t / 0.020)))
    return np.sin(phase) * envelope(t, 0.006, 0.045)


def brush(rng: np.random.Generator) -> np.ndarray:
    t = np.arange(round(0.22 * RATE)) / RATE
    noise = rng.normal(size=len(t))
    # Moving-average filtering removes sharp high frequencies from the brush.
    noise = np.convolve(noise, np.ones(19) / 19, mode="same")
    return noise * envelope(t, 0.010, 0.030)


def add(mix: np.ndarray, mono: np.ndarray, beat: float, gain: float, pan: float) -> None:
    start = round(beat * BEAT * RATE) % FRAMES
    stereo = mono[:, None] * gain * np.array([
        math.cos((pan + 1) * math.pi / 4), math.sin((pan + 1) * math.pi / 4)
    ])
    end = start + len(stereo)
    if end <= FRAMES:
        mix[start:end] += stereo
    else:
        count = FRAMES - start
        mix[start:] += stereo[:count]
        mix[:len(stereo) - count] += stereo[count:]


def render() -> np.ndarray:
    rng = np.random.default_rng(SEED)
    mix = np.zeros((FRAMES, 2), dtype=np.float64)
    for bar in range(BARS):
        start = bar * 4
        root, chord = CHORDS[bar % 4]
        # Short offbeat chord answers and root/fifth bass create a light bounce.
        for beat, chord_gain in [(0.52, 0.052), (1.52, 0.035), (2.52, 0.052), (3.52, 0.035)]:
            for position, note in enumerate(chord[1:]):
                add(mix, key(note), start + beat + position * 0.018,
                    chord_gain * (0.92 + rng.random() * 0.12), -0.28 + position * 0.18)
        add(mix, bass(root - 12), start, 0.25, 0)
        add(mix, bass(root - 5, 0.7), start + 1.04, 0.14, 0)
        add(mix, bass(root - 12), start + 2.02, 0.20, 0)
        add(mix, bass(root - 5, 0.7), start + 3.04, 0.14, 0)
        phrase = PHRASES[bar // 4][bar % 4]
        for beat, note, length in phrase:
            velocity = 0.22 * (0.88 + rng.random() * 0.16)
            add(mix, pluck(note, length), start + beat, velocity, -0.12)
        # Soft hand taps and regular brush upbeats support the lively melody.
        add(mix, tap(), start + 0.02, 0.048, 0.03)
        add(mix, tap(), start + 2.02, 0.036, -0.03)
        for beat in [0.53, 1.53, 2.53, 3.53]:
            add(mix, brush(rng), start + beat, 0.035 if beat in (1.53, 3.53) else 0.024, 0.20)
        if bar % 4 in (1, 3):
            add(mix, brush(rng), start + 3.80, 0.015, -0.20)

    # Circular room reflections preserve the previous phrase across the seam.
    dry = mix.copy()
    for seconds, gain, swap in [(0.109, 0.060, False), (0.241, 0.035, True),
                                (0.367, 0.025, False), (0.523, 0.017, True)]:
        reflection = np.roll(dry, round(seconds * RATE), axis=0)
        mix += gain * (reflection[:, ::-1] if swap else reflection)
    mix -= np.mean(mix, axis=0)
    # Conservative source levels complement quiet app playback beneath voices.
    gain = min(10 ** (-22 / 20) / np.sqrt(np.mean(mix ** 2)),
               10 ** (-9 / 20) / np.max(np.abs(mix)))
    return mix * gain


def analysis(samples: np.ndarray) -> dict:
    rms = float(np.sqrt(np.mean(samples ** 2)))
    peak = float(np.max(np.abs(samples)))
    steps = np.diff(samples, axis=0)
    seam = float(np.max(np.abs(samples[0] - samples[-1])))
    return {
        "frames": len(samples), "durationSeconds": round(len(samples) / RATE, 6),
        "rmsDbfs": round(20 * math.log10(max(rms, 1e-12)), 3),
        "peakDbfs": round(20 * math.log10(max(peak, 1e-12)), 3),
        "clippedSamples": int(np.count_nonzero(np.abs(samples) >= 1)),
        "seamStepDbfs": round(20 * math.log10(max(seam, 1e-12)), 3),
        "maximumStepDbfs": round(20 * math.log10(max(float(np.max(np.abs(steps))), 1e-12)), 3),
    }


def write_wav(path: Path, samples: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = np.round(np.clip(samples, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(pcm.tobytes())


def read_wav(path: Path) -> np.ndarray:
    # afconvert may write WAVE_FORMAT_EXTENSIBLE, unsupported by Python 3.9 wave.
    data = path.read_bytes()
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError("Expected RIFF WAV")
    offset, fmt, pcm = 12, None, None
    while offset + 8 <= len(data):
        tag, length = struct.unpack_from("<4sI", data, offset)
        chunk = data[offset + 8:offset + 8 + length]
        if tag == b"fmt ":
            fmt = struct.unpack_from("<HHIIHH", chunk)
        elif tag == b"data":
            pcm = chunk
        offset += 8 + length + length % 2
    if not fmt or pcm is None or fmt[0] not in (1, 65534) or fmt[1] != 2 or fmt[2] != RATE or fmt[5] != 16:
        raise ValueError("Expected 44.1 kHz stereo PCM16")
    return np.frombuffer(pcm, dtype="<i2").reshape(-1, 2).astype(np.float64) / 32768


def run(arguments: list[str]) -> None:
    result = subprocess.run(arguments, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"Audio conversion failed ({result.returncode}): {result.stderr[:500]}")


def encode(source: Path, destination: Path, decoded: Path) -> str:
    if shutil.which("afconvert"):
        run(["afconvert", str(source), str(destination), "-f", "m4af", "-d", "aac",
             "-b", "96000", "-c", "2", "-q", "127", "--no-filler"])
        run(["afconvert", str(destination), str(decoded), "-f", "WAVE", "-d", "LEI16"])
        return "afconvert"
    if shutil.which("ffmpeg"):
        run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
             "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart", str(destination)])
        run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(destination),
             "-c:a", "pcm_s16le", str(decoded)])
        return "ffmpeg"
    raise RuntimeError("AAC encoding requires afconvert (macOS) or ffmpeg")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--wav-output", type=Path, help="Optional lossless audition/archive")
    parser.add_argument("--no-encode", action="store_true", help="Render PCM only; requires --wav-output")
    args = parser.parse_args()
    if args.no_encode and not args.wav_output:
        parser.error("--no-encode requires --wav-output")
    samples = render()
    source_stats = analysis(samples)
    if source_stats["clippedSamples"] or source_stats["seamStepDbfs"] > -55:
        raise ValueError(f"Unexpected source audio or loop seam: {source_stats}")
    if args.wav_output:
        write_wav(args.wav_output, samples)
    if args.no_encode:
        print(json.dumps(source_stats, indent=2))
        return
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="ddz-music-") as temp:
        source = Path(temp) / "source.wav"
        decoded = Path(temp) / "decoded.wav"
        temporary_output = Path(temp) / "table_theme.m4a"
        write_wav(source, samples)
        encoder = encode(source, temporary_output, decoded)
        decoded_samples = read_wav(decoded)
        decoded_stats = analysis(decoded_samples)
        # Reject encoders/decoders that expose AAC packet padding instead of
        # preserving the exact loop length recorded in the M4A trim metadata.
        if len(decoded_samples) != FRAMES:
            raise ValueError(f"Unexpected AAC duration: {decoded_stats}")
        if decoded_stats["clippedSamples"] or decoded_stats["peakDbfs"] > -6:
            raise ValueError(f"Unexpected AAC peak: {decoded_stats}")
        if decoded_stats["seamStepDbfs"] > -48:
            raise ValueError(f"Unexpected AAC loop seam: {decoded_stats}")
        encoded = temporary_output.read_bytes()
        args.output.write_bytes(encoded)
        manifest = {
            "title": "欢乐牌桌", "path": f"music/{args.output.name}",
            "style": "Cheerful pentatonic plucked strings/soft marimba, bouncing bass and light percussion",
            "composition": "Original score and procedural synthesis in scripts/generate_music.py",
            "provenance": "Original project asset; no third-party music, recordings, or samples",
            "seed": SEED, "bpm": BPM, "bars": BARS, "sampleRate": RATE,
            "channels": 2, "codec": "AAC-LC", "targetBitrate": 96000,
            "encoder": encoder, "encodedBytes": len(encoded),
            "sha256": hashlib.sha256(encoded).hexdigest(),
            "pcmSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
            "source": source_stats, "decoded": decoded_stats,
            "roundTripSnrDb": round(10 * math.log10(
                float(np.sum(samples ** 2)) /
                max(float(np.sum((samples - decoded_samples[:FRAMES]) ** 2)), 1e-12)), 3),
        }
        args.output.with_name("catalog.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
