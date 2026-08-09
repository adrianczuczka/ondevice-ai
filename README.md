# ondevice-ai

One Kotlin Multiplatform API over the **system** on-device AI models – Gemini
Nano (ML Kit GenAI / AICore) on Android, Apple Foundation Models on iOS 26+.
Zero bundled weights: the OS owns the models.

> **Status: pre-1.0, API will change. Not yet on Maven Central.** Both engines
> are validated against live models: Android on hardware (Pixel 10 Pro XL,
> nano-v3, integrated in a production app), iOS against Apple Foundation
> Models (iOS 26 simulator with an Apple Intelligence host). Apache-2.0.

```kotlin
val ai = OnDeviceAi()

when (val a = ai.availability()) {
    is Availability.Available    -> ai.chat(SessionConfig(instructions = "You are terse.")).use { session ->
        session.stream("Summarize these notes in one sentence: $notes").collect(::print)
    }
    is Availability.Downloadable -> ai.download().collect { /* progress UI */ }
    is Availability.Downloading  -> { /* spinner */ }
    is Availability.Unavailable  -> when (a.reason) { /* reason drives the UI */ }
}
```

Structured output from any `@Serializable` class:

```kotlin
@Serializable
data class ActionItem(val what: String, val dueDate: String?)

val item: ActionItem = session.respondStructured("Extract the action item: $message")
```

## Getting it

Until the first Maven Central release, consume it as a Gradle composite build:

```kotlin
// settings.gradle.kts
includeBuild("path/to/ondevice-ai")
```

```kotlin
// your module
dependencies {
    implementation("com.adrianczuczka.ondeviceai:core:0.1.0-SNAPSHOT")
}
```

**Requirements**

- Built with Kotlin 2.3, AGP 9.2 (`com.android.kotlin.multiplatform.library`),
  Gradle 9.5 – composite-build consumers need compatible tooling.
- **Android:** minSdk 26. Live inference needs an AICore-capable device
  (Pixel 8+, Galaxy S24+, and other recent flagships) with the model
  provisioned – `availability()` tells you which state you are in. Pulls
  `com.google.mlkit:genai-prompt` (beta) and enforces
  kotlinx-coroutines ≥ 1.11.0 (see field notes).
- **iOS:** iOS 26+, device or simulator with Apple Intelligence available.
  The host app registers a small Swift bridge – see below.

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

Two steps. First, export this library through your shared module's iOS
framework so its types are visible from Swift:

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets.commonMain.dependencies {
        api("com.adrianczuczka.ondeviceai:core:0.1.0-SNAPSHOT")
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            export("com.adrianczuczka.ondeviceai:core:0.1.0-SNAPSHOT")
        }
    }
}
```

Second: FoundationModels is Swift-only and unreachable from Kotlin/Native, so
the host app implements a small bridge protocol and registers it once at
startup (copy the reference implementation from
`ios-bridge/FoundationModelsBridgeImpl.swift` into your app target):

```swift
OnDeviceAiIos.shared.bridge = FoundationModelsBridgeImpl()
```

On iOS < 26 leave the bridge unregistered – the library reports
`Unavailable(NotConfigured)` and your fallback path takes over.

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

Findings from integrating this library into a production Android app, filed
upstream where they belong:
[mlkit#1068](https://github.com/googlesamples/mlkit/issues/1068) (coroutines
POM defect), [mlkit#1069](https://github.com/googlesamples/mlkit/issues/1069)
(undocumented background-inference block – surfaced as the typed
`BackgroundBlocked` error), and
[mlkit#1070](https://github.com/googlesamples/mlkit/issues/1070)
(undocumented BUSY quota – surfaced as `Busy(retryDelay)`).

- **Context windows differ meaningfully across platforms:** nano-v3 reports
  8192 tokens, Apple's Foundation Models 4096. Size prompts for the smaller
  one, or branch on `ModelInfo.contextWindowTokens`.
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
- [ ] Image input (removed from the public surface until implemented)
- [ ] Native multi-turn requests on Android (history currently rides in the prompt text)
- [ ] `ContentBlocked` mapping for platform safety signals (`Busy`, `BackgroundBlocked`, and `ContextWindowExceeded` shipped)
- [ ] Binary-compat validator, publishing to Maven Central (`explicitApi` shipped)
