package io.github.firstred.iptvproxy.classes

import io.github.firstred.iptvproxy.serialization.serializers.UIntWithUnderscoreSerializer
import io.github.firstred.iptvproxy.utils.aesEncryptToHexString
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable

@Serializable
class IptvUser(
    val username: String,
    val password: String,
    @Serializable(with = UIntWithUnderscoreSerializer::class) val maxConnections: UInt,
    val channelBlacklist: List<String> = emptyList(),
    val channelWhitelist: List<String> = emptyList(),
    val categoryBlacklist: List<String> = emptyList(),
    val categoryWhitelist: List<String> = emptyList(),
    val moviesEnabled: Boolean = true,
    val seriesEnabled: Boolean = true,
) {
    val semaphore = Semaphore(maxConnections.toInt())

    fun toEncryptedAccountHexString() = "${username}_$password".aesEncryptToHexString()
}
