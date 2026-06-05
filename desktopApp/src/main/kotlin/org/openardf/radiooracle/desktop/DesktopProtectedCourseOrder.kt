package org.openardf.radiooracle.desktop

import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object DesktopProtectedCourseOrder {
    private const val PREFIX = "ro-ideal-v1"
    private const val KDF = "pbkdf2-sha256"
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private val random = SecureRandom()
    private val base64: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    fun encrypt(plainText: String, password: String): String {
        val trimmedPassword = password.trim()
        require(trimmedPassword.isNotEmpty()) {
            "Password cannot be blank."
        }

        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(trimmedPassword, salt), GCMParameterSpec(TAG_BITS, nonce))
        val encrypted = cipher.doFinal(plainText.trim().encodeToByteArray())
        return listOf(
            PREFIX,
            KDF,
            ITERATIONS.toString(),
            base64.encodeToString(salt),
            base64.encodeToString(nonce),
            base64.encodeToString(encrypted)
        ).joinToString(":")
    }

    fun decrypt(encryptedValue: String, password: String): String {
        val trimmedPassword = password.trim()
        require(trimmedPassword.isNotEmpty()) {
            "Password cannot be blank."
        }

        val fields = encryptedValue.split(":")
        require(fields.size == 6 && fields[0] == PREFIX && fields[1] == KDF) {
            "Unsupported protected course order format."
        }

        val iterations = fields[2].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid protected course order format.")
        val salt = decoder.decode(fields[3])
        val nonce = decoder.decode(fields[4])
        val encrypted = decoder.decode(fields[5])
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(trimmedPassword, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
            cipher.doFinal(encrypted).decodeToString()
        }.getOrElse {
            throw IllegalArgumentException("Password did not unlock protected course order.")
        }
    }

    private fun secretKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
