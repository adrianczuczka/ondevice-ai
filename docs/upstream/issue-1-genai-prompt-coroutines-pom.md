# Issue 1 – file at: https://github.com/googlesamples/mlkit/issues/new

**Title:** genai-prompt 1.0.0-beta4: NoSuchMethodError at runtime with kotlinx-coroutines 1.10.x – POM omits the required >= 1.11.0 constraint

---

### Description

`com.google.mlkit:genai-prompt:1.0.0-beta4` crashes at runtime with a `NoSuchMethodError` when the app's dependency graph resolves kotlinx-coroutines 1.10.x. The library's bytecode requires coroutines
>= 1.11.0, but its published POM does not declare that requirement, so Gradle
happily resolves older versions and the failure only appears on device.

### Minimal reproduction

https://github.com/adrianczuczka/mlkit-genai-coroutines-repro – clone, run, tap the button. The app declares `genai-prompt:1.0.0-beta4` plus `kotlinx-coroutines-android:1.10.2` (a current, valid version) and calls `GenerativeModel.download()`.

### Observed

```
java.lang.NoSuchMethodError: No static method cancel$default(Lkotlinx/coroutines/Job;Ljava/util/concurrent/CancellationException;ILjava/lang/Object;)V in class Lkotlinx/coroutines/Job; or its super classes
    at com.google.android.gms.internal.mlkit_genai_prompt.zzyl.invoke(com.google.mlkit:genai-prompt@@1.0.0-beta4:1)
    at kotlinx.coroutines.channels.ProduceKt.awaitClose(Produce.kt:69)
    at kotlinx.coroutines.channels.ProduceKt$awaitClose$1.invokeSuspend(Produce.kt:13)
    at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
    at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:232)
```

Observed on a Pixel 10 Pro XL (Android 17) inside the `download()` flow; the failure is class linkage, not AICore behavior.

### Root cause

kotlinx-coroutines switched interface compilation to `-Xjvm-default=all` in 1.11.0. Verified with `javap -p` against both published jars:

| | `Job.cancel$default` (interface static) | `Job$DefaultImpls.cancel$default` |
|---|---|---|
| coroutines 1.10.2 | absent | present |
| coroutines 1.11.0 | present | present (compat) |

genai-prompt 1.0.0-beta4 was compiled against >= 1.11.0 and its bytecode invokes the interface-static form, which does not exist on a 1.10.x runtime classpath.

### Expected

The published POM (or Gradle module metadata) declares the real minimum:

```xml
<dependency>
  <groupId>org.jetbrains.kotlinx</groupId>
  <artifactId>kotlinx-coroutines-core</artifactId>
  <version>1.11.0</version>
</dependency>
```

so dependency resolution upgrades automatically instead of crashing at runtime.

### Workaround

Pin `kotlinx-coroutines-android:1.11.0` (or newer) in the app.
