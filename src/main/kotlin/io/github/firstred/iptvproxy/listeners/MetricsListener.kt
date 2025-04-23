package io.github.firstred.iptvproxy.listeners

import org.koin.core.component.KoinComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MetricsListener : KoinComponent {
    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MetricsListener::class.java)
    }
}
