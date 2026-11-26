package org.openardf.radiooracle.backend.room

import androidx.room.withTransaction
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.openardf.radiooracle.backend.room.database.EventDatabase
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.shared.encodePortableCourseData
import org.openardf.radiooracle.backend.shared.toEventCategoryData
import org.openardf.radiooracle.backend.shared.toEventCompetitorData
import org.openardf.radiooracle.backend.shared.toEventRace
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.backend.shared.toRoomCategory
import org.openardf.radiooracle.shared.event.CourseControlEdits
import org.openardf.radiooracle.shared.event.EventCategoryCompetitorSync
import org.openardf.radiooracle.shared.event.EventCategoryCompetitorSyncPlan
import org.openardf.radiooracle.shared.event.EventControlCatalog
import org.openardf.radiooracle.shared.event.EventCourseDrafts
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import java.util.UUID

/** Applies reviewed setup changes in place. Importing a replacement race would lose local identities and series membership. */
class AndroidRaceSetup(private val db: EventDatabase) {
    private suspend fun snapshot(raceId: UUID): RaceData {
        val race = requireNotNull(db.raceDao().getRace(raceId)) { "The selected race no longer exists." }
        return RaceData(race, db.categoryDao().getCategoryDataForRace(raceId).sortedBy { it.category.order },
            db.aliasDao().getAliasesByRace(raceId),
            db.competitorDao().getRegistrationCategories(raceId).map { CompetitorData(it, null) }, emptyList())
    }

    private suspend fun stationProject(raceId: UUID): EventProjectFile {
        // Check the result table directly: even an unmatched readout locks the course design.
        require(!db.resultDao().hasRecordedActivity(raceId)) { EventCourseDrafts.RecordedActivityDesignRestriction }
        return EventProjectFile(raceData = snapshot(raceId).toEventRaceData()).also(CourseControlEdits::requireReviewable)
    }

    suspend fun stationCatalog(raceId: UUID): EventProjectFile = db.withTransaction { stationProject(raceId) }

    class StationReview internal constructor(
        internal val before: EventProjectFile, internal val after: EventProjectFile,
        val controlId: String, val oldCode: Int, val newCode: Int
    )

    fun reviewStationCode(project: EventProjectFile, controlId: String, newCode: Int, password: String?): StationReview {
        val control = project.raceData.controls.single { it.id == controlId }
        return StationReview(project, CourseControlEdits.prepareStationCode(project, controlId, newCode, password),
            controlId, control.siCode, newCode)
    }

    suspend fun applyStationCode(review: StationReview) = db.withTransaction {
        val raceId = UUID.fromString(review.before.raceData.race.id)
        require(stationProject(raceId) == review.before) { "Race data changed. Review the station change again." }
        val updated = review.after.raceData
        val race = requireNotNull(db.raceDao().getRace(raceId))
        db.raceDao().updateRace(race.copy(portableCourseData = encodePortableCourseData(updated)))
        val originals = review.before.raceData.categories.associateBy { it.category.id }
        for (data in updated.categories) {
            val category = data.category
            if (data == originals.getValue(category.id)) continue
            val id = UUID.fromString(category.id)
            val row = requireNotNull(db.categoryDao().getCategory(id))
            // Room still stores the catalog editor’s derived text for older Android readers.
            @Suppress("DEPRECATION")
            db.categoryDao().createOrUpdateCategory(row.copy(controlPointsString = category.controlPointsString,
                idealOrder = category.idealOrder, encryptedIdealOrder = category.encryptedIdealOrder,
                courseInfo = category.courseInfo?.let(ProtectedCourseCipher::encodeCourseInfo),
                encryptedCourseInfo = category.encryptedCourseInfo))
            for (point in data.controlPoints.filter { it.controlId == review.controlId }) {
                db.controlPointDao().updateStation(UUID.fromString(point.id), id, review.controlId, review.newCode)
            }
        }
        // Aliases are race-local Room rows. Portable control IDs can be identical in two imported races.
        val changedCodes = listOf(review.oldCode, review.newCode)
        db.aliasDao().deleteStationAliases(raceId, changedCodes)
        EventControlCatalog.resolvedAliases(updated).filter { it.siCode in changedCodes }.forEach {
            db.aliasDao().createOrUpdateAlias(Alias(UUID.randomUUID(), raceId, it.siCode, it.name))
        }
    }

    // Category review does not require reconstructing or unlocking the race’s control catalog.
    private fun RaceData.toReviewRaceData() = EventRaceData(
        race.toEventRace(), categories.map { it.toEventCategoryData() }, emptyList(),
        competitorData.map { it.toEventCompetitorData() }, emptyList()
    )

    fun categoryReviews(raceId: UUID) = db.invalidationTracker.createFlow("competitor", "category", "control_point", "race")
        .map { db.withTransaction {
            // A deletion or reimport can invalidate this screen before the race selector closes it.
            if (db.raceDao().getRace(raceId) == null) EventCategoryCompetitorSyncPlan(false, emptyList(), emptyList())
            else {
                val race = snapshot(raceId)
                EventCategoryCompetitorSync.plan(race.toReviewRaceData())
            }
        } }
        .distinctUntilChanged()

    internal suspend fun applyCategoryReview(
        raceId: UUID, reviewed: EventCategoryCompetitorSyncPlan, missingIds: Set<String>, emptyIds: Set<String>,
        recalculateResults: suspend (UUID, Race) -> Unit
    ) = db.withTransaction {
        val native = snapshot(raceId)
        val before = EventProjectFile(raceData = native.toReviewRaceData())
        // Counts, names and the course-removal warning must still match the choices shown in the dialog.
        require(EventCategoryCompetitorSync.plan(before.raceData) == reviewed) {
            "Category assignments changed. Review the current list again."
        }
        val outcome = EventCategoryCompetitorSync.apply(before, missingIds, emptyIds)
        val updated = outcome.projectFile.raceData
        val existing = native.categories.associateBy { it.category.id.toString() }
        val localTargetIds = mutableMapOf<String, UUID>()
        for (data in updated.categories) {
            val original = existing[data.category.id]?.category
            if (original == null) {
                val added = data.category.toRoomCategory()
                // Legacy Room foreign keys allow a runner to reference a different race’s category.
                // Allocate a local identity in that case so repairing this race cannot move the other row.
                val id = if (db.categoryDao().getCategory(added.id) == null) added.id else UUID.randomUUID()
                db.categoryDao().createOrUpdateCategory(added.copy(id = id, portableCategoryId = id.toString()))
                localTargetIds[data.category.id] = id
            } else {
                localTargetIds[data.category.id] = original.id
                if (original.order != data.category.order) db.categoryDao().createOrUpdateCategory(original.copy(order = data.category.order))
            }
        }
        val assignments = updated.competitorData.associate { data ->
            val competitor = data.competitorCategory.competitor
            competitor.id to localTargetIds[competitor.categoryId]
        }
        native.competitorData.map { it.competitorCategory.competitor }.forEach { row ->
            val target = assignments[row.id.toString()]
            if (target != null && target != row.categoryId) {
                db.competitorDao().createCompetitor(row.copy(categoryId = target, pendingCategory = null))
                // Commit the assignment and its corrected, ready-to-publish results together.
                recalculateResults(row.id, native.race)
            }
        }
        existing.filterKeys { it !in localTargetIds }.values.forEach {
            require(db.categoryDao().deleteUnassignedCategory(it.category.id, raceId) == 1) {
                "An existing registration still uses ${it.category.name}. Repair its assignment before removing the category."
            }
        }
        outcome
    }
}
