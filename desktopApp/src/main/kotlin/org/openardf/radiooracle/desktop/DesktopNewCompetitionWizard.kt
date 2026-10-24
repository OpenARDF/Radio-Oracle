/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventSeriesArchive
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.toDisplayLabel
import org.openardf.radiooracle.shared.files.IofXmlImportSeverity
import org.openardf.radiooracle.shared.files.IofXmlImports
import org.openardf.radiooracle.shared.files.IofXmlSchemaResource
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

/** Optional CourseData source attached to one race by the standard IOF raceNumber field. */
data class DesktopWizardCourseData(
    val sourceName: String,
    val xml: String,
    val raceNumber: Int?
)

/** One fully specified race collected by the new-competition wizard. */
data class DesktopWizardRace(
    val name: String,
    val format: DesktopRaceFormat,
    val level: RaceLevel,
    val timeLimitMinutes: String,
    val startDateTime: LocalDateTime,
    val courseData: DesktopWizardCourseData? = null
) {
    fun validated(): DesktopWizardRace {
        require(name.trim().isNotEmpty()) { "Race name is required." }
        val minutes = timeLimitMinutes.trim().toLongOrNull()
            ?: throw IllegalArgumentException("Time limit must be a number of minutes.")
        require(minutes >= 0) { "Time limit cannot be negative." }
        return copy(name = name.trim(), timeLimitMinutes = minutes.toString())
    }
}

/** Final wizard review payload. A series name is required only after two or more races exist. */
data class DesktopNewCompetitionPlan(
    val races: List<DesktopWizardRace>,
    val seriesName: String? = null
) {
    fun validated(): DesktopNewCompetitionPlan {
        require(races.isNotEmpty()) { "Add at least one race." }
        val validatedRaces = races.mapIndexed { index, race ->
            try {
                race.validated()
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("Race ${index + 1}: ${error.message}", error)
            }
        }
        val trimmedSeriesName = seriesName?.trim()
        if (validatedRaces.size > 1) {
            require(!trimmedSeriesName.isNullOrEmpty()) { "Series name is required." }
        }
        return copy(races = validatedRaces, seriesName = trimmedSeriesName)
    }
}

data class DesktopNewCompetitionCreateResult(
    val openPath: Path,
    val rememberedPath: Path,
    val createdRaceCount: Int,
    val warnings: List<String>
)

/**
 * Creates wizard output through the existing shared Race File editor and authoritative .roseries
 * workspace. UI and filesystem selection stay desktop-only; race aggregates and IOF conversion
 * remain shared with other platforms.
 */
object DesktopNewCompetitionCreator {
    fun create(
        plan: DesktopNewCompetitionPlan,
        destination: Path,
        replaceExisting: Boolean = false,
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): DesktopNewCompetitionCreateResult {
        val accepted = plan.validated()
        val warnings = mutableListOf<String>()
        val projects = accepted.races.map { race ->
            buildProject(race, idFactory(), warnings)
        }
        if (projects.size == 1) {
            require(replaceExisting || !Files.exists(destination)) {
                "Race File already exists at ${destination.fileName}."
            }
            DesktopProjectFiles.write(destination, projects.single())
            return DesktopNewCompetitionCreateResult(
                openPath = destination,
                rememberedPath = destination,
                createdRaceCount = 1,
                warnings = warnings
            )
        }

        val seriesId = idFactory()
        val fileNames = uniqueRaceFileNames(accepted.races.map { it.name })
        val events = projects.mapIndexed { index, project ->
            val race = project.raceData.race
            EventSeriesEvent(
                seriesEventId = race.id,
                eventFilePath = fileNames[index],
                order = index,
                displayName = race.name,
                startDateTimeIso = race.startDateTimeIso,
                formatLabel = accepted.races[index].format.label
            )
        }
        val seriesFile = EventSeriesFile(
            seriesId = seriesId,
            name = requireNotNull(accepted.seriesName),
            events = events
        )
        val linkedProjects = projects.mapIndexed { index, project ->
            EventProjectEditor.updateSeriesLink(project, seriesId, events[index].seriesEventId)
        }
        val archive = EventSeriesArchive(
            seriesFile = seriesFile,
            membersBySeriesEventId = events.indices.associate { index ->
                events[index].seriesEventId to linkedProjects[index]
            }
        )
        val workspace = DesktopEventSeriesArchiveWorkspaces.create(
            destination,
            archive,
            replaceExisting = replaceExisting
        )
        return DesktopNewCompetitionCreateResult(
            openPath = workspace.memberPaths.first(),
            rememberedPath = workspace.containerPath,
            createdRaceCount = projects.size,
            warnings = warnings
        )
    }

