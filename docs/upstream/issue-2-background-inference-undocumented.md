# Issue 2 – FILED: https://github.com/googlesamples/mlkit/issues/1069 (IssueTracker escalation still possible if it stalls)

**Title:** Document that AICore blocks inference for backgrounded apps (GenAiException BACKGROUND_USE_BLOCKED / error code 30)

---

### Description

AICore refuses inference requests from apps that are not in the foreground. The Prompt API surfaces this as a `GenAiException` with error code 30 (`ErrorCode.BACKGROUND_USE_BLOCKED`):

```
AiCoreInferenceHelper: runInference onFailure
bmj: null (statusCode = 30)
```

Availability checks (`checkStatus()`) succeed from the background; only inference is blocked. None of this is currently documented in the Prompt API guides or the AICore documentation.

### Why this matters

The natural architecture for many on-device GenAI features is background generation – e.g. a WorkManager job that prepares a daily summary before the user wakes up, precisely the kind of privacy-friendly use case on-device models are marketed for. Developers will design and build such architectures and only discover at runtime, on device, that they cannot work. In our case (a production weather app), a WorkManager-based morning brief had to be redesigned around foreground-only generation after the failure was discovered on hardware.

### Concrete reproduction

Run any `generateContent()` call from an instrumentation test without a resumed activity (or from a WorkManager worker): error code 30. Launch an activity first (e.g. `ActivityScenario.launch`) and the same call succeeds. Observed on Pixel 10 Pro XL, Android 17, genai-prompt 1.0.0-beta4.

### Requested

1. Document the foreground requirement prominently in the Prompt API and
GenAI API guides – ideally under a "Constraints" heading alongside device eligibility.
2. Document `ErrorCode.BACKGROUND_USE_BLOCKED` and the recommended handling.
3. Ideally: expose the policy programmatically (e.g. a queryable capability
or a documented contract for what counts as "foreground") so apps can route to a server fallback proactively instead of reactively.
