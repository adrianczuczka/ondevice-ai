package dev.ondeviceai

import android.content.Context
import androidx.startup.Initializer

/**
 * Application context captured at startup – needed by the ML Kit feature
 * clients (Summarization, Proofreading, …) when they get wired in.
 */
internal object AppContextHolder {
    @Volatile
    lateinit var context: Context
    val isInitialized: Boolean get() = ::context.isInitialized
}

/** Runs automatically via androidx.startup; no app code needed. */
class OnDeviceAiInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        AppContextHolder.context = context.applicationContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
