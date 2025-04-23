package io.github.firstred.iptvproxy.di

import io.github.firstred.iptvproxy.IptvChannel
import io.github.firstred.iptvproxy.managers.ChannelManager
import org.koin.dsl.module

class IptvChannelsByReference : LinkedHashMap<String, IptvChannel>()

val channelModule = module {
    single { ChannelManager() }
    single { IptvChannelsByReference() }
}
