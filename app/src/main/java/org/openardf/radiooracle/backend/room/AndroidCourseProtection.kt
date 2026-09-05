package org.openardf.radiooracle.backend.room

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.backend.room.database.EventDatabase
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.shared.toEventCategory
import org.openardf.radiooracle.backend.shared.toEventRace
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import java.util.UUID

data class AndroidCourseProtectionState(val encrypted: Boolean, val hasCourseData: Boolean)

/** Uses desktop's cipher and updates only course payloads, atomically across all series members. */
class AndroidCourseProtection(private val database: EventDatabase) {
    suspend fun state(raceId: UUID, wholeSeries: Boolean): AndroidCourseProtectionState =
        withContext(Dispatchers.IO) {
            database.withTransaction { stateOf(targets(raceId, wholeSeries).flatMap { it.second }) }
        }

    suspend fun update(raceId: UUID, wholeSeries: Boolean, password: String, encrypt: Boolean) =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val targets = targets(raceId, wholeSeries)
                val state = stateOf(targets.flatMap { it.second })
                require(state.hasCourseData) { "There is no course or route data to encrypt." }
                require(state.encrypted != encrypt) {
                    "Protection has changed. Close and reopen Race Password Protection."
                }
                // Authenticate and transform every payload before writing any category.
                val changed = withContext(Dispatchers.Default) {
                    targets.flatMap { (race, categories) ->
                        val project = EventProjectFile(raceData = EventRaceData(
                            race.toEventRace(),
                            categories.map { EventCategoryData(it.toEventCategory(), emptyList(), emptyList()) },
                            emptyList(), emptyList(), emptyList()
                        ))
                        val result = try {
                            if (encrypt) ProtectedCourseCipher.protectProjectCourseData(project, password)
                            else ProtectedCourseCipher.removeProjectCourseProtection(project, password)
                        } catch (error: IllegalArgumentException) {
                            throw IllegalArgumentException("Race '${race.name}': ${error.message}", error)
                        }
                        categories.zip(result.raceData.categories) { original, converted ->
                            original.copy(
                                encryptedIdealOrder = converted.category.encryptedIdealOrder,
                                encryptedCourseInfo = converted.category.encryptedCourseInfo,
                                idealOrder = converted.category.idealOrder,
                                courseInfo = converted.category.courseInfo?.let(ProtectedCourseCipher::encodeCourseInfo)
                            )
                        }
                    }
                }
                changed.forEach { database.categoryDao().createOrUpdateCategory(it) }
            }
        }

    private suspend fun targets(raceId: UUID, wholeSeries: Boolean): List<Pair<Race, List<Category>>> {
        val ids = if (wholeSeries) {
            val series = requireNotNull(database.eventSeriesDao().getSeriesForRace(raceId)) {
                "This race is no longer part of a Race Series."
            }
            series.orderedMembers().map { it.localRaceId }.distinct().also {
                require(it.isNotEmpty() && raceId in it) { "Race Series membership is incomplete." }
            }
        } else listOf(raceId)
        return ids.map { id ->
            val race = requireNotNull(database.raceDao().getRace(id)) { "A selected race is missing." }
            race to database.categoryDao().getCategoriesForRace(id)
        }
    }

    private fun stateOf(categories: List<Category>): AndroidCourseProtectionState {
        val encrypted = categories.any { !it.encryptedIdealOrder.isNullOrBlank() || !it.encryptedCourseInfo.isNullOrBlank() }
        return AndroidCourseProtectionState(encrypted, encrypted || categories.any {
            it.idealOrder != null || it.courseInfo != null
        })
    }
}
