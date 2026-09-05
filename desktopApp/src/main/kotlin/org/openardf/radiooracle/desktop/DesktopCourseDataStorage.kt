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

import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo

/** Existing encryption is preserved; otherwise callers may store course data as plaintext. */
internal enum class DesktopCourseDataStorageMode {
    Plaintext,
    Encrypted
}

internal fun EventProjectFile.courseDataStorageMode(): DesktopCourseDataStorageMode =
    if (hasEncryptedCategoryData()) DesktopCourseDataStorageMode.Encrypted
    else DesktopCourseDataStorageMode.Plaintext

internal fun EventProjectFile.courseDataPassword(password: String?): String? {
    val trimmedPassword = password?.trim()?.takeIf(String::isNotEmpty)
    return when (courseDataStorageMode()) {
        // A caller may still explicitly opt into encrypted output. The UI omits
        // the password for plaintext files, so routine tool use stays plaintext.
        DesktopCourseDataStorageMode.Plaintext -> trimmedPassword
        DesktopCourseDataStorageMode.Encrypted -> requireNotNull(trimmedPassword) {
            "Race Password is required for encrypted course data."
        }
    }
}

internal fun EventCategory.storedCourseInfo(password: String?): ProtectedCourseInfo? =
    encryptedCourseInfo
        ?.takeIf(String::isNotBlank)
        ?.let { encrypted ->
            val unlockedPassword = requireNotNull(password?.trim()?.takeIf(String::isNotEmpty)) {
                "Race Password is required for encrypted course data."
            }
            DesktopProtectedCourseOrder.decryptCourseInfo(encrypted, unlockedPassword)
        }
        ?: courseInfo

internal fun EventCategory.storedIdealOrder(password: String?): String =
    encryptedIdealOrder
        ?.takeIf(String::isNotBlank)
        ?.let { encrypted ->
            val unlockedPassword = requireNotNull(password?.trim()?.takeIf(String::isNotEmpty)) {
                "Race Password is required for encrypted course data."
            }
            DesktopProtectedCourseOrder.decrypt(encrypted, unlockedPassword)
        }
        ?: idealOrder.orEmpty()

internal fun EventProjectFile.withStoredCourseInfo(
    categoryId: String,
    courseInfo: ProtectedCourseInfo?,
    password: String?
): EventProjectFile = when (val unlockedPassword = courseDataPassword(password)) {
    null -> EventProjectEditor.updateCategoryCourseInfo(this, categoryId, courseInfo)
    else -> EventProjectEditor.updateCategoryEncryptedCourseInfo(
        this,
        categoryId,
        courseInfo?.let { DesktopProtectedCourseOrder.encryptCourseInfo(it, unlockedPassword) }
    )
}

internal fun EventProjectFile.withStoredIdealOrder(
    categoryId: String,
    idealOrder: String?,
    password: String?
): EventProjectFile = when (val unlockedPassword = courseDataPassword(password)) {
    null -> EventProjectEditor.updateCategoryIdealOrder(this, categoryId, idealOrder)
    else -> EventProjectEditor.updateCategoryEncryptedIdealOrder(
        this,
        categoryId,
        idealOrder?.takeIf(String::isNotBlank)?.let {
            DesktopProtectedCourseOrder.encrypt(it, unlockedPassword)
        }
    )
}

internal fun EventProjectFile.hasProtectedCategoryData(): Boolean =
    protectedCourseDataContainers(raceData).any {
        it.category.encryptedIdealOrder?.isNotBlank() == true ||
            it.category.encryptedCourseInfo?.isNotBlank() == true ||
            it.category.idealOrder?.isNotBlank() == true ||
            it.category.courseInfo != null
    }

internal fun EventProjectFile.hasEncryptedCategoryData(): Boolean =
    protectedCourseDataContainers(raceData).any {
        it.category.encryptedIdealOrder?.isNotBlank() == true ||
            it.category.encryptedCourseInfo?.isNotBlank() == true
    }

internal fun EventProjectFile.hasUnencryptedCategoryData(): Boolean =
    protectedCourseDataContainers(raceData).any {
        it.category.idealOrder != null || it.category.courseInfo != null
    }

internal fun EventProjectFile.hasLockedProtectedCourseData(isProtectedCourseOrderUnlocked: Boolean): Boolean =
    !isProtectedCourseOrderUnlocked && hasEncryptedCategoryData()
