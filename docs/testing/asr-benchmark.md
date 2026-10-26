# Real-device ASR benchmark

Purpose: replace TTS-only confidence with measurements from the actual phone,
microphone, room, accent, and language mix. Run this after every ASR/model or
segmentation change.

## Fixtures

Read each line naturally, once. Do not whisper or exaggerate.

| # | Language | Text |
|---|---|---|
| 1 | Chinese | 春眠不觉晓，处处闻啼鸟。夜来风雨声，花落知多少。 |
| 2 | Chinese | 今天天气很好，我们下午三点开会。 |
| 3 | Chinese | 我明天要去实验室，十点到。 |
| 4 | Chinese | 这个项目的截止日期是下周五。 |
| 5 | English | This is a test. We will meet at three tomorrow. |
| 6 | English | I need to submit the report by Friday. |
| 7 | Mixed | 明天下午三点我们开个 meeting，讨论 project timeline。 |

## Procedure

1. Install the current debug APK and open **Transcripts** once to confirm the
   app launches. Old sessions may contain the previous model's output; do not
   use them as a baseline.
2. Open **Settings** → Appearance = System (or the mode you will actually use);
   Language = English or 简体中文 as appropriate. ASR language is automatic.
3. Return Home → **Start capture**. Wait until the status says **Listening**.
4. Read fixture #1 once, pause one second, then read #2. Stop capture.
5. Open **Transcripts** → newest session. Copy the text verbatim into a results
   file. Repeat for the remaining fixtures, one capture per fixture, so a bad
   segment does not contaminate the next.
6. Repeat the Chinese fixtures at a normal distance and, if possible, at ~3 m
   and with background noise (fan, café, TV). Record which condition was used.
7. For Live: open **Live transcript**, speak fixture #2, stop, wait for
   “Refining the transcript…”, then save. Compare the saved text.

## Collect diagnostics

The capture transcriber logs only segment boundaries and text lengths, never the
transcript text:

```bash
adb -s <phone-tailscale-ip>:<adb-port> logcat -d -s CaptureTranscriber
```

Look for:

- `ASR model loaded: sherpa-onnx-sense-voice-...` — confirms SenseVoice, not the
  streaming fallback.
- `segment <start>-<end> samples=<n> textLength=<n>` — confirms the VAD did not
  clip the start and that the segment is not tiny.
- `merged segment into previous utterance` — confirms adjacent segments were
  coalesced.

If the first syllable is missing, check whether the segment starts near 0 ms. If
it starts at 300–500 ms, the pre-roll is not reaching the word onset.

## Scoring

Use character error rate (CER) for Chinese and word error rate (WER) for English.
A small script is in `scripts/asr_score.py`; it accepts JSON lines:

```json
{"expected":"今天天气很好，我们下午三点开会。","actual":"今天天气很好，我们下午3点开会。"}
```

```bash
python3 scripts/asr_score.py results.jsonl
```

Targets for clear, close speech: Chinese CER < 10%, English WER < 10%. These are
targets, not claims; report the measured numbers with the device, distance, and
noise condition.

## What to fix from the results

| Symptom | Likely cause | Action |
|---|---|---|
| First word missing | VAD latency / pre-roll | Increase pre-roll, lower VAD threshold |
| One sentence split into many rows | 600 ms end silence | Merge adjacent utterances; raise endSilence |
| Correct words, wrong characters | model/domain | Hotwords, larger model, cloud re-decode |
| English words in Chinese text | code-switch | Keep SenseVoice auto language; add hotwords |
| Silence produces text | no VAD gate | Gate final ASR on VAD-confirmed speech |
| Very slow first result | model load | Preload after start; show a loading state |
