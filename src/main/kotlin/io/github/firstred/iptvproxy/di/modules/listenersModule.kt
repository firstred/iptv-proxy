package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.listeners.SchedulerListener
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.binds
import org.koin.dsl.module

val listenersModule = module {
    singleOf(::SchedulerListener) binds hooksOf(SchedulerListener::class)
}
