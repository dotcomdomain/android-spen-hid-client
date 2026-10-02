# Hold to dictate

This optional feature is disabled by default. In Settings, download the model,
grant microphone permission, and enable Voice transcription. On this Note9 the
default trigger is the Bixby button: `gpio_keys`, Linux `EV_KEY` type 1, code 703.
Hold to record, then release to transcribe and type to the connected USB host.
The host must use a US keyboard layout. Unsupported characters leave the full
transcript available in Settings rather than silently dropping part of it.

The app records mono 16 kHz PCM into memory, caps recording at five minutes,
ignores key repeat, and sends each recording only once. Holding the activation
input again starts a new recording. With "Replace pending dictation" enabled
(the default), it cancels all earlier pending decoding and typing. With this
switch off, recording can overlap earlier processing. "Parallel transcription"
selects queued or concurrent decoding, with a limit slider from one to five.
Extra recordings wait for a decoding slot. Text delivery always follows recording
order, including when a short later clip finishes first or an earlier clip fails.
Parallel jobs share immutable model weights and use separate whisper states and
CPU groups. Idle extra states are released when a smaller limit is used. More
jobs divide CPU time and need more inference-buffer memory. The default is
queued decoding; the stored parallel limit defaults to two. Already typed
characters cannot be recalled. Native inference uses
an abort callback, a duration-based time limit, and one greedy decoding pass
without temperature fallback. Per-recording state prevents a cancelled operation
from stopping a newer recording. Nonblocking HID writes have bounded waits and
attempt to release keys when cancelled. Audio
is discarded after inference. The latest text remains in process memory for
inspection if the host disconnects. It is never queued for a later USB host.

The foreground service provides a notification and the app shows a status pill.
Grant the overlay permission in Settings to show the pill over other apps.
Only one pill appears when the system overlay is available. It disappears as
soon as sending finishes; errors dismiss after two seconds and remain readable
in Settings. Opening a temporarily unavailable HID keyboard retries for up to
two seconds before any text is sent, so this retry cannot duplicate typed text.
Android's microphone privacy indicator remains active during recording.
After an OS force-stop, open the app to resume an enabled input listener.

## Raw input mapping

The implementation follows Key Mapper expert mode's underlying evdev approach:
https://github.com/keymapperorg/KeyMapper/tree/develop/evdev

Our root service uses Android's `getevent -t` to observe raw press/release events.
It does not copy Key Mapper's code or grab entire input devices. This preserves
the volume and power keys sharing `gpio_keys`; an existing Android action mapped
to the chosen button can also run. Learning captures a Linux device name, event
type, and input code. Advanced users can enter these fields directly. Types 1
and 5 support held keys and binary switches; relative axes and gestures do not
have reliable release events and are deliberately excluded.

## Inference and model

Vendored MIT-licensed whisper.cpp v1.8.3:
https://github.com/ggml-org/whisper.cpp/tree/v1.8.3
The runtime is built from source for both NDK and Termux builds. No model weights
are packaged as assets or native resources. JNI keeps the model in memory between
dictations and releases it when the service stops. English decoding uses greedy
sampling, CPU flash attention, ARM NEON, at most four threads per inference, and
CPU affinity when the kernel allows it. Single inference uses performance cores;
parallel inference divides the available fast and slow cores between jobs and
limits each job's threads to its CPU group size. Short audio uses a smaller
encoder context to avoid processing the full 30-second padded window.

The multilingual Whisper base model is quantized to Q5_1. English decoding is
selected because the current USB text path uses a US HID layout. Download:
https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin

Size: 59,707,625 bytes. SHA-256:
`422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`

The download goes to a partial file under `noBackupFilesDir` and becomes usable
only after both size and hash checks pass. Interrupted downloads can be retried;
they never replace a working model until verified.

The connected Note9's system Vulkan driver was probed directly: Mali-G72,
Vulkan 1.1.213. whisper.cpp's current ggml Vulkan backend requires Vulkan 1.2,
so this build uses the ARM CPU backend. Exynos 9810 has no supported whisper.cpp
NPU backend. Termux's software Vulkan adapter must not be mistaken for Mali GPU
acceleration. The implementation uses no proprietary inference blobs.

## Validation on the connected Note9

The debug APK and Android test APK built successfully on the phone. The app's
own download service downloaded the model and verified its size and SHA-256.
Two instrumented tests passed against the installed JNI library and root reader:

- Four seconds from the upstream JFK speech sample transcribed in 0.85 seconds.
- Eleven seconds using the already loaded model transcribed in 1.68 seconds.
- Root input service connected and remained running without starting recording.
- The feature remained disabled throughout validation.

These timings describe those clean speech samples, not a guarantee for arbitrary
audio or a thermally throttled phone. Validation did not record live microphone
audio or inject text into the connected computer. Physical hold/release and live
microphone capture are available for the user's first enabled-use check.
