package org.openardf.radiooracle.publicresults

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.backend.publicresults.AndroidPublicResultsTarget
import org.openardf.radiooracle.backend.publicresults.needsPasswordForCourseDiagrams
import org.openardf.radiooracle.backend.publicresults.unlockedPublicResultsCourseInfo
import org.openardf.radiooracle.backend.room.entity.*
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.*
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo

class AndroidCoursePasswordPromptTest {
    @Test fun plaintextResultDiagramsDoNotAskForPasswordOrDecryptUnusedCategories() {
        val data = fixture()
        assertFalse(data.needsPasswordForCourseDiagrams())
        assertEquals("plain-course", data.unlockedPublicResultsCourseInfo(null).values.single().sourceName)
        val target = target(data.needsPasswordForCourseDiagrams())
        assertFalse(target.requiresCoursePassword(true))
        assertFalse(target.requiresCoursePassword(false))
    }

    @Test fun encryptedResultDiagramsRequirePasswordOnlyWhenIncluded() {
        val data = fixture()
        val category = data.categories.first().category
        category.encryptedCourseInfo = ProtectedCourseCipher.encryptCourseInfo(ProtectedCourseInfo(sourceName = "protected-course"), "test-password")
        category.courseInfo = null
        assertTrue(data.needsPasswordForCourseDiagrams())
        assertTrue(target(true).requiresCoursePassword(true))
        assertFalse(target(true).requiresCoursePassword(false))
        assertThrows(IllegalArgumentException::class.java) { data.unlockedPublicResultsCourseInfo(null) }
        assertEquals("protected-course", data.unlockedPublicResultsCourseInfo("test-password").values.single().sourceName)
    }

    private fun target(protected: Boolean) = AndroidPublicResultsTarget(
        "Race", false, 1, null, null, true, protected
    )

    private fun fixture(): RaceData {
        val race = Race()
        val plain = Category("M21").apply {
            raceId = race.id
            courseInfo = ProtectedCourseCipher.encodeCourseInfo(ProtectedCourseInfo(sourceName = "plain-course"))
        }
        val unused = Category("M70").apply {
            raceId = race.id
            encryptedCourseInfo = "unused-encrypted-data-must-not-be-decrypted"
        }
        val competitor = Competitor().apply { raceId = race.id; categoryId = plain.id }
        val result = Result().apply { raceId = race.id; competitorId = competitor.id; resultStatus = ResultStatus.OK }
        return RaceData(race,
            listOf(CategoryData(plain, emptyList(), listOf(competitor)), CategoryData(unused, emptyList(), emptyList())),
            emptyList(), listOf(CompetitorData(CompetitorCategory(competitor, plain), ReadoutData(result, emptyList()))), emptyList())
    }
}
