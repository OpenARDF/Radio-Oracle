package org.openardf.radiooracle.backend.room

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import org.openardf.radiooracle.backend.logging.DebugLog
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.backend.room.database.EventDatabase
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.backend.shared.encodePortableCourseData
import org.openardf.radiooracle.shared.event.EventCourseDrafts
import org.openardf.radiooracle.shared.event.EventProjectFile
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
            database.withTransaction { stateOf(targets(raceId, wholeSeries)) }
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
        val state = stateOf(snapshot)
        require(state.hasCourseData) { "There is no course or route data to encrypt." }
        require(state.encrypted != encrypt) {
            "Protection has changed. Close and reopen Race Password Protection."
        }
        val total = snapshot.sumOf { EventCourseDrafts.protectedCategories(it.toEventRaceData()).size }
        val started = System.nanoTime()
        DebugLog.info("CourseProtection", "Started encrypt=$encrypt series=$wholeSeries races=${snapshot.size} categories=$total")
        try {
            var completed = 0
            val changed = withContext(Dispatchers.Default) {
                snapshot.map { original ->
                    currentCoroutineContext().ensureActive()
                    onProgress(AndroidCourseProtectionProgress(completed, total, original.race.name))
                    val project = EventProjectFile(raceData = original.toEventRaceData())
                    val result = try {
                        if (encrypt) ProtectedCourseCipher.protectProjectCourseData(project, password)
                        else ProtectedCourseCipher.removeProjectCourseProtection(project, password)
                    } catch (error: IllegalArgumentException) {
                        throw IllegalArgumentException("Race '${original.race.name}': ${error.message}", error)
                    }
                    currentCoroutineContext().ensureActive()
                    val byId = result.raceData.categories.associateBy { it.category.id }
                    completed += EventCourseDrafts.protectedCategories(result.raceData).size
                    original.copy(race = original.race.copy(portableCourseData = original.race.portableCourseData?.let { encodePortableCourseData(result.raceData) }),
                        categories = original.categories.map { data ->
                            val converted = byId.getValue(data.category.id.toString()).category
                            data.copy(category = data.category.copy(
                                encryptedIdealOrder = converted.encryptedIdealOrder, encryptedCourseInfo = converted.encryptedCourseInfo,
                                idealOrder = converted.idealOrder, courseInfo = converted.courseInfo?.let(ProtectedCourseCipher::encodeCourseInfo),
                                portableCategoryId = if (result.raceData.courseDraft != null) data.category.id.toString() else data.category.portableCategoryId))
                        })
                }
            }
            onProgress(AndroidCourseProtectionProgress(completed, total, "", saving = true))
            currentCoroutineContext().ensureActive()
            // Authenticate all payloads before writing, and reject concurrent edits instead of overwriting them.
            database.withTransaction {
                require(targets(raceId, wholeSeries) == snapshot) {
                    "Race data changed during course protection. No protection changes were saved. Please retry."
                }
                changed.forEach { race ->
                    database.raceDao().updateRace(race.race)
                    race.categories.forEach { database.categoryDao().createOrUpdateCategory(it.category) }
                }
            }
            DebugLog.info("CourseProtection", "Completed categories=$total elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
        } catch (error: Exception) {
            DebugLog.info("CourseProtection", "Stopped reason=${error.javaClass.simpleName} elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
            throw error
        }
    }

    private suspend fun targets(raceId: UUID, wholeSeries: Boolean): List<RaceData> {
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
            RaceData(race, database.categoryDao().getCategoryDataForRace(id).map { it.copy(competitors = emptyList()) },
                database.aliasDao().getAliasesByRace(id), emptyList(), emptyList())
        }
    }

    private fun stateOf(races: List<RaceData>): AndroidCourseProtectionState {
        val categories = races.flatMap { EventCourseDrafts.protectedCategories(it.toEventRaceData()) }.map { it.category }
        val encrypted = categories.any { !it.encryptedIdealOrder.isNullOrBlank() || !it.encryptedCourseInfo.isNullOrBlank() }
        return AndroidCourseProtectionState(encrypted, encrypted || categories.any {
            it.idealOrder != null || it.courseInfo != null
        })
    }
}
