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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo

class DesktopCourseDataStorageTest {
    @Test
    fun plaintextProjectReadsAndWritesCourseDataWithoutPassword() {
        val courseInfo = ProtectedCourseInfo(
            idealOrder = "1 2 3",
            lengthMeters = 2_300,
            climbMeters = 48
        )

        val updated = project()
            .withStoredIdealOrder(CATEGORY_ID, "1 2 3", null)
            .withStoredCourseInfo(CATEGORY_ID, courseInfo, null)
        val category = updated.raceData.categories.single().category

        assertEquals(DesktopCourseDataStorageMode.Plaintext, updated.courseDataStorageMode())
        assertEquals("1 2 3", category.storedIdealOrder(null))
        assertEquals(courseInfo, category.storedCourseInfo(null))
        assertEquals("1 2 3", category.idealOrder)
        assertEquals(courseInfo, category.courseInfo)
        assertNull(category.encryptedIdealOrder)
        assertNull(category.encryptedCourseInfo)
    }

    @Test
    fun explicitPasswordStillAllowsCallerToOptIntoEncryption() {
        val courseInfo = ProtectedCourseInfo(idealOrder = "1 2 3", lengthMeters = 2_300)

        val updated = project()
            .withStoredIdealOrder(CATEGORY_ID, "1 2 3", PASSWORD)
            .withStoredCourseInfo(CATEGORY_ID, courseInfo, PASSWORD)
        val category = updated.raceData.categories.single().category

        assertEquals(DesktopCourseDataStorageMode.Encrypted, updated.courseDataStorageMode())
        assertEquals("1 2 3", category.storedIdealOrder(PASSWORD))
        assertEquals(courseInfo, category.storedCourseInfo(PASSWORD))
        assertNull(category.idealOrder)
        assertNull(category.courseInfo)
        assertTrue(!category.encryptedIdealOrder.isNullOrBlank())
        assertTrue(!category.encryptedCourseInfo.isNullOrBlank())
    }

    @Test
    fun encryptedProjectRequiresPasswordAndNeverDowngradesTouchedData() {
        val encrypted = project().withStoredIdealOrder(CATEGORY_ID, "1 2 3", PASSWORD)

        assertThrows(IllegalArgumentException::class.java) {
            encrypted.withStoredCourseInfo(CATEGORY_ID, ProtectedCourseInfo(idealOrder = "1 2 3"), null)
        }

        val updated = encrypted.withStoredCourseInfo(
            CATEGORY_ID,
            ProtectedCourseInfo(idealOrder = "1 2 3", climbMeters = 42),
            PASSWORD
        )
        val category = updated.raceData.categories.single().category
        assertNull(category.courseInfo)
        assertEquals(42, category.storedCourseInfo(PASSWORD)?.climbMeters)
    }

    private fun project() = EventProjectEditor.addCategory(
        EventProjectFactory.createEmptyProject("race", "Storage policy", "2026-09-06T09:00"),
        categoryId = CATEGORY_ID,
        name = "M21"
    )

    private companion object {
        const val CATEGORY_ID = "cat-m21"
        const val PASSWORD = "course-key"
    }
}
