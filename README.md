# ondevice-ai

One Kotlin Multiplatform API over the **system** on-device AI models – Gemini
Nano (ML Kit GenAI / AICore) on Android, Apple Foundation Models on iOS 26+.
Zero bundled weights: the OS owns the models.

> **Status: pre-1.0, API will change. Not yet on Maven Central** – consume via
> Gradle `includeBuild` for now. Both engines are validated against live
> models: Android on hardware (Pixel 10 Pro XL, nano-v3, production app
> integration), iOS against Apple Foundation Models (iOS 26 simulator,
> Apple Intelligence host). Apache-2.0.

```kotlin
val ai = OnDeviceAi()

when (val a = ai.availability()) {
    is Availability.Available    -> ai.chat(SessionConfig(instructions = "…")).use { session ->
        session.stream("Should I bring a jacket today?").collect(::print)
    }
    is Availability.Downloadable -> ai.download().collect { /* progress UI */ }
    is Availability.Downloading  -> { /* spinner */ }
    is Availability.Unavailable  -> when (a.reason) { /* reason drives the UI */ }
}
```

Structured output from a `@Serializable` class:

```kotlin
@Serializable
data class Advice(val wearJacket: Boolean, val reason: String)

val advice: Advice = session.respondStructured("Given $forecast, jacket or not?")
```

## Design decisions

- **Sessions are the core primitive** – both platform APIs are session-based;
  `ChatSession` is `AutoCloseable` because sessions hold native resources.
- **Availability is a type, not a boolean** – `Available / Downloadable /
  Downloading / Unavailable(reason)`, because each case demands different UI.
- **Thin facade over an engine SPI** – the core artifact ships only
  `SystemEngine`. Bundled-weight engines (llama.cpp-style) and a cloud escape
  hatch are future opt-in artifacts; `RoutingPolicy.OnDeviceOnly` is the
  privacy guarantee, enforced by type.
- **`stream()` emits deltas everywhere** – ML Kit already does; the iOS engine
  diffs Foundation Models' cumulative snapshots.

## Platform mapping

| commonMain | Android | iOS |
|---|---|---|
| `availability()` | `GenerativeModel.checkStatus()` | `SystemLanguageModel.availability` (via bridge) |
| `chat()` / `respond()` | Prompt API `generateContent` | `LanguageModelSession.respond` |
| `stream()` | `generateContentStream` (deltas) | `streamResponse` snapshots → diffed |
| `respondStructured()` | JSON prompt + validate/retry | JSON prompt + validate/retry |
| instructions / options | `SystemInstruction`, request builder | `LanguageModelSession(instructions:)` |

## iOS setup

FoundationModels is Swift-only, so the host app registers a small bridge once
at startup (reference implementation in `ios-bridge/FoundationModelsBridgeImpl.swift`):

```swift
OnDeviceAiIos.shared.bridge = FoundationModelsBridgeImpl()
```

## How this compares

| | this library | [Llamatik](https://github.com/ferranpons/llamatik) | [litertlm-kmp](https://github.com/sagar-develop/litertlm-kmp) |
|---|---|---|---|
| Model source | **System models** (Gemini Nano via ML Kit / Apple Foundation Models) – zero bundled weights, OS-updated | Bring-your-own via llama.cpp / whisper.cpp / SD | Bring-your-own Gemma via LiteRT-LM |
| App size cost | ~0 | Native libs + model files (hundreds of MB) | Engine + model files |
| Platforms | Android (AICore devices), iOS 26+ | Android, iOS, Desktop, JVM, WASM | Android-first |
| Typed errors for platform policy (quota, background block) | Yes – field-verified | n/a | n/a |
| License | Apache-2.0 | see repo | AGPL-3.0 / commercial |

The niches are complementary: system models when you want zero-download,
private, OS-maintained inference on eligible devices; bundled engines when
you need every device or custom weights. The engine SPI is designed so both
can sit behind one `OnDeviceAi` facade.

## Field notes (Pixel 10 Pro XL, Nano v3, Aug 2026)

Three findings from production integration are filed upstream:
[mlkit#1068](https://github.com/googlesamples/mlkit/issues/1068) (coroutines
POM defect), [mlkit#1069](https://github.com/googlesamples/mlkit/issues/1069)
(undocumented background-inference block – surfaced as the typed
`BackgroundBlocked` error), and
[mlkit#1070](https://github.com/googlesamples/mlkit/issues/1070)
(undocumented BUSY quota – surfaced as `Busy(retryDelay)`).

- First live inference through the full stack succeeded; `ModelInfo` reported
  `nano-v3` with an 8192-token context window.
- **AICore blocks inference from backgrounded apps** (`BACKGROUND_USE_BLOCKED`,
  error code 30). Availability checks work from anywhere; generation needs a
  foreground app. Design consequence: background workers cannot generate.
- **`genai-prompt:1.0.0-beta4` requires kotlinx-coroutines ≥ 1.11.0** at
  runtime (`NoSuchMethodError: Job.cancel$default` on 1.10.x) and its POM does
  not enforce that – this library pins 1.11.0 so consumers inherit it.

## Roadmap

- [ ] ML Kit LoRA-tuned feature clients behind `summarize` / `proofread` / `rewrite`
- [ ] Constrained decoding: `SerialDescriptor` → `DynamicGenerationSchema` (iOS),
      `genai-schema` typed requests (Android)
- [ ] Image input (`describeImage` currently `TODO()`)
- [ ] Native multi-turn requests on Android (history currently rides in the prompt text)
- [ ] Error-code mapping to `ContentBlocked` / `ContextWindowExceeded` /
      a typed background-blocked error (GenAiException code 30)
- [ ] `explicitApi()`, binary-compat validator, publishing to Maven Central
