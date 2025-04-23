package io.github.firstred.iptvproxy.listeners.hooks.lifecycle

import io.github.firstred.iptvproxy.listeners.hooks.ApplicationHook
import org.koin.java.KoinJavaComponent.getKoin

interface HasApplicationOnStartHook : ApplicationHook {
    fun onApplicationStartHook()

    companion object {
        fun dispatchHookToListeners() {
            for (listener in getKoin().getAll<HasApplicationOnStartHook>()) {
                listener.onApplicationStartHook()
            }
        }
    }
}
