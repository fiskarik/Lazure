# Lazure — журнал разработки

> Хронологический журнал этапов, замеров и найденных причин ошибок. Записи сохранены в том виде, в каком делались,
> поэтому ранние разделы описывают состояние до появления ASR. Актуальное состояние — в [README](../README.md),
> [ARCHITECTURE](ARCHITECTURE.md) и [LIMITATIONS](LIMITATIONS.md). После очистки репозитория (последний раздел)
> `speech-decoder/scripts` и `speech-decoder/models` переехали в `tools/models`, `speech-decoder/bench` — в `tools/bench`.

Updated: 2026-11-07

## Product constraints

- Keep the application name **Lazure** and application ID `ru.myessentiality.essential`.
- Extend the existing Compose, Material 3, Hilt, Room, DataStore and LiteRT-LM architecture. Preserve the current Mont typography and cyan/neutral color kit unless a small token change is needed for accessible contrast.
- Never substitute mock transcript text or speakers for model inference. The app must disclose missing local models.
- All speech and language inference must remain on-device. INTERNET is permitted only for optional local model downloads; the redesigned AI chat does not expose cloud inference.

## Stage 0 — audit

**Status: Completed.**

- Gradle has one Android application module `:app` at `src/mobile/android`, minSdk 24 / compileSdk 36, Kotlin/Java, Compose Material 3, Navigation Compose, Hilt, Room 2.7, DataStore, LiteRT-LM, CMake/JNI.
- Entry point: `MainActivity` → `EssentialApp`; existing app navigation covers onboarding, note/chat home, graph, editor, and settings.
- Theme already supports light/dark, Android dynamic color, edge-to-edge system bars, Mont and Oddval fonts, and project-specific neutral/cyan tokens.
- Room `essential.db` version 7 holds note indexing, markdown notes, wiki edges, chat sessions/messages, recordings, and transcript segments. Migrations preserve existing data; debug destructive fallback was removed.
- Existing local LLM integration is in `data/llm` and `ChatEngine`; the transcript pipeline does not yet use it.
- Current audio feature only imports/copies local files, stores duration/status, plays/deletes files, and exports structurally valid empty JSON. No mic capture, ASR runtime/assets, diarization, transcript editor, summary or recording RAG exists.
- No ASR or speaker model is bundled in app assets/dependencies. A model/runtime and redistribution license need verification before inference work can be marked complete.
- Baseline `./gradlew :app:assembleDebug` passed on 2026-11-07. Latest connected Android instrumentation passed 3 tests on the ARM64 `processing-phone` emulator; 41 unit tests passed.

## Stages 1–9

| Stage | Status | Current verified state / remaining work |
|---|---|---|
| 1. Architecture and storage | Completed | Room v8 adds recording model selections/updatedAt, speakers, summaries, per-recording chat sessions, and 7→8 migration. Added Room transcript repository, validated segment replacement, export/model/decoder/VAD/ASR/diarization/recorder/coordinator contracts, and tested state transitions. |
| 2. Offline ML pipeline | Blocked / not implemented | Audited Android-compatible offline candidate(s), but no selected runtime/model dependency or licensed weights are bundled yet. Import is real; speech inference is not. |
| 3. Main UI | Partial | Added a four-tab Home / Vault / AI / Settings shell, branded Home dashboard, adaptive action grid, recent Room recordings, direct Import picker, Vault search/filter/sort, rename/delete confirmation, audio playback, transcript segment seeking/editing, speaker renaming and exact-shape JSON export. Missing retry/reprocess, waveform/playback progress, current-segment highlight, and Summary/Ask AI recording actions. Existing Notes workspace is reachable from Home. |
| 4. Live recording | Partial | Microphone permission flow, AAC/M4A capture, pause/resume (API 24+), timer, local file registration in Room and Vault handoff work in the foreground screen. No foreground service or process-death recovery yet; transcription stays unavailable without the speech model. |
| 5. AI models | Partial | Restored the LiteRT-LM preset catalog with resumable download, selection and removal. ASR/diarization models and engines are still missing. |
| 6. AI Summary | Not implemented | Add persisted summary generation through local LLM after transcript availability. |
| 7. AI Chat and RAG | Not implemented for recordings | Reuse existing ChatEngine and local retrieval; isolate sessions and sources by recording. |
| 8. Polish and performance | Not started | Accessibility, small/wide layouts, offline/error states, memory, long audio. |
| 9. Release readiness | Partial | Build, unit and emulator tests exist; no model license dossier, evaluation set, inference benchmark, or full audio user journey. |

## Next actions

1. Select and integrate an Android ARM64 offline ASR/diarization runtime, acquire/verify redistributable model artifacts and implement a real end-to-end transcript pass. Candidate review was documented in `docs/TRANSCRIPTION_STATUS.md` (removed once the pipeline was implemented).
2. Move microphone capture into a foreground service with persistent notification and recovery after lifecycle/process interruption, then exercise capture with a real microphone.
3. Finish Vault actions (rename/delete confirmation, sort and retry/reprocess), transcript editing and speaker metadata controls; then implement persisted summaries and recording-scoped chat/RAG.

## Last verification