    private fun buildProject(
        race: DesktopWizardRace,
        raceId: String,
        warnings: MutableList<String>
    ): EventProjectFile {
        val empty = EventProjectFactory.createEmptyProject(
            raceId = raceId,
            raceName = race.name,
            startDateTimeIso = DesktopDateTimeText.isoText(race.startDateTime)
        )
        val configured = EventProjectEditor.updateRaceSettings(
            projectFile = empty,
            raceType = race.format.raceType,
            raceLevel = race.level,
            raceBand = race.format.raceBand,
            timeLimitMinutes = race.timeLimitMinutes
        )
        val courseData = race.courseData ?: return configured
        val parsed = if (courseData.raceNumber == null) {
            IofXmlImports.validatedCourseData(
                courseData.xml,
                IofXmlSchemaResource.loadBundledSchema(),
                configured.raceData.race
            )
        } else {
            IofXmlImports.validatedCourseDataRace(
                courseData.xml,
                IofXmlSchemaResource.loadBundledSchema(),
                configured.raceData.race,
                courseData.raceNumber
            )
        }
        warnings += parsed.unsupportedItems.map { item ->
            val prefix = if (item.severity == IofXmlImportSeverity.UNSUPPORTED) "Not imported" else "Review"
            "$prefix ${item.location}: ${item.reason}"
        }
        return EventProjectEditor.importIofCourseData(configured, parsed.parsedData).projectFile
    }

    private fun uniqueRaceFileNames(raceNames: List<String>): List<String> {
        val used = linkedSetOf<String>()
        return raceNames.map { raceName ->
            val defaultName = DesktopProjectFilePaths.defaultProjectFileName(raceName)
            val stem = DesktopProjectFilePaths.projectFileDisplayStem(defaultName)
            var candidate = defaultName
            var suffix = 2
            while (!used.add(candidate.lowercase())) {
                candidate = DesktopProjectFilePaths.defaultProjectFileName("$stem $suffix")
                suffix++
            }
            candidate
        }
    }
}

internal fun iofClassificationRaceLevel(classification: String?): RaceLevel =
    when (classification?.trim()?.lowercase()) {
        "international" -> RaceLevel.INTERNATIONAL
        "national" -> RaceLevel.NATIONAL
        "regional" -> RaceLevel.REGIONAL
        "local" -> RaceLevel.DISTRICT
        "club" -> RaceLevel.PRACTICE
        else -> RaceLevel.PRACTICE
    }

private enum class DesktopNewCompetitionWizardStep {
    RACES,
    SERIES_NAME,
    REVIEW
}

/** Startup decision shown only when the remembered Race File or Series still exists. */
@Composable
internal fun DesktopStartupDecisionDialog(
    previousPath: Path,
    errorText: String? = null,
    onOpenPrevious: () -> Unit,
    onStartNew: () -> Unit,
    onOpenOther: () -> Unit
) {
    DesktopAlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome Back") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Radio-Oracle found the last competition you used:")
                Text(
                    previousPath.fileName.toString(),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    previousPath.toAbsolutePath().normalize().toString(),
                    color = DesktopPalette.Disconnected,
                    fontSize = 12.sp
                )
                Text("Open it, choose another file, or start a new race or series.")
                errorText?.let { Text(it, color = DesktopPalette.Error, fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = {
            Button(onClick = onOpenPrevious) { Text("Open Last") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenOther) { Text("Open Other...") }
                OutlinedButton(onClick = onStartNew) { Text("Start New") }
            }
        }
    )
}

