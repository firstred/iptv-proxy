package io.github.firstred.iptvproxy.listeners.lifecycle

import io.github.firstred.iptvproxy.listeners.ApplicationHook
import org.koin.java.KoinJavaComponent.getKoin

interface HasApplicationOnTrimMemoryHook : ApplicationHook {
    fun onApplicationTrimMemoryHook()

    companion object {
        fun dispatchHookToListeners() {
            for (listener in getKoin().getAll<HasApplicationOnTrimMemoryHook>()) {
                listener.onApplicationTrimMemoryHook()
            }
        }
    }
}