- `./gradlew :app:assembleDebug` — passed.
- `./gradlew :app:testDebugUnitTest` — passed, 41 tests.
- `./gradlew :app:connectedDebugAndroidTest` — passed, 3 tests on ARM64 emulator.
- After stage 1: `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` passed; Android instrumentation passed 4 tests, including 7→8 table/data preservation.
- Current UI/recording pass: `./gradlew :app:assembleDebug` passed; `./gradlew :app:testDebugUnitTest` passed (44 tests); `./gradlew :app:connectedDebugAndroidTest` passed (4 tests on processing-phone API 33).
- Latest APK installed and opened with `adb shell am start -n ru.myessentiality.essential/com.rassvet.essential.MainActivity`. No real audio import or microphone recording was performed in this verification.

## Stage 3 — main UI report

**Status: Partial.** The app now opens to a branded Lazure workspace using the existing Mont typography, cyan/neutral theme, Material 3 shapes and edge-to-edge handling. Home actions route to local recording, SAF import, model catalog and Vault. The bottom bar contains Home, Vault, AI and Settings. Recent recordings are read from Room. The old notes workspace remains reachable from Home.

Vault supports SAF import, Room-backed search/status filters, date/title sorting, rename and confirmed delete, audio playback, transcript segment seek, text correction with originalText preservation, speaker display-name edits and exact-schema JSON export. Retry/reprocess and waveform/current-segment highlighting remain. AI still uses the existing general ChatScreen; no recording-scoped chat or summary is claimed.

## Stage 4 — live capture report

**Status: Partial.** Live requests `RECORD_AUDIO`, records local AAC in an M4A file, shows elapsed time, supports pause/resume on Android N+ and stop/save, then registers the audio as an `awaiting_asr` Room recording and returns to Vault. The same `recordings/` app-private directory is used by imports and live audio. Capture remains owned by the Compose screen: leaving the screen or process death can interrupt or lose an unfinished capture. A foreground service and on-device microphone exercise are still required.

## Frontend consistency update — 2026-11-07

Latest requested UI adjustment: Live now uses the same neutral card treatment as the other Home actions; the global microphone FAB has been removed. The static light/dark Material 3 palettes explicitly define primary, secondary, tertiary, container, inverse and surface roles around #00CED1 and turquoise shades, removing inherited default purple roles. Android Dynamic Color continues to follow the system palette when enabled. `./gradlew :app:assembleDebug` passed after this adjustment; no device visual check was performed.

**Status: Completed for this design pass; overall product remains partial.**

- Removed Spanish locale resources and its Settings option; RU and EN remain.
- Removed the old visible Essential logo treatment from splash and the retained notes chat placeholder. The launcher name and product wordmark remain Lazure; package/application ID stay stable for upgrade compatibility.
- Made Android Dynamic Color the default and consolidated Material 3 shapes with larger 12–32 dp rounded tokens. Home action cards, settings surfaces, chat bubbles, input and the floating bottom navigation now use the same palette and shape system.
- Reworked the AI chat into a local-only conversation screen with an empty state, prompt chips, rounded message cards and a direct link to model selection. Chat requests force local mode.
- Returned the existing downloadable LiteRT-LM catalog in place of manual `.litertlm` file import on the model screen. Fixed model catalog options so downloadable presets expose their install action. Selection, resumable downloads and deletion remain backed by the existing downloader.
- Added the Android INTERNET permission only because catalog downloads need it. Inference remains local; documentation now records this boundary.

**Verification:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed; the current unit-test XML reports 44 tests, 0 failures/errors. The merged debug manifest contains INTERNET. Emulator launch was attempted, but this host has no connected device and the `processing-phone` AVD exits with `Incompatible processor ... requires ... neon`; this pass was not visually exercised on-device.

**Next:** Resume concrete offline ASR and diarization implementation; then exercise the UI on an ARM64 device/emulator and validate model download and microphone flows. ASR, diarization, recording-scoped summary and RAG remain incomplete.

## Settings and PIN update — 2026-11-07

- Removed Account/subscription and Privacy/Policy/Terms blocks from Settings, including the account refresh effect.
- Changed user-facing storage/repository naming to Storage in onboarding, settings and Home/navigation. Internal database paths and repository class names stay compatible.
- Added `PinRepository` with DataStore persistence, a random salt and HMAC-SHA256 verifier using a non-exportable Android Keystore key. The PIN itself is never persisted. Five incorrect attempts impose a persisted 30-second cooldown.
- Added Material 3 PIN setup/confirmation (4 or 6 ASCII digits), unlock, and disabling with the existing PIN. `PinGuard` blocks initial content until settings load and authentication succeeds, and locks again on activity stop. Screen contents are protected against screenshots/task snapshots while PIN is enabled. PIN is an application access lock, not file encryption.
- PIN controls and dialogs use the shared typography, rounded shapes and theme colors. Legacy chrome now uses the active Material color scheme for dark mode as well as light mode.
- Added repository tests for both PIN lengths, persistence without plaintext, rejection of wrong codes, disabling, overwrite prevention, cooldown and failure reset. Device validation of Android Keystore and lifecycle/UI remains required.

