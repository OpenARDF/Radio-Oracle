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

import org.openardf.radiooracle.shared.event.CourseControlEditRequest
import org.openardf.radiooracle.shared.event.CourseControlEdits
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventProjectFile

/** Applies ordinary control fields without exposing encrypted course payloads in the result. */
internal object DesktopProtectedControlEditor {
    fun applyDetails(
        projectFile: EventProjectFile,
        courseState: DesktopProtectedCourseState,
        edit: CourseControlEditRequest,
        password: String?
    ): EventProjectFile {
        val current = requireNotNull(projectFile.raceData.controls.firstOrNull { it.id == edit.controlId }) {
            "Control was not found: ${edit.controlId}"
        }
        val identityChanged = current.label != edit.label.trim() ||
            current.siCode != edit.siCode ||
            current.type != edit.type ||
            current.publicLabel.orEmpty() != edit.publicLabel.trim()
        // Notes and scoring metadata do not touch course bindings, so preserve existing ciphertext.
        if (!identityChanged || projectFile.courseDataStorageMode() == DesktopCourseDataStorageMode.Plaintext) {
            return CourseControlEdits.applyDetails(projectFile, edit)
        }
        val storagePassword = projectFile.courseDataPassword(password)

        fun decrypted(data: EventCategoryData): EventCategoryData = data.copy(
            category = data.category.copy(
                idealOrder = courseState.protectedIdealOrderByCategoryId[data.category.id]
                    ?.takeIf {
                        // Retain an encrypted empty-order marker so applying an unrelated
                        // identity edit cannot silently remove Race Password protection.
                        data.category.encryptedIdealOrder?.isNotBlank() == true ||
                            data.category.idealOrder != null
                    },
                encryptedIdealOrder = null,
                courseInfo = courseState.protectedCourseInfoByCategoryId[data.category.id],
                encryptedCourseInfo = null
            )
        )

        // Shared catalog rules need plaintext course bindings while they cascade identity and
        // role changes. Re-encrypt every payload before returning the temporary review candidate.
        val plaintext = projectFile.copy(
            raceData = projectFile.raceData.copy(
                categories = projectFile.raceData.categories.map(::decrypted),
                courseMappings = projectFile.raceData.courseMappings.map(::decrypted)
            )
        )
        val updatedPlaintext = CourseControlEdits.applyDetails(plaintext, edit)
        // Reuse the established whole-project encryption boundary instead of maintaining a
        // second per-category protection loop for control edits.
        return DesktopProtectedCourseOrder.protectProjectCourseData(
            projectFile = updatedPlaintext,
            password = requireNotNull(storagePassword)
        )
    }
}
