/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.EventProjectFile
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
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

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
            "Unsupported course order format."
        }

        val iterations = fields[2].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid course order format.")
        val salt = decoder.decode(fields[3])
        val nonce = decoder.decode(fields[4])
        val encrypted = decoder.decode(fields[5])
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(trimmedPassword, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
            cipher.doFinal(encrypted).decodeToString()
        }.getOrElse {
            throw IllegalArgumentException("Password did not unlock course order.")
        }
    }

    fun encryptCourseInfo(courseInfo: ProtectedCourseInfo, password: String): String =
        encrypt(json.encodeToString(courseInfo), password)

    fun decryptCourseInfo(encryptedValue: String, password: String): ProtectedCourseInfo =
        json.decodeFromString(ProtectedCourseInfo.serializer(), decrypt(encryptedValue, password))

    fun reencryptProjectCourseProtection(
        projectFile: EventProjectFile,
        oldPassword: String,
        newPassword: String
    ): EventProjectFile {
        val trimmedOldPassword = oldPassword.trim()
        val trimmedNewPassword = newPassword.trim()
        require(trimmedOldPassword.isNotEmpty()) {
            "Current Event Password cannot be blank."
        }
        require(trimmedNewPassword.isNotEmpty()) {
            "New Event Password cannot be blank."
        }

        val categories = projectFile.raceData.categories.map { categoryData ->
            val category = categoryData.category
            val reencryptedIdealOrder = category.encryptedIdealOrder?.takeIf { it.isNotBlank() }?.let { encryptedValue ->
                encrypt(decrypt(encryptedValue, trimmedOldPassword), trimmedNewPassword)
            }
            val reencryptedCourseInfo = category.encryptedCourseInfo?.takeIf { it.isNotBlank() }?.let { encryptedValue ->
                encryptCourseInfo(decryptCourseInfo(encryptedValue, trimmedOldPassword), trimmedNewPassword)
            }
            categoryData.copy(
                category = category.copy(
                    encryptedIdealOrder = reencryptedIdealOrder,
                    encryptedCourseInfo = reencryptedCourseInfo
                )
            )
        }
        return projectFile.copy(
            raceData = projectFile.raceData.copy(categories = categories)
        )
    }

    private fun secretKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
