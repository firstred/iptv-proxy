package io.github.firstred.iptvproxy

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class IptvChannel(
    val id: String,
    val name: String,
    val logo: String,
    groups: Collection<String>,
    val xmltvId: String,
    val catchupDays: Int
) {
    val groups: Set<String> =
        Collections.unmodifiableSet(TreeSet(groups))

    private val rand = Random()

    private val serverChannels: MutableList<IptvServerChannel> = ArrayList()

    fun addServerChannel(serverChannel: IptvServerChannel) {
        serverChannels.add(serverChannel)
    }

    fun acquire(userId: String?): IptvServerChannel? {
        val scs: List<IptvServerChannel> = ArrayList(serverChannels)
        Collections.shuffle(scs, rand)

        for (sc in scs) {
            if (sc.acquire(userId)) {
                return sc
            }
        }

        LOG.info("[{}] can't acquire channel: {}", userId, name)

        return null
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvChannel::class.java)
    }
}
