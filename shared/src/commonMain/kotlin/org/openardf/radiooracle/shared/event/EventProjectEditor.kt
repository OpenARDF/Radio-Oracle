package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.alias.AliasRules
import org.openardf.radiooracle.shared.alias.AliasValidationResult
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.results.CourseEvaluator
import org.openardf.radiooracle.shared.results.EvaluationControlPoint
import org.openardf.radiooracle.shared.results.EvaluationPunch
import org.openardf.radiooracle.shared.sportident.SportIdentCodes

/** Shared event-project editing helpers used by desktop and future non-Android flows. */
object EventProjectEditor {
    /** Returns a copy of the project file with a validated race name. */
    fun renameRace(projectFile: EventProjectFile, name: String): EventProjectFile {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) {
            "Race name cannot be blank."
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                race = projectFile.raceData.race.copy(name = trimmedName)
            )
        )
    }

    /** Returns a copy of the project file with race-level settings changed. */
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

    /** Returns a copy of the project file with a validated race start date/time string. */
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

    /** Returns a copy of the project file with one validated category name changed. */
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

    /** Returns a copy of the project file with a new category using conservative defaults. */
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
     * Returns a copy of the project file with one category and its course removed.
     *
     * Desktop project files do not have Room foreign keys, so this helper makes
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

    /** Returns a copy of the project file with a category course parsed from a control-point string. */
    fun updateCategoryControlPoints(
        projectFile: EventProjectFile,
        categoryId: String,
        controlPointsText: String,
        controlPointIdFactory: (Int) -> String
    ): EventProjectFile {
        val categoryData = projectFile.raceData.categories.firstOrNull { it.category.id == categoryId }
            ?: throw IllegalArgumentException("Category was not found: $categoryId")

        val definitions = ControlPointRules.parseControlPoints(
            input = controlPointsText.trim(),
            raceType = categoryData.category.effectiveRaceType(projectFile.raceData.race)
        )
        val controlPoints = definitions.mapIndexed { index, definition ->
            EventControlPoint(
                id = controlPointIdFactory(index),
                categoryId = categoryId,
                siCode = definition.siCode,
                type = definition.type,
                order = definition.order
            )
        }
        val formattedControlPoints = ControlPointRules.formatControlPoints(definitions)

        val categories = projectFile.raceData.categories.map { data ->
            if (data.category.id == categoryId) {
                data.copy(
                    category = data.category.copy(controlPointsString = formattedControlPoints),
                    controlPoints = controlPoints
                )
            } else {
                data
            }
        }

        return projectFile.copy(
            raceData = projectFile.raceData.copy(categories = categories)
        )
    }

    /** Returns a copy of the project file with one competitor's validated name changed. */
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

    /** Returns a copy of the project file with one competitor assigned to a category, or to no category. */
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

    /** Returns a copy of the project file with one competitor's validated numbers changed. */
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

    /** Returns a copy of the project file with a new uncategorized competitor appended. */
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

    /**
     * Returns a copy of the project file with one competitor removed.
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
     * Returns a copy of the project file with one readout/result removed.
     *
     * Android deletes the result row and relies on Room to cascade punch rows.
     * Desktop project files keep result and punch data together, so removing
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

    /**
     * Returns a copy of the project file with one readout set to a manual status.
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
                controlPoints = data.controlPoints.map { EvaluationControlPoint(it.siCode, it.type) }
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

    /** Returns a copy of the project file with one validated alias changed. */
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

    /** Returns a copy of the project file with a validated alias appended. */
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

    /** Returns a copy of the project file with one alias removed. */
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

    private fun EventRaceData.containsReadout(resultId: String): Boolean =
        competitorData.any { it.readoutData?.result?.id == resultId } ||
            unmatchedReadoutData.any { it.result.id == resultId }

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
}
