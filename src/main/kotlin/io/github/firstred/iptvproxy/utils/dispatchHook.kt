package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.listeners.hooks.ApplicationHook
import io.github.firstred.iptvproxy.listeners.hooks.HasOnApplicationEventHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnDatabaseInitializedHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnStartHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTerminateHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTrimDiskSpaceHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTrimMemoryHook
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

private val LOG = LoggerFactory.getLogger("dispatchHook")

fun dispatchHook(
    hook: KClass<out ApplicationHook>,
    vararg ts: Any,
) {
    LOG.info("Dispatching hook $hook to listeners")

    when (hook) {
        HasApplicationOnStartHook::class -> HasApplicationOnStartHook.dispatchHookToListeners()
        HasApplicationOnTerminateHook::class -> HasApplicationOnTerminateHook.dispatchHookToListeners()
        HasApplicationOnTrimMemoryHook::class -> HasApplicationOnTrimMemoryHook.dispatchHookToListeners()
        HasApplicationOnTrimDiskSpaceHook::class -> HasApplicationOnTrimDiskSpaceHook.dispatchHookToListeners()

        HasApplicationOnDatabaseInitializedHook::class -> HasApplicationOnDatabaseInitializedHook.dispatchHookToListeners()

        HasOnApplicationEventHook::class -> HasOnApplicationEventHook.dispatchHookToListeners(ts[0])
    }
}