private class DesktopNewCompetitionWizardState {
    var races by mutableStateOf(listOf(defaultWizardRace()))
    var raceIndex by mutableStateOf(0)
    var step by mutableStateOf(DesktopNewCompetitionWizardStep.RACES)
    var seriesName by mutableStateOf("")
    var helpTitle by mutableStateOf<String?>(null)
    var helpText by mutableStateOf("")
    var validationError by mutableStateOf<String?>(null)
    var importStatus by mutableStateOf<String?>(null)

    fun updateCurrent(transform: (DesktopWizardRace) -> DesktopWizardRace) {
        races = races.mapIndexed { index, race -> if (index == raceIndex) transform(race) else race }
        validationError = null
    }

    fun showHelp(title: String, text: String) {
        helpTitle = title
        helpText = text
    }

    fun validateRaces(): Boolean = runCatching {
        DesktopNewCompetitionPlan(races, if (races.size > 1) "pending" else null).validated()
    }.fold(
        onSuccess = { validationError = null; true },
        onFailure = { validationError = it.message ?: "Review the race information."; false }
    )

    fun removeCurrentRace() {
        if (races.size <= 1) return
        races = races.filterIndexed { index, _ -> index != raceIndex }
        raceIndex = raceIndex.coerceAtMost(races.lastIndex)
        validationError = null
    }

    fun importCourseData(path: Path) {
        runCatching {
            val xml = Files.readString(path)
            val metadata = IofXmlImports.validatedCourseDataCompetitionMetadata(
                xml,
                IofXmlSchemaResource.loadBundledSchema()
            )
            races = metadata.races.map { imported ->
                // Radio-orienteering format and time limit are not standard IOF fields; never infer them from names.
                val format = DesktopRaceFormat.Classic80m
                DesktopWizardRace(
                    name = imported.name,
                    format = format,
                    level = iofClassificationRaceLevel(imported.classification),
                    timeLimitMinutes = "120",
                    startDateTime = imported.startDateTimeOrDefault(),
                    courseData = DesktopWizardCourseData(path.fileName.toString(), xml, imported.raceNumber)
                )
            }
            raceIndex = 0
            seriesName = if (races.size > 1) metadata.eventName else ""
            importStatus = "Loaded ${path.fileName}: ${races.size} race${if (races.size == 1) "" else "s"}."
            validationError = null
        }.onFailure { error ->
            validationError = "CourseData import failed: ${error.message ?: error::class.simpleName}"
        }
    }
}

/** First-cut desktop wizard for creating one Race File or an authoritative Race Series file. */
@Composable
internal fun DesktopNewCompetitionWizardDialog(
    creationError: String?,
    onClearCreationError: () -> Unit,
    onCreate: (DesktopNewCompetitionPlan) -> Unit,
    onDismiss: () -> Unit
) {
    val state = remember { DesktopNewCompetitionWizardState() }
    DesktopAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(wizardTitle(state.step))
                // This foundational explanation stays opt-in so experienced users are not
                // required to repeat it each time they create a race or series.
                TextButton(
                    onClick = {
                        state.showHelp(
                            "How races, series, and courses work",
                            WIZARD_COMPETITION_STRUCTURE_HELP
                        )
                    }
                ) {
                    Text("Help")
                }
            }
        },
        text = { DesktopNewCompetitionWizardBody(state, creationError, onClearCreationError) },
        confirmButton = { DesktopNewCompetitionWizardConfirmButton(state, onCreate) },
        dismissButton = { DesktopNewCompetitionWizardBackButton(state, onDismiss) }
    )
    state.helpTitle?.let { title ->
        DesktopAlertDialog(
            onDismissRequest = { state.helpTitle = null },
            title = { Text(title) },
            text = { Text(state.helpText) },
            confirmButton = { Button(onClick = { state.helpTitle = null }) { Text("OK") } }
        )
    }
}

