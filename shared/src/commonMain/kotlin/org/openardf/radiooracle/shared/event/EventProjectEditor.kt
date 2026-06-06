package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.alias.AliasRules
import org.openardf.radiooracle.shared.alias.AliasValidationResult
import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.files.CategoryCsvImportRow
import org.openardf.radiooracle.shared.files.CompetitorCsvImportRow
import org.openardf.radiooracle.shared.files.CompetitorStartCsvImportRow
import org.openardf.radiooracle.shared.files.ControlCsvImportRow
import org.openardf.radiooracle.shared.results.CourseEvaluator
import org.openardf.radiooracle.shared.results.EvaluationControlPoint
import org.openardf.radiooracle.shared.results.EvaluationPunch
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadout
import org.openardf.radiooracle.shared.sportident.SportIdentCodes
import org.openardf.radiooracle.shared.time.DurationFormatter

enum class CompetitorCsvImportDuplicatePolicy {
    REJECT_DUPLICATES,
    UPDATE_EXISTING_BY_INDEX,
    SKIP_EXISTING_BY_IMPORT_KEY,
    UPDATE_EXISTING_BY_IMPORT_KEY
}

data class CompetitorCsvImportOutcome(
    val projectFile: EventProjectFile,
    val importedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int = 0,
    val deletedCount: Int = 0,
    val warnings: List<String>
)

