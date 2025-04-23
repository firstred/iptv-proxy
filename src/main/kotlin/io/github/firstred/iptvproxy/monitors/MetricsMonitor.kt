package io.github.firstred.iptvproxy.monitors

import org.koin.core.component.KoinComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MetricsMonitor : KoinComponent {
    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MetricsMonitor::class.java)
    }
}
