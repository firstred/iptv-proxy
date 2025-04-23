package io.github.firstred.iptvproxy.listeners.lifecycle

import io.github.firstred.iptvproxy.listeners.ApplicationHook
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
