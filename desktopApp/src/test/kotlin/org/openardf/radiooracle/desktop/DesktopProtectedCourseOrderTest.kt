package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

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
}
