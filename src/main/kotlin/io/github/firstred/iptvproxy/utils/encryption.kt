package io.github.firstred.iptvproxy.utils

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.dotenv
import java.io.File
import java.net.URI
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class)
private fun getSecretSalt(): ByteArray {
    val confKey = "IPTV_PROXY_SALT"
    var salt = dotenv.get(confKey) // Should be a hex string

    if (null == salt) {
        salt = CryptographyRandom.nextBytes(16).toHexString()
        // Save new salt to .env file to ensure URLs stay valid
        val file = File(".env")

        // Create if not exists
        if (!file.exists()) file.createNewFile()

        // Read the file content
        var content = file.readText()

        // Add the salt to the file
        // Newline if necessary
        if (!content.endsWith("\n")) content += "\n"
        // Add the new salt
        content += "$confKey=\"$salt\"\n"

        // Write the content back to the file
        file.writeText(content)
    }

    return salt.hexToByteArray()
}

@OptIn(ExperimentalStdlibApi::class)
private fun getSecretIterations(): Int {
    val confKey = "IPTV_PROXY_KEY_ITERATIONS"
    var iterations = dotenv.get(confKey) // Should be a hex string

    if (null == iterations) {
        iterations = Random.nextInt(200_000, 1_000_000).toString()

        // Save new salt to .env file to ensure URLs stay valid
        val file = File(".env")

        // Create if not exists
        if (!file.exists()) file.createNewFile()

        // Read the file content
        var content = file.readText()

        // Add the salt to the file
        // Newline if necessary
        if (!content.endsWith("\n")) content += "\n"
        // Add the new salt
        content += "$confKey=$iterations\n"

        // Write the content back to the file
        file.writeText(content)
    }

    return iterations.toInt()
}

lateinit var privateSecretKey: AES.CBC.Key
private fun getSecretKey(): AES.CBC.Key {
    if (!::privateSecretKey.isInitialized) {
        val provider = CryptographyProvider.Default
        val secretDerivation = provider.get(PBKDF2).secretDerivation(
            digest = SHA256,
            iterations = getSecretIterations(),
            outputSize = 32.bytes,
            salt = getSecretSalt(),
        )

        val secret = secretDerivation.deriveSecretBlocking(config.appSecret.toByteArray())

        val decoder = provider.get(AES.CBC).keyDecoder()
        privateSecretKey = decoder.decodeFromByteStringBlocking(AES.Key.Format.RAW, secret)
    }

    return privateSecretKey
}

fun String.aesEncrypt(): ByteArray = getSecretKey().cipher().encryptBlocking(toByteArray())

@OptIn(ExperimentalStdlibApi::class)
fun String.aesEncryptToHexString(): String = aesEncrypt().toHexString()
@OptIn(ExperimentalStdlibApi::class)
fun URI.aesEncryptToHexString(): String = toString().aesEncrypt().toHexString()

fun ByteArray.aesDecrypt(): String = getSecretKey().cipher().decryptBlocking(this).decodeToString()

@OptIn(ExperimentalStdlibApi::class)
fun String.aesDecryptFromHexString(): String = hexToByteArray().aesDecrypt()
