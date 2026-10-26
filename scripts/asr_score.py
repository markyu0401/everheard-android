#!/usr/bin/env python3
"""Score ASR JSONL fixtures with CER (Chinese) and WER (English).

Usage:
  python3 scripts/asr_score.py results.jsonl

Each line: {"expected": "...", "actual": "..."}
"""
import json
import re
import sys
from pathlib import Path


def edit_distance(a, b):
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(
                previous[j] + 1,
                current[j - 1] + 1,
                previous[j - 1] + (ca != cb),
            ))
        previous = current
    return previous[-1]


def tokens(text, language):
    text = text.strip()
    if language == "zh":
        return [c for c in text if not c.isspace()]
    return re.findall(r"[\w']+", text.lower())


def language_of(expected):
    return "zh" if any("\u4e00" <= c <= "\u9fff" for c in expected) else "en"


def main(path):
    lines = [line for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]
    if not lines:
        raise SystemExit("no fixtures")
    for index, line in enumerate(lines, 1):
        item = json.loads(line)
        expected = item["expected"]
        actual = item["actual"]
        lang = language_of(expected)
        ref = tokens(expected, lang)
        hyp = tokens(actual, lang)
        distance = edit_distance(ref, hyp)
        rate = distance / max(1, len(ref))
        print(f"{index}: {lang} {'CER' if lang == 'zh' else 'WER'}={rate:.1%} "
              f"distance={distance} ref={len(ref)} hyp={len(hyp)}")
        print(f"   expected: {expected}")
        print(f"   actual:   {actual}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: asr_score.py results.jsonl")
    main(sys.argv[1])
