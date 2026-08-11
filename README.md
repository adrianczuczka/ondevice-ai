# ondevice-ai

**One Kotlin Multiplatform API over the system on-device AI models – Gemini
Nano on Android, Apple Foundation Models on iOS.**

[![Maven Central](https://img.shields.io/maven-central/v/com.adrianczuczka.ondeviceai/core)](https://central.sonatype.com/artifact/com.adrianczuczka.ondeviceai/core)
![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Platforms](https://img.shields.io/badge/Platforms-Android_%7C_iOS-3DDC84)
![Status](https://img.shields.io/badge/Status-pre--1.0-orange)

Both platforms now ship a foundation model *inside the OS* – free to call,
private by default, updated by the system, and adding zero megabytes to your
app. This library gives your KMP shared code one API over both, with the
platform differences – availability, download states, quotas, policies –
handled where they belong.

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

```kotlin
dependencies {
    implementation("com.adrianczuczka.ondeviceai:core:0.1.0")
}
```

For local development against a checkout, a Gradle composite build works too:
`includeBuild("path/to/ondevice-ai")` in `settings.gradle.kts`.

### Requirements

| | |
|---|---|
| Toolchain | Kotlin 2.3+; any modern AGP/Gradle able to consume KMP libraries |
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
        api("com.adrianczuczka.ondeviceai:core:0.1.0")
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            export("com.adrianczuczka.ondeviceai:core:0.1.0")
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

## Contributing

Issues and PRs are welcome – especially reports from devices and OS versions
not covered above, which is exactly the data this space is missing. If
something behaves differently on your hardware, an issue with the
`availability()` output and the failing call is genuinely useful.

## License

[Apache 2.0](LICENSE)
