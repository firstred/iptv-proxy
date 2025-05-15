package io.github.firstred.iptvproxy.classes

import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTerminateHook
import java.util.Timer
import java.util.concurrent.ConcurrentHashMap

class CacheTimers : ConcurrentHashMap<String, Timer>(), HasApplicationOnTerminateHook {
    fun add(key: String, timer: Timer) {
        this[key]?.cancel()
        this[key] = timer
    }

    fun cancel(key: String) {
        this[key]?.cancel()
        remove(key)
    }

    fun cancelAll() {
        forEach { (_, timer) ->
            timer.cancel()
        }
        clear()
    }

    fun cancelAndRemove(key: String) {
        this[key]?.cancel()
        remove(key)
    }

    override fun onApplicationTerminateHook() {
        cancelAll()
    }
}
