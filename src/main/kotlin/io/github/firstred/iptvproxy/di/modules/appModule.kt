package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.di.httpClientModule
import org.koin.dsl.module

val appModule = module {
    includes(
        coreModule,
        httpClientModule,
        userModule,
        iptvServerModule,
        channelModule,
        listenersModule,
    )
}
