package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.CourseControlEditRequest
import org.openardf.radiooracle.shared.event.CourseControlEdits
import org.openardf.radiooracle.shared.event.CourseControlFieldChange
import org.openardf.radiooracle.shared.event.CourseControlLocationCourseChange
import org.openardf.radiooracle.shared.event.CourseControlLocationEdits
import org.openardf.radiooracle.shared.event.CourseControlLocationUpdate
import org.openardf.radiooracle.shared.event.CourseControlResolver
import org.openardf.radiooracle.shared.event.CourseCoordinateRules
import org.openardf.radiooracle.shared.event.CourseResolutionStatus
import org.openardf.radiooracle.shared.event.EventCourseDrafts
import org.openardf.radiooracle.shared.event.EventProjectFile

internal class DesktopControlEditReview internal constructor(
    internal val baseProject: EventProjectFile,
    internal val candidateProject: EventProjectFile,
    val controlId: String,
    val controlLabel: String,
    val fieldChanges: List<CourseControlFieldChange>,
    val courseChanges: List<CourseControlLocationCourseChange>
)

/** Builds a complete control-edit candidate without changing the open Race File. */
internal object DesktopControlEditReviewer {
    fun prepare(
        projectFile: EventProjectFile,
        edit: CourseControlEditRequest,
        password: String?,
        elevationLookup: (CourseGeoPoint) -> Double? = { null },
        checkCancelled: () -> Unit = {}
    ): DesktopControlEditReview {
        EventCourseDrafts.requireCurrent(projectFile)
        require(projectFile.raceData.courseDraft == null) {
            "Apply or discard the current course draft before reviewing control changes."
        }
        val baseCandidate = EventCourseDrafts.candidate(projectFile)
        val storagePassword = baseCandidate.courseDataPassword(password)
        val beforeState = decryptedProtectedCourseState(baseCandidate, password.orEmpty())
        val control = requireNotNull(baseCandidate.raceData.controls.firstOrNull { it.id == edit.controlId }) {
            "Control was not found: ${edit.controlId}"
        }
        val previousLocation = CourseControlResolver.resolve(
            control,
            beforeState.protectedCourseInfoByCategoryId.values.toList()
        )
        val previousResolvedLocation = previousLocation.location
        val fieldChanges = CourseControlEdits.changes(control, previousResolvedLocation, edit)
        require(fieldChanges.isNotEmpty()) { "No control changes were made." }

        val detailsCandidate = DesktopProtectedControlEditor.applyDetails(
            projectFile = baseCandidate,
            courseState = beforeState,
            edit = edit,
            password = storagePassword
        )
        val detailsState = decryptedProtectedCourseState(detailsCandidate, password.orEmpty())
        val requestedLocation = edit.location
        val locationChanged = requestedLocation != null && (
            previousResolvedLocation == null ||
                !CourseCoordinateRules.same(previousResolvedLocation.latitude, requestedLocation.latitude) ||
                !CourseCoordinateRules.same(previousResolvedLocation.longitude, requestedLocation.longitude)
            )

        val directlyAffectedCategoryIds = linkedSetOf<String>()
        val candidateProject = if (locationChanged) {
            require(previousLocation.status == CourseResolutionStatus.RESOLVED && previousResolvedLocation != null) {
                previousLocation.explanation ?: "This control does not have one accepted course location to edit."
            }
            val locationUpdate = DesktopProtectedControlLocationUpdater.applyControlLocations(
                projectFile = detailsCandidate,
                courseInfoByCategoryId = detailsState.protectedCourseInfoByCategoryId,
                updates = listOf(
                    CourseControlLocationUpdate(
                        controlId = edit.controlId,
                        latitude = requestedLocation.latitude,
                        longitude = requestedLocation.longitude
                    )
                ),
                password = storagePassword,
                elevationLookup = elevationLookup
            )
            require(locationUpdate.affectedCategoryIds.isNotEmpty()) {
                "This control is not used by a stored course, so there are no protected course locations to update."
            }
            directlyAffectedCategoryIds += locationUpdate.affectedCategoryIds
            recalculateLocationCourses(
                sourceProject = projectFile,
                baseCandidate = baseCandidate,
                stagedCandidate = locationUpdate.projectFile,
                affectedCategoryIds = locationUpdate.affectedCategoryIds,
                password = storagePassword,
                elevationLookup = elevationLookup,
                checkCancelled = checkCancelled
            )
        } else {
            detailsCandidate
        }
        checkCancelled()

        val afterState = decryptedProtectedCourseState(candidateProject, password.orEmpty())
        val changes = CourseControlLocationEdits.changes(
            race = baseCandidate.raceData,
            beforeByCategoryId = beforeState.protectedCourseInfoByCategoryId,
            afterByCategoryId = afterState.protectedCourseInfoByCategoryId,
            directlyAffectedCategoryIds = directlyAffectedCategoryIds
        )
        val updatedControl = candidateProject.raceData.controls.first { it.id == edit.controlId }
        return DesktopControlEditReview(
            baseProject = projectFile,
            candidateProject = candidateProject,
            controlId = edit.controlId,
            controlLabel = updatedControl.publicLabel?.trim()?.takeIf(String::isNotEmpty)
                ?: updatedControl.label.ifBlank { updatedControl.siCode.toString() },
            fieldChanges = fieldChanges,
            courseChanges = changes
        )
    }

    private fun recalculateLocationCourses(
        sourceProject: EventProjectFile,
        baseCandidate: EventProjectFile,
        stagedCandidate: EventProjectFile,
        affectedCategoryIds: List<String>,
        password: String?,
        elevationLookup: (CourseGeoPoint) -> Double?,
        checkCancelled: () -> Unit
    ): EventProjectFile {
        val stagedProject = EventCourseDrafts.edit(sourceProject) { currentCandidate ->
            require(EventCourseDrafts.snapshotHash(currentCandidate) == EventCourseDrafts.snapshotHash(baseCandidate)) {
                "Course data changed while the control review was being prepared."
            }
            stagedCandidate
        }
        val candidate = EventCourseDrafts.candidate(stagedProject)
        val courseState = decryptedProtectedCourseState(candidate, password.orEmpty())
        val stationChoices = courseStationChoices(candidate, courseState.protectedCourseInfoByCategoryId)
        val unresolved = stationChoices.filter { it.controlId == null }
        require(unresolved.isEmpty()) {
            "Review the course station assignment for ${unresolved.first().categoryName} before changing control locations."
        }
        val bindingsByCategoryId = stationChoices
            .groupBy { it.categoryId }
            .mapValues { (_, choices) -> choices.associate { it.placementId to requireNotNull(it.controlId) } }
        val acceptedCategoryId = affectedCategoryIds.first { it in courseState.protectedCourseInfoByCategoryId }
        val acceptedInfo = courseState.protectedCourseInfoByCategoryId.getValue(acceptedCategoryId)
        checkCancelled()
        val application = requireNotNull(
            DesktopCourseAnalyzer.analyze(
                projectFile = candidate,
                categoryId = acceptedCategoryId,
                protectedCourseInfo = acceptedInfo,
                protectedIdealOrderText = candidate.raceData
                    .let { race -> (race.categories + race.courseMappings).first { it.category.id == acceptedCategoryId } }
                    .category.storedIdealOrder(password),
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
            password = password,
            elevationLookup = elevationLookup,
            checkCancelled = checkCancelled
        )
        checkCancelled()
        // Commit here is still pure: stagedProject is temporary and the live session is untouched.
        return DesktopCourseAnalysisApplier.commit(stagedProject, prepared)
    }
}
