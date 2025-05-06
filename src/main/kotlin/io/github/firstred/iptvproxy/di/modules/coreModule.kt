package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.config
import org.koin.dsl.module
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

val coreModule = module {
    single<ScheduledExecutorService> {
        val s = ScheduledThreadPoolExecutor(config.schedulerThreadPoolSize.toInt()) { _, _ -> }
        s.removeOnCancelPolicy = true
        s.maximumPoolSize = config.schedulerThreadPoolSize.toInt()

        s
    }
}
