package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.IptvUpdateScheduler
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject

private val iptvProxyServiceScope = CoroutineScope(Job())

fun Application.configureScheduler() {
    val scheduler: IptvUpdateScheduler by inject()

    iptvProxyServiceScope.launch { scheduler.start() }
    Runtime.getRuntime().addShutdownHook(Thread { scheduler.stop() })
}
