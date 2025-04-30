package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.di.hooksOf
import io.github.firstred.iptvproxy.listeners.HealthListener
import org.koin.dsl.binds
import org.koin.dsl.module

val healthcheckModule = module {
    single { HealthListener() } binds hooksOf(HealthListener::class)
}
