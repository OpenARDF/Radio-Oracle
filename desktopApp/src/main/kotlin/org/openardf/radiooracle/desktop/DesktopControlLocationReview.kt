package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.CourseControlLocationCourseChange
import org.openardf.radiooracle.shared.event.CourseControlLocationEdits
import org.openardf.radiooracle.shared.event.CourseControlResolver
import org.openardf.radiooracle.shared.event.CourseResolutionStatus
import org.openardf.radiooracle.shared.event.EventCourseDrafts
import org.openardf.radiooracle.shared.event.EventProjectFile

internal class DesktopControlLocationReview internal constructor(
    internal val baseProject: EventProjectFile,
    internal val stagedProject: EventProjectFile,
    internal val preparedDesign: DesktopPreparedCourseDesign,
    val controlId: String,
    val controlLabel: String,
    val previousLatitude: Double,
    val previousLongitude: Double,
    val updatedLatitude: Double,
    val updatedLongitude: Double,
    val courseChanges: List<CourseControlLocationCourseChange>
)

/** Builds a complete, temporary course revision without changing the open Race File. */
internal object DesktopControlLocationReviewer {
    fun prepare(
        projectFile: EventProjectFile,
        controlId: String,
        latitudeText: String,
        longitudeText: String,
        password: String?,
        elevationLookup: (CourseGeoPoint) -> Double? = { null },
        checkCancelled: () -> Unit = {}
    ): DesktopControlLocationReview {
        EventCourseDrafts.requireCurrent(projectFile)
        require(projectFile.raceData.courseDraft == null) {
            "Apply or discard the current course draft before reviewing a control location change."
        }
        val baseCandidate = EventCourseDrafts.candidate(projectFile)
        val storagePassword = baseCandidate.courseDataPassword(password)
        val beforeState = decryptedProtectedCourseState(baseCandidate, password.orEmpty())
        val control = requireNotNull(baseCandidate.raceData.controls.firstOrNull { it.id == controlId }) {
            "Control was not found: $controlId"
        }
        val previousLocation = CourseControlResolver.resolve(
            control,
            beforeState.protectedCourseInfoByCategoryId.values.toList()
        )
        require(previousLocation.status == CourseResolutionStatus.RESOLVED && previousLocation.location != null) {
            previousLocation.explanation ?: "This control does not have one accepted course location to edit."
        }

        val locationUpdate = DesktopProtectedControlLocationUpdater.applyControlLocation(
            projectFile = baseCandidate,
            courseInfoByCategoryId = beforeState.protectedCourseInfoByCategoryId,
            controlId = controlId,
            latitudeText = latitudeText,
            longitudeText = longitudeText,
            password = storagePassword,
            elevationLookup = elevationLookup
        )
        require(locationUpdate.affectedCategoryIds.isNotEmpty()) {
            "This control is not used by a stored course, so there are no protected course locations to update."
        }
        val stagedProject = EventCourseDrafts.edit(projectFile) { candidate ->
            require(EventCourseDrafts.snapshotHash(candidate) == EventCourseDrafts.snapshotHash(baseCandidate)) {
                "Course data changed while the location review was being prepared."
            }
            locationUpdate.projectFile
        }
        val stagedCandidate = EventCourseDrafts.candidate(stagedProject)
        val stagedState = decryptedProtectedCourseState(stagedCandidate, password.orEmpty())
        val stationChoices = courseStationChoices(stagedCandidate, stagedState.protectedCourseInfoByCategoryId)
        val unresolved = stationChoices.filter { it.controlId == null }
        require(unresolved.isEmpty()) {
            "Review the course station assignment for ${unresolved.first().categoryName} before changing control locations."
        }
        val bindingsByCategoryId = stationChoices
            .groupBy { it.categoryId }
            .mapValues { (_, choices) -> choices.associate { it.placementId to requireNotNull(it.controlId) } }
        val acceptedCategoryId = locationUpdate.affectedCategoryIds.first { it in stagedState.protectedCourseInfoByCategoryId }
        val acceptedInfo = stagedState.protectedCourseInfoByCategoryId.getValue(acceptedCategoryId)
        checkCancelled()
        val application = requireNotNull(
            DesktopCourseAnalyzer.analyze(
                projectFile = stagedCandidate,
                categoryId = acceptedCategoryId,
                protectedCourseInfo = acceptedInfo,
                protectedIdealOrderText = stagedCandidate.raceData
                    .let { race -> (race.categories + race.courseMappings).first { it.category.id == acceptedCategoryId } }
                    .category.storedIdealOrder(storagePassword),
                elevationLookup = elevationLookup,
                allowFoxRenumbering = false,
                prepareApplication = true,
                routeSource = DesktopCourseRouteSource.Draft
            ).calculatedRouteApplication
        ) { "A complete revised route could not be calculated for the affected course." }
        val prepared = DesktopCourseAnalysisApplier.prepareAll(
            project = stagedProject,
            accepted = DesktopCourseRouteSelection(
                courseInfo = acceptedInfo,
                application = application,
                controlIdsByPlacementId = bindingsByCategoryId.getValue(acceptedCategoryId)
            ),
            reviewedBindingsByCategoryId = bindingsByCategoryId,
            password = storagePassword,
            elevationLookup = elevationLookup,
            checkCancelled = checkCancelled
        )
        checkCancelled()

        val revisedProject = prepared.candidate
        val afterState = decryptedProtectedCourseState(revisedProject, password.orEmpty())
        val changes = CourseControlLocationEdits.changes(
            race = baseCandidate.raceData,
            beforeByCategoryId = beforeState.protectedCourseInfoByCategoryId,
            afterByCategoryId = afterState.protectedCourseInfoByCategoryId,
            directlyAffectedCategoryIds = locationUpdate.affectedCategoryIds.toSet()
        )
        val updatedLocation = CourseControlResolver.resolve(
            control,
            locationUpdate.courseInfoByCategoryId.values.toList()
        ).location ?: error("The revised control location could not be read back.")
        val previousResolvedLocation = requireNotNull(previousLocation.location)
        return DesktopControlLocationReview(
            baseProject = projectFile,
            stagedProject = stagedProject,
            preparedDesign = prepared,
            controlId = controlId,
            controlLabel = locationUpdate.controlLabel,
            previousLatitude = previousResolvedLocation.latitude,
            previousLongitude = previousResolvedLocation.longitude,
            updatedLatitude = updatedLocation.latitude,
            updatedLongitude = updatedLocation.longitude,
            courseChanges = changes
        )
    }
}
