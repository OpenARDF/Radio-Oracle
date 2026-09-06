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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo

class DesktopProtectedCourseOrderTest {
    @Test
    fun encryptsAndDecryptsProtectedCourseOrder() {
        val encrypted = DesktopProtectedCourseOrder.encrypt("31 33 32", "course-password")

        assertNotEquals("31 33 32", encrypted)
        assertEquals("31 33 32", DesktopProtectedCourseOrder.decrypt(encrypted, "course-password"))
    }

    @Test
    fun rejectsWrongPassword() {
        val encrypted = DesktopProtectedCourseOrder.encrypt("31", "course-password")

        assertThrows(IllegalArgumentException::class.java) {
            DesktopProtectedCourseOrder.decrypt(encrypted, "wrong-password")
        }
    }

    @Test
    fun reencryptsProjectCourseProtectionWithNewPassword() {
        val courseInfo = ProtectedCourseInfo(
            idealOrder = "31 32",
            lengthMeters = 220,
            climbMeters = 15
        )
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        ).let { projectFile ->
            EventProjectEditor.updateCategoryEncryptedIdealOrder(
                projectFile,
                "cat-m21",
                DesktopProtectedCourseOrder.encrypt("31 32", "old-password")
            )
        }.let { projectFile ->
            EventProjectEditor.updateCategoryEncryptedCourseInfo(
                projectFile,
                "cat-m21",
                DesktopProtectedCourseOrder.encryptCourseInfo(courseInfo, "old-password")
            )
        }

        val reencrypted = DesktopProtectedCourseOrder.reencryptProjectCourseProtection(
            project,
            oldPassword = "old-password",
            newPassword = "new-password"
        )
        val category = reencrypted.raceData.categories.single().category

        assertEquals("31 32", DesktopProtectedCourseOrder.decrypt(category.encryptedIdealOrder!!, "new-password"))
        assertEquals(
            courseInfo,
            DesktopProtectedCourseOrder.decryptCourseInfo(category.encryptedCourseInfo!!, "new-password")
        )
        assertThrows(IllegalArgumentException::class.java) {
            DesktopProtectedCourseOrder.decrypt(category.encryptedIdealOrder!!, "old-password")
        }
    }

    @Test
    fun reencryptProjectCourseProtectionRejectsWrongOldPassword() {
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        ).let { projectFile ->
            EventProjectEditor.updateCategoryEncryptedIdealOrder(
                projectFile,
                "cat-m21",
                DesktopProtectedCourseOrder.encrypt("31 32", "old-password")
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            DesktopProtectedCourseOrder.reencryptProjectCourseProtection(
                project,
                oldPassword = "wrong-password",
                newPassword = "new-password"
            )
        }
    }

    @Test
    fun removesProtectionWithoutDiscardingCourseDataAndCanProtectItAgain() {
        val courseInfo = ProtectedCourseInfo(idealOrder = "31 32", sourceName = "course.kml")
        val encrypted = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        ).let { project ->
            EventProjectEditor.updateCategoryEncryptedIdealOrder(
                project,
                "cat-m21",
                DesktopProtectedCourseOrder.encrypt("31 32", "race-password")
            )
        }.let { project ->
            EventProjectEditor.updateCategoryEncryptedCourseInfo(
                project,
                "cat-m21",
                DesktopProtectedCourseOrder.encryptCourseInfo(courseInfo, "race-password")
            )
        }

        val unprotected = DesktopProtectedCourseOrder.removeProjectCourseProtection(encrypted, " race-password ")
        val stored = EventProjectFileJson.decode(EventProjectFileJson.encode(unprotected))
        val category = stored.raceData.categories.single().category

        assertEquals(org.openardf.radiooracle.shared.event.EventProjectFileFormat.CURRENT_SCHEMA_VERSION, stored.schemaVersion)
        assertEquals("31 32", category.idealOrder)
        assertEquals(courseInfo, category.courseInfo)
        assertEquals(null, category.encryptedIdealOrder)
        assertEquals(null, category.encryptedCourseInfo)

        val protectedAgain = DesktopProtectedCourseOrder.protectProjectCourseData(stored, "new-password")
        val protectedCategory = protectedAgain.raceData.categories.single().category
        assertEquals(null, protectedCategory.idealOrder)
        assertEquals(null, protectedCategory.courseInfo)
        assertEquals(
            "31 32",
            DesktopProtectedCourseOrder.decrypt(protectedCategory.encryptedIdealOrder!!, "new-password")
        )
        assertEquals(
            courseInfo,
            DesktopProtectedCourseOrder.decryptCourseInfo(protectedCategory.encryptedCourseInfo!!, "new-password")
        )
    }

    @Test
    fun removalRejectsWrongPasswordWithoutReturningPartialProject() {
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        ).let { projectFile ->
            EventProjectEditor.updateCategoryEncryptedIdealOrder(
                projectFile,
                "cat-m21",
                DesktopProtectedCourseOrder.encrypt("31", "race-password")
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            DesktopProtectedCourseOrder.removeProjectCourseProtection(project, "wrong-password")
        }
        assertTrue(project.raceData.categories.single().category.encryptedIdealOrder?.isNotBlank() == true)
    }

    @Test
    fun racePasswordAuthorizesCloudflareTokenReveal() {
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        ).let { projectFile ->
            EventProjectEditor.updateCategoryEncryptedIdealOrder(
                projectFile,
                "cat-m21",
                DesktopProtectedCourseOrder.encrypt("31 32", "race-password")
            )
        }

        assertTrue(project.racePasswordAuthorizesCloudflareTokenReveal(" race-password "))
        assertFalse(project.racePasswordAuthorizesCloudflareTokenReveal("wrong-password"))
        assertFalse(project.racePasswordAuthorizesCloudflareTokenReveal(""))
    }

    @Test
    fun cloudflareTokenRevealRequiresAnExistingRacePassword() {
        val project = EventProjectFactory.createEmptyProject(
            "race",
            "Course Test",
            "2026-06-05T09:00"
        )

        assertFalse(project.racePasswordAuthorizesCloudflareTokenReveal("race-password"))
    }
}
