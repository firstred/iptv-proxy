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
) {
    val semaphore = Semaphore(maxConnections.toInt())

    fun toEncryptedAccountHexString() = "${username}_$password".aesEncryptToHexString()
}
