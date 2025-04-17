package io.github.firstred.iptvproxy.utils.digest

import java.security.MessageDigest

class Digest private constructor(algorithm: String) {
    private var md: MessageDigest? = null

    init {
        try {
            md = MessageDigest.getInstance(algorithm)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun digest(str: String): String {
        md!!.update(str.toByteArray())
        val digest = md!!.digest()
        md?.reset()
        return toHex(digest)
    }

    companion object {
        private val HEX = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

        fun sha512(): Digest {
            return Digest("SHA-512")
        }

        fun sha512(str: String): String {
            return sha512().digest(str)
        }

        fun sha256(): Digest {
            return Digest("SHA-256")
        }

        fun sha256(str: String): String {
            return sha256().digest(str)
        }

        fun md5(): Digest {
            return Digest("MD5")
        }

        fun md5(str: String): String {
            return md5().digest(str)
        }

        private fun toHex(digest: ByteArray): String {
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) {
                sb.append(HEX[(b.toInt() and 0xf0) shr 4]).append(
                    HEX[b.toInt() and 0x0f]
                )
            }

            return sb.toString()
        }
    }
}