private fun wizardTitle(step: DesktopNewCompetitionWizardStep): String = when (step) {
    DesktopNewCompetitionWizardStep.RACES -> "New Race or Series"
    DesktopNewCompetitionWizardStep.SERIES_NAME -> "Name the Series"
    DesktopNewCompetitionWizardStep.REVIEW -> "Review New Competition"
}

@Composable
private fun DesktopNewCompetitionWizardBody(
    state: DesktopNewCompetitionWizardState,
    creationError: String?,
    onClearCreationError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (state.step) {
            DesktopNewCompetitionWizardStep.RACES -> WizardRacePage(
                race = state.races[state.raceIndex],
                raceNumber = state.raceIndex + 1,
                raceCount = state.races.size,
                // The import confirmation belongs on the first race page; repeating it reduces
                // the later race forms without adding new information.
                importStatus = state.importStatus.takeIf { state.raceIndex == 0 },
                onRaceChange = { transform -> state.updateCurrent(transform); onClearCreationError() },
                onHelp = state::showHelp,
                onPreviousRace = { state.raceIndex-- },
                onRemoveRace = state::removeCurrentRace,
                onImportCourseData = {
                    DesktopFileDialogs.chooseImportIofXml("Import IOF CourseData for New Competition")
                        ?.let(state::importCourseData)
                }
            )
            DesktopNewCompetitionWizardStep.SERIES_NAME -> WizardSeriesNamePage(state, onClearCreationError)
            DesktopNewCompetitionWizardStep.REVIEW -> WizardReviewPage(state)
        }
        (state.validationError ?: creationError)?.let { error ->
            Text(error, color = DesktopPalette.Error, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WizardSeriesNamePage(state: DesktopNewCompetitionWizardState, onClearCreationError: () -> Unit) {
    Text("You finished adding ${state.races.size} races. Name the series that will contain them.")
    WizardFieldLabel("Series name") {
        state.showHelp(
            "Series name",
            "The series name identifies the collection of Race Files. It is requested only after you finish adding two or more races."
        )
    }
    TextField(
        value = state.seriesName,
        onValueChange = {
            state.seriesName = it
            state.validationError = null
            onClearCreationError()
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Example: 2026 USA Radio Orienteering Championships") }
    )
}

@Composable
private fun WizardReviewPage(state: DesktopNewCompetitionWizardState) {
    Text(if (state.races.size == 1) "One Race File will be created." else "A Series File containing ${state.races.size} Race Files will be created.")
    state.seriesName.takeIf { state.races.size > 1 }?.let { Text("Series: $it", fontWeight = FontWeight.Bold) }
    state.races.forEachIndexed { index, race ->
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("${index + 1}. ${race.name}", fontWeight = FontWeight.Bold)
            Text(
                "${race.format.label}; ${race.level.toDisplayLabel()}; ${race.timeLimitMinutes} minutes; " +
                    DesktopDateTimeText.displayText(race.startDateTime),
                fontSize = 13.sp,
                color = DesktopPalette.Disconnected
            )
            race.courseData?.let { Text("CourseData: ${it.sourceName}", fontSize = 12.sp) }
        }
    }
    Text("No files are written until you choose Create.", color = DesktopPalette.Disconnected, fontSize = 12.sp)
}

@Composable
private fun DesktopNewCompetitionWizardConfirmButton(
    state: DesktopNewCompetitionWizardState,
    onCreate: (DesktopNewCompetitionPlan) -> Unit
) {
    when (state.step) {
        DesktopNewCompetitionWizardStep.RACES -> WizardRaceNavigationButtons(state)
        DesktopNewCompetitionWizardStep.SERIES_NAME -> Button(onClick = {
            if (state.seriesName.isBlank()) state.validationError = "Series name is required."
            else { state.validationError = null; state.step = DesktopNewCompetitionWizardStep.REVIEW }
        }) { Text("Review") }
        DesktopNewCompetitionWizardStep.REVIEW -> Button(onClick = {
            onCreate(DesktopNewCompetitionPlan(state.races, state.seriesName.takeIf { state.races.size > 1 }))
        }) { Text("Create") }
    }
}

@Composable
private fun WizardRaceNavigationButtons(state: DesktopNewCompetitionWizardState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.raceIndex < state.races.lastIndex) {
            Button(onClick = {
                if (state.races[state.raceIndex].runCatchingValid { state.validationError = it }) state.raceIndex++
            }) { Text("Next Race") }
        } else {
            OutlinedButton(onClick = {
                if (state.races[state.raceIndex].runCatchingValid { state.validationError = it }) {
                    state.races = state.races + defaultWizardRace(state.races.last().startDateTime.plusDays(1))
                    state.raceIndex = state.races.lastIndex
                }
            }) { Text("Add Another Race") }
        }
        Button(onClick = {
            if (state.validateRaces()) {
                state.step = if (state.races.size > 1) DesktopNewCompetitionWizardStep.SERIES_NAME
                else DesktopNewCompetitionWizardStep.REVIEW
            }
        }) { Text("Finished Adding Races") }
    }
}

@Composable
private fun DesktopNewCompetitionWizardBackButton(state: DesktopNewCompetitionWizardState, onDismiss: () -> Unit) {
    when (state.step) {
        DesktopNewCompetitionWizardStep.RACES -> TextButton(onClick = onDismiss) { Text("Cancel") }
        DesktopNewCompetitionWizardStep.SERIES_NAME ->
            TextButton(onClick = { state.step = DesktopNewCompetitionWizardStep.RACES }) { Text("Back") }
        DesktopNewCompetitionWizardStep.REVIEW -> TextButton(onClick = {
            state.step = if (state.races.size > 1) DesktopNewCompetitionWizardStep.SERIES_NAME
            else DesktopNewCompetitionWizardStep.RACES
        }) { Text("Back") }
    }
}

@Composable
private fun WizardRacePage(
    race: DesktopWizardRace,
    raceNumber: Int,
    raceCount: Int,
    importStatus: String?,
    onRaceChange: ((DesktopWizardRace) -> DesktopWizardRace) -> Unit,
    onHelp: (String, String) -> Unit,
    onPreviousRace: () -> Unit,
    onRemoveRace: () -> Unit,
    onImportCourseData: () -> Unit
) {
    // Keep page navigation out of the dialog footer so its extra button cannot wrap the actions
    // and reduce the form viewport when the user adds a second race.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Race $raceNumber of $raceCount", fontWeight = FontWeight.Bold)
        if (raceNumber > 1) TextButton(onClick = onPreviousRace) { Text("Previous Race") }
    }
    if (raceCount == 1) {
        WizardFieldLabel("One race or a series?") {
            onHelp(
                "One race or a series?",
                "Begin with one race. Choose Add Another Race for each additional race. After you finish adding two or more races, Radio-Oracle asks for the Series name."
            )
        }
        Text("Enter the race information, then add another race or finish.", fontSize = 13.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onImportCourseData) { Text("Import IOF 3.0 CourseData...") }
        if (raceCount > 1) {
            TextButton(onClick = onRemoveRace) { Text("Remove This Race") }
        }
    }
    importStatus?.let { Text(it, color = DesktopPalette.Disconnected, fontSize = 12.sp) }

    WizardFieldLabel("Race name") {
        onHelp("Race name", "Use a descriptive name such as Classic 80m. The name is also used to suggest the Race File name.")
    }
    TextField(
        value = race.name,
        onValueChange = { value -> onRaceChange { it.copy(name = value) } },
        modifier = Modifier.fillMaxWidth().testTag("wizard-race-name"),
        singleLine = true,
        placeholder = { Text("Example: Classic 80m") }
    )

    WizardFieldLabel("Competition format") {
        onHelp("Competition format", "Choose the radio-orienteering format and band. This sets the initial scoring rule family for the Race File.")
    }
    WizardDropdown(
        selected = race.format.label,
        choices = DesktopRaceFormat.selectableEntries.map { it.label },
        onSelected = { label ->
            val format = DesktopRaceFormat.selectableEntries.first { it.label == label }
            onRaceChange { it.copy(format = format) }
        }
    )

    WizardFieldLabel("Competition type") {
        onHelp("Competition type", "Choose the event level. Practice enables practice-oriented defaults; formal events retain stricter competition safeguards.")
    }
    WizardDropdown(
        selected = race.level.toDisplayLabel(),
        choices = RaceLevel.entries.map { it.toDisplayLabel() },
        onSelected = { label -> onRaceChange { it.copy(level = RaceLevel.entries.first { level -> level.toDisplayLabel() == label }) } }
    )

    WizardFieldLabel("Time limit (minutes)") {
        onHelp(
            "Time limit (minutes)",
            "The maximum allowed race time; competitors exceeding it are overtime. You can adjust it later in Edit Race Details."
        )
    }
    TextField(
        value = race.timeLimitMinutes,
        onValueChange = { value -> onRaceChange { it.copy(timeLimitMinutes = value.filter(Char::isDigit)) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Minutes") }
    )

    WizardFieldLabel("Race date/time") {
        onHelp("Race date/time", "Choose the expected start date and local time. Radio-Oracle uses it for Race Ops timing, exports, and series ordering.")
    }
    DateTimePickerField(
        label = "Race Start Date/Time",
        value = race.startDateTime,
        onValueChange = { value -> onRaceChange { it.copy(startDateTime = value) } },
        modifier = Modifier.fillMaxWidth().testTag("wizard-race-date-time")
    )
}

internal const val WIZARD_COMPETITION_STRUCTURE_HELP =
    "A race is one continuous competition activity with its own format, date and time, time limit, participants, and one or more courses.\n\n" +
        "A series is a named collection of two or more related races, with each race retaining its own settings and Race File.\n\n" +
        "A course is the set of controls or required route used by one or more classes within a race.\n\n" +
        "Competition\n" +
        "├── Single race\n" +
        "│   └── Race File\n" +
        "│       ├── Course A\n" +
        "│       └── Course B\n" +
        "└── Series\n" +
        "    ├── Race 1 — Race File\n" +
        "    │   └── Course(s)\n" +
        "    └── Race 2 — Race File\n" +
        "        └── Course(s)"

@Composable
private fun WizardFieldLabel(label: String, onHelp: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text(
            "(?)",
            color = DesktopPalette.Primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onHelp)
        )
    }
}