Verification for this update: `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed (50 unit tests, including 6 PIN tests; 0 failures/errors). The repository tests use a test HMAC key and a real temporary DataStore; they do not establish Android Keystore/device behavior. No new device visual or lifecycle test is claimed.

## Home sizing, chat IME and motion correction — 2026-11-07

- Home actions now use two equal-weight columns and an explicit 156 dp height for all four cards, replacing the nested adaptive lazy grid.
- The root Scaffold owns IME padding and hides bottom navigation while the keyboard is open. Its padding is consumed before child screens lay out, preventing duplicate bottom insets; the chat composer sits above the keyboard.
- Bounded the chat model selector to 152 dp, shortened its label and limited the heading to one line to prevent toolbar collisions. The empty state can scroll on short viewports. Removed the multiline hint above the input.
- Navigation uses short 120/90 ms fades instead of full-screen slides. Streaming reply publication is capped to approximately 20 UI updates per second, and the thinking indicator no longer rotates animated text.
- Static light-theme accent is now #007F82 with white foreground; dark theme uses #41B7BA with a dark foreground. Dynamic Color remains available. Saved Dynamic Color choice is no longer overwritten on every launch.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` passed (50 tests). Keyboard geometry and frame smoothness still require device validation; no FPS/device visual result is claimed. Updated installable APK is placed in `dist/Lazure-debug.apk`.

## PIN keypad and compact Home cards — 2026-11-07

Replaced the PIN text field with a Material 3 numeric keypad (0–9, backspace), masked digit indicators and explicit confirmation. Setup, repeated entry, unlocking and disabling share this UI and the existing PIN verifier; the system keyboard is not needed. The form scrolls on short screens, keypad buttons have 54 dp touch height, and accessibility labels expose entered-digit count without exposing the PIN. Home action cards now have equal 140 dp height (previously 156 dp). `./gradlew :app:assembleDebug` passed; no device visual check is claimed. Updated APK: `dist/Lazure-debug.apk`.

Splash branding adjustment: replaced the microphone and its rounded background with the actual application logo from `ic_splash_logo`, retaining the entrance scale animation and theme-aware contrast. `./gradlew :app:assembleDebug` passed; `dist/Lazure-debug.apk` updated.

## PIN length-aware automatic unlock — 2026-11-07

- PIN setup now saves digit count alongside the salted, Keystore-keyed verifier. Login renders exactly 4 or 6 dots and automatically verifies when the last digit is entered; the login/disable forms have no confirmation button. Setup retains its deliberate repeat/confirmation step.
- Existing PINs without length metadata have a one-time 4/6 length selector. A successful verification persists the length; a failed attempt does not migrate metadata or bypass cooldown. No PIN reset or change is required.
- Keypad buttons are true circles, up to 80 dp in diameter, with 12 dp spacing, responsive sizing and shared Material theme colors/typography. A reserved status area prevents layout jumps while checking or reporting errors.
- Background completion cannot mark the app unlocked while the activity is stopped.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed: 51 tests, 0 failures/errors. Tests cover both persisted lengths and legacy length migration; no device UI verification is claimed. Updated signed debug APK: `dist/Lazure-debug.apk`.

## Retiring Essential notes and light-card contrast — 2026-11-07

Removed notes, markdown editor, graph, legacy note-folder onboarding and note-storage management destinations from the live app navigation. New installs open the audio workspace without selecting/creating a notes folder. Removed Home notes entry and notes-specific settings. The AI chat no longer passes any note storage URI or enables note retrieval; its starter prompt and system identity describe conversations rather than note creation. Legacy Room note tables and shared rendering/retrieval code are retained for data compatibility; user files were not deleted.

Static light theme now has a distinct tinted `surfaceContainerLow` and stronger outline tokens. Home action and recent-recording cards have a consistent 1 dp theme-aware border, so they remain visible with Dynamic Color disabled. `./gradlew :app:assembleDebug :app:testDebugUnitTest` passed (51 tests). Device visual validation remains outstanding. Updated installable APK: `dist/Lazure-debug.apk`.

## Full UI/UX polish — 2026-11-07

**Status: Completed for the requested UI scope.** Existing Compose screens remain in use. Home action cards have a 116 dp minimum instead of 140 dp (about 17% smaller); recording/import use primary containers, while models/archive use neutral surfaces. At narrow widths with large fonts they reflow to one column rather than clipping words. Removed the duplicate workspace headline and compacted the empty state with a real Start Recording action.

Replaced the floating capsule with standard inset-aware Material 3 NavigationBar. Added 34 bundled official Material Symbols Rounded and their Apache 2.0 license, a coherent cool-neutral/turquoise light/dark palette and shared 24/18/13–14 sp typography. Localized active Storage labels as Архив / Archive and model descriptions/download units. Chat header and setting-value layout handle large text without competing horizontal labels. No audio/ML/PIN verification behavior was added or changed by this UI pass.

Verification: `:app:assembleDebug` and 59 local tests passed, including seven Compose UI tests and seven contrast-pair checks in one test. `:app:connectedDebugAndroidTest` passed nine tests on processing-phone ARM64 Android 13/API 33: four small-viewport/theme/font UI cases plus a real MainActivity navigation smoke test and existing DB/package tests. MainActivity was checked through Home, Archive, AI, Settings, PIN setup, model catalog and recording screen. Screenshots are saved under `docs/ui-polish/` and were visually inspected. No physical-device, audio-capture or inference-quality claim is made by these UI checks.

Full changed-file list, screenshot provenance and verification scope: `docs/UI_POLISH_REPORT.md`. Updated installable signed debug APK: `dist/Lazure-debug.apk`.


## Stage 2 update — real offline speech pipeline (2026-11-07)

**Status: Partial → working end to end on the ARM64 emulator.**

