package org.openardf.radiooracle.backend.room

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import org.openardf.radiooracle.backend.logging.DebugLog
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

data class AndroidCourseProtectionProgress(
    val completedCategories: Int,
    val totalCategories: Int,
    val raceName: String,
    val saving: Boolean = false
)

/** Uses desktop's cipher and updates only course payloads, atomically across all series members. */
class AndroidCourseProtection(private val database: EventDatabase) {
    suspend fun state(raceId: UUID, wholeSeries: Boolean): AndroidCourseProtectionState =
        withContext(Dispatchers.IO) {
            database.withTransaction { stateOf(targets(raceId, wholeSeries).flatMap { it.second }) }
        }

    suspend fun update(
        raceId: UUID,
        wholeSeries: Boolean,
        password: String,
        encrypt: Boolean,
        onProgress: suspend (AndroidCourseProtectionProgress) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        // Release Room's transaction while doing expensive PBKDF2 work. Reads and readouts can continue.
        val snapshot = database.withTransaction { targets(raceId, wholeSeries) }
        val state = stateOf(snapshot.flatMap { it.second })
        require(state.hasCourseData) { "There is no course or route data to encrypt." }
        require(state.encrypted != encrypt) {
            "Protection has changed. Close and reopen Race Password Protection."
        }
        val total = snapshot.sumOf { it.second.size }
        val started = System.nanoTime()
        DebugLog.info("CourseProtection", "Started encrypt=$encrypt series=$wholeSeries races=${snapshot.size} categories=$total")
        try {
            var completed = 0
            val changed = withContext(Dispatchers.Default) {
                snapshot.flatMap { (race, categories) ->
                    categories.map { original ->
                        currentCoroutineContext().ensureActive()
                        onProgress(AndroidCourseProtectionProgress(completed, total, race.name))
                        val project = EventProjectFile(raceData = EventRaceData(
                            race.toEventRace(),
                            listOf(EventCategoryData(original.toEventCategory(), emptyList(), emptyList())),
                            emptyList(), emptyList(), emptyList()
                        ))
                        val result = try {
                            if (encrypt) ProtectedCourseCipher.protectProjectCourseData(project, password)
                            else ProtectedCourseCipher.removeProjectCourseProtection(project, password)
                        } catch (error: IllegalArgumentException) {
                            throw IllegalArgumentException("Race '${race.name}': ${error.message}", error)
                        }
                        currentCoroutineContext().ensureActive()
                        val converted = result.raceData.categories.single().category
                        completed++
                        original.copy(
                            encryptedIdealOrder = converted.encryptedIdealOrder,
                            encryptedCourseInfo = converted.encryptedCourseInfo,
                            idealOrder = converted.idealOrder,
                            courseInfo = converted.courseInfo?.let(ProtectedCourseCipher::encodeCourseInfo)
                        )
                    }.also {
                        DebugLog.info("CourseProtection", "Processed categories=$completed/$total")
                    }
                }
            }
            onProgress(AndroidCourseProtectionProgress(completed, total, "", saving = true))
            currentCoroutineContext().ensureActive()
            // Authenticate all payloads before writing, and reject concurrent edits instead of overwriting them.
            database.withTransaction {
                require(targets(raceId, wholeSeries) == snapshot) {
                    "Race data changed during course protection. No protection changes were saved. Please retry."
                }
                changed.forEach { database.categoryDao().createOrUpdateCategory(it) }
            }
            DebugLog.info("CourseProtection", "Completed categories=$total elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
        } catch (error: Exception) {
            DebugLog.info("CourseProtection", "Stopped reason=${error.javaClass.simpleName} elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
            throw error
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
