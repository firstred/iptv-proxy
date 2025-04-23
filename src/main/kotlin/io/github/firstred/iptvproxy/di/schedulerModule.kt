package io.github.firstred.iptvproxy.di

import io.github.firstred.iptvproxy.IptvUpdateScheduler
import io.github.firstred.iptvproxy.config
import org.koin.dsl.module
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

private val LOG: Logger = LoggerFactory.getLogger("Scheduler")

val schedulerModule = module {
    single<ScheduledExecutorService> {
        val s = ScheduledThreadPoolExecutor(config.schedulerThreadPoolSize) { _, _ -> LOG.error("execution rejected") }
        s.removeOnCancelPolicy = true
        s.maximumPoolSize = config.schedulerThreadPoolSize

        s
    }

    single { IptvUpdateScheduler() }
}
