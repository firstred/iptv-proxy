package io.github.firstred.iptvproxy

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by kva on 14:02 20.01.2020
 */
object RequestCounter {
    private val isDebug = LoggerFactory.getLogger(RequestCounter::class.java).isDebugEnabled

    private val counter = AtomicInteger()

    fun next(): String {
        if (isDebug) {
            val c = counter.incrementAndGet() % 100000
            return String.format("%05d| ", c)
        } else {
            return ""
        }
    }
}
