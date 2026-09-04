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

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher

/**
 * Desktop compatibility facade for the shared protected-course format.
 *
 * New platform code should use [ProtectedCourseCipher] directly.
 */
object DesktopProtectedCourseOrder {
    fun encrypt(plainText: String, password: String): String =
        ProtectedCourseCipher.encrypt(plainText, password)

    fun decrypt(encryptedValue: String, password: String): String =
        ProtectedCourseCipher.decrypt(encryptedValue, password)

    fun encryptCourseInfo(courseInfo: ProtectedCourseInfo, password: String): String =
        ProtectedCourseCipher.encryptCourseInfo(courseInfo, password)

    fun decryptCourseInfo(encryptedValue: String, password: String): ProtectedCourseInfo =
        ProtectedCourseCipher.decryptCourseInfo(encryptedValue, password)

    fun reencryptProjectCourseProtection(
        projectFile: EventProjectFile,
        oldPassword: String,
        newPassword: String
    ): EventProjectFile =
        ProtectedCourseCipher.reencryptProjectCourseProtection(
            projectFile = projectFile,
            oldPassword = oldPassword,
            newPassword = newPassword
        )

    fun removeProjectCourseProtection(
        projectFile: EventProjectFile,
        password: String
    ): EventProjectFile =
        ProtectedCourseCipher.removeProjectCourseProtection(projectFile, password)

    fun protectProjectCourseData(
        projectFile: EventProjectFile,
        password: String
    ): EventProjectFile =
        ProtectedCourseCipher.protectProjectCourseData(projectFile, password)
}
