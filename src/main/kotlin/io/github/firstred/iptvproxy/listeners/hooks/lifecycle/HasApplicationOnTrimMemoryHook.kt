package io.github.firstred.iptvproxy.listeners.hooks.lifecycle

import io.github.firstred.iptvproxy.listeners.hooks.ApplicationHook
import org.koin.mp.KoinPlatform.getKoin

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
