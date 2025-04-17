package io.github.firstred.iptvproxy

import io.undertow.io.IoCallback
import io.undertow.io.Sender
import io.undertow.server.HttpServerExchange
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Flow
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile

class IptvStream(
    private val exchange: HttpServerExchange,
    private val rid: String,
    private val user: IptvUser,
    private val userTimeout: Long,
    private val readTimeout: Long,
    private val scheduler: ScheduledExecutorService,
    startNanos: Long
) : Flow.Subscriber<List<ByteBuffer>> {
    private val buffers: Queue<ByteBuffer> = LinkedBlockingQueue()
    private val busy = AtomicBoolean()

    @Volatile
    private var subscription: Flow.Subscription? = null

    private val readMeter = SpeedMeter(rid + "read: ", startNanos)
    private val writeMeter = SpeedMeter(rid + "write: ", startNanos)

    @Volatile
    private var timeoutTime: Long = 0

    @Volatile
    private var timeoutFuture: ScheduledFuture<*>

    private val startNanos: Long

    init {
        updateReadTimeout()
        timeoutFuture = scheduler.schedule({ this.onTimeout() }, readTimeout, TimeUnit.MILLISECONDS)

        this.startNanos = startNanos
    }

    private fun updateReadTimeout() {
        timeoutTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) + readTimeout
    }

    private fun onTimeout() {
        val now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
        if (now >= timeoutTime) {
            LOG.warn("{}read timeout on loading stream", rid)
            finish()
        } else {
            timeoutFuture = scheduler.schedule({ this.onTimeout() }, timeoutTime - now, TimeUnit.MILLISECONDS)
        }
    }

    private fun updateTimeouts() {
        user.lock()
        try {
            user.setExpireTime(System.currentTimeMillis() + userTimeout)
        } finally {
            user.unlock()
        }

        updateReadTimeout()
    }

    override fun onSubscribe(subscription: Flow.Subscription) {
        if (this.subscription != null) {
            LOG.error("{}already subscribed", rid)
            subscription.cancel()
            return
        }

        this.subscription = subscription
        subscription.request(Long.MAX_VALUE)
    }

    private fun finish() {
        // cancel any timeouts
        timeoutFuture.cancel(false)

        // subscription can't be null at this place
        subscription!!.cancel()

        onNext(END_ARRAY_MARKER)
    }

    override fun onNext(item: List<ByteBuffer>) {
        var len = 0
        for (b in item) {
            len += b.remaining()
        }
        readMeter.processed(len.toLong())

        if (len > 0) {
            updateTimeouts()
        }

        buffers.addAll(item)

        if (busy.compareAndSet(false, true)) {
            sendNext()
        }

        subscription!!.request(Long.MAX_VALUE)
    }

    private fun sendNext() {
        var b: ByteBuffer?
        while ((buffers.poll().also { b = it }) != null) {
            b?.let { if (!sendNext(it)) return }
        }

        busy.set(false)
    }

    private fun sendNext(b: ByteBuffer): Boolean {
        updateTimeouts()

        if (b === END_MARKER) {
            exchange.endExchange()
            writeMeter.finish()
            return true
        }

        val completed = AtomicBoolean(false)

        val len = b.remaining()
        exchange.responseSender.send(b, object : IoCallback {
            override fun onComplete(exchange: HttpServerExchange, sender: Sender) {
                writeMeter.processed(len.toLong())

                if (!completed.compareAndSet(false, true)) {
                    sendNext()
                }
            }

            override fun onException(exchange: HttpServerExchange, sender: Sender, exception: IOException) {
                LOG.warn("{}error on sending stream: {}", rid, exception.message)
                finish()
            }
        })

        return !completed.compareAndSet(false, true)
    }

    override fun onError(throwable: Throwable) {
        LOG.warn("{}error on loading stream: {}", rid, throwable.printStackTrace())
        finish()
    }

    override fun onComplete() {
        readMeter.finish()
        finish()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvStream::class.java)

        private val END_MARKER: ByteBuffer = ByteBuffer.allocate(0)
        private val END_ARRAY_MARKER = listOf(END_MARKER)
    }
}
