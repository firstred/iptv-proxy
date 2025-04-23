package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.entities.IptvChannel
import io.github.firstred.iptvproxy.managers.ChannelManager
import org.koin.dsl.module
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

class IptvChannelsByReference : LinkedHashMap<String, IptvChannel>()

val channelModule = module {
    single<ScheduledExecutorService> {
        val s = ScheduledThreadPoolExecutor(config.schedulerThreadPoolSize) { _, _ -> }
        s.removeOnCancelPolicy = true
        s.maximumPoolSize = config.schedulerThreadPoolSize

        s
    }

    single { ChannelManager() }
    single { IptvChannelsByReference() }
}
