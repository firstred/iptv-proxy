package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.monitors.HealthMonitor
import io.github.firstred.iptvproxy.monitors.MetricsMonitor
import org.koin.dsl.module

val coreModule = module {
    single { HealthMonitor() }
    single { MetricsMonitor() }
}
