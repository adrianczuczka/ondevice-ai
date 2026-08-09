# Issue 3 – FILED: https://github.com/googlesamples/mlkit/issues/1070 (IssueTracker escalation still possible if it stalls)

**Title:** Document AICore's per-app inference quota – sustained generation returns GenAiException BUSY (error code 9) after ~40 requests

---

### Description

Sustained back-to-back inference through the Prompt API hits a rolling per-app quota that is not documented anywhere. After roughly 36–41 consecutive generations (~1.5 s each, so about a minute of sustained load), every subsequent request fails fast (~15–25 ms) with `GenAiException` error code 9 (`ErrorCode.BUSY`) and the message:

```
Request cannot be processed. Either your app is out of usage quota (try
again later) or the request is from disallowed background usage (use the
API while the app is in the foreground).
```

The app was foregrounded the entire time, so this is the quota branch.

### Data

From an evaluation harness running 93 prompts through Gemini Nano (nano-v3) on a Pixel 10 Pro XL, Android 17, genai-prompt 1.0.0-beta4 – `.` is a successful generation, `E` is BUSY:

```
run A: ....................................EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE
run B: .........................................EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE.EEEEEEEEEEEEE...
```

Run B started ~2.5 minutes after run A and received a fresh allowance of almost identical size, suggesting a rolling window. Pacing requests ~1 s apart and honoring `GenAiException.getRetryDelay()` avoids the wall entirely.

### Why this matters

Any batch-style usage – evaluation harnesses, prefetching several summaries, retry loops – hits this wall with no way to anticipate it. The error is retryable, and the SDK even ships `getRetryDelay()`, but neither the quota's existence, its approximate size, nor the retry-delay semantics are documented. Developers will misread mass BUSY failures as model or device breakage (we initially did).

### Requested

1. Document the per-app quota's existence and rough shape (requests per
window, replenishment behavior) in the GenAI API guides.
2. Document `ErrorCode.BUSY` and `getRetryDelay()` semantics and recommended
backoff handling.
3. Ideally: expose remaining quota (even coarsely) so apps can route to a
fallback before hitting the wall.
