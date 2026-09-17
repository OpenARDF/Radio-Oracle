package org.openardf.radiooracle.desktop

import java.nio.file.Path
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.openardf.radiooracle.shared.files.*
import org.openardf.radiooracle.shared.event.*

/** The selected XML bytes are frozen for review; nothing is written to the race at this stage. */
internal data class PendingIofControlMappingReview(
    val path: Path,
    val transaction: DesktopCourseImportTransaction,
    val xml: String,
    val sources: List<IofCourseControlSource>,
    val mappings: List<IofCourseControlMapping>
)

/** Reuse the accepted-course catalog refresher when a shared control is renamed by import. */
internal object DesktopIofControlMappingReview {
    fun prepareCourseReview(review: PendingIofControlMappingReview, mappings: List<IofCourseControlMapping>,
                            useRouteBends: Boolean, password: String?): PendingIofCourseDataImportReview {
        val transaction = review.transaction
        val currentProject = transaction.baseProject
        val path = review.path
        val parsed = IofXmlImports.courseDataWithControlMappings(review.xml, currentProject.raceData.race,
            mappings, useRouteBends)
        val warningLines = iofWarningLines(parsed.unsupportedItems)
        val importedSiCodes = parsed.parsedData.categories
            .flatMap { categoryData -> categoryData.controlPoints.map { it.siCode } }
            .toSet()
        val previewImportProject = DesktopIofControlMappingReview.refreshSharedNames(currentProject,
            EventProjectEditor.importIofCourseData(currentProject, parsed.parsedData).projectFile,
            parsed.parsedData.reviewedControlNames, password)
        val previewImportedControlIds = previewImportProject.raceData.controls
            .filter { it.siCode in importedSiCodes }
            .mapTo(mutableSetOf()) { it.id }
        val deletedControlNames = DesktopControlImportPruning
            .unmatchedControlsExceedingRaceLimits(previewImportProject, previewImportedControlIds)
            .map { it.importDeletedControlDisplayName() }
        return PendingIofCourseDataImportReview(
            path = path,
            transaction = transaction,
            courseData = parsed.parsedData,
            newCourseMappingNames = newIofCourseMappingNames(currentProject, parsed.parsedData.categories),
            preview = DesktopImportPreviews.categoryDataPreview(
                projectFile = currentProject,
                sourceName = path.fileName.toString(),
                categories = parsed.parsedData.categories
            ),
            deletedControlNames = deletedControlNames,
            warningLines = warningLines,
            useRouteBends = useRouteBends
        )
    }

    fun refreshSharedNames(base: EventProjectFile, imported: EventProjectFile,
                           names: Map<Int, String>, password: String?): EventProjectFile {
        val renamedIds = base.raceData.controls.filter {
            names[it.siCode]?.let { name -> name != EventControlCatalog.displayLabel(it) } == true
        }.map { it.id }.toSet()
        if (renamedIds.isEmpty()) return imported
        var candidate = imported
        val byId = imported.raceData.controls.associateBy { it.id }
        (imported.raceData.categories + imported.raceData.courseMappings).filter { data ->
            data.controlPoints.any { it.controlId in renamedIds } || data.publicControlIds.any { it in renamedIds }
        }.forEach { data ->
            require(data.category.encryptedCourseInfo == null || !password.isNullOrBlank()) {
                "Unlock course data before changing public names used by protected courses, then import again."
            }
            val info = data.category.storedCourseInfo(password) ?: return@forEach
            val refreshed = if (info.appliedBindings != null) AppliedCourseEdits.refreshCatalog(info, imported.raceData.controls)
                else info.copy(
                    controlPoints = info.controlPoints.map { point -> byId[point.controlId]?.let {
                        point.copy(label = EventControlCatalog.displayLabel(it))
                    } ?: point },
                    courseObjects = info.courseObjects.map { point -> byId[point.id]?.let {
                        point.copy(label = EventControlCatalog.displayLabel(it))
                    } ?: point }
                )
            if (refreshed != info) {
                candidate = candidate.withStoredCourseInfo(data.category.id, refreshed, password)
                if (refreshed.idealOrder.isNotBlank()) candidate = candidate.withStoredIdealOrder(data.category.id, refreshed.idealOrder, password)
            }
        }
        return candidate
    }
}

