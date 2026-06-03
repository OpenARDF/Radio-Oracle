package org.openardf.radiooracle.shared.event

/** Shared read-only summary of a portable Event File. */
data class EventProjectSummary(
    val raceName: String,
    val categoryCount: Int,
    val competitorCount: Int,
    val readoutCount: Int,
    val resultCount: Int
) {
    companion object {
        /** Builds a summary from an Event File without changing Event File data. */
        fun from(projectFile: EventProjectFile): EventProjectSummary {
            val raceData = projectFile.raceData
            val competitorReadoutCount = raceData.competitorData.count { it.readoutData != null }
            return EventProjectSummary(
                raceName = raceData.race.name,
                categoryCount = raceData.categories.size,
                competitorCount = raceData.competitorData.size,
                readoutCount = competitorReadoutCount + raceData.unmatchedReadoutData.size,
                resultCount = competitorReadoutCount
            )
        }
    }
}
