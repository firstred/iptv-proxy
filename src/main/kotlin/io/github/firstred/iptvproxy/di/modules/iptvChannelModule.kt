package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.di.hooksOf
import io.github.firstred.iptvproxy.managers.ChannelManager
import org.koin.dsl.binds
import org.koin.dsl.module

val channelModule = module {
    single { ChannelManager() } binds hooksOf(ChannelManager::class)
}