- Model set is declared in `speech-decoder/models/manifest.json`; `speech-decoder/scripts/fetch_models.sh` downloads it into `assets/models` and `jniLibs` (sherpa-onnx 2.0.1 runtime = ONNX Runtime, arm64-v8a + x86_64).
- Pipeline (`data/recording/SpeechPipeline.kt`): MediaCodec decode → mono 16 kHz resample (down and up) → Silero VAD → GigaAM v3 CTC (Russian, with punctuation) per VAD segment → pyannote-segmentation-3.0 + CAM++ embeddings clustering → per-token speaker split and timestamp alignment → Room segments/speakers.
- `RecordingProcessor` is the single queue for Import, Live and Reprocess; interrupted jobs are marked failed on next launch; Vault detail has progress bar and Reprocess.
- Chat microphone now uses on-device GigaAM (`LocalDictation`) instead of the system recognizer.
- Verified on emulator: Russian 8 kHz call clip → "Сейчас к тебе приедет бригада, давай" / "Давай, я жду" in 1.4 s; two-speaker English clip → 2 speakers (Russian model, so text is garbage by design); in-memory Room E2E (register → process → exportJson) passed.
- Not done: DTLN denoise, MossFormer separation and ECAPA from speech-decoder (no verified ONNX exports; CAM++ is the embedding stand-in), WER/DER/RTF on a labelled set, real microphone test, Summary / Ask-AI on recordings, foreground recording service. No accuracy numbers are claimed.
- Re-check: `adb shell am instrument -w -e class com.rassvet.essential.data.recording.SpeechPipelineDeviceTest ru.myessentiality.essential.test/androidx.test.runner.AndroidJUnitRunner` (push wavs to `files/eval` via run-as).

## Stages 6–7 update — recording summary and Ask AI (2026-11-07)

- `RecordingAi` (map-reduce summary over ~3000-char chunks, TF-IDF retrieval with minute boost, [m:ss] citations) runs through `ChatEngine.completeLocalIsolated`, which skips vault and web research so transcript text never leaves the device.
- Recording detail now has Summarize (saved to `recording_summaries`, shown as Markdown) and Ask AI (bottom sheet; source chips seek the audio). Both need a selected local LLM.
- Verified: build, unit tests `RecordingAiTest` (timecodes, retrieval) pass; app launches without crash. NOT verified: actual LLM output quality — no LLM is downloaded on the emulator. Ask-AI history is in-memory only (no message table yet).

## Stage 4 update — foreground recording service (2026-11-07)

- `RecordingService` (foreground, type microphone, notification) owns MediaRecorder; `CaptureSession` exposes state/elapsed/level to `LiveRecordingScreen`, which now shows a level bar and restores state after leaving/returning.
- Verified on emulator: start → service foreground → app sent to background → returned with timer continuing (0:18) → Stop → recording registered, processed through the pipeline and shown as "Transcript ready" (the emulator mic is silent, so the transcript is empty; no speech was actually recorded).
- Not done: recovery of a partially written .m4a after process kill; real-voice microphone test on hardware.

## Bundled LLM (2026-11-07)

- Qwen3 0.6B (`assets/bundled_llm/Qwen3-0.6B.litertlm`, 614 MB, from litert-community/Qwen3-0.6B) ships in the APK; `BundledModelInstaller` copies it to `llm_models` on first launch and selects it if no model is active. APK is now ~1.0 GB and needs ~0.6 GB extra storage after install.
- Verified on emulator: file installed, shown as selected in AI models. Summary / Ask AI output quality with this model is NOT yet verified.

## Enhancement chain (2026-11-07)

- Added `AudioDsp.kt` (HPF 80 Hz Butterworth, RMS normalisation with noise gate, compressor 3:1 + limiter) and `DtlnDenoiser.kt` (DTLN ONNX via onnxruntime-android 1.28.0, 512/128 blocks, delay-compensated). sherpa-onnx is now the static-ORT build to avoid a libonnxruntime.so version clash (VERS_1.28.2 vs 1.28.0).
- Design finding (emulator smoke test, 6 s synthetic clips, one clip per condition, no WER computed): GigaAM transcribed DTLN-denoised audio slightly worse (e.g. "Ддавай", artefacts), so DTLN output feeds **VAD only**; ASR/diarization use HPF+normalised audio. With white noise at −5 dB SNR and low-pass noise at 0 dB, plain VAD found 0 speech segments while DTLN-driven VAD recovered the utterance (text had one error at −5 dB). Clean and +5 dB clips were unchanged.
- Overlap: `overlapSeconds` measures overlapped speech from diarization turns and logs it. Separation (MossFormer) is NOT implemented: no ONNX export exists and a self-converted model is unverified; overlapped words go to the dominant speaker. AEC stage not implemented (file-based pipeline, MIC source).
- Re-check: SpeechPipelineDeviceTest#transcribesPushedFiles logs raw / norm / dtln variants per clip.

## AEC capture and separation status (2026-11-07)

