package io.github.firstred.iptvproxy.di

import io.github.firstred.iptvproxy.IptvProxyService
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvUtils
import io.github.firstred.iptvproxy.events.EventBus
import io.github.firstred.iptvproxy.monitors.HealthMonitor
import org.koin.dsl.module

val coreModule = module {
    single { IptvProxyService() }
    single { XmltvUtils() }
    single { EventBus() }
    single { HealthMonitor() }
}
