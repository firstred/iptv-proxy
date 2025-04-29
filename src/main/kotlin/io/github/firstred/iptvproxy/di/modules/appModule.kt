package io.github.firstred.iptvproxy.di.modules

import org.koin.dsl.module

val appModule = module {
    includes(
        coreModule,
        databaseModule,
        listenerModule,
        httpClientModule,
        userModule,
        iptvServerModule,
        channelModule,
    )
}