- Capture moved from MediaRecorder to `AudioRecord` (VOICE_RECOGNITION, mono, probes 16/48/44.1 kHz) with the platform `AcousticEchoCanceler` attached when the device provides one; output is WAV. Verified on emulator: 6 s capture → 192,044-byte 16 kHz WAV → processed. The emulator has no AEC effect (`AEC=false`), so AEC itself is unverified; it needs a device that implements it, with audio playing near the mic.
- Separation NOT done. Conversion of MossFormer2_SS_16K (ClearerVoice, Apache-2.0) needs running third-party model code plus a downloaded checkpoint. The assistant's sandbox policy blocked executing it, so `speech-decoder/scripts/export_mossformer2.py` was written but never run. Next step: run it (or approve running it), verify torch/ONNX parity, then add the 2 s-window separator on overlapped regions, ASR per separated stream and speaker matching. Quality on Russian is unmeasured.

## Overlapped speech separation (2026-11-07)

- MossFormer2_SS_16K converted to ONNX (fixed 2 s window; torch vs ONNX max abs diff 6e-4 on random input) and int8-quantised (98 MB). Python check on a Russian+English two-voice mix: SI-SNR of separated sources 16.6 and 16.9 dB vs mixture 0.1 dB. This is one synthetic mix, not a benchmark.
- Pipeline: diarization turns → overlap regions (≥ 0.4 s, ≥ 2 speakers) → separate (75 %-overlapped windows, permutation aligned by correlation) → GigaAM per source → tokens replace the mixed-audio tokens inside the region, per-speaker grouping so segments of different speakers may overlap in time. ASR model is released while the separator runs (memory); region budget 120 s.
- Fix found on the way: diarization now gets HPF-only audio. On the compressed/normalised signal the test clip split into 4 speakers instead of 2.
- Verified on ARM64 emulator: no crash/low-memory kill; synthetic overlapped clip (one Russian voice, time-stretched copy) gives two speaker streams with partial text, 3.2 s of overlap took ~14 s extra (RTF ≈ 4 on the emulator; phones unmeasured). Speaker↔source assignment uses exclusive-speech energy and falls back to arbitrary order when none exists (it did in this test). Accuracy on real Russian overlaps is NOT measured.

## Thinking toggle and Live screen redesign (2026-11-07)

- Settings → Chat and AI → "Thinking mode" (DataStore `thinking_enabled`, default off). For Qwen3 the engine appends the `/think` or `/no_think` soft switch; `ThinkFilter` (unit-tested, handles tags split across chunks) hides `<think>` blocks or, when thinking is on, shows them as a quote above the answer. Summaries and recording Q&A always hide reasoning. DeepSeek R1 always reasons; the toggle only controls whether it is shown. Not verified with a real Qwen3 generation on the emulator.
- Live recording screen redesigned: status pill with pulsing REC dot, tabular timer, rolling waveform from live input level, large record/stop button with pulse ring, tonal pause/resume, saved-state card with Open Archive / Record another. Checked on emulator screenshots (idle, recording, saved); the emulator mic is silent, so the waveform stayed flat there.

## Hardening pass (2026-11-07)

- `.gitignore`: models, bundled LLM, sherpa/ORT JNI libs, APKs, `*.onnx/*.litertlm/*.pt`, build output, venvs and test audio are ignored; `dist/Lazure-debug.apk` was removed from the index (file kept on disk). Rebuild artifacts with `speech-decoder/scripts/fetch_models.sh` (+ `export_mossformer2.py` for separation).
- Speaker↔source matching: separated sources are now matched with CAM++ embeddings against per-speaker reference embeddings built from that speaker's non-overlapped speech (best distinct pair over ALL speakers, so a wrongly labelled overlap pair is corrected), then energy, then arbitrary. Synthetic Russian+English test: the Russian words in the overlap were attributed to the Russian speaker (margin 0.93) where the diarization label pair was wrong. One synthetic clip, not a benchmark.
- Separation cost: regions < 0.6 s skipped, longest regions first, 90 s wall-clock cap (`maxSeparationWallMs`), Settings → AI models toggles for separation and DTLN. Real-phone speed still unmeasured.
- Long audio: enhancement chain is now chunked PCM16 (HPF, RMS norm, compressor, DTLN read/write blocks directly), so extra memory is ~2 B/sample/signal instead of several float copies. Also fixed a DTLN bug that dropped the last ~256 samples. Verified: 20-minute file (163 Russian utterances) processed in 126.5 s (RTF 0.10 on the emulator) with no OOM; diarization still needs one float copy of the audio (about 230 MB per hour). Recordings of 1+ hours are untested.
- Crash recovery: WAV header is patched every ~2 s; on launch orphaned WAVs not in the database are repaired, registered as "Recovered recording" and processed. Verified by force-stopping the app mid-recording (9 s recovered, transcript produced).
- Recording chat is persisted (Room v9, `recording_chat_messages`, migration 8→9 with an instrumented test; sources re-resolved by start time; "Clear chat").
- Player: waveform from decoded audio (cached `.peaks`), tap/drag seek, speaker strip, playhead, current-segment highlight with auto-scroll, speaker colours. Verified on emulator screenshots.
- AI models screen now lists LLM, ASR (GigaAM + Silero VAD), diarization, DTLN and MossFormer2 with real sizes/licences from the APK and on/off switches for the two optional stages. There is still exactly one real ASR and one diarization model, so nothing else can be "selected"; importing other models is not implemented.

## LLM, recording chat, speakers and interruptions (2026-11-07)

