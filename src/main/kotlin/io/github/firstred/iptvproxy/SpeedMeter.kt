package io.github.firstred.iptvproxy

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Created by kva on 21:39 27.03.2020
 */
class SpeedMeter(private val rid: String, private val reqStartNanos: Long) {
    private val time: Long
    private var bytes: Long = 0

    private var partTime: Long
    private var partBytes: Long = 0

    init {
        this.partTime = monotonicMillis
        this.time = this.partTime
    }

    fun processed(len: Long) {
        if (bytes == 0L) {
            LOG.debug("{}start", rid)
        }

        bytes += len
        partBytes += len

        val now = monotonicMillis
        if ((now - partTime) > 1000) {
            logPart()
        }
    }

    fun finish() {
        val now = monotonicMillis
        if ((now - partTime) > 1000) {
            logPart()
        }

        LOG.debug(
            "{}finished: {}, speed: {}/s, {}ms",
            rid, format(bytes),
            format(bytes * 1000 / (now - time)),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - reqStartNanos)
        )
    }

    private fun logPart() {
        val now = monotonicMillis
        val delta = max(1.0, (now - partTime).toDouble()).toLong()

        LOG.debug("{}progress: {} speed: {}/s", rid, format(partBytes), format(partBytes * 1000 / delta))
        partTime = now
        partBytes = 0
    }

    private fun format(value: Long): String {
        return if (value < KB) {
            String.format("%db", value)
        } else if (value < MB) {
            String.format("%.2fKb", value.toDouble() / KB)
        } else {
            String.format("%.2fMb", value.toDouble() / MB)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(SpeedMeter::class.java)

        private const val KB = 1024
        private const val MB = 1024 * 1024

        private val monotonicMillis: Long
            get() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
    }
}