@Composable
internal fun IofControlMappingReviewDialog(
    review: PendingIofControlMappingReview,
    disabledReason: String? = null,
    onReview: (List<IofCourseControlMapping>, Boolean) -> String?,
    onCancel: () -> Unit
) {
    var mappings by remember(review) { mutableStateOf(review.mappings) }
    var useRouteBends by remember(review) { mutableStateOf(false) }
    var preparationError by remember(review) { mutableStateOf<String?>(null) }
    val errors = remember(mappings, useRouteBends) {
        IofCourseControlMappings.errors(review.sources, mappings, useRouteBends)
    }
    fun update(index: Int, row: IofCourseControlMapping) {
        mappings = mappings.mapIndexed { i, old -> if (i == index) row else old }
        preparationError = null
    }
    DesktopAlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Review XML Control Mapping") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("File: ${review.path.fileName}")
                Text("Review every point before continuing. Names, roles and SI codes are prefilled from the XML and existing race controls; correct any mistakes. One mapping is used by every course that references the same XML Id.")
                if (review.sources.any { it.id.toIntOrNull() in 900..999 }) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(useRouteBends, {
                            useRouteBends = it
                            preparationError = null
                        }, modifier = Modifier.testTag("xml-mapping-route-bends"))
                        Text("Use 900–999 as route points (no punches)")
                    }
                    Text("Route points guide the line and are hidden on 2D diagrams. They do not need SI station codes.")
                }
                mappings.forEachIndexed { index, row ->
                    val source = review.sources.single { it.id == row.sourceId }
                    val routePoint = IofCourseControlMappings.isRoutePoint(row.sourceId, useRouteBends)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("XML Id: ${row.sourceId} · XML type: ${source.xmlType}", style = MaterialTheme.typography.subtitle2)
                        OutlinedTextField(row.publicName, { update(index, row.copy(publicName = it)) },
                            label = { Text("Alias / public name") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("xml-name-${row.sourceId}"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                var expanded by remember { mutableStateOf(false) }
                                OutlinedButton(onClick = { expanded = true }, enabled = !routePoint,
                                    modifier = Modifier.fillMaxWidth().testTag("xml-role-${row.sourceId}")) {
                                    Text(if (routePoint) "Route point" else IofCourseControlMappings.typeLabel(row.pointType))
                                }
                                DropdownMenu(expanded, { expanded = false }) {
                                    IofCourseControlMappings.pointTypes.forEach { type ->
                                        DropdownMenuItem(onClick = {
                                            update(index, row.copy(pointType = type))
                                            expanded = false
                                        }) { Text(IofCourseControlMappings.typeLabel(type)) }
                                    }
                                }
                            }
                            OutlinedTextField(if (routePoint) "" else row.siCode,
                                { update(index, row.copy(siCode = it)) }, label = { Text("SI station code") },
                                singleLine = true,
                                enabled = !routePoint && row.pointType.controlRole() != null,
                                modifier = Modifier.weight(1f).testTag("xml-si-${row.sourceId}"))
                        }
                        if (row.pointType in listOf(ProtectedCourseObjectType.START, ProtectedCourseObjectType.FINISH))
                            Text("This is a course endpoint location; it does not create a control station assignment.")
                        row.notes.forEach { Text(it, color = DesktopPalette.Warning) }
                        errors.filter { it.startsWith("${row.sourceId}:") }.forEach { Text(it, color = DesktopPalette.Error) }
                        Divider()
                    }
                }
                errors.filterNot { error -> mappings.any { error.startsWith("${it.sourceId}:") } }
                    .forEach { Text(it, color = DesktopPalette.Error) }
                disabledReason?.let { Text(it, color = DesktopPalette.Error) }
                preparationError?.let { Text(it, color = DesktopPalette.Error) }
                Text("Continue to review the courses and reports. The race is changed only when you accept the prepared import.")
            }
        },
        confirmButton = {
            Button(enabled = errors.isEmpty() && disabledReason == null, onClick = {
                preparationError = onReview(mappings, useRouteBends)
            }) { Text("Review Courses") }
        },
        dismissButton = { Button(onClick = onCancel) { Text("Cancel") } }
    )
}
