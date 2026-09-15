package org.openardf.radiooracle.desktop

import java.util.UUID
import org.openardf.radiooracle.shared.event.*

internal sealed interface DesktopCourseLibraryEdit {
    val courseId: String
    data class Assign(override val courseId: String, val categoryIds: Set<String>, val newCategoryName: String = "") : DesktopCourseLibraryEdit
    data class Delete(override val courseId: String) : DesktopCourseLibraryEdit
}

/** Manages stored courses through the same design validation and protection used by imports. */
internal object DesktopCourseLibrary {
    fun disabledReason(project: EventProjectFile): String? =
        DesktopCourseImportAvailability.disabledReason(project)
            ?: if (project.raceData.courseDraft != null) "Apply or discard the pending course draft before assigning or deleting courses." else null

    fun apply(project: EventProjectFile, edit: DesktopCourseLibraryEdit, password: String?,
              idFactory: () -> String = { UUID.randomUUID().toString() }): EventProjectFile {
        disabledReason(project)?.let { throw IllegalArgumentException(it) }
        val source = requireNotNull(project.raceData.courseMappings.singleOrNull { it.category.id == edit.courseId }) {
            "This unassigned course is no longer available. Reopen the course list."
        }
        val transaction = DesktopCourseImportTransaction.prepare(project)
        if (edit is DesktopCourseLibraryEdit.Delete) return transaction.applyTo(project) { base ->
            base.copy(raceData = base.raceData.copy(courseMappings = base.raceData.courseMappings.filterNot { it.category.id == edit.courseId }))
        }
        edit as DesktopCourseLibraryEdit.Assign
        val storagePassword = project.courseDataPassword(password)
        // Validate protected data before modifying any category.
        source.category.storedCourseInfo(storagePassword)
        var candidate = project
        val targets = edit.categoryIds.toMutableSet()
        require(targets.all { id -> project.raceData.categories.any { it.category.id == id } }) { "A selected category is no longer available." }
        val newName = edit.newCategoryName.trim()
        if (newName.isNotEmpty()) {
            require(project.raceData.categories.none { StandardCategoryRules.categoryNamesEquivalent(it.category.name, newName) }) {
                "That category already exists. Select it from the list instead."
            }
            val id = idFactory()
            candidate = EventProjectEditor.addCategory(candidate, id, newName)
            targets += id
        }
        require(targets.isNotEmpty()) { "Select a category or enter a new category name." }
        val categories = candidate.raceData.categories.map { target ->
            if (target.category.id !in targets) target else target.copy(
                category = target.category.copy(
                    lengthMeters = source.category.lengthMeters, climbMeters = source.category.climbMeters,
                    controlPointsString = source.category.controlPointsString,
                    courseInfo = source.category.courseInfo, encryptedCourseInfo = source.category.encryptedCourseInfo,
                    idealOrder = source.category.idealOrder, encryptedIdealOrder = source.category.encryptedIdealOrder),
                controlPoints = source.controlPoints.mapIndexed { index, point -> point.copy(
                    id = "${target.category.id}-course-$index", categoryId = target.category.id) },
                publicControlIds = source.publicControlIds)
        }
        candidate = candidate.copy(raceData = candidate.raceData.copy(categories = categories,
            courseMappings = candidate.raceData.courseMappings.filterNot { it.category.id == edit.courseId }))
        candidate = DesktopAuthoritativeCourseImport.prepare(candidate, targets, storagePassword)
        return transaction.applyTo(project) { candidate }
    }
}