/** Shared Event File editing helpers used by desktop and future non-Android flows. */
object EventProjectEditor {
    /** Returns a copy of the Event File with a validated race name. */
    fun renameRace(projectFile: EventProjectFile, name: String): EventProjectFile {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) {
            "Event name cannot be blank."
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                race = projectFile.raceData.race.copy(name = trimmedName)
            )
        )
    }

    /** Returns a copy of the Event File with race-level settings changed. */
    fun updateRaceSettings(
        projectFile: EventProjectFile,
        raceType: RaceType,
        raceLevel: RaceLevel,
        raceBand: RaceBand,
        timeLimitMinutes: String
    ): EventProjectFile {
        val trimmedTimeLimit = timeLimitMinutes.trim()
        require(trimmedTimeLimit.isNotEmpty()) {
            "Race time limit is required."
        }
        val timeLimitMinutesValue = trimmedTimeLimit.toLongOrNull()
            ?: throw IllegalArgumentException("Race time limit is invalid.")
        require(timeLimitMinutesValue >= 0) {
            "Race time limit cannot be negative."
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                race = projectFile.raceData.race.copy(
                    raceType = raceType,
                    raceLevel = raceLevel,
                    raceBand = raceBand,
                    timeLimitSeconds = timeLimitMinutesValue * 60
                )
            )
        )
    }

    /** Returns a copy of the Event File with a validated race start date/time string. */
    fun updateRaceStartDateTime(projectFile: EventProjectFile, startDateTimeIso: String): EventProjectFile {
        val trimmedStart = startDateTimeIso.trim()
        require(trimmedStart.isNotEmpty()) {
            "Race start date/time cannot be blank."
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                race = projectFile.raceData.race.copy(startDateTimeIso = trimmedStart)
            )
        )
    }

    /** Returns a copy of the Event File with one validated category name changed. */
    fun renameCategory(
        projectFile: EventProjectFile,
        categoryId: String,
        name: String
    ): EventProjectFile {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) {
            "Category name cannot be blank."
        }
        require(
            projectFile.raceData.categories.none {
                it.category.id != categoryId && it.category.name == trimmedName
            }
        ) {
            "Category name must be unique."
        }

        var foundCategory = false
        val categories = projectFile.raceData.categories.map { categoryData ->
            if (categoryData.category.id == categoryId) {
                foundCategory = true
                categoryData.copy(category = categoryData.category.copy(name = trimmedName))
            } else {
                categoryData
            }
        }
        require(foundCategory) {
            "Category was not found: $categoryId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(categories = categories)
        )
    }

    /** Returns a copy of the Event File with a new category using conservative defaults. */
    fun addCategory(
        projectFile: EventProjectFile,
        categoryId: String,
        name: String
    ): EventProjectFile {
        val trimmedName = name.trim()
        require(categoryId.isNotBlank()) {
            "Category ID cannot be blank."
        }
        require(trimmedName.isNotEmpty()) {
            "Category name cannot be blank."
        }
        require(projectFile.raceData.categories.none { it.category.id == categoryId }) {
            "Category ID already exists: $categoryId"
        }
        require(projectFile.raceData.categories.none { it.category.name == trimmedName }) {
            "Category name must be unique."
        }

        val nextOrder = (projectFile.raceData.categories.maxOfOrNull { it.category.order } ?: 0) + 1
        val category = EventCategory(
            id = categoryId,
            raceId = projectFile.raceData.race.id,
            name = trimmedName,
            isMan = true,
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = nextOrder,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                categories = projectFile.raceData.categories + EventCategoryData(
                    category = category,
                    controlPoints = emptyList(),
                    competitors = emptyList()
                )
            )
        )
    }

    /**
     * Returns a copy of the Event File with one category and its course removed.
     *
     * Desktop Event Files do not have Room foreign keys, so this helper makes
     * the deletion policy explicit: category-owned control points disappear with
     * the category, remaining categories are renumbered, and kept competitors are
     * made uncategorized instead of retaining an invisible dangling category ID.
     */
    fun removeCategory(
        projectFile: EventProjectFile,
        categoryId: String,
        deleteCompetitors: Boolean
    ): EventProjectFile {
        require(projectFile.raceData.categories.any { it.category.id == categoryId }) {
            "Category was not found: $categoryId"
        }

        val categories = projectFile.raceData.categories
            .filterNot { it.category.id == categoryId }
            .mapIndexed { index, categoryData ->
                categoryData.copy(category = categoryData.category.copy(order = index))
            }

        val competitorData = if (deleteCompetitors) {
            projectFile.raceData.competitorData.filterNot { data ->
                data.competitorCategory.competitor.categoryId == categoryId ||
                    data.competitorCategory.category?.id == categoryId
            }
        } else {
            projectFile.raceData.competitorData.map { data ->
                val competitorCategory = data.competitorCategory
                val competitor = competitorCategory.competitor
                if (competitor.categoryId == categoryId || competitorCategory.category?.id == categoryId) {
                    data.copy(
                        competitorCategory = competitorCategory.copy(
                            competitor = competitor.copy(categoryId = null),
                            category = null
                        )
                    )
                } else {
                    data
                }
            }
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                categories = categories,
                competitorData = competitorData
            )
        )
    }

    /** Returns a copy of the Event File with a category course parsed from a control-point string. */
    fun updateCategoryControlPoints(
        projectFile: EventProjectFile,
        categoryId: String,
        controlPointsText: String,
        controlPointIdFactory: (Int) -> String
    ): EventProjectFile {
        val categoryData = projectFile.raceData.categories.firstOrNull { it.category.id == categoryId }
            ?: throw IllegalArgumentException("Category was not found: $categoryId")

        val matchedControlsByOrder = mutableMapOf<Int, EventControl>()
        val controlsByEntryToken = projectFile.raceData.controls.entryTokenMap()
        val definitions = ControlPointRules.parseAssignedControlPoints(
            input = controlPointsText.trim(),
            raceType = categoryData.category.effectiveRaceType(projectFile.raceData.race)
        ) { token, order ->
            controlsByEntryToken[token]?.let { control ->
                matchedControlsByOrder[order] = control
                ControlPointDefinition(control.siCode, control.type, order)
            }
        }
        val controlPoints = definitions.mapIndexed { index, definition ->
            val control = matchedControlsByOrder[definition.order]
                ?: EventControlCatalog.controlForDefinition(projectFile.raceData.race.id, definition)
            EventControlPoint(
                id = controlPointIdFactory(index),
                categoryId = categoryId,
                controlId = control.id,
                siCode = definition.siCode,
                type = definition.type,
                order = definition.order
            )
        }
        val controls = EventControlCatalog.mergeControls(
            projectFile.raceData.controls,
            definitions.map {
                matchedControlsByOrder[it.order]
                    ?: EventControlCatalog.controlForDefinition(projectFile.raceData.race.id, it)
            }
        )
        val formattedControlPoints = ControlPointRules.formatControlPoints(definitions)

        val categories = projectFile.raceData.categories.map { data ->
            if (data.category.id == categoryId) {
                data.copy(
                    category = data.category.copy(controlPointsString = formattedControlPoints),
                    controlPoints = controlPoints,
                    publicControlIds = controlPoints
                        .map { it.controlId }
                )
            } else {
                data
            }
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(categories = categories, controls = controls)
        )
    }

    private fun List<EventControl>.entryTokenMap(): Map<String, EventControl> =
        flatMap { control ->
            listOfNotNull(
                control.siCode.toString() to control,
                control.label.takeIf { it.isNotBlank() }?.let { it to control },
                control.publicLabel?.trim()?.takeIf { it.isNotBlank() }?.let { it to control }
            )
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, controls) -> controls.distinctBy { it.id } }
            .filterValues { controls -> controls.size == 1 }
            .mapValues { (_, controls) -> controls.single() }

    /** Returns a copy of the Event File with validated category length and climb. */
    fun updateCategoryPhysicalStats(
        projectFile: EventProjectFile,
        categoryId: String,
        lengthMeters: String,
        climbMeters: String
    ): EventProjectFile {
        val length = parseNonNegativeInt(lengthMeters, "Category length")
        val climb = parseNonNegativeInt(climbMeters, "Category climb")

        var foundCategory = false
        val categories = projectFile.raceData.categories.map { categoryData ->
            if (categoryData.category.id == categoryId) {
                foundCategory = true
                categoryData.copy(
                    category = categoryData.category.copy(
                        lengthMeters = length,
                        climbMeters = climb
                    )
                )
            } else {
                categoryData
            }
        }
        require(foundCategory) {
            "Category was not found: $categoryId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(categories = categories)
        )
    }

    /** Returns a copy of the Event File with one category's encrypted protected course order changed. */
    fun updateCategoryEncryptedIdealOrder(
        projectFile: EventProjectFile,
        categoryId: String,
        encryptedIdealOrder: String?
    ): EventProjectFile {
        var foundCategory = false
        val categories = projectFile.raceData.categories.map { categoryData ->
            if (categoryData.category.id == categoryId) {
                foundCategory = true
                categoryData.copy(
                    category = categoryData.category.copy(
                        encryptedIdealOrder = encryptedIdealOrder?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
            } else {
                categoryData
            }
        }
        require(foundCategory) {
            "Category was not found: $categoryId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(categories = categories)
        )
    }

    /** Returns a copy of the Event File with one category's encrypted protected course data changed. */
    fun updateCategoryEncryptedCourseInfo(
        projectFile: EventProjectFile,
        categoryId: String,
        encryptedCourseInfo: String?
    ): EventProjectFile {
        var foundCategory = false
        val categories = projectFile.raceData.categories.map { categoryData ->
            if (categoryData.category.id == categoryId) {
                foundCategory = true
                categoryData.copy(
                    category = categoryData.category.copy(
                        encryptedCourseInfo = encryptedCourseInfo?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
            } else {
                categoryData
            }
        }
        require(foundCategory) {
            "Category was not found: $categoryId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(categories = categories)
        )
    }

    /** Returns a copy of the Event File with one global logical control changed. */
    fun updateControl(
        projectFile: EventProjectFile,
        controlId: String,
        label: String,
        siCode: String,
        type: org.openardf.radiooracle.shared.domain.ControlPointType,
        scored: Boolean,
        publicLabel: String,
        notes: String
    ): EventProjectFile {
        val controlPosition = projectFile.raceData.controls.indexOfFirst { it.id == controlId }
        require(controlPosition >= 0) {
            "Control was not found: $controlId"
        }
        val updatedControl = validatedControl(
            projectFile = projectFile,
            existingControlPosition = controlPosition,
            controlId = controlId,
            label = label,
            siCode = siCode,
            type = type,
            scored = scored,
            publicLabel = publicLabel,
            notes = notes
        )
        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                controls = projectFile.raceData.controls.mapIndexed { index, control ->
                    if (index == controlPosition) updatedControl else control
                }
            )
        )
    }

    /** Returns a copy of the Event File with a global logical control appended. */
    fun addControl(
        projectFile: EventProjectFile,
        controlId: String,
        label: String,
        siCode: String,
        type: org.openardf.radiooracle.shared.domain.ControlPointType,
        scored: Boolean = type.defaultScored(),
        publicLabel: String = "",
        notes: String = ""
    ): EventProjectFile {
        require(controlId.isNotBlank()) {
            "Control ID cannot be blank."
        }
        require(projectFile.raceData.controls.none { it.id == controlId }) {
            "Control ID already exists: $controlId"
        }
        val control = validatedControl(
            projectFile = projectFile,
            existingControlPosition = null,
            controlId = controlId,
            label = label,
            siCode = siCode,
            type = type,
            scored = scored,
            publicLabel = publicLabel,
            notes = notes
        )
        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                controls = EventControlCatalog.mergeControls(projectFile.raceData.controls, listOf(control))
            )
        )
    }

    /** Returns a copy of the Event File with imported global controls added or merged by SI code and role. */
    fun importControlRows(
        projectFile: EventProjectFile,
        rows: List<ControlCsvImportRow>,
        controlIdFactory: () -> String
    ): EventProjectFile =
        rows.fold(projectFile) { currentProject, row ->
            val existingControl = currentProject.raceData.controls.firstOrNull {
                it.siCode == row.siCode && it.type == row.type
            }
            if (existingControl == null) {
                addControl(
                    projectFile = currentProject,
                    controlId = controlIdFactory(),
                    label = "",
                    siCode = row.siCode.toString(),
                    type = row.type,
                    scored = row.scored,
                    publicLabel = row.publicLabel,
                    notes = row.notes
                )
            } else {
                updateControl(
                    projectFile = currentProject,
                    controlId = existingControl.id,
                    label = existingControl.label,
                    siCode = row.siCode.toString(),
                    type = row.type,
                    scored = row.scored,
                    publicLabel = row.publicLabel,
                    notes = row.notes
                )
            }
        }

    /** Returns a copy of the Event File with an unused global logical control removed. */
    fun removeControl(projectFile: EventProjectFile, controlId: String): EventProjectFile {
        require(projectFile.raceData.controls.any { it.id == controlId }) {
            "Control was not found: $controlId"
        }
        require(projectFile.raceData.categories.none { categoryData ->
            categoryData.controlPoints.any { it.controlId == controlId } ||
                categoryData.publicControlIds.contains(controlId)
        }) {
            "Control is used by one or more categories."
        }
        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                controls = projectFile.raceData.controls.filterNot { it.id == controlId }
            )
        )
    }

    /** Returns a copy of the Event File with one competitor's validated name changed. */
    fun renameCompetitor(
        projectFile: EventProjectFile,
        competitorId: String,
        firstName: String,
        lastName: String
    ): EventProjectFile {
        val trimmedFirstName = firstName.trim()
        val trimmedLastName = lastName.trim()
        require(trimmedFirstName.isNotEmpty()) {
            "Competitor first name cannot be blank."
        }
        require(trimmedLastName.isNotEmpty()) {
            "Competitor last name cannot be blank."
        }

        var foundCompetitor = false
        val competitorData = projectFile.raceData.competitorData.map { data ->
            val competitorCategory = data.competitorCategory
            val competitor = competitorCategory.competitor
            if (competitor.id == competitorId) {
                foundCompetitor = true
                data.copy(
                    competitorCategory = competitorCategory.copy(
                        competitor = competitor.copy(
                            firstName = trimmedFirstName,
                            lastName = trimmedLastName
                        )
                    )
                )
            } else {
                data
            }
        }
        require(foundCompetitor) {
            "Competitor was not found: $competitorId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(competitorData = competitorData)
        )
    }

    /** Returns a copy of the Event File with one competitor assigned to a category, or to no category. */
    fun assignCompetitorCategory(
        projectFile: EventProjectFile,
        competitorId: String,
        categoryId: String?
    ): EventProjectFile {
        val trimmedCategoryId = categoryId?.trim()?.takeIf { it.isNotEmpty() }
        val category = trimmedCategoryId?.let { requestedCategoryId ->
            projectFile.raceData.categories
                .firstOrNull { it.category.id == requestedCategoryId }
                ?.category
                ?: throw IllegalArgumentException("Category was not found: $requestedCategoryId")
        }

        var foundCompetitor = false
        val competitorData = projectFile.raceData.competitorData.map { data ->
            val competitorCategory = data.competitorCategory
            val competitor = competitorCategory.competitor
            if (competitor.id == competitorId) {
                foundCompetitor = true
                data.copy(
                    competitorCategory = competitorCategory.copy(
                        competitor = competitor.copy(categoryId = category?.id),
                        category = category
                    )
                )
            } else {
                data
            }
        }
        require(foundCompetitor) {
            "Competitor was not found: $competitorId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(competitorData = competitorData)
        )
    }

    /** Returns a copy of the Event File with one competitor's club and legacy index changed. */
    fun updateCompetitorClubIndex(
        projectFile: EventProjectFile,
        competitorId: String,
        club: String,
        index: String
    ): EventProjectFile =
        updateCompetitorClubBibCallSign(
            projectFile = projectFile,
            competitorId = competitorId,
            club = club,
            bibNumber = index,
            callSign = "",
            legacyIndex = index
        )

    /** Returns a copy of the Event File with one competitor's team and visible identity fields changed. */
    fun updateCompetitorClubBibCallSign(
        projectFile: EventProjectFile,
        competitorId: String,
        club: String,
        bibNumber: String,
        callSign: String,
        legacyIndex: String? = null
    ): EventProjectFile {
        val trimmedBibNumber = bibNumber.trim()
        val trimmedCallSign = callSign.trim()
        require(
            trimmedBibNumber.isBlank() || projectFile.raceData.competitorData.none { data ->
                val competitor = data.competitorCategory.competitor
                competitor.id != competitorId && competitor.bibNumber == trimmedBibNumber
            }
        ) {
            "Bib number must be unique."
        }
        require(
            trimmedCallSign.isBlank() || projectFile.raceData.competitorData.none { data ->
                val competitor = data.competitorCategory.competitor
                competitor.id != competitorId && competitor.callSign.equals(trimmedCallSign, ignoreCase = true)
            }
        ) {
            "Call sign must be unique."
        }
        var foundCompetitor = false
        val competitorData = projectFile.raceData.competitorData.map { data ->
            val competitorCategory = data.competitorCategory
            val competitor = competitorCategory.competitor
            if (competitor.id == competitorId) {
                foundCompetitor = true
                data.copy(
                    competitorCategory = competitorCategory.copy(
                        competitor = competitor.copy(
                            club = club.trim(),
                            index = legacyIndex?.trim() ?: competitor.index,
                            bibNumber = trimmedBibNumber,
                            callSign = trimmedCallSign
                        )
                    )
                )
            } else {
                data
            }
        }
        require(foundCompetitor) {
            "Competitor was not found: $competitorId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(competitorData = competitorData)
        )
    }

    /** Returns a copy of the Event File with one competitor's optional birth year changed. */
    fun updateCompetitorBirthYear(
        projectFile: EventProjectFile,
        competitorId: String,
        birthYear: String
    ): EventProjectFile {
        val trimmedBirthYear = birthYear.trim()
        val birthYearValue = if (trimmedBirthYear.isEmpty()) {
            null
        } else {
            trimmedBirthYear.toIntOrNull()
                ?: throw IllegalArgumentException("Birth year is invalid.")
        }
        require(birthYearValue == null || birthYearValue > 0) {
            "Birth year must be positive."
        }

        var foundCompetitor = false
        val competitorData = projectFile.raceData.competitorData.map { data ->
            val competitorCategory = data.competitorCategory
            val competitor = competitorCategory.competitor
            if (competitor.id == competitorId) {
                foundCompetitor = true
                data.copy(
                    competitorCategory = competitorCategory.copy(
                        competitor = competitor.copy(birthYear = birthYearValue)
                    )
                )
            } else {
                data
            }
        }
        require(foundCompetitor) {
            "Competitor was not found: $competitorId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(competitorData = competitorData)
        )
    }

    /** Returns a copy of the Event File with one competitor's optional drawn start time changed. */
    fun updateCompetitorStartTime(
        projectFile: EventProjectFile,
        competitorId: String,
        startTime: String
    ): EventProjectFile {
        val trimmedStartTime = startTime.trim()
        val startTimeSeconds = if (trimmedStartTime.isEmpty()) {
            null
        } else {
            DurationFormatter.minuteStringToSeconds(trimmedStartTime)
        }

        var foundCompetitor = false
        val competitorData = projectFile.raceData.competitorData.map { data ->
            val competitorCategory = data.competitorCategory
            val competitor = competitorCategory.competitor
            if (competitor.id == competitorId) {
                foundCompetitor = true
                data.copy(
                    competitorCategory = competitorCategory.copy(
                        competitor = competitor.copy(drawnStartTimeSeconds = startTimeSeconds)
                    )
                )
            } else {
                data
            }
        }
        require(foundCompetitor) {
            "Competitor was not found: $competitorId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(competitorData = competitorData)
        )
    }

    /** Returns a copy of the Event File with persisted start-list generator settings changed. */
    fun updateStartDrawSettings(
        projectFile: EventProjectFile,
        intervalText: String,
        options: StartDrawOptions
    ): EventProjectFile {
        val intervalSeconds = DurationFormatter.minuteStringToSeconds(intervalText.trim())
        require(intervalSeconds > 0) {
            "Start interval must be greater than zero."
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                startDrawSettings = StartDrawSettings(
                    intervalSeconds = intervalSeconds,
                    options = options.withDefaultSeed().copy(idealFirstFoxByCategoryId = emptyMap())
                )
            )
        )
    }

    /**
     * Draws start times from per-category queues.
     *
     * The implementation keeps "must not exceed N starters at a time" as a hard
     * capacity rule and treats spacing rules as best-effort constraints. Category,
     * club, and first-fox conflicts are avoided whenever an alternate queue or
     * alternate competitor is available. If the remaining field makes a best
     * practice impossible, the draw still completes and EventStartListQuality
     * reports the compromise as orange or red.
     */
    fun drawStartList(
        projectFile: EventProjectFile,
        intervalText: String,
        options: StartDrawOptions = StartDrawOptions()
    ): EventProjectFile {
        val intervalSeconds = DurationFormatter.minuteStringToSeconds(intervalText.trim())
        require(intervalSeconds > 0) {
            "Start interval must be greater than zero."
        }

        val drawOptions = options.withDefaultSeed()
        val competitorStartTimes = mutableMapOf<String, Long>()
        drawQueuedStartList(projectFile, drawOptions, competitorStartTimes, intervalSeconds)

        val competitorData = projectFile.raceData.competitorData.map { data ->
            val competitor = data.competitorCategory.competitor
            val startTimeSeconds = competitorStartTimes[competitor.id]
            if (startTimeSeconds == null) {
                data
            } else {
                data.copy(
                    competitorCategory = data.competitorCategory.copy(
                        competitor = competitor.copy(drawnStartTimeSeconds = startTimeSeconds)
                    )
                )
            }
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                competitorData = competitorData,
                startDrawSettings = updateStartDrawSettings(projectFile, intervalText, drawOptions)
                    .raceData
                    .startDrawSettings
            )
        )
    }

    /**
     * Draws the current event after deriving current-day start thirds from
     * previously exported start-list CSVs.
     *
     * The historical inputs are intentionally the simple starts CSV rows rather
     * than full Event Files: organizers can select the exported starts from
     * previous championship days and Radio-Oracle can infer which third each
     * competitor occupied on each day. Matching is by SI number when available,
     * with start number as a fallback for older or incomplete starts files.
     */
    fun drawStartListWithBalancedStartGroups(
        projectFile: EventProjectFile,
        intervalText: String,
        options: StartDrawOptions,
        previousStartLists: List<List<CompetitorStartCsvImportRow>>
    ): EventProjectFile {
        val drawOptions = options.withDefaultSeed().copy(startGroupMode = StartDrawStartGroupMode.BALANCED_MULTI_DAY_THIRDS)
        val historyByCompetitorKey = previousStartLists
            .flatMap(::startGroupHistoryFromStartRows)
            .groupBy({ it.competitorKey }, { it.startGroup })
            .mapValues { (_, startGroups) -> startGroups.groupingBy { it }.eachCount() }
        val currentCompetitors = projectFile.raceData.competitorData.map { it.competitorCategory.competitor }
        val assignedStartGroups = balancedStartGroupAssignments(
            competitors = currentCompetitors,
            historyByCompetitorKey = historyByCompetitorKey,
            options = drawOptions
        )

        val projectWithAssignedGroups = projectFile.copy(
            raceData = projectFile.raceData.copy(
                competitorData = projectFile.raceData.competitorData.map { data ->
                    val competitor = data.competitorCategory.competitor
                    data.copy(
                        competitorCategory = data.competitorCategory.copy(
                            competitor = competitor.copy(preferredStartGroup = assignedStartGroups[competitor.id])
                        )
                    )
                }
            )
        )

        return drawStartList(projectWithAssignedGroups, intervalText, drawOptions)
    }

    private fun drawQueuedStartList(
        projectFile: EventProjectFile,
        options: StartDrawOptions,
        competitorStartTimes: MutableMap<String, Long>,
        intervalSeconds: Long
    ) {
        // Each category is represented as a queue. Queue order is stable for the
        // default seed and repeatably shuffled for non-default seeds, but the
        // selection phase below is still responsible for enforcing cross-category
        // rules such as "do not start the same category consecutively".
        val categoryQueues = projectFile.raceData.categories
            .sortedWith(compareBy({ it.category.order }, { it.category.name }))
            .mapNotNull { categoryData ->
                val categoryCompetitors = projectFile.raceData.competitorData
                    .filter { data ->
                        data.competitorCategory.category?.id == categoryData.category.id ||
                            data.competitorCategory.competitor.categoryId == categoryData.category.id
                    }
                    .map { it.competitorCategory.competitor }
                val drawnCompetitors = drawCategoryCompetitors(categoryCompetitors, null, options)
                if (drawnCompetitors.isEmpty()) {
                    null
                } else {
                    CategoryStartQueue(
                        category = categoryData.category,
                        firstFox = options.idealFirstFoxByCategoryId[categoryData.category.id],
                        speedGroup = categoryData.category.startDrawSpeedGroup(),
                        competitors = drawnCompetitors.toMutableList()
                    )
                }
            }
            .toMutableList()

        val totalStartSlots = ceilDiv(
            categoryQueues.sumOf { it.competitors.size },
            options.startersPerStartTime
        )
        var nextStartSeconds = 0L
        var startSlotIndex = 0
        var previousClub: String? = null
        var previousCategoryId: String? = null
        while (categoryQueues.isNotEmpty()) {
            // A start slot can contain more than one competitor. We build the
            // slot incrementally so every additional starter can be checked
            // against the starters already placed at this same start time.
            val preferredStartGroup = options.preferredStartGroupForSlot(startSlotIndex, totalStartSlots)
            val selectedQueues = mutableListOf<CategoryStartQueue>()
            val selectedCompetitors = mutableListOf<EventCompetitor>()
            repeat(options.startersPerStartTime) {
                val selectedQueue = selectStartSlotQueue(
                    categoryQueues,
                    selectedQueues,
                    selectedCompetitors,
                    previousClub,
                    previousCategoryId,
                    preferredStartGroup,
                    options
                )
                    ?: return@repeat
                // A selected category queue may still have a club conflict at
                // its head. When possible, take a later competitor from the same
                // queue rather than rejecting the category entirely.
                val competitorIndex = selectedQueue.competitorIndexForStartSlot(
                    selectedCompetitors,
                    previousClub,
                    preferredStartGroup,
                    options
                )
                val competitor = selectedQueue.competitors.removeAt(competitorIndex)
                selectedQueues += selectedQueue
                selectedCompetitors += competitor
                competitorStartTimes[competitor.id] = nextStartSeconds
                if (selectedQueue.competitors.isEmpty()) {
                    categoryQueues.remove(selectedQueue)
                }
            }
            if (options.clubHandling == StartDrawClubHandling.AVOID_BACK_TO_BACK) {
                selectedCompetitors.lastOrNull()?.clubKey()?.let { previousClub = it }
            }
            // The last selected category is the one adjacent to the next slot.
            // With multiple starters per slot, earlier categories in the same
            // slot are not consecutive with the next start time.
            selectedQueues.lastOrNull()?.category?.id?.let { previousCategoryId = it }
            nextStartSeconds += intervalSeconds
            startSlotIndex++
        }
    }

    /** Returns a copy of the Event File with one competitor's validated numbers changed. */
    fun updateCompetitorNumbers(
        projectFile: EventProjectFile,
        competitorId: String,
        startNumber: String,
        siNumber: String
    ): EventProjectFile {
        val competitorPosition = projectFile.raceData.competitorData.indexOfFirst {
            it.competitorCategory.competitor.id == competitorId
        }
        require(competitorPosition >= 0) {
            "Competitor was not found: $competitorId"
        }

        val trimmedStartNumber = startNumber.trim()
        require(trimmedStartNumber.isNotEmpty()) {
            "Start number is required."
        }
        val startNumberValue = trimmedStartNumber.toIntOrNull()
            ?: throw IllegalArgumentException("Start number is invalid.")
        require(
            projectFile.raceData.competitorData.noneIndexed { index, data ->
                index != competitorPosition && data.competitorCategory.competitor.startNumber == startNumberValue
            }
        ) {
            "Start number must be unique."
        }

        val trimmedSiNumber = siNumber.trim()
        val siNumberValue = if (trimmedSiNumber.isEmpty()) {
            null
        } else {
            trimmedSiNumber.toIntOrNull()
                ?: throw IllegalArgumentException("SI number is invalid.")
        }
        require(siNumberValue == null || SportIdentCodes.isSINumberValid(siNumberValue)) {
            "SI number is outside the supported SportIdent card range."
        }
        require(
            siNumberValue == null || projectFile.raceData.competitorData.noneIndexed { index, data ->
                index != competitorPosition && data.competitorCategory.competitor.siNumber == siNumberValue
            }
        ) {
            "SI number must be unique."
        }

        val competitorData = projectFile.raceData.competitorData.mapIndexed { index, data ->
            if (index == competitorPosition) {
                val competitorCategory = data.competitorCategory
                data.copy(
                    competitorCategory = competitorCategory.copy(
                        competitor = competitorCategory.competitor.copy(
                            startNumber = startNumberValue,
                            siNumber = siNumberValue
                        )
                    )
                )
            } else {
                data
            }
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(competitorData = competitorData)
        )
    }

    /** Returns a copy of the Event File with a new uncategorized competitor appended. */
    fun addCompetitor(
        projectFile: EventProjectFile,
        competitorId: String,
        firstName: String,
        lastName: String,
        startNumber: String,
        siNumber: String
    ): EventProjectFile {
        require(competitorId.isNotBlank()) {
            "Competitor ID cannot be blank."
        }
        require(projectFile.raceData.competitorData.none { it.competitorCategory.competitor.id == competitorId }) {
            "Competitor ID already exists: $competitorId"
        }

        val competitor = validatedCompetitorBasics(
            raceId = projectFile.raceData.race.id,
            competitorId = competitorId,
            firstName = firstName,
            lastName = lastName,
            startNumber = startNumber,
            siNumber = siNumber,
            existingCompetitors = projectFile.raceData.competitorData,
            existingCompetitorPosition = null
        )

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                competitorData = projectFile.raceData.competitorData + EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(
                        competitor = competitor,
                        category = null
                    ),
                    readoutData = null
                )
            )
        )
    }

    /** Appends parsed Android-format category CSV rows, including course control points. */
    fun importCategoryRows(
        projectFile: EventProjectFile,
        rows: List<CategoryCsvImportRow>,
        categoryIdFactory: () -> String,
        controlPointIdFactory: (String, Int) -> String
    ): EventProjectFile {
        var nextCategoryOrder = (projectFile.raceData.categories.maxOfOrNull { it.category.order } ?: -1) + 1
        val categoryNames = projectFile.raceData.categories.mapTo(mutableSetOf()) { it.category.name }
        val importedCategories = rows.map { row ->
            require(categoryNames.add(row.name)) {
                "Category name must be unique."
            }

            val categoryId = categoryIdFactory()
            val category = EventCategory(
                id = categoryId,
                raceId = projectFile.raceData.race.id,
                name = row.name,
                isMan = row.isMan,
                maxAge = row.maxAge,
                lengthMeters = row.lengthMeters,
                climbMeters = row.climbMeters,
                order = nextCategoryOrder++,
                differentProperties = !row.followsRacePresets,
                raceType = row.raceType,
                raceBand = row.raceBand,
                timeLimitSeconds = row.timeLimitMinutes?.times(60),
                controlPointsString = "",
                encryptedIdealOrder = row.encryptedIdealOrder
            )
            val definitions = ControlPointRules.parseAssignedControlPoints(
                input = row.controlPointsText,
                raceType = category.effectiveRaceType(projectFile.raceData.race)
            )
            val controlPoints = definitions.mapIndexed { index, definition ->
                val control = EventControlCatalog.controlForDefinition(projectFile.raceData.race.id, definition)
                EventControlPoint(
                    id = controlPointIdFactory(categoryId, index),
                    categoryId = categoryId,
                    controlId = control.id,
                    siCode = definition.siCode,
                    type = definition.type,
                    order = definition.order
                )
            }

            EventCategoryData(
                category = category.copy(controlPointsString = ControlPointRules.formatControlPoints(definitions)),
                controlPoints = controlPoints,
                competitors = emptyList(),
                publicControlIds = controlPoints
                    .map { it.controlId }
            )
        }
        val importedControls = importedCategories.flatMap { categoryData ->
            categoryData.controlPoints.map { controlPoint ->
                EventControl(
                    id = controlPoint.controlId,
                    raceId = projectFile.raceData.race.id,
                    label = when (controlPoint.type) {
                        org.openardf.radiooracle.shared.domain.ControlPointType.BEACON -> "${controlPoint.siCode}B"
                        org.openardf.radiooracle.shared.domain.ControlPointType.SEPARATOR -> "${controlPoint.siCode}S"
                        org.openardf.radiooracle.shared.domain.ControlPointType.CONTROL -> controlPoint.siCode.toString()
                    },
                    siCode = controlPoint.siCode,
                    type = controlPoint.type
                )
            }
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                categories = projectFile.raceData.categories + importedCategories,
                controls = EventControlCatalog.mergeControls(projectFile.raceData.controls, importedControls)
            )
        )
    }

    /** Appends parsed Android-format competitor CSV rows, creating category placeholders as needed. */
    fun importCompetitorRows(
        projectFile: EventProjectFile,
        rows: List<CompetitorCsvImportRow>,
        competitorIdFactory: () -> String,
        categoryIdFactory: () -> String
    ): EventProjectFile =
        importCompetitorRowsWithOutcome(
            projectFile = projectFile,
            rows = rows,
            competitorIdFactory = competitorIdFactory,
            categoryIdFactory = categoryIdFactory
        ).projectFile

    fun importCompetitorRowsWithOutcome(
        projectFile: EventProjectFile,
        rows: List<CompetitorCsvImportRow>,
        competitorIdFactory: () -> String,
        categoryIdFactory: () -> String,
        duplicatePolicy: CompetitorCsvImportDuplicatePolicy = CompetitorCsvImportDuplicatePolicy.REJECT_DUPLICATES,
        deleteMissingByImportKey: Boolean = false
    ): CompetitorCsvImportOutcome {
        var categories = projectFile.raceData.categories
        val competitors = projectFile.raceData.competitorData.toMutableList()
        val warnings = mutableListOf<String>()
        var nextCategoryOrder = (categories.maxOfOrNull { it.category.order } ?: -1) + 1
        var nextStartNumber = (competitors.maxOfOrNull { it.competitorCategory.competitor.startNumber } ?: 0) + 1
        var importedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        val importKeys = rows.map { it.importKey() }.toSet()

        rows.forEachIndexed { rowIndex, row ->
            val existingPosition = row.existingCompetitorPosition(competitors, duplicatePolicy)
            if (
                existingPosition >= 0 &&
                duplicatePolicy == CompetitorCsvImportDuplicatePolicy.SKIP_EXISTING_BY_IMPORT_KEY
            ) {
                skippedCount++
                return@forEachIndexed
            }

            if (row.categoryName.isBlank()) {
                warnings += "Line ${rowIndex + 1}: competitor ${row.lastName} ${row.firstName} has no category."
            }
            val category = row.categoryName.takeIf { it.isNotEmpty() }?.let { categoryName ->
                categories.firstOrNull { it.category.name == categoryName }?.category
                    ?: EventCategory(
                        id = categoryIdFactory(),
                        raceId = projectFile.raceData.race.id,
                        name = categoryName,
                        isMan = false,
                        maxAge = null,
                        lengthMeters = 0,
                        climbMeters = 0,
                        order = nextCategoryOrder++,
                        differentProperties = false,
                        raceType = null,
                        raceBand = null,
                        timeLimitSeconds = null,
                        controlPointsString = ""
                    ).also { newCategory ->
                        warnings += "Line ${rowIndex + 1}: created placeholder category '${newCategory.name}'."
                        categories += EventCategoryData(
                            category = newCategory,
                            controlPoints = emptyList(),
                            competitors = emptyList()
                        )
                    }
            }

            val startNumber = row.startNumber ?: if (existingPosition >= 0) {
                competitors[existingPosition].competitorCategory.competitor.startNumber
            } else {
                nextStartNumber++
            }
            if (startNumber >= nextStartNumber) {
                nextStartNumber = startNumber + 1
            }
            require(competitors.noneIndexed { index, data ->
                index != existingPosition && data.competitorCategory.competitor.startNumber == startNumber
            }) {
                "Start number must be unique."
            }
            require(
                row.siNumber == null || competitors.noneIndexed { index, data ->
                    index != existingPosition && data.competitorCategory.competitor.siNumber == row.siNumber
                }
            ) {
                "SI number must be unique."
            }
            require(
                row.index.isBlank() || competitors.noneIndexed { index, data ->
                    index != existingPosition && data.competitorCategory.competitor.index == row.index
                }
            ) {
                "Registration index must be unique."
            }
            require(
                row.bibNumber.isBlank() || competitors.noneIndexed { index, data ->
                    index != existingPosition && data.competitorCategory.competitor.bibNumber == row.bibNumber
                }
            ) {
                "Bib number must be unique."
            }
            require(
                row.callSign.isBlank() || competitors.noneIndexed { index, data ->
                    index != existingPosition &&
                        data.competitorCategory.competitor.callSign.equals(row.callSign, ignoreCase = true)
                }
            ) {
                "Call sign must be unique."
            }

            if (existingPosition >= 0) {
                val existingData = competitors[existingPosition]
                val existingCompetitor = existingData.competitorCategory.competitor
                val updatedCompetitor = existingCompetitor.copy(
                    categoryId = category?.id,
                    firstName = row.firstName,
                    lastName = row.lastName,
                    club = row.club,
                    index = row.index,
                    bibNumber = row.bibNumber,
                    callSign = row.callSign,
                    isMan = row.isMan,
                    birthYear = row.birthYear,
                    siNumber = row.siNumber ?: existingCompetitor.siNumber,
                    siRent = row.siRent,
                    startNumber = startNumber,
                    drawnStartTimeSeconds = row.startTimeText?.let(DurationFormatter::minuteStringToSeconds)
                        ?: existingCompetitor.drawnStartTimeSeconds,
                    preferredStartGroup = row.preferredStartGroup ?: existingCompetitor.preferredStartGroup
                )
                competitors[existingPosition] = existingData.copy(
                    competitorCategory = existingData.competitorCategory.copy(
                        competitor = updatedCompetitor,
                        category = category
                    )
                )
                updatedCount++
                return@forEachIndexed
            }

            val competitor = EventCompetitor(
                id = competitorIdFactory(),
                raceId = projectFile.raceData.race.id,
                categoryId = category?.id,
                firstName = row.firstName,
                lastName = row.lastName,
                club = row.club,
                index = row.index,
                bibNumber = row.bibNumber,
                callSign = row.callSign,
                isMan = row.isMan,
                birthYear = row.birthYear,
                siNumber = row.siNumber,
                siRent = row.siRent,
                startNumber = startNumber,
                drawnStartTimeSeconds = row.startTimeText?.let(DurationFormatter::minuteStringToSeconds),
                preferredStartGroup = row.preferredStartGroup
            )
            competitors += EventCompetitorData(
                competitorCategory = EventCompetitorCategory(
                    competitor = competitor,
                    category = category
                ),
                readoutData = null
            )
            importedCount++
        }

        val removedCompetitors = if (deleteMissingByImportKey) {
            competitors.filter { it.competitorCategory.competitor.importKey() !in importKeys }
        } else {
            emptyList()
        }
        if (removedCompetitors.isNotEmpty()) {
            val removedIds = removedCompetitors
                .map { it.competitorCategory.competitor.id }
                .toSet()
            val unmatchedReadouts = removedCompetitors.mapNotNull { data ->
                data.readoutData?.let { readoutData ->
                    readoutData.copy(result = readoutData.result.copy(competitorId = null))
                }
            }
            competitors.removeAll { it.competitorCategory.competitor.id in removedIds }
            if (unmatchedReadouts.isNotEmpty()) {
                return importOutcome(
                    projectFile = projectFile,
                    categories = categories,
                    competitors = competitors,
                    unmatchedReadouts = projectFile.raceData.unmatchedReadoutData + unmatchedReadouts,
                    importedCount = importedCount,
                    updatedCount = updatedCount,
                    skippedCount = skippedCount,
                    deletedCount = removedCompetitors.size,
                    warnings = warnings
                )
            }
        }

        return importOutcome(
            projectFile = projectFile,
            categories = categories,
            competitors = competitors,
            unmatchedReadouts = projectFile.raceData.unmatchedReadoutData,
            importedCount = importedCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
            deletedCount = removedCompetitors.size,
            warnings = warnings
        )
    }

    private fun importOutcome(
        projectFile: EventProjectFile,
        categories: List<EventCategoryData>,
        competitors: List<EventCompetitorData>,
        unmatchedReadouts: List<EventReadoutData>,
        importedCount: Int,
        updatedCount: Int,
        skippedCount: Int,
        deletedCount: Int,
        warnings: List<String>
    ): CompetitorCsvImportOutcome {
        val raceData = projectFile.raceData.copy(
            categories = categories,
            competitorData = competitors,
            unmatchedReadoutData = unmatchedReadouts
        )
        return CompetitorCsvImportOutcome(
            projectFile = projectFile.copy(
                raceData = raceData.copy(
                    categories = raceData.categories.map { categoryData ->
                        categoryData.copy(
                            competitors = competitors
                                .map { it.competitorCategory.competitor }
                                .filter { it.categoryId == categoryData.category.id }
                        )
                    }
                )
            ),
            importedCount = importedCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
            deletedCount = deletedCount,
            warnings = warnings
        )
    }

    /** Applies parsed Android-format competitor-start rows to existing competitors by start number. */
    fun importCompetitorStartRows(
        projectFile: EventProjectFile,
        rows: List<CompetitorStartCsvImportRow>
    ): EventProjectFile {
        var competitorData = projectFile.raceData.competitorData

        rows.forEach { row ->
            val competitorPosition = competitorData.indexOfFirst {
                it.competitorCategory.competitor.startNumber == row.startNumber
            }
            if (competitorPosition >= 0) {
                val siNumber = row.siNumber
                require(
                    siNumber == null || competitorData.noneIndexed { index, data ->
                        index != competitorPosition && data.competitorCategory.competitor.siNumber == siNumber
                    }
                ) {
                    "SI number must be unique."
                }

                competitorData = competitorData.mapIndexed { index, data ->
                    if (index == competitorPosition) {
                        val competitorCategory = data.competitorCategory
                        val competitor = competitorCategory.competitor
                        data.copy(
                            competitorCategory = competitorCategory.copy(
                                competitor = competitor.copy(
                                    siNumber = siNumber ?: competitor.siNumber,
                                    drawnStartTimeSeconds = DurationFormatter.minuteStringToSeconds(row.startTimeText)
                                )
                            )
                        )
                    } else {
                        data
                    }
                }
            }
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(competitorData = competitorData)
        )
    }

    /**
     * Returns a copy of the Event File with one competitor removed.
     *
     * This mirrors Android's Room-backed deletion policy for retained results:
     * the competitor record is always removed, and its matched readout is either
     * deleted too or moved to the unmatched readout list with the competitor
     * reference cleared.
     */
    fun removeCompetitor(
        projectFile: EventProjectFile,
        competitorId: String,
        deleteReadout: Boolean
    ): EventProjectFile {
        val competitorData = projectFile.raceData.competitorData
        val removedCompetitorData = competitorData.firstOrNull {
            it.competitorCategory.competitor.id == competitorId
        } ?: throw IllegalArgumentException("Competitor was not found: $competitorId")

        val categories = projectFile.raceData.categories.map { categoryData ->
            categoryData.copy(
                competitors = categoryData.competitors.filterNot { it.id == competitorId }
            )
        }
        val unmatchedReadoutData = if (deleteReadout) {
            projectFile.raceData.unmatchedReadoutData
        } else {
            removedCompetitorData.readoutData?.let { readoutData ->
                projectFile.raceData.unmatchedReadoutData + readoutData.copy(
                    result = readoutData.result.copy(competitorId = null)
                )
            } ?: projectFile.raceData.unmatchedReadoutData
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                categories = categories,
                competitorData = competitorData.filterNot {
                    it.competitorCategory.competitor.id == competitorId
                },
                unmatchedReadoutData = unmatchedReadoutData
            )
        )
    }

    /**
     * Returns a copy of the Event File with one readout/result removed.
     *
     * Android deletes the result row and relies on Room to cascade punch rows.
     * Desktop Event Files keep result and punch data together, so removing
     * the readout data from its matched competitor or unmatched list expresses
     * the same policy without a database.
     */
    fun removeReadout(projectFile: EventProjectFile, resultId: String): EventProjectFile {
        var foundReadout = false
        val competitorData = projectFile.raceData.competitorData.map { data ->
            if (data.readoutData?.result?.id == resultId) {
                foundReadout = true
                data.copy(readoutData = null)
            } else {
                data
            }
        }
        val unmatchedReadoutData = projectFile.raceData.unmatchedReadoutData.filterNot { readoutData ->
            val matches = readoutData.result.id == resultId
            if (matches) {
                foundReadout = true
            }
            matches
        }
        require(foundReadout) {
            "Readout was not found: $resultId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                competitorData = competitorData,
                unmatchedReadoutData = unmatchedReadoutData
            )
        )
    }

    /** Assigns an unmatched readout to a competitor who does not already have a readout. */
    fun assignUnmatchedReadout(
        projectFile: EventProjectFile,
        resultId: String,
        competitorId: String
    ): EventProjectFile {
        val trimmedCompetitorId = competitorId.trim()
        require(trimmedCompetitorId.isNotEmpty()) {
            "Competitor was not selected."
        }
        val readoutData = projectFile.raceData.unmatchedReadoutData.firstOrNull { it.result.id == resultId }
            ?: throw IllegalArgumentException("Unmatched readout was not found: $resultId")
        val competitorIndex = projectFile.raceData.competitorData.indexOfFirst {
            it.competitorCategory.competitor.id == trimmedCompetitorId
        }
        require(competitorIndex >= 0) {
            "Competitor was not found: $trimmedCompetitorId"
        }
        require(projectFile.raceData.competitorData[competitorIndex].readoutData == null) {
            "Competitor already has a readout."
        }

        val assignedReadoutData = readoutData.copy(
            result = readoutData.result.copy(
                competitorId = trimmedCompetitorId,
                modified = true,
                sent = false
            )
        )

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                competitorData = projectFile.raceData.competitorData.mapIndexed { index, data ->
                    if (index == competitorIndex) data.copy(readoutData = assignedReadoutData) else data
                },
                unmatchedReadoutData = projectFile.raceData.unmatchedReadoutData.filterNot {
                    it.result.id == resultId
                }
            )
        )
    }

    /**
     * Returns a copy of the Event File with one readout set to a manual status.
     *
     * Android can recalculate automatic status because it has Room-backed race,
     * category, and punch services available. The desktop project editor keeps
     * this operation intentionally explicit: choosing a status makes the readout
     * manual, marks it modified, and marks it unsent.
     */
    fun updateReadoutManualStatus(
        projectFile: EventProjectFile,
        resultId: String,
        resultStatus: ResultStatus
    ): EventProjectFile {
        var foundReadout = false
        fun EventReadoutData.withManualStatus(): EventReadoutData {
            foundReadout = true
            return copy(
                result = result.copy(
                    automaticStatus = false,
                    resultStatus = resultStatus,
                    modified = true,
                    sent = false
                )
            )
        }

        val competitorData = projectFile.raceData.competitorData.map { data ->
            if (data.readoutData?.result?.id == resultId) {
                data.copy(readoutData = data.readoutData.withManualStatus())
            } else {
                data
            }
        }
        val unmatchedReadoutData = projectFile.raceData.unmatchedReadoutData.map { readoutData ->
            if (readoutData.result.id == resultId) readoutData.withManualStatus() else readoutData
        }
        require(foundReadout) {
            "Readout was not found: $resultId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                competitorData = competitorData,
                unmatchedReadoutData = unmatchedReadoutData
            )
        )
    }

    /** Returns a copy of the Event File with successfully exported matched readouts marked sent. */
    fun markReadoutsSent(
        projectFile: EventProjectFile,
        resultIds: Set<String>
    ): EventProjectFile {
        if (resultIds.isEmpty()) {
            return projectFile
        }

        val remainingResultIds = resultIds.toMutableSet()
        val competitorData = projectFile.raceData.competitorData.map { data ->
            val readoutData = data.readoutData
            if (readoutData != null && remainingResultIds.remove(readoutData.result.id)) {
                data.copy(
                    readoutData = readoutData.copy(
                        result = readoutData.result.copy(sent = true)
                    )
                )
            } else {
                data
            }
        }
        require(remainingResultIds.isEmpty()) {
            "Readout was not found: ${remainingResultIds.first()}"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                competitorData = competitorData
            )
        )
    }

    /** Adds a status-only DNS result for a competitor who has no SI-card readout. */
    fun markCompetitorDidNotStart(
        projectFile: EventProjectFile,
        competitorId: String,
        resultId: String,
        readoutDateTimeIso: String
    ): EventProjectFile {
        require(resultId.isNotBlank()) {
            "Readout ID cannot be blank."
        }
        require(!projectFile.raceData.containsReadout(resultId)) {
            "Readout ID already exists: $resultId"
        }
        require(readoutDateTimeIso.isNotBlank()) {
            "Readout date/time cannot be blank."
        }

        val competitorIndex = projectFile.raceData.competitorData.indexOfFirst {
            it.competitorCategory.competitor.id == competitorId
        }
        require(competitorIndex >= 0) {
            "Competitor was not found: $competitorId"
        }
        require(projectFile.raceData.competitorData[competitorIndex].readoutData == null) {
            "Competitor already has a readout."
        }

        val competitor = projectFile.raceData.competitorData[competitorIndex].competitorCategory.competitor
        val readoutData = EventReadoutData(
            result = EventResult(
                id = resultId,
                raceId = projectFile.raceData.race.id,
                competitorId = competitor.id,
                siNumber = competitor.siNumber,
                cardType = 0,
                checkTimeSeconds = null,
                startTimeSeconds = null,
                finishTimeSeconds = null,
                readoutDateTimeIso = readoutDateTimeIso,
                automaticStatus = false,
                resultStatus = ResultStatus.DID_NOT_START,
                points = 0,
                runTimeSeconds = 0,
                modified = true,
                sent = false
            ),
            punches = emptyList()
        )

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                competitorData = projectFile.raceData.competitorData.mapIndexed { index, data ->
                    if (index == competitorIndex) data.copy(readoutData = readoutData) else data
                }
            )
        )
    }

    /**
     * Adds a manually entered readout with competitor matching, timing, controls, and status in one operation.
     *
     * The shared implementation deliberately accepts plain text values so desktop and future UI
     * layers can stay thin while this helper owns validation, category-course evaluation, and the
     * matched-versus-unmatched project-file placement policy.
     */
    fun addManualReadout(
        projectFile: EventProjectFile,
        resultId: String,
        competitorId: String?,
        siNumber: String,
        startSeconds: String,
        finishSeconds: String,
        controlCodes: String,
        resultStatus: ResultStatus,
        readoutDateTimeIso: String,
        punchIdFactory: (Int, SIRecordType) -> String
    ): EventProjectFile {
        require(resultId.isNotBlank()) {
            "Readout ID cannot be blank."
        }
        require(!projectFile.raceData.containsReadout(resultId)) {
            "Readout ID already exists: $resultId"
        }
        val matchedCompetitorIndex = competitorId?.trim()?.takeIf { it.isNotEmpty() }?.let { requestedCompetitorId ->
            val index = projectFile.raceData.competitorData.indexOfFirst {
                it.competitorCategory.competitor.id == requestedCompetitorId
            }
            require(index >= 0) {
                "Competitor was not found: $requestedCompetitorId"
            }
            require(projectFile.raceData.competitorData[index].readoutData == null) {
                "Competitor already has a readout."
            }
            index
        }

        val siNumberValue = parseOptionalSiNumber(siNumber, matchedCompetitorIndex?.let { index ->
            projectFile.raceData.competitorData[index].competitorCategory.competitor.siNumber
        })
        val startSecondsValue = parseOptionalDaySeconds(startSeconds, "Start time")
        val finishSecondsValue = parseOptionalDaySeconds(finishSeconds, "Finish time")
        require(startSecondsValue == null || finishSecondsValue == null || finishSecondsValue >= startSecondsValue) {
            "Finish time cannot be earlier than start time."
        }
        val controlCodeValues = parseControlCodes(controlCodes)
        require(readoutDateTimeIso.isNotBlank()) {
            "Readout date/time cannot be blank."
        }

        val matchedCompetitorData = matchedCompetitorIndex?.let { projectFile.raceData.competitorData[it] }
        val categoryData = matchedCompetitorData?.competitorCategory?.competitor?.categoryId?.let { categoryId ->
            projectFile.raceData.categories.firstOrNull { it.category.id == categoryId }
        }
        val evaluation = categoryData?.let { data ->
            CourseEvaluator.evaluate(
                raceType = data.category.effectiveRaceType(projectFile.raceData.race),
                punches = controlCodeValues.map { EvaluationPunch(it, SIRecordType.CONTROL) },
                controlPoints = projectFile.raceData.evaluationControlPoints(data)
            )
        }
        val effectiveStatus = if (resultStatus == ResultStatus.OK && evaluation != null) {
            evaluation.resultStatus
        } else {
            resultStatus
        }
        val runTimeSeconds = if (startSecondsValue != null && finishSecondsValue != null) {
            finishSecondsValue - startSecondsValue
        } else {
            0
        }
        val punches = buildManualPunches(
            raceId = projectFile.raceData.race.id,
            resultId = resultId,
            siNumber = siNumberValue,
            startSeconds = startSecondsValue,
            finishSeconds = finishSecondsValue,
            controlCodes = controlCodeValues,
            controlStatuses = evaluation?.punchStatuses,
            punchIdFactory = punchIdFactory
        )
        val readoutData = EventReadoutData(
            result = EventResult(
                id = resultId,
                raceId = projectFile.raceData.race.id,
                competitorId = matchedCompetitorData?.competitorCategory?.competitor?.id,
                siNumber = siNumberValue,
                cardType = 0,
                checkTimeSeconds = null,
                startTimeSeconds = startSecondsValue,
                finishTimeSeconds = finishSecondsValue,
                readoutDateTimeIso = readoutDateTimeIso,
                automaticStatus = false,
                resultStatus = effectiveStatus,
                points = evaluation?.points ?: 0,
                runTimeSeconds = runTimeSeconds,
                modified = true,
                sent = false
            ),
            punches = punches
        )

        return if (matchedCompetitorIndex != null) {
            projectFile.copy(
                raceData = projectFile.raceData.copy(
                    competitorData = projectFile.raceData.competitorData.mapIndexed { index, data ->
                        if (index == matchedCompetitorIndex) data.copy(readoutData = readoutData) else data
                    }
                )
            )
        } else {
            projectFile.copy(
                raceData = projectFile.raceData.copy(
                    unmatchedReadoutData = projectFile.raceData.unmatchedReadoutData + readoutData
                )
            )
        }
    }

    /** Adds one downloaded SPORTident readout, auto-matching a competitor by SI number when possible. */
    fun addDownloadedSportIdentReadout(
        projectFile: EventProjectFile,
        resultId: String,
        cardType: Byte,
        readout: SportIdentCardReadout,
        readoutDateTimeIso: String,
        duplicatePolicy: EventReadoutDuplicatePolicy = EventReadoutDuplicatePolicy.Reject,
        punchIdFactory: (Int, SIRecordType) -> String
    ): EventProjectFile {
        require(resultId.isNotBlank()) {
            "Readout ID cannot be blank."
        }
        require(!projectFile.raceData.containsReadout(resultId)) {
            "Readout ID already exists: $resultId"
        }
        require(readoutDateTimeIso.isNotBlank()) {
            "Readout date/time cannot be blank."
        }
        val hasDuplicateSiNumber = projectFile.raceData.containsReadoutForSiNumber(readout.siNumber)
        val workingProjectFile = when {
            !hasDuplicateSiNumber -> projectFile
            duplicatePolicy == EventReadoutDuplicatePolicy.Replace -> removeReadoutForSiNumber(projectFile, readout.siNumber)
            duplicatePolicy == EventReadoutDuplicatePolicy.CreateNew -> projectFile
            else -> throw IllegalArgumentException("Readout already exists for SI number: ${readout.siNumber}")
        }

        val createNewReadout = hasDuplicateSiNumber && duplicatePolicy == EventReadoutDuplicatePolicy.CreateNew
        val matchedCompetitorIndex = if (createNewReadout) {
            null
        } else {
            workingProjectFile.raceData.competitorData.indexOfFirst { competitorData ->
                competitorData.competitorCategory.competitor.siNumber == readout.siNumber
            }.takeIf { it >= 0 }
        }
        matchedCompetitorIndex?.let { index ->
            require(workingProjectFile.raceData.competitorData[index].readoutData == null) {
                "Competitor already has a readout."
            }
        }
        val matchedCompetitorData = matchedCompetitorIndex?.let { workingProjectFile.raceData.competitorData[it] }
        val matchedCompetitor = matchedCompetitorData?.competitorCategory?.competitor
        val categoryData = matchedCompetitor?.categoryId?.let { categoryId ->
            workingProjectFile.raceData.categories.firstOrNull { it.category.id == categoryId }
        }

        val controlPunches = readout.punches
        val evaluation = categoryData?.let { data ->
            CourseEvaluator.evaluate(
                raceType = data.category.effectiveRaceType(projectFile.raceData.race),
                punches = controlPunches.map { EvaluationPunch(it.siCode, SIRecordType.CONTROL) },
                controlPoints = workingProjectFile.raceData.evaluationControlPoints(data)
            )
        }
        val startSeconds = readout.startTime?.getSeconds()
            ?: matchedCompetitor?.drawnStartTimeSeconds?.let { drawnStart ->
                raceStartSecondsOfDay(workingProjectFile.raceData.race.startDateTimeIso)?.let { raceStart ->
                    (raceStart + drawnStart) % SportIdentCodes.SECONDS_DAY
                }
            }
        val finishSeconds = readout.finishTime?.getSeconds()
        val runTimeSeconds = if (startSeconds != null && finishSeconds != null) {
            finishSeconds - startSeconds
        } else {
            0
        }
        val timeLimitSeconds = categoryData?.category?.effectiveTimeLimitSeconds(projectFile.raceData.race)
            ?: projectFile.raceData.race.timeLimitSeconds
        val resultStatus = when {
            startSeconds == null || finishSeconds == null -> ResultStatus.ERROR
            runTimeSeconds > timeLimitSeconds -> ResultStatus.OVER_TIME_LIMIT
            evaluation != null -> evaluation.resultStatus
            else -> ResultStatus.NO_RANKING
        }
        val punches = buildDownloadedPunches(
            raceId = projectFile.raceData.race.id,
            resultId = resultId,
            siNumber = readout.siNumber,
            startSeconds = startSeconds,
            finishSeconds = finishSeconds,
            controlPunches = controlPunches,
            controlStatuses = evaluation?.punchStatuses,
            aliases = projectFile.raceData.aliases,
            punchIdFactory = punchIdFactory
        )
        val readoutData = EventReadoutData(
            result = EventResult(
                id = resultId,
                raceId = workingProjectFile.raceData.race.id,
                competitorId = matchedCompetitor?.id,
                siNumber = readout.siNumber,
                cardType = cardType,
                checkTimeSeconds = readout.checkTime?.getSeconds(),
                startTimeSeconds = startSeconds,
                finishTimeSeconds = finishSeconds,
                readoutDateTimeIso = readoutDateTimeIso,
                automaticStatus = true,
                resultStatus = resultStatus,
                points = evaluation?.points ?: 0,
                runTimeSeconds = runTimeSeconds,
                modified = false,
                sent = false
            ),
            punches = punches
        )

        return if (matchedCompetitorIndex != null) {
            workingProjectFile.copy(
                raceData = workingProjectFile.raceData.copy(
                    competitorData = workingProjectFile.raceData.competitorData.mapIndexed { index, data ->
                        if (index == matchedCompetitorIndex) data.copy(readoutData = readoutData) else data
                    }
                )
            )
        } else {
            workingProjectFile.copy(
                raceData = workingProjectFile.raceData.copy(
                    unmatchedReadoutData = workingProjectFile.raceData.unmatchedReadoutData + readoutData
                )
            )
        }
    }

    /** Returns a copy of the Event File with one validated alias changed. */
    fun updateAlias(
        projectFile: EventProjectFile,
        aliasId: String,
        siCode: String,
        name: String
    ): EventProjectFile {
        val aliasPosition = projectFile.raceData.aliases.indexOfFirst { it.id == aliasId }
        require(aliasPosition >= 0) {
            "Alias was not found: $aliasId"
        }

        val trimmedCode = siCode.trim()
        val trimmedName = name.trim()
        val existingCodes = projectFile.raceData.aliases.map { it.siCode }
        val existingNames = projectFile.raceData.aliases.map { it.name }

        require(AliasRules.validateCode(trimmedCode, existingCodes, aliasPosition) == AliasValidationResult.Valid) {
            "Alias SI code is invalid or duplicated."
        }
        require(AliasRules.validateName(trimmedName, existingNames, aliasPosition) == AliasValidationResult.Valid) {
            "Alias name is invalid or duplicated."
        }

        val aliases = projectFile.raceData.aliases.mapIndexed { index, alias ->
            if (index == aliasPosition) {
                alias.copy(siCode = trimmedCode.toInt(), name = trimmedName)
            } else {
                alias
            }
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(aliases = aliases)
        )
    }

    /** Returns a copy of the Event File with a validated alias appended. */
    fun addAlias(
        projectFile: EventProjectFile,
        aliasId: String,
        siCode: String,
        name: String
    ): EventProjectFile {
        require(aliasId.isNotBlank()) {
            "Alias ID cannot be blank."
        }
        require(projectFile.raceData.aliases.none { it.id == aliasId }) {
            "Alias ID already exists: $aliasId"
        }

        val aliasPosition = projectFile.raceData.aliases.size
        val trimmedCode = siCode.trim()
        val trimmedName = name.trim()
        val existingCodes = projectFile.raceData.aliases.map { it.siCode }
        val existingNames = projectFile.raceData.aliases.map { it.name }

        require(AliasRules.validateCode(trimmedCode, existingCodes, aliasPosition) == AliasValidationResult.Valid) {
            "Alias SI code is invalid or duplicated."
        }
        require(AliasRules.validateName(trimmedName, existingNames, aliasPosition) == AliasValidationResult.Valid) {
            "Alias name is invalid or duplicated."
        }

        val alias = EventAlias(
            id = aliasId,
            raceId = projectFile.raceData.race.id,
            siCode = trimmedCode.toInt(),
            name = trimmedName
        )

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                aliases = projectFile.raceData.aliases + alias
            )
        )
    }

    /** Returns a copy of the Event File with one alias removed. */
    fun removeAlias(projectFile: EventProjectFile, aliasId: String): EventProjectFile {
        require(projectFile.raceData.aliases.any { it.id == aliasId }) {
            "Alias was not found: $aliasId"
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                aliases = projectFile.raceData.aliases.filterNot { it.id == aliasId }
            )
        )
    }

    private inline fun <T> Iterable<T>.noneIndexed(predicate: (index: Int, T) -> Boolean): Boolean =
        withIndex().none { (index, value) -> predicate(index, value) }

    private fun CompetitorCsvImportRow.existingCompetitorPosition(
        competitors: List<EventCompetitorData>,
        duplicatePolicy: CompetitorCsvImportDuplicatePolicy
    ): Int =
        when (duplicatePolicy) {
            CompetitorCsvImportDuplicatePolicy.REJECT_DUPLICATES -> -1
            CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_INDEX ->
                index.takeIf { it.isNotBlank() }?.let { registrationIndex ->
                    competitors.indexOfFirst { data ->
                        data.competitorCategory.competitor.index == registrationIndex
                    }
                } ?: -1
            CompetitorCsvImportDuplicatePolicy.SKIP_EXISTING_BY_IMPORT_KEY,
            CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_IMPORT_KEY ->
                competitors.indexOfFirst { data ->
                    data.competitorCategory.competitor.importKey() == importKey()
                }
        }

    /*
     * EventReg registration tables do not always expose a stable registration
     * index.  When an index is available it remains the strongest identity; for
     * EventReg website imports without an index, name plus club is the best
     * repeatable key available for deciding whether a downloaded competitor is
     * already present in the current Event File.
     */
    private fun CompetitorCsvImportRow.importKey(): String =
        competitorImportKey(index = index, firstName = firstName, lastName = lastName, club = club)

    private fun EventCompetitor.importKey(): String =
        competitorImportKey(index = index, firstName = firstName, lastName = lastName, club = club)

    private fun competitorImportKey(index: String, firstName: String, lastName: String, club: String): String {
        val trimmedIndex = index.trim()
        if (trimmedIndex.isNotEmpty()) {
            return "index:${trimmedIndex.lowercase()}"
        }
        val normalizedName = "${lastName.trim().lowercase()}|${firstName.trim().lowercase()}"
        val normalizedClub = club.trim().lowercase()
        return if (normalizedClub.isEmpty()) {
            "name:$normalizedName"
        } else {
            "name-club:$normalizedName|$normalizedClub"
        }
    }

    private fun validatedControl(
        projectFile: EventProjectFile,
        existingControlPosition: Int?,
        controlId: String,
        label: String,
        siCode: String,
        type: org.openardf.radiooracle.shared.domain.ControlPointType,
        scored: Boolean,
        publicLabel: String,
        notes: String
    ): EventControl {
        val trimmedCode = siCode.trim()
        val trimmedPublicLabel = publicLabel.trim()
        val trimmedNotes = notes.trim()
        val code = trimmedCode.toIntOrNull()
            ?: throw IllegalArgumentException("Control SI code is invalid.")
        require(SportIdentCodes.isSICodeValid(code)) {
            "Control SI code is outside the supported SportIdent station range."
        }
        val trimmedLabel = label.trim().takeIf { it.isNotEmpty() }
            ?: EventControlCatalog.defaultLabel(code, type)
        require(projectFile.raceData.controls.noneIndexed { index, control ->
            index != existingControlPosition && control.label == trimmedLabel
        }) {
            "Control label must be unique."
        }
        return EventControl(
            id = controlId,
            raceId = projectFile.raceData.race.id,
            label = trimmedLabel,
            siCode = code,
            type = type,
            scored = scored,
            mandatory = false,
            publicLabel = trimmedPublicLabel.takeIf { it.isNotEmpty() },
            notes = trimmedNotes.takeIf { it.isNotEmpty() }
        )
    }

    private fun EventRaceData.evaluationControlPoints(categoryData: EventCategoryData): List<EvaluationControlPoint> {
        val controlsById = controls.associateBy { it.id }
        return categoryData.controlPoints.map { controlPoint ->
            val control = controlsById[controlPoint.controlId]
            EvaluationControlPoint(
                siCode = control?.siCode ?: controlPoint.siCode,
                type = control?.type ?: controlPoint.type,
                scored = control?.scored ?: controlPoint.type.defaultScored()
            )
        }
    }

    private fun EventRaceData.containsReadout(resultId: String): Boolean =
        competitorData.any { it.readoutData?.result?.id == resultId } ||
            unmatchedReadoutData.any { it.result.id == resultId }

    private fun EventRaceData.containsReadoutForSiNumber(siNumber: Int): Boolean =
        competitorData.any { it.readoutData?.result?.siNumber == siNumber } ||
            unmatchedReadoutData.any { it.result.siNumber == siNumber }

    private fun removeReadoutForSiNumber(projectFile: EventProjectFile, siNumber: Int): EventProjectFile =
        projectFile.copy(
            raceData = projectFile.raceData.copy(
                competitorData = projectFile.raceData.competitorData.map { data ->
                    if (data.readoutData?.result?.siNumber == siNumber) {
                        data.copy(readoutData = null)
                    } else {
                        data
                    }
                },
                unmatchedReadoutData = projectFile.raceData.unmatchedReadoutData.filterNot {
                    it.result.siNumber == siNumber
                }
            )
        )

    private fun parseOptionalSiNumber(siNumber: String, fallbackSiNumber: Int?): Int? {
        val trimmedSiNumber = siNumber.trim()
        val siNumberValue = if (trimmedSiNumber.isEmpty()) {
            fallbackSiNumber
        } else {
            trimmedSiNumber.toIntOrNull()
                ?: throw IllegalArgumentException("SI number is invalid.")
        }
        require(siNumberValue == null || SportIdentCodes.isSINumberValid(siNumberValue)) {
            "SI number is outside the supported SportIdent card range."
        }
        return siNumberValue
    }

    private fun parseOptionalDaySeconds(value: String, fieldName: String): Long? {
        val trimmedValue = value.trim()
        if (trimmedValue.isEmpty()) {
            return null
        }
        val seconds = trimmedValue.toLongOrNull()
            ?: throw IllegalArgumentException("$fieldName is invalid.")
        require(seconds in 0..<SportIdentCodes.SECONDS_DAY) {
            "$fieldName must be within one day."
        }
        return seconds
    }

    private fun parseControlCodes(controlCodes: String): List<Int> =
        controlCodes
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.split(Regex("[,\\s]+"))
            ?.map { token ->
                val code = token.toIntOrNull()
                    ?: throw IllegalArgumentException("Control code is invalid: $token")
                require(SportIdentCodes.isSICodeValid(code)) {
                    "Control code is outside the supported SportIdent station range: $code"
                }
                code
            }
            ?: emptyList()

    private fun buildManualPunches(
        raceId: String,
        resultId: String,
        siNumber: Int?,
        startSeconds: Long?,
        finishSeconds: Long?,
        controlCodes: List<Int>,
        controlStatuses: List<PunchStatus>?,
        punchIdFactory: (Int, SIRecordType) -> String
    ): List<EventAliasPunch> {
        val punches = mutableListOf<EventAliasPunch>()
        startSeconds?.let {
            punches += manualPunch(
                id = punchIdFactory(punches.size, SIRecordType.START),
                raceId = raceId,
                resultId = resultId,
                siNumber = siNumber,
                siCode = 0,
                siTimeSeconds = it,
                punchType = SIRecordType.START,
                order = punches.size,
                punchStatus = PunchStatus.VALID
            )
        }
        controlCodes.forEachIndexed { index, code ->
            punches += manualPunch(
                id = punchIdFactory(punches.size, SIRecordType.CONTROL),
                raceId = raceId,
                resultId = resultId,
                siNumber = siNumber,
                siCode = code,
                siTimeSeconds = startSeconds ?: 0,
                punchType = SIRecordType.CONTROL,
                order = punches.size,
                punchStatus = controlStatuses?.getOrNull(index) ?: PunchStatus.UNKNOWN
            )
        }
        finishSeconds?.let {
            punches += manualPunch(
                id = punchIdFactory(punches.size, SIRecordType.FINISH),
                raceId = raceId,
                resultId = resultId,
                siNumber = siNumber,
                siCode = 0,
                siTimeSeconds = it,
                punchType = SIRecordType.FINISH,
                order = punches.size,
                punchStatus = PunchStatus.VALID
            )
        }
        return punches
    }

    private fun manualPunch(
        id: String,
        raceId: String,
        resultId: String,
        siNumber: Int?,
        siCode: Int,
        siTimeSeconds: Long,
        punchType: SIRecordType,
        order: Int,
        punchStatus: PunchStatus
    ): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = id,
                raceId = raceId,
                resultId = resultId,
                cardNumber = siNumber,
                siCode = siCode,
                siTimeSeconds = siTimeSeconds,
                originalSiTimeSeconds = siTimeSeconds,
                punchType = punchType,
                order = order,
                punchStatus = punchStatus,
                splitSeconds = 0
            ),
            alias = null
        )

    private fun buildDownloadedPunches(
        raceId: String,
        resultId: String,
        siNumber: Int,
        startSeconds: Long?,
        finishSeconds: Long?,
        controlPunches: List<org.openardf.radiooracle.shared.sportident.SportIdentCardPunch>,
        controlStatuses: List<PunchStatus>?,
        aliases: List<EventAlias>,
        punchIdFactory: (Int, SIRecordType) -> String
    ): List<EventAliasPunch> {
        val punches = mutableListOf<EventAliasPunch>()
        startSeconds?.let {
            punches += downloadedPunch(
                id = punchIdFactory(punches.size, SIRecordType.START),
                raceId = raceId,
                resultId = resultId,
                siNumber = siNumber,
                siCode = 0,
                siTimeSeconds = it,
                punchType = SIRecordType.START,
                order = punches.size,
                punchStatus = PunchStatus.VALID,
                alias = null
            )
        }
        controlPunches.forEachIndexed { index, punch ->
            val siCode = punch.siCode
            punches += downloadedPunch(
                id = punchIdFactory(punches.size, SIRecordType.CONTROL),
                raceId = raceId,
                resultId = resultId,
                siNumber = siNumber,
                siCode = siCode,
                siTimeSeconds = punch.siTime.getSeconds(),
                punchType = SIRecordType.CONTROL,
                order = punches.size,
                punchStatus = controlStatuses?.getOrNull(index) ?: PunchStatus.UNKNOWN,
                alias = aliases.firstOrNull { it.siCode == siCode }
            )
        }
        finishSeconds?.let {
            punches += downloadedPunch(
                id = punchIdFactory(punches.size, SIRecordType.FINISH),
                raceId = raceId,
                resultId = resultId,
                siNumber = siNumber,
                siCode = 0,
                siTimeSeconds = it,
                punchType = SIRecordType.FINISH,
                order = punches.size,
                punchStatus = PunchStatus.VALID,
                alias = null
            )
        }
        return punches.mapIndexed { index, aliasPunch ->
            if (index == 0) {
                aliasPunch
            } else {
                aliasPunch.copy(
                    punch = aliasPunch.punch.copy(
                        splitSeconds = aliasPunch.punch.siTimeSeconds - punches[index - 1].punch.siTimeSeconds
                    )
                )
            }
        }
    }

    private fun downloadedPunch(
        id: String,
        raceId: String,
        resultId: String,
        siNumber: Int,
        siCode: Int,
        siTimeSeconds: Long,
        punchType: SIRecordType,
        order: Int,
        punchStatus: PunchStatus,
        alias: EventAlias?
    ): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = id,
                raceId = raceId,
                resultId = resultId,
                cardNumber = siNumber,
                siCode = siCode,
                siTimeSeconds = siTimeSeconds,
                originalSiTimeSeconds = siTimeSeconds,
                punchType = punchType,
                order = order,
                punchStatus = punchStatus,
                splitSeconds = 0
            ),
            alias = alias
        )

    private fun raceStartSecondsOfDay(startDateTimeIso: String): Long? {
        val time = startDateTimeIso.substringAfter('T', missingDelimiterValue = "")
            .substringBefore('.')
            .substringBefore('Z')
            .substringBefore('+')
            .substringBefore('-')
        if (time.isBlank()) {
            return null
        }
        val parts = time.split(":")
        if (parts.size < 2) {
            return null
        }
        val hour = parts[0].toLongOrNull() ?: return null
        val minute = parts[1].toLongOrNull() ?: return null
        val second = parts.getOrNull(2)?.toLongOrNull() ?: 0
        return if (hour in 0..23 && minute in 0..59 && second in 0..59) {
            hour * 3600 + minute * 60 + second
        } else {
            null
        }
    }

    private fun parseNonNegativeInt(value: String, label: String): Int {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) {
            "$label is required."
        }
        val parsed = trimmed.toIntOrNull()
            ?: throw IllegalArgumentException("$label is invalid.")
        require(parsed >= 0) {
            "$label cannot be negative."
        }
        return parsed
    }

    private fun validatedCompetitorBasics(
        raceId: String,
        competitorId: String,
        firstName: String,
        lastName: String,
        startNumber: String,
        siNumber: String,
        existingCompetitors: List<EventCompetitorData>,
        existingCompetitorPosition: Int?
    ): EventCompetitor {
        val trimmedFirstName = firstName.trim()
        val trimmedLastName = lastName.trim()
        require(trimmedFirstName.isNotEmpty()) {
            "Competitor first name cannot be blank."
        }
        require(trimmedLastName.isNotEmpty()) {
            "Competitor last name cannot be blank."
        }

        val trimmedStartNumber = startNumber.trim()
        require(trimmedStartNumber.isNotEmpty()) {
            "Start number is required."
        }
        val startNumberValue = trimmedStartNumber.toIntOrNull()
            ?: throw IllegalArgumentException("Start number is invalid.")
        require(
            existingCompetitors.noneIndexed { index, data ->
                index != existingCompetitorPosition && data.competitorCategory.competitor.startNumber == startNumberValue
            }
        ) {
            "Start number must be unique."
        }

        val trimmedSiNumber = siNumber.trim()
        val siNumberValue = if (trimmedSiNumber.isEmpty()) {
            null
        } else {
            trimmedSiNumber.toIntOrNull()
                ?: throw IllegalArgumentException("SI number is invalid.")
        }
        require(siNumberValue == null || SportIdentCodes.isSINumberValid(siNumberValue)) {
            "SI number is outside the supported SportIdent card range."
        }
        require(
            siNumberValue == null || existingCompetitors.noneIndexed { index, data ->
                index != existingCompetitorPosition && data.competitorCategory.competitor.siNumber == siNumberValue
            }
        ) {
            "SI number must be unique."
        }

        return EventCompetitor(
            id = competitorId,
            raceId = raceId,
            categoryId = null,
            firstName = trimmedFirstName,
            lastName = trimmedLastName,
            club = "",
            index = "",
            isMan = true,
            birthYear = null,
            siNumber = siNumberValue,
            siRent = false,
            startNumber = startNumberValue,
            drawnStartTimeSeconds = null
        )
    }

    private fun drawCategoryCompetitors(
        competitors: List<EventCompetitor>,
        previousClub: String?,
        options: StartDrawOptions
    ): List<EventCompetitor> =
        when (options.clubHandling) {
            StartDrawClubHandling.IGNORE -> competitors.sortedWith(competitorStartDrawComparator(options, "category"))
            StartDrawClubHandling.AVOID_BACK_TO_BACK -> drawClubRotatedCategory(competitors, previousClub, options)
        }

    private fun selectStartSlotQueue(
        categoryQueues: List<CategoryStartQueue>,
        selectedQueues: List<CategoryStartQueue>,
        selectedCompetitors: List<EventCompetitor>,
        previousClub: String?,
        previousCategoryId: String?,
        preferredStartGroup: Int?,
        options: StartDrawOptions
    ): CategoryStartQueue? {
        // First remove queues that are incompatible with the current start slot:
        // duplicate category at the same time, or same first fox among categories
        // expected to have similar speed. If all queues conflict, the slot is
        // left partially filled and the next start time is attempted.
        val compatibleQueues = if (selectedQueues.isEmpty()) {
            categoryQueues
        } else {
            val selectedCategoryIds = selectedQueues.map { it.category.id }.toSet()
            categoryQueues
                .filterNot { it.category.id in selectedCategoryIds }
                .filterNot { it.hasFirstFoxSpeedConflict(selectedQueues) }
        }

        if (compatibleQueues.isEmpty()) {
            return null
        }

        /*
         * Championship preferred thirds are stronger than Radio-Oracle's normal
         * spacing best practices. A queue is preferred for this start slot when
         * it still has at least one competitor assigned to the current third, or
         * a competitor with no assigned third who may fill any blank. If the
         * remaining field makes the current third impossible, fall back so the
         * draw completes; the quality evaluator will mark the saved order red.
         */
        val startGroupSafe = compatibleQueues
            .filter { it.hasPreferredStartGroupCandidate(preferredStartGroup) }
            .takeIf { it.isNotEmpty() }
            ?: compatibleQueues

        // Category adjacency is a best practice, not a hard stop. Prefer any
        // category other than the one that ended the previous start time, but
        // fall back when only that category remains.
        val categorySafe = startGroupSafe
            .filterNot { it.category.id == previousCategoryId }
            .takeIf { it.isNotEmpty() }
            ?: startGroupSafe

        val clubSafe = if (options.clubHandling == StartDrawClubHandling.AVOID_BACK_TO_BACK) {
            val selectedClubs = selectedCompetitors.mapNotNull { it.clubKey() }.toSet()
            // Same-club starters at the same time are worse than a partially
            // filled time slot, so return null if every compatible queue would
            // duplicate a club already chosen for this slot.
            val clubSafeAfterSelectedClubs = categorySafe
                .filterNot { candidate ->
                    val candidateClub = candidate.competitorForStartSlot(
                        selectedCompetitors,
                        previousClub,
                        preferredStartGroup,
                        options
                    )?.clubKey()
                    candidateClub != null && candidateClub in selectedClubs
                }
                .takeIf { it.isNotEmpty() }
                ?: return null

            // Same-club adjacency across start times is avoidable only when at
            // least one otherwise compatible queue has a different club at its
            // head. Fall back when the field leaves no alternative.
            clubSafeAfterSelectedClubs
                .filterNot { candidate ->
                    val candidateClub = candidate.competitorForStartSlot(
                        selectedCompetitors,
                        previousClub,
                        preferredStartGroup,
                        options
                    )?.clubKey()
                    candidateClub != null && candidateClub == previousClub
                }
                .takeIf { it.isNotEmpty() }
                ?: clubSafeAfterSelectedClubs
        } else {
            categorySafe
        }
        return clubSafe.sortedWith(categoryStartQueueComparator(options)).firstOrNull()
    }

    private fun CategoryStartQueue.hasFirstFoxSpeedConflict(selectedQueues: List<CategoryStartQueue>): Boolean =
        firstFox != null && speedGroup != null && selectedQueues.any { selected ->
            selected.firstFox == firstFox && selected.speedGroup == speedGroup
        }

    /**
     * Chooses the competitor to remove from an already-selected category queue.
     *
     * Most of the draw works at category-queue granularity, but club rules are
     * competitor-specific. Looking past the head of the queue preserves the
     * selected category while avoiding same-club adjacency when that category has
     * a later runner from a different club.
     */
    private fun CategoryStartQueue.competitorIndexForStartSlot(
        selectedCompetitors: List<EventCompetitor>,
        previousClub: String?,
        preferredStartGroup: Int?,
        options: StartDrawOptions
    ): Int {
        val startGroupIndexes = competitors.indices
            .filter { competitors[it].isAllowedInPreferredStartGroup(preferredStartGroup) }
            .takeIf { it.isNotEmpty() }
            ?: competitors.indices.toList()

        if (options.clubHandling == StartDrawClubHandling.IGNORE) {
            return startGroupIndexes.firstOrNull() ?: 0
        }
        val selectedClubs = selectedCompetitors.mapNotNull { it.clubKey() }.toSet()
        return startGroupIndexes.firstOrNull { index ->
            val competitor = competitors[index]
            val club = competitor.clubKey()
            club == null || (club !in selectedClubs && club != previousClub)
        } ?: startGroupIndexes.firstOrNull() ?: 0
    }

    private fun CategoryStartQueue.competitorForStartSlot(
        selectedCompetitors: List<EventCompetitor>,
        previousClub: String?,
        preferredStartGroup: Int?,
        options: StartDrawOptions
    ): EventCompetitor? =
        competitors.getOrNull(
            competitorIndexForStartSlot(selectedCompetitors, previousClub, preferredStartGroup, options)
        )

    private fun CategoryStartQueue.hasPreferredStartGroupCandidate(preferredStartGroup: Int?): Boolean =
        preferredStartGroup == null || competitors.any { it.isAllowedInPreferredStartGroup(preferredStartGroup) }

    private fun drawClubRotatedCategory(
        competitors: List<EventCompetitor>,
        previousClub: String?,
        options: StartDrawOptions
    ): List<EventCompetitor> {
        // Within one category, competitors are grouped by club and the largest
        // club queues are drained first. This "largest first" rule is the
        // standard greedy strategy for avoiding adjacency in uneven groups: it
        // spreads the most constrained club before smaller clubs are exhausted.
        val clubQueues = competitors
            .groupBy { it.clubKey() ?: "competitor:${it.id}" }
            .values
            .map { clubCompetitors ->
                ClubStartQueue(
                    club = clubCompetitors.firstOrNull()?.clubKey(),
                    competitors = clubCompetitors
                        .sortedWith(competitorStartDrawComparator(options, "club:${clubCompetitors.firstOrNull()?.clubKey() ?: ""}"))
                        .toMutableList()
                )
            }
            .sortedWith(clubStartQueueComparator(options))
            .toMutableList()

        val drawn = mutableListOf<EventCompetitor>()
        var lastClub = previousClub
        while (clubQueues.isNotEmpty()) {
            val selectedQueue = clubQueues
                .filterNot { it.club != null && it.club == lastClub }
                .sortedWith(clubStartQueueComparator(options))
                .firstOrNull()
                ?: clubQueues.sortedWith(clubStartQueueComparator(options)).first()
            val competitor = selectedQueue.competitors.removeAt(0)
            drawn += competitor
            lastClub = selectedQueue.club
            if (selectedQueue.competitors.isEmpty()) {
                clubQueues.remove(selectedQueue)
            }
        }
        return drawn
    }

    /*
     * Comparator policy:
     * - Default seed: preserve predictable category/start-number order for users
     *   who do not request randomization.
     * - Non-default seed: keep the same high-level constraints, but use a stable
     *   seed hash to break otherwise equal or flexible choices.
     */
    private fun clubStartQueueComparator(options: StartDrawOptions): Comparator<ClubStartQueue> =
        if (!options.usesSeededRandomization()) {
            compareByDescending<ClubStartQueue> { it.competitors.size }
                .thenBy { it.club ?: "" }
                .thenBy { it.competitors.firstOrNull()?.startNumber ?: Int.MAX_VALUE }
        } else {
            compareByDescending<ClubStartQueue> { it.competitors.size }
                .thenBy { seededRank(options.seed, "club:${it.club ?: it.competitors.firstOrNull()?.id ?: ""}") }
                .thenBy { it.club ?: "" }
                .thenBy { it.competitors.firstOrNull()?.startNumber ?: Int.MAX_VALUE }
        }

    private data class ClubStartQueue(
        val club: String?,
        val competitors: MutableList<EventCompetitor>
    )

    private data class CompetitorStartGroupHistory(
        val competitorKey: String,
        val startGroup: Int
    )

    private data class CategoryStartQueue(
        val category: EventCategory,
        val firstFox: Int?,
        val speedGroup: StartDrawSpeedGroup?,
        val competitors: MutableList<EventCompetitor>
    )

    private enum class StartDrawSpeedGroup {
        FAST,
        SLOW
    }

    private fun categoryStartQueueComparator(options: StartDrawOptions): Comparator<CategoryStartQueue> =
        if (!options.usesSeededRandomization()) {
            compareBy<CategoryStartQueue> { it.category.order }
                .thenBy { it.category.name }
                .thenBy { it.competitors.firstOrNull()?.startNumber ?: Int.MAX_VALUE }
        } else {
            compareByDescending<CategoryStartQueue> { it.competitors.size }
                .thenBy { seededRank(options.seed, "category:${it.category.id}:${it.competitors.firstOrNull()?.id ?: ""}") }
                .thenBy { it.category.order }
                .thenBy { it.category.name }
        }

    private fun competitorStartDrawComparator(options: StartDrawOptions, scope: String): Comparator<EventCompetitor> =
        if (!options.usesSeededRandomization()) {
            compareBy<EventCompetitor> { it.startNumber }
                .thenBy { it.fullName() }
        } else {
            compareBy<EventCompetitor> { seededRank(options.seed, "$scope:${it.id}:${it.startNumber}:${it.fullName()}") }
                .thenBy { it.startNumber }
                .thenBy { it.fullName() }
        }

    private fun StartDrawOptions.usesSeededRandomization(): Boolean =
        seed != StartDrawOptions.DEFAULT_SEED

    private fun StartDrawOptions.preferredStartGroupForSlot(startSlotIndex: Int, totalStartSlots: Int): Int? =
        if (startGroupMode == StartDrawStartGroupMode.DISABLED || totalStartSlots <= 0) {
            null
        } else {
            ((startSlotIndex * 3) / totalStartSlots + 1).coerceIn(1, 3)
        }

    private fun EventCompetitor.isAllowedInPreferredStartGroup(startGroup: Int?): Boolean =
        startGroup == null || preferredStartGroup == null || preferredStartGroup == startGroup

    private fun ceilDiv(value: Int, divisor: Int): Int =
        if (value == 0) 0 else (value + divisor - 1) / divisor

    private fun startGroupHistoryFromStartRows(rows: List<CompetitorStartCsvImportRow>): List<CompetitorStartGroupHistory> {
        val scheduledRows = rows
            .map { row ->
                val startSeconds = DurationFormatter.minuteStringToSeconds(row.startTimeText.trim())
                val competitorKey = row.historyKey()
                row to startSeconds to competitorKey
            }
        val startSlotIndexBySeconds = scheduledRows
            .map { it.first.second }
            .distinct()
            .sorted()
            .withIndex()
            .associate { it.value to it.index }
        val totalStartSlots = startSlotIndexBySeconds.size
        if (totalStartSlots == 0) {
            return emptyList()
        }

        return scheduledRows.map { rowWithKey ->
            val startSeconds = rowWithKey.first.second
            CompetitorStartGroupHistory(
                competitorKey = rowWithKey.second,
                startGroup = startGroupForSlotIndex(startSlotIndexBySeconds.getValue(startSeconds), totalStartSlots)
            )
        }
    }

    private fun balancedStartGroupAssignments(
        competitors: List<EventCompetitor>,
        historyByCompetitorKey: Map<String, Map<Int, Int>>,
        options: StartDrawOptions
    ): Map<String, Int> {
        val totalStartSlots = ceilDiv(competitors.size, options.startersPerStartTime)
        val capacityByStartGroup = (0 until totalStartSlots)
            .map { startGroupForSlotIndex(it, totalStartSlots) }
            .groupingBy { it }
            .eachCount()
            .mapValues { (_, slots) -> slots * options.startersPerStartTime }
        val assignedCountByStartGroup = mutableMapOf(1 to 0, 2 to 0, 3 to 0)
        val assignments = mutableMapOf<String, Int>()

        /*
         * Draw the most constrained people first. A person who already has two
         * starts in one third has fewer fair choices than a person with no
         * history, so assigning constrained competitors first reduces the need
         * for late fallbacks.
         */
        competitors
            .sortedWith(
                compareByDescending<EventCompetitor> { competitor ->
                    historyByCompetitorKey[competitor.historyKey()]?.values?.maxOrNull() ?: 0
                }
                    .thenByDescending { competitor -> historyByCompetitorKey[competitor.historyKey()]?.values?.sum() ?: 0 }
                    .then(competitorStartDrawComparator(options, "balanced-start-groups"))
            )
            .forEach { competitor ->
                val history = historyByCompetitorKey[competitor.historyKey()].orEmpty()
                val selectedStartGroup = (1..3)
                    .minWithOrNull(
                        compareBy<Int> { candidateStartGroup ->
                            balancedStartGroupCost(
                                candidateStartGroup = candidateStartGroup,
                                history = history,
                                assignedCountByStartGroup = assignedCountByStartGroup,
                                capacityByStartGroup = capacityByStartGroup
                            )
                        }
                            .thenBy { candidateStartGroup ->
                                if (options.usesSeededRandomization()) {
                                    seededRank(options.seed, "balanced:${competitor.id}:$candidateStartGroup")
                                } else {
                                    candidateStartGroup
                                }
                            }
                    )
                    ?: 1
                assignments[competitor.id] = selectedStartGroup
                assignedCountByStartGroup[selectedStartGroup] = assignedCountByStartGroup.getValue(selectedStartGroup) + 1
            }

        return assignments
    }

    private fun balancedStartGroupCost(
        candidateStartGroup: Int,
        history: Map<Int, Int>,
        assignedCountByStartGroup: Map<Int, Int>,
        capacityByStartGroup: Map<Int, Int>
    ): Int {
        val previousCountForGroup = history[candidateStartGroup] ?: 0
        val historyAfterAssignment = history + (candidateStartGroup to (previousCountForGroup + 1))
        val previousStarts = history.values.sum()
        val currentAssignments = assignedCountByStartGroup[candidateStartGroup] ?: 0
        val currentCapacity = capacityByStartGroup[candidateStartGroup] ?: 0

        /*
         * Cost order:
         * 1. Do not exceed physical capacity in the current event's third.
         * 2. Avoid assigning the same person to one third more than twice over
         *    the multi-day series.
         * 3. If this is likely the fourth event and the person has never had an
         *    early start, strongly prefer the first third now.
         * 4. Keep the current event balanced across thirds.
         * 5. Normalize desirability over the series: middle is best, late is
         *    second, early is least desirable.
         */
        val capacityPenalty = if (currentAssignments >= currentCapacity) 20_000 else 0
        val repeatPenalty = if (previousCountForGroup >= 2) 10_000 + previousCountForGroup * 500 else previousCountForGroup * 300
        val noEarlyStartPenalty = if (previousStarts >= 3 && (historyAfterAssignment[1] ?: 0) == 0) 4_000 else 0
        val balancePenalty = currentAssignments * 80
        val desirabilityPenalty = startGroupDesirability(candidateStartGroup) * 20 +
            ((historyAfterAssignment[2] ?: 0) + (historyAfterAssignment[3] ?: 0) - (historyAfterAssignment[1] ?: 0)).coerceAtLeast(0) * 30

        return capacityPenalty + repeatPenalty + noEarlyStartPenalty + balancePenalty + desirabilityPenalty
    }

    private fun startGroupForSlotIndex(startSlotIndex: Int, totalStartSlots: Int): Int =
        ((startSlotIndex * 3) / totalStartSlots + 1).coerceIn(1, 3)

    private fun startGroupDesirability(startGroup: Int): Int =
        when (startGroup) {
            2 -> 3
            3 -> 2
            else -> 1
        }

    private fun CompetitorStartCsvImportRow.historyKey(): String =
        siNumber?.let { "si:$it" } ?: "start:$startNumber"

    private fun EventCompetitor.historyKey(): String =
        siNumber?.let { "si:$it" } ?: "start:$startNumber"

    private fun seededRank(seed: String, value: String): Long {
        // FNV-1a followed by MurmurHash3-style finalization. Kotlin/Native/JVM
        // Random APIs are intentionally avoided here because the draw must be
        // reproducible across platforms and future runtime versions.
        var hash = 0xcbf29ce484222325UL
        val text = "$seed|$value"
        text.encodeToByteArray().forEach { byte ->
            hash = hash xor byte.toUByte().toULong()
            hash *= 0x100000001b3UL
        }
        var mixed = hash.toLong()
        mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
        mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
        return mixed xor (mixed ushr 31)
    }

    private fun EventCategory.startDrawSpeedGroup(): StartDrawSpeedGroup? {
        val ageClass = Regex("""\d+""").find(name)?.value?.toIntOrNull() ?: return null
        return when {
            ageClass <= 50 -> StartDrawSpeedGroup.FAST
            ageClass >= 60 -> StartDrawSpeedGroup.SLOW
            else -> null
        }
    }

    private fun EventCompetitor.clubKey(): String? =
        club.trim().takeIf { it.isNotEmpty() }
}