- **Root cause of "thinks but never answers" and canned "I cannot assist" replies:** `litert-community/Qwen3-0.6B` was updated on 2026-10-12/13 (and again 11-07) for LiteRT-LM 0.18+; with the bundled LiteRT-LM 0.12.0 the new `Qwen3-0.6B.litertlm` renders `<|im_start|>user\n<|im_end|>`, i.e. the user turn is dropped (confirmed with `Conversation.renderMessageIntoString`). The model then answered a message it never saw. The bundled and catalog file is now pinned to revision `49837332af…` (614,236,160 bytes, renders the user text correctly with 0.12.0). Upgrading the library is blocked: LiteRT-LM 0.17.1+ is compiled with Kotlin 2.4 metadata, the project uses Kotlin 2.2 (0.16.1 compiles but its GPU path failed on the emulator). `BundledModelInstaller` now replaces a stale copy when sizes differ.
- Qwen3 reasoning comes on LiteRT-LM's `thought` channel; the engine now sets `extraContext = {enable_thinking}` from the Settings toggle and shows the thought as a quote only when thinking is on.
- Ask AI on a recording now opens the AI tab with the conversation attached as context; in chat a conversation can be attached/detached at any time (retrieval over transcript lines, `[m:ss]` sources appended). Recording rename is available from the detail screen.
- Speakers: pyannote turn boundaries lagged at speaker changes and its clusterer merged similar voices. Each sentence is now re-labelled from its own CAM++ embedding (online centroids, similarity 0.7, optional fixed speaker count), mapped back to diarizer ids. Synthetic Russian 2-speaker TTS dialog (voice B = pitch-shifted voice A, 6 lines, 30 s): all six turns recovered within ~0.5 s of ground truth; before the change B was cut off/merged. Real two-person English clip: 4 → 2 speakers. Single speaker clip stays 1. TTS-based, not a benchmark.
- Interruptions: segments overlapping another speaker (separated streams or a diarizer overlap region) are flagged (`transcript_segments.overlap`, Room v10), shown with an "Interruption · who" chip, a red outline, and a red band under the player's speaker strip. Setting "Follow playback" (Settings → Recordings) turns transcript auto-scroll on or off; the current quote is highlighted while playing.

## N simultaneous speakers, playback highlight, LLM quality (2026-11-07)

- **More than two voices at once.** Overlap regions now carry every speaker the diarizer saw (chains A+B, B+C merge into one region, up to 4). `splitSources` separates with the 2-speaker MossFormer2, then re-splits any source that still sounds mixed (forced when the diarizer reports more speakers than sources; "hunted" when references exist for more people than sources and both halves come out cleaner and belong to different speakers, otherwise a clean voice would be duplicated). Sources are assigned to speakers by the best injective assignment of CAM++ similarity over all known speakers; two-source fallback is energy. pyannote-3.0 itself cannot see more than two simultaneous voices, which is why the hunt exists. Synthetic test (one Milena TTS voice at 3 pitches, 3 people talking at once for 5 s): before, 2 streams with blended text; now 3 streams assigned to 3 speakers with interruption chips, but one stream is still a blend (the 2-speaker separator leaks on 3 voices), separation took ~90 s for a 31 s clip on the emulator. No WER/DER measured. Two-voice clips were not split further.
- **Playback highlight was lost** because position polling ran inside `PlayerBar`, a list item that leaves composition once auto-scroll moves away; polling now lives in the screen. The highlight also covers pauses between sentences (the sentence that just ended stays lit for 2.5 s) and lights all overlapping blocks. Checked on the emulator: block lit and list followed at ~9 s, three blocks lit together in the overlap at ~23 s.
- **Local LLM.** Same root cause as the Qwen3 case, but for the whole catalog: litert-community re-exported its models on 2026-10-11 (chat template for LiteRT-LM 0.18, "content parts"); the 0.12 runtime drops the user turn from those files. Qwen 2.5 1.5B from `main` answered "I'm not sure what you're asking". All catalog URLs are now pinned to the last commit before that date with an unchanged file size (SmolLM2, Qwen3 0.6B, Qwen 2.5 1.5B, DeepSeek R1 1.5B, Gemma 4 E2B/E4B, Phi-4 mini, Qwen3 4B). Gemma 3 270M is gated on Hugging Face (needs a token), so it was removed from the catalog. Summaries and transcript Q&A now sample at temperature 0.2 / top-k 20 and the excerpts go in the user turn. Models are already quantised (Qwen3 0.6B and Qwen 2.5 are int8); Gemma 3 1B int4 (584 MB) would be the better small model but is gated.

## LLM speed (2026-11-07)

- Default and bundled model is now the **INT4 no-think** Qwen3 0.6B (`qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm`, 347 MB, litert-community/Qwen3-0.6B-int4 @ 6aa2daf8ab, built in June so it works with LiteRT-LM 0.12). The previous INT8 file (614 MB) stays in the catalog as "Qwen3 0.6B INT8". Debug APK: 1.81 GB → 1.20 GB.
- Same question on the same recording, ARM64 emulator, CPU: INT8 first token 12.6 s, 14 tokens, 34.1 s total; INT4 first token 4.6 s, 15 tokens, 4.9 s total. Recording summary (136 tokens) on INT4: 7.5 s; on INT8 the summary used to take 20+ minutes on this emulator. These are emulator numbers; phones are not measured, and INT4 is not necessarily 7x faster there. Quality was only eyeballed on two prompts (answers and the summary are plausible but short and sometimes inexact); no benchmark.
- The thinking toggle has no effect on the no-think file (thinking is baked out); the INT8 file still supports it.
- The engine's CPU thread count was computed but never passed to LiteRT-LM; it is now (`Backend.CPU(threads)`, clamped to 2–4). The KV length in the file name (`ekv1280`) caps the context; summary chunks (1500 chars) and retrieved excerpts (4) are sized to fit ~1300 tokens. The summary asks for four sections instead of six.
- Speed log line `speed: tokens=… firstToken=… tok/s` in `EssentialLlm` for every generation.
- Overlap separation skips silent windows (31 s three-voice clip: 92 s → 72 s, same transcript) and the hidden-voice hunt is limited to regions up to 12 s.

