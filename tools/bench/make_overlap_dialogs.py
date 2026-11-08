"""Two-person dialogues with interruptions built from REAL Russian speech, with competition-format references.

Phrases are cut with Silero VAD from the public test recordings shipped with sherpa-onnx models and transcribed with
GigaAM on the clean phrase (so cpWER/DER measure who-said-what, not recognition). Replies are placed one after another;
with probability overlap_prob a reply starts 0.3-0.9 s before the previous one ends (an interruption). Voices are
level-matched; one pair uses a pitch-shifted copy of the same speaker (similar voices, the hard case).

Setup: models/ holds silero_vad.onnx, campplus.onnx (3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx) and
the extracted sherpa-onnx-nemo-ctc-punct-giga-am-v3-russian-2025-12-16, sherpa-onnx-nemo-ctc-giga-am-russian-2024-10-24
and sherpa-onnx-small-zipformer-ru-2024-09-18 archives (for their test_wavs).

    pip install sherpa-onnx==2.0.1 numpy soundfile
    python3 tools/bench/make_overlap_dialogs.py models eval refs

Then run the app's pipeline on eval/ (SpeechPipelineJvmTest, see docs/TEST_RESULTS.md) and score with
tools/eval/evaluate.py --ref refs --hyp eval/out.
"""
import json
import random
import sys
from pathlib import Path

import numpy as np
import sherpa_onnx
import soundfile as sf

M = Path(sys.argv[1]); OUT = Path(sys.argv[2]); REF = Path(sys.argv[3]); OUT.mkdir(parents=True, exist_ok=True); REF.mkdir(parents=True, exist_ok=True)
SR = 16000
ASR = M / "sherpa-onnx-nemo-ctc-punct-giga-am-v3-russian-2025-12-16"
rec = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=str(ASR / "model.int8.onnx"), tokens=str(ASR / "tokens.txt"), num_threads=4)
ext_cfg = sherpa_onnx.SpeakerEmbeddingExtractorConfig(model=str(M / "campplus.onnx"), num_threads=4)
ext = sherpa_onnx.SpeakerEmbeddingExtractor(ext_cfg)


def load(p):
    x, sr = sf.read(p, dtype="float32")
    if x.ndim > 1: x = x.mean(1)
    assert sr == SR, (p, sr)
    return x


def text_of(x):
    s = rec.create_stream(); s.accept_waveform(SR, x); rec.decode_stream(s); return s.result.text.strip()


def emb(x):
    s = ext.create_stream(); s.accept_waveform(SR, x); s.input_finished(); e = np.array(ext.compute(s)); return e / np.linalg.norm(e)


def phrases(x, min_len=0.6):
    cfg = sherpa_onnx.VadModelConfig(); cfg.silero_vad.model = str(M / "silero_vad.onnx"); cfg.silero_vad.threshold = 0.5
    cfg.silero_vad.min_silence_duration = 0.25; cfg.silero_vad.min_speech_duration = 0.3; cfg.silero_vad.max_speech_duration = 6
    cfg.sample_rate = SR
    vad = sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=200); out = []
    for i in range(0, len(x), 512):
        vad.accept_waveform(x[i:i + 512])
        while not vad.empty(): out.append(np.array(vad.front.samples, "float32")); vad.pop()
    vad.flush()
    while not vad.empty(): out.append(np.array(vad.front.samples, "float32")); vad.pop()
    res = []
    for p in out:
        if len(p) < min_len * SR: continue
        t = text_of(p)
        if len(t) >= 2: res.append((p, t))
    return res


def pitch(x, semitones):
    """Pitch shift by resampling (also changes tempo slightly; fine for a 'different voice')."""
    f = 2 ** (semitones / 12)
    idx = np.arange(0, len(x) / f) * f
    return np.interp(idx, np.arange(len(x)), x).astype("float32")


src = {
    "long": load(M / "sherpa-onnx-nemo-ctc-giga-am-russian-2024-10-24/test_wavs/long_example.wav"),
    "ex": load(M / "sherpa-onnx-nemo-ctc-punct-giga-am-v3-russian-2025-12-16/test_wavs/example.wav"),
    "z0": load(M / "sherpa-onnx-small-zipformer-ru-2024-09-18/test_wavs/0.wav"),
    "z1": load(M / "sherpa-onnx-small-zipformer-ru-2024-09-18/test_wavs/1.wav"),
}

e = {k: emb(v) for k, v in src.items()}
print("speaker similarity between sources:", {f"{a}-{b}": round(float(e[a] @ e[b]), 2) for a in e for b in e if a < b})
ph = {k: phrases(v) for k, v in src.items()}
print({k: len(v) for k, v in ph.items()})


def build(name, A, B, seed, overlap_prob=0.6, n_turns=12):
    rnd = random.Random(seed)
    y = np.zeros(SR * 200, "float32"); pos = int(0.5 * SR); prev_end = pos; ref = []; prev_spk = None
    order = ["A" if i % 2 == 0 else "B" for i in range(n_turns)]
    ia = ib = 0
    for spk in order:
        if spk == "A": x, t = A[ia % len(A)]; ia += 1
        else: x, t = B[ib % len(B)]; ib += 1
        x = x / max(1e-6, np.sqrt((x ** 2).mean())) * 0.08                       # equal loudness
        if prev_spk and rnd.random() < overlap_prob:
            start = prev_end - int(rnd.uniform(0.3, 0.9) * SR)                   # interruption
        else:
            start = prev_end + int(rnd.choice([0.15, 0.3, 0.5]) * SR)
        y[start:start + len(x)] += x
        ref.append({"speaker_id": "speaker_1" if spk == "A" else "speaker_2", "start_ms": start * 1000 // SR,
                    "end_ms": (start + len(x)) * 1000 // SR, "text": t})
        prev_end = max(prev_end, start + len(x)); prev_spk = spk
    y = y[: prev_end + SR // 2]
    y = np.clip(y + np.random.default_rng(seed).normal(0, 0.002, len(y)).astype("float32"), -1, 1)
    sf.write(OUT / f"{name}.wav", y, SR, subtype="PCM_16")
    ref.sort(key=lambda r: r["start_ms"])
    (REF / f"{name}.json").write_text(json.dumps({"transcript": ref}, ensure_ascii=False, indent=1), encoding="utf-8")


longp = ph["long"]
build("real_long_z0", longp[:12], ph["z0"] + ph["z1"], 21)
build("real_long_ex", longp[12:24], ph["ex"], 22)
build("real_long_pitch", longp[24:36], [(pitch(x, 4), t) for x, t in longp[:8]], 23)      # similar voices
build("real_long_pitch_turns", longp[30:42], [(pitch(x, 4), t) for x, t in longp[8:16]], 24, overlap_prob=0.0)
print("ok")
