package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnStartHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTerminateHook
import io.github.firstred.iptvproxy.utils.dispatchHook
import io.ktor.server.application.*

fun Application.startLifecycleHooks() {
    dispatchHook(HasApplicationOnStartHook::class)

    Runtime.getRuntime().addShutdownHook(Thread {
        dispatchHook(HasApplicationOnTerminateHook::class)
    })
}
