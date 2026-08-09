# ondevice-ai

**One Kotlin Multiplatform API over the system on-device AI models – Gemini
Nano on Android, Apple Foundation Models on iOS.**

![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Platforms](https://img.shields.io/badge/Platforms-Android_%7C_iOS-3DDC84)
![Status](https://img.shields.io/badge/Status-pre--1.0-orange)

Both platforms now ship a foundation model *inside the OS* – free to call,
private by default, updated by the system, and adding zero megabytes to your
app. This library gives your KMP shared code one API over both, with the
platform differences – availability, download states, quotas, policies –
handled where they belong.

> **Pre-1.0: the API will change. Not yet on Maven Central.** Both engines are
> validated against live models: Android on hardware (Pixel 10 Pro XL,
> nano-v3, integrated in a production app), iOS against Apple Foundation
> Models (iOS 26 simulator with an Apple Intelligence host).

## Why this exists

Using the built-in models from shared Kotlin code means fighting four
platforms' worth of friction:

- **Two unrelated APIs** – ML Kit GenAI on Android, the Swift-only
  FoundationModels framework on iOS (which Kotlin/Native cannot even see).
- **Fragmented availability** – eligible devices, model downloads, an Apple
  Intelligence toggle, models still provisioning. Every state needs different
  UI.
- **Undocumented platform policy** – AICore enforces a per-app inference
  quota and refuses calls from backgrounded apps. Neither is in the docs;
  both are typed errors here.
- **Real apps need fallbacks** – on-device when possible, your server when
  not, and a privacy mode that guarantees data never leaves the device.

## Features

- ✅ **One `ChatSession` API** – sessions, transcripts, and streaming that
  behave identically on both platforms (deltas everywhere; the iOS engine
  diffs Foundation Models' cumulative snapshots for you)
- ✅ **Availability as a sealed type** – `Available / Downloadable /
  Downloading / Unavailable(reason)` with six distinct reasons, because
  "show a download button" and "send the user to Settings" are different UIs
- ✅ **Structured output** from any `@Serializable` class
- ✅ **Typed platform-policy errors** – `Busy(retryDelay)`,
  `BackgroundBlocked`, `ContextWindowExceeded` – field-verified on hardware
- ✅ **Engine SPI with routing policies** – `RoutingPolicy.OnDeviceOnly` is a
  privacy guarantee enforced by type; bundled-weight and cloud engines can
  plug in behind the same facade
- ✅ **Zero bundled weights** – the OS owns and updates the models

## Quick start

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

Platform policies surface as typed errors, not mystery failures:

```kotlin
try {
    session.respond(prompt)
} catch (e: OnDeviceAiException.Busy) {
    // AICore's rolling per-app quota – it tells you when to come back
    retryLaterOrFallBack(e.retryDelay)
} catch (e: OnDeviceAiException.BackgroundBlocked) {
    // Inference requires a foregrounded app – route to your server path
    fallBack()
}
```

And when the data must never leave the device:

```kotlin
val ai = OnDeviceAi(policy = RoutingPolicy.OnDeviceOnly)
```

## Installation

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

### Requirements

| | |
|---|---|
| Toolchain | Kotlin 2.3+, AGP 9.2+ (`com.android.kotlin.multiplatform.library`), Gradle 9.5+ |
| Android | minSdk 26; live inference needs an AICore-capable device (Pixel 8+, Galaxy S24+, recent flagships) – `availability()` reports the state. Pulls `com.google.mlkit:genai-prompt` (beta); enforces kotlinx-coroutines ≥ 1.11.0 (see field notes) |
| iOS | iOS 26+ with Apple Intelligence; the app registers a small Swift bridge (below) |

## iOS setup

FoundationModels is Swift-only, so setup is two steps.

**1.** Export this library through your shared module's iOS framework so its
types are visible from Swift:

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

**2.** Copy the reference bridge from
[`ios-bridge/FoundationModelsBridgeImpl.swift`](ios-bridge/FoundationModelsBridgeImpl.swift)
into your app target and register it once at startup:

```swift
OnDeviceAiIos.shared.bridge = FoundationModelsBridgeImpl()
```

On iOS < 26, leave the bridge unregistered – the library reports
`Unavailable(NotConfigured)` and your fallback path takes over.

## Platform mapping

| commonMain | Android | iOS |
|---|---|---|
| `availability()` | `GenerativeModel.checkStatus()` | `SystemLanguageModel.availability` (via bridge) |
| `chat()` / `respond()` | Prompt API `generateContent` | `LanguageModelSession.respond` |
| `stream()` | `generateContentStream` (deltas) | `streamResponse` snapshots → diffed |
| `respondStructured()` | JSON prompt + validate/retry | JSON prompt + validate/retry |
| instructions / options | `SystemInstruction`, request builder | `LanguageModelSession(instructions:)` |

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

## Contributing

Issues and PRs are welcome – especially reports from devices and OS versions
not covered above, which is exactly the data this space is missing. If
something behaves differently on your hardware, an issue with the
`availability()` output and the failing call is genuinely useful.

## License

[Apache 2.0](LICENSE)
