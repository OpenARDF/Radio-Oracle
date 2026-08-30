/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolsky
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

package org.openardf.radiooracle.shared.publicresults

import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.PRELIMINARY_RESULT_NOTICE
import org.openardf.radiooracle.shared.event.PublicResultsPublicationStatus

data class PublicResultsPublicationReview(
    val status: PublicResultsPublicationStatus,
    val issues: List<String>
) {
    val isReady: Boolean
        get() = issues.isEmpty()

    fun requireReady() {
        require(isReady) {
            buildString {
                append("Official results are not ready to publish:\n")
                issues.forEach { issue -> append("- ").append(issue).append('\n') }
                append("Resolve these items or publish Preliminary Results instead.")
            }
        }
    }
}

/** Shared rules gate for an official result website and its downloadable files. */
object PublicResultsPublicationRules {
    fun review(
        raceData: EventRaceData,
        status: PublicResultsPublicationStatus
    ): PublicResultsPublicationReview {
        if (status == PublicResultsPublicationStatus.PRELIMINARY) {
            return PublicResultsPublicationReview(status, emptyList())
        }

        val issues = mutableListOf<String>()
        val competitorsWithoutResults = raceData.competitorData
            .filter { it.readoutData == null }
            .map { it.competitorCategory.competitor.fullName() }
        if (competitorsWithoutResults.isNotEmpty()) {
            issues += summarizedNames(
                label = "competitors have no result; mark non-starters DNS or remove non-participants",
                names = competitorsWithoutResults
            )
        }

        val competitorsWithoutBibs = raceData.competitorData
            .filter { it.competitorCategory.competitor.bibNumber.isBlank() }
            .map { it.competitorCategory.competitor.fullName() }
        if (competitorsWithoutBibs.isNotEmpty()) {
            issues += summarizedNames("competitors have no bib number", competitorsWithoutBibs)
        }

        val duplicateBibs = raceData.competitorData
            .map { it.competitorCategory.competitor }
            .filter { it.bibNumber.isNotBlank() }
            .groupBy { it.bibNumber.trim() }
            .filterValues { it.size > 1 }
        if (duplicateBibs.isNotEmpty()) {
            issues += "duplicate bib numbers: " + duplicateBibs.entries.joinToString { (bib, competitors) ->
                "$bib (${competitors.joinToString { it.fullName() }})"
            }
        }

        val errorResults = raceData.competitorData.mapNotNull { competitorData ->
            competitorData.readoutData?.result
                ?.takeIf { it.resultStatus == ResultStatus.ERROR }
                ?.let { competitorData.competitorCategory.competitor.fullName() }
        }
        if (errorResults.isNotEmpty()) {
            issues += summarizedNames("competitors still have Error result status", errorResults)
        }

        if (raceData.unmatchedReadoutData.isNotEmpty()) {
            issues += "${raceData.unmatchedReadoutData.size} SI readout(s) remain unmatched"
        }

        return PublicResultsPublicationReview(status, issues)
    }

    fun requireReady(raceData: EventRaceData, status: PublicResultsPublicationStatus) {
        review(raceData, status).requireReady()
    }

    fun publicationNotice(status: PublicResultsPublicationStatus): String? =
        PRELIMINARY_RESULT_NOTICE.takeIf { status == PublicResultsPublicationStatus.PRELIMINARY }

    private fun summarizedNames(label: String, names: List<String>): String {
        val shown = names.take(5)
        val remaining = names.size - shown.size
        return "${names.size} $label: ${shown.joinToString()}" +
            if (remaining > 0) " and $remaining more" else ""
    }
}
