package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.di.hooksOf
import io.github.firstred.iptvproxy.listeners.HealthListener
import io.github.firstred.iptvproxy.listeners.MetricsListener
import org.koin.dsl.binds
import org.koin.dsl.module

val listenerModule = module {
    single { HealthListener() } binds hooksOf(HealthListener::class)
    single { MetricsListener() } binds hooksOf(MetricsListener::class)
}
