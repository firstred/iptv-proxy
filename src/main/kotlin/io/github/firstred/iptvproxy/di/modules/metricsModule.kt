package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.di.hooksOf
import io.github.firstred.iptvproxy.listeners.MetricsListener
import org.koin.dsl.binds
import org.koin.dsl.module

val metricsModule = module {
    single { MetricsListener() } binds hooksOf(MetricsListener::class)
}