## Too many speakers, CPU default, chat history (2026-11-07)

- **Why extra speakers appeared.** (1) Two id spaces: the sentence-level re-clustering produced new ids that did not match the diarizer's, and separated overlap streams were labelled with diarizer ids, so one person got two labels (synthetic 3-voice clip: 4 labels for 3 people). (2) The online clustering join threshold (cosine 0.7) is strict for a voice that varies in tone and nothing merged or absorbed the resulting stragglers. (3) "Overlaps" the diarizer found between two ids that are really the same person.
- **Fix.** `refineSpeakers` now runs before separation and owns the final ids (0..k-1 in order of first appearance): online clustering → two re-assignment passes → agglomerative merge of centroids with cosine ≥ 0.75 → absorption of tiny clusters (< 4 s or one sentence, and < 8 % of speech, nearest speaker ≥ 0.35) → soft cap at the diarizer's speaker count (pairs ≥ 0.5 only, the diarizer is tuned to over-split) → fixed count when the user sets one. Diarizer ids are translated with `diarizerToFinal`; separation uses the refined centroids as references and the same ids; overlap regions whose speakers collapse into one person are dropped. Tokens inside overlap regions are left out of the embeddings. Checked on the emulator: 3-voice TTS clip 3 speakers (was 4 labels), 2-voice TTS dialog 2, real 2-person English clip 2. A first try with merge ≥ 0.6 collapsed the real 2-person clip into 1 speaker (centroid similarity 0.73), so 0.75 it is. These are three small clips, not a benchmark; the user's own recordings are the real test.
- **Manual controls.** Settings → Recordings → "Number of speakers" (Auto, 2–6; forces the clusterer and the diarizer to that count) and, in a recording, tapping a speaker offers "Merge into: …" (moves the lines, drops the speaker).
- **CPU is the default LLM backend** (it used to switch to GPU on first launch when available, then fall back to CPU after a failed load). Existing installs are moved to CPU once (`llm_backend_cpu_default_v1`); GPU stays a manual option in Settings.
- **AI chat history.** The AI tab now saves every chat in `chat_sessions` / `chat_messages` (tables and DAOs already existed, no migration): new chat and history buttons in the top bar, the existing `ChatHistorySheet` lists, opens, deletes ("copy") chats, and an opened chat restores its attached conversation (stored in the user message's `sources_json`).

## Speaker boundaries and labelling accuracy (2026-11-07)

- Benchmark: `speech-decoder/bench` (synthetic Russian dialogues with ground truth, voices = one TTS voice pitch-shifted, so harder than real pairs). Accuracy = share of true speech time carrying the right speaker (best mapping). Baseline 91.4 % overall (rapid 3-voice 82.0 %, mixed 81.5 %); now 96.6 % (rapid 94.3 %, mixed 90.4 %, 2-voice dialogue 97.4 %, long turns 99.6 %); on speech that was transcribed at all the label is right 98.7 % of the time. The rest is coverage (a dropped one-word reply) and one-word replies of 0.4-0.7 s, which no voice embedding can place reliably.
- What was wrong: sentences shorter than 1 s were merged into the previous one, so "Угу"/"Да" went to the wrong person and a short sentence poisoned the embedding of the next one; turn changes inside a VAD segment were invisible; the recogniser lost the first word of a reply because Silero reports speech starts late.
- What changed: sentence units are cut at the diarizer's change points (accurate in time, ids unreliable), GigaAM's dialogue dash and VAD segment starts, each cut verified by the two sides' CAM++ embeddings when both are >= 1 s (different voices: cosine < 0.55 / 0.65 / 0.6), taken on the cue alone next to a shorter reply; short units are no longer merged. Replies < 1 s are labelled by embedding (>= 0.45) combined with the purity of the diarizer id (P(final speaker | diarizer id) weighted 0.3), else the previous speaker. Weak clusters (< 4 s) are folded into a cluster they resemble at cosine >= 0.5, which keeps one person from becoming three "speakers" when the recording is cut into short pieces (real 2-person clip: 4 -> 2 again). ASR now gets 0.25 s before and 0.15 s after each VAD segment (never into a neighbour).
- Not measured: real Russian conversations (no labelled corpus here), WER, and anything beyond ~4 voices.

## Noise reduction strength (2026-11-07)

- The recogniser never gets the DTLN output (it hears high-passed, normalised audio); DTLN only feeds the speech detector, which is where quiet words and soft onsets get "swallowed" (a segment the detector never sees is never recognised). The detector signal is now a dry/wet mix (`denoiseStrength` 0.7, was full DTLN) and the Silero threshold is 0.45 (was 0.5). With the 0.25 s / 0.15 s padding added earlier, the recogniser also gets audio around each detected segment.
- Measured on the synthetic bench (clean and with pink noise at roughly 7 dB SNR): no measurable gain or loss from weaker denoising (clean 96.1-96.7 %, noisy 79-87 %, spread comparable to the differences); the bench does not reproduce the "swallowed words" effect, so this is a conservative change, not a verified fix. Disabling denoise entirely was worse on noisy speech (rapid 3-voice 79 % vs 89 %), so the toggle stays on by default.

## Очистка репозитория и подготовка к сдаче (2026-11-07)

- Удалён весь облачный код, унаследованный от исходного приложения заметок: REST-клиент, авторизация, подписки, синхронизация, клиенты Gemini, OpenAI-совместимых API и Timeweb, а также веб-поиск DuckDuckGo/wttr.in в чате (он был включён по умолчанию). `ChatEngine` теперь только локальный. Из `BuildConfig` убраны `ALLOW_DIRECT_CLOUD` и ключи API.
- Удалены недостижимые экраны (заметки, редактор, граф, онбординг хранилища, авторизация), около 1200 неиспользуемых строк в трёх локалях, вендорная копия `llama.cpp` (2598 файлов, 149 МБ; в CMake не собиралась), прототип `speech-decoder` (не компилировался) и скрипты интеграции с локальными путями.
- **Временные метки:** проверена гипотеза, что «метка последнего токена + 80 мс» обрезает конец реплики. Замер на реальной GigaAM v3 через sherpa-onnx (`tools/bench/token_timing_check.py`, 24 реплики) её опроверг. CTC ставит метку ближе к концу токена: «+80 мс» даёт MAE 0,38 с, а продление конца до следующего токена или конца сегмента VAD — 0,54 с со сдвигом +0,52 с. Правило «+80 мс» оставлено, пробная правка откатана.
- **Скорость:** в Room добавлена колонка `processing_ms` (миграция 10→11). Карточка записи показывает «Обработано за X с · RTF Y», в logcat пишется строка с RTF.
- **Release-сборка:** добавлены keep-правила R8 для sherpa-onnx и ONNX Runtime (JNI ищет поля и классы по имени, без правил распознавание в release упало бы). Release подписывается debug-ключом, если не задан свой. Выбор ABI: `-Plazure.abis=arm64-v8a`.
- **CI:** исправлены версия NDK (27.1, как в сборке), пути к APK; добавлены самотест скрипта оценки и ручная сборка с моделями.
- **Оценка:** `tools/eval/evaluate.py` считает WER, cpWER, DER, ошибки границ и RTF по JSON в формате конкурса.
- Проверка: `assembleDebug`, `assembleRelease -Plazure.abis=arm64-v8a`, `compileDebugAndroidTestKotlin` собираются; `testDebugUnitTest` — 49 тестов, 0 ошибок. На устройстве после этих правок приложение ещё не запускалось.

## Перебивания: говорящие и пометки (2026-11-08)

- **Симптом.** На реальной записи двух человек с перебиваниями приложение нашло 3 «говорящих». Реплики перебивающего достались перебитому, ни одно перебивание не было отмечено.
- **Как искали.** `SpeechPipeline` получил `SpeechModelSource`: модели берутся из assets или из каталога на диске. Обработка вынесена в `process(decode)`. `SpeechPipelineJvmTest` гоняет настоящий пайплайн на Linux-сборке sherpa-onnx 2.0.1 без телефона.
- **Что выяснилось.** pyannote видит оба наложения (0,97 и 0,92) и почти верно размечает людей, только дробит B на два id. Ломало уточнение говорящих:
  1. «чистота» id перебивающего считалась по словам перебитого, потому что этот id есть только в наложениях;
  2. две короткие реплики B (сходство CAM++ 0,23) не объединялись;
  3. наложение 0,54 с отсекалось порогом 0,6 с.
- **Что сделано** (`DiarizationEvidence`, `refineSpeakers`):
  - кто есть кто — только по исключительной речи;
  - id, которые слышали одновременно ≥ 0,3 с, — разные люди: для кластеров с ≥ 3 с речи и для коротких реплик;
  - короткие кластеры с одним id сливаются, если цель — их ближайший по CAM++ (допуск 0,1);
  - говорящий назначается по словам;
  - реплика не продлевается через чужую;
  - перебивание отмечается от 0,3 с (разделение MossFormer2 — по-прежнему от 0,6 с).
- **Отвергнутые варианты** (проверены на наборе и ухудшили его):
  - слияние по id диаризатора без проверки «ближайшего»: похожие голоса под одним id слились, cpWER 32,6 % → 56,5 %;
  - «новый говорящий» для id, который слышали поверх всех: лишний говорящий там, где диаризатор смешал двух людей;
  - запрет связи для кластеров короче 3 с: короткий кластер, попавший в чужой турн диаризатора, не мог вернуться к своему.
- **Результат** на 7 файлах (запись и 6 диалогов из живой речи): cpWER 42,4 % → 36,9 %, DER 9,6 % → 8,2 %. На реальной записи cpWER 48,8 % → 16,3 %, 2 говорящих, оба перебивания отмечены. Подробности — в [TEST_RESULTS](TEST_RESULTS.md).
- **Проверка.** `testDebugUnitTest` — 54 теста, 0 ошибок (1 пропущен без моделей). `assembleDebug` и `assembleRelease` собираются. На телефоне не проверялось.

