package io.github.firstred.iptvproxy.di.modules

import org.koin.dsl.module

val appModule = module {
    includes(
        coreModule,

        cacheModule,
        databaseModule,
        httpClientModule,

        userModule,
        iptvServerModule,
        channelModule,

        sentryModule,

        healthcheckModule,
        metricsModule,
    )
}
