package io.github.firstred.iptvproxy.di

import org.koin.dsl.module

val appModule = module {
    includes(
        coreModule,
        schedulerModule,
        httpClientModule,
        userModule,
        iptvServerModule,
        channelModule,
    )
}