@Composable
private fun WizardDropdown(selected: String, choices: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(onClick = {
                    expanded = false
                    onSelected(choice)
                }) { Text(choice) }
            }
        }
    }
}

private fun defaultWizardRace(
    startDateTime: LocalDateTime = DesktopDateTimeText.defaultStartDateTime()
): DesktopWizardRace =
    DesktopWizardRace(
        name = "",
        format = DesktopRaceFormat.Classic80m,
        level = RaceLevel.PRACTICE,
        timeLimitMinutes = "120",
        startDateTime = startDateTime
    )

private fun org.openardf.radiooracle.shared.files.IofCourseDataRaceMetadata.startDateTimeOrDefault(): LocalDateTime {
    val iso = startDate?.let { date -> startTime?.let { time -> "${date}T$time" } }
    return iso?.let { text ->
        runCatching { LocalDateTime.parse(text) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toLocalDateTime() }.getOrNull()
    }
        ?: DesktopDateTimeText.defaultStartDateTime()
}

private fun DesktopWizardRace.runCatchingValid(validation: (String?) -> Unit = {}): Boolean =
    runCatching { validated() }.fold(
        onSuccess = {
            validation(null)
            true
        },
        onFailure = {
            validation(it.message ?: "Review the race information.")
            false
        }
    )
