package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.di.hooksOf
import io.github.firstred.iptvproxy.monitors.HealthMonitor
import io.github.firstred.iptvproxy.monitors.MetricsMonitor
import org.koin.dsl.binds
import org.koin.dsl.module

val coreModule = module {
    single { HealthMonitor() } binds hooksOf(HealthMonitor::class)
    single { MetricsMonitor() } binds hooksOf(MetricsMonitor::class)
}
