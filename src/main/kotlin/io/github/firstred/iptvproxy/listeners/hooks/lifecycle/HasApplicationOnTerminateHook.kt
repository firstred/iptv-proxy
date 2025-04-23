package io.github.firstred.iptvproxy.listeners.hooks.lifecycle

import io.github.firstred.iptvproxy.listeners.hooks.ApplicationHook
import org.koin.java.KoinJavaComponent.getKoin

interface HasApplicationOnTerminateHook : ApplicationHook {
    fun onApplicationTerminateHook()

    companion object {
        fun dispatchHookToListeners() {
            for (listener in getKoin().getAll<HasApplicationOnTerminateHook>()) {
                listener.onApplicationTerminateHook()
            }
        }
    }
}
