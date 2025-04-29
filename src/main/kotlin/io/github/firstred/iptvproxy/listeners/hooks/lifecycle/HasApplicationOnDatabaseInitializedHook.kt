package io.github.firstred.iptvproxy.listeners.hooks.lifecycle

import io.github.firstred.iptvproxy.listeners.hooks.ApplicationHook
import org.koin.java.KoinJavaComponent.getKoin

interface HasApplicationOnDatabaseInitializedHook : ApplicationHook {
    fun onApplicationDatabaseInitializedHook()

    companion object {
        fun dispatchHookToListeners() {
            for (listener in getKoin().getAll<HasApplicationOnDatabaseInitializedHook>()) {
                listener.onApplicationDatabaseInitializedHook()
            }
        }
    }
}
