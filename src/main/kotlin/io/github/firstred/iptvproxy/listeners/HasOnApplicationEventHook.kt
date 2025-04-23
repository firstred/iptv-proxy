package io.github.firstred.iptvproxy.listeners

import org.koin.java.KoinJavaComponent.getKoin

interface HasOnApplicationEventHook : ApplicationHook  {
    fun <T: Any> onApplicationEvent(event: T)

    companion object {
        fun <T: Any> dispatchHookToListeners(event: T) {
            for (listener in getKoin().getAll<HasOnApplicationEventHook>()) {
                listener.onApplicationEvent(event)
            }
        }
    }
}
