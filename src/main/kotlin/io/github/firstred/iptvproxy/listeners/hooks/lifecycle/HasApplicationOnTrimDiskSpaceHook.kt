package io.github.firstred.iptvproxy.listeners.hooks.lifecycle

import io.github.firstred.iptvproxy.listeners.hooks.ApplicationHook
import org.koin.java.KoinJavaComponent.getKoin

interface HasApplicationOnTrimDiskSpaceHook : ApplicationHook {
    fun onApplicationTrimDiskSpaceHook()

    companion object {
        fun dispatchHookToListeners() {
            for (listener in getKoin().getAll<HasApplicationOnTrimDiskSpaceHook>()) {
                listener.onApplicationTrimDiskSpaceHook()
            }
        }
    }
}
