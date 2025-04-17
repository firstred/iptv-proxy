package io.github.firstred.iptvproxy

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.BiConsumer
import kotlin.concurrent.Volatile
import kotlin.math.max

class IptvUser(
    val id: String,
    private val scheduler: ScheduledExecutorService,
    private val unregister: BiConsumer<String, IptvUser>
) {
    private val lock: Lock = ReentrantLock()

    private var expireTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(1)

    private var timeoutTime: Long = 0
    private var timeoutFuture: ScheduledFuture<*>? = null

    @Volatile
    private var serverChannel: IptvServerChannel? = null

    init {
        LOG.info("[{}] user created", id)
    }

    fun lock() {
        lock.lock()
    }

    fun unlock() {
        // small optimization to have less removes and Future recreates in priority queue
        if (timeoutTime == 0L || timeoutTime > expireTime) {
            schedule()
        }

        lock.unlock()
    }

    private fun schedule() {
        if (timeoutFuture != null) {
            timeoutFuture!!.cancel(false)
        }

        // 100ms jitter
        timeoutFuture = scheduler.schedule({ this.removeIfNeed() }, expireDelay() + 100, TimeUnit.MILLISECONDS)
        timeoutTime = expireTime
    }

    private fun removeIfNeed() {
        lock()
        try {
            if (System.currentTimeMillis() < expireTime) {
                timeoutTime = 0
                schedule()
            } else {
                unregister.accept(id, this)
                releaseChannel()

                LOG.info("[{}] user removed", id)
            }
        } finally {
            unlock()
        }
    }

    private fun expireDelay(): Long {
        return max(100.0, (expireTime - System.currentTimeMillis()).toDouble()).toLong()
    }

    fun setExpireTime(expireTime: Long) {
        this.expireTime = max(this.expireTime.toDouble(), expireTime.toDouble()).toLong()
    }

    fun releaseChannel() {
        if (serverChannel != null) {
            serverChannel!!.release(id)
            serverChannel = null
        }
    }

    fun getServerChannel(channel: IptvChannel): IptvServerChannel? {
        if (serverChannel != null) {
            if (serverChannel?.channelId == channel.id) {
                return serverChannel
            }

            serverChannel!!.release(id)
        }

        expireTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(1)

        serverChannel = channel.acquire(id)

        return serverChannel
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvUser::class.java)
    }
}
