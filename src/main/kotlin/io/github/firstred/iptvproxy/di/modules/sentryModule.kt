
package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.config
import io.sentry.MonitorConfig
import io.sentry.MonitorSchedule
import io.sentry.MonitorScheduleUnit
import org.koin.core.qualifier.named
import org.koin.dsl.module

@Suppress("UnstableApiUsage")
val sentryModule = module {
    single(named("update-channels")) {
        MonitorConfig(MonitorSchedule.interval(config.updateInterval.inWholeMinutes.toInt(), MonitorScheduleUnit.MINUTE))
    }
    single(named("cleanup-channels")) {
        MonitorConfig(MonitorSchedule.interval(config.cleanupInterval.inWholeMinutes.toInt(), MonitorScheduleUnit.MINUTE))
    }
    single(named("cleanup-cache")) {
        MonitorConfig(MonitorSchedule.interval(config.cleanupInterval.inWholeMinutes.toInt(), MonitorScheduleUnit.MINUTE))
    }
}

