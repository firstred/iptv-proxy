package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.listeners.ApplicationHook
import io.github.firstred.iptvproxy.listeners.HasOnApplicationEventHook
import io.github.firstred.iptvproxy.listeners.lifecycle.HasApplicationOnStartHook
import io.github.firstred.iptvproxy.listeners.lifecycle.HasApplicationOnTerminateHook
import io.github.firstred.iptvproxy.listeners.lifecycle.HasApplicationOnTrimDiskSpaceHook
import io.github.firstred.iptvproxy.listeners.lifecycle.HasApplicationOnTrimMemoryHook
import kotlin.reflect.KClass

fun dispatchHook(
    hook: KClass<out ApplicationHook>,
    vararg ts: Any,
) {
    when (hook) {
        HasApplicationOnStartHook::class -> HasApplicationOnStartHook.dispatchHookToListeners()
        HasApplicationOnTerminateHook::class -> HasApplicationOnTerminateHook.dispatchHookToListeners()
        HasApplicationOnTrimMemoryHook::class -> HasApplicationOnTrimMemoryHook.dispatchHookToListeners()
        HasApplicationOnTrimDiskSpaceHook::class -> HasApplicationOnTrimDiskSpaceHook.dispatchHookToListeners()
        HasOnApplicationEventHook::class -> HasOnApplicationEventHook.dispatchHookToListeners(ts[0])
    }
}
