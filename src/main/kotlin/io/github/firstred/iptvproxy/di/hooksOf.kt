package io.github.firstred.iptvproxy.di

import io.github.firstred.iptvproxy.listeners.hooks.HasOnApplicationEventHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnStartHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTerminateHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTrimDiskSpaceHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTrimMemoryHook
import kotlin.reflect.KClass

/**
 * Return all the implemented hooks of a class
 */
fun <T : Any> hooksOf(clazz: KClass<T>): Array<KClass<*>> {
    return clazz.supertypes.map { it.classifier as KClass<*> }.filter {
        it in arrayOf(
            HasApplicationOnStartHook::class,
            HasApplicationOnTerminateHook::class,
            HasApplicationOnTrimDiskSpaceHook::class,
            HasApplicationOnTrimMemoryHook::class,

            HasOnApplicationEventHook::class,
        )
    }.toTypedArray()
}
