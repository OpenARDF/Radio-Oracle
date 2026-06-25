package org.openardf.radiooracle.ui.series

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import java.util.UUID

class EventSeriesListItemsTest {
    @Test
    fun formatsOrderedMemberLinesAndSortsSeriesByName() {
        val beta = seriesData(
            seriesId = "beta",
            name = "Beta Series",
            members = listOf(member("day-2", 1, "Day 2"), member("day-1", 0, "Day 1"))
        )
        val alpha = seriesData(
            seriesId = "alpha",
            name = "Alpha Series",
            members = listOf(member("solo", 0, "Solo", startDateTimeIso = ""))
        )

        val items = EventSeriesListItems.sort(listOf(beta, alpha))

        assertEquals(listOf("Alpha Series", "Beta Series"), items.map { it.name })
        assertEquals(1, items[0].memberCount)
        assertEquals(listOf("1. Solo"), items[0].memberLines)
        assertEquals(
            listOf("1. 2026-06-20 - Day 1", "2. 2026-06-21 - Day 2"),
            items[1].memberLines
        )
    }

    private fun seriesData(
        seriesId: String,
        name: String,
        members: List<EventSeriesMember>
    ): EventSeriesData =
        EventSeriesData(
            series = EventSeries(seriesId = seriesId, name = name),
            members = members
        )

    private fun member(
        seriesEventId: String,
        order: Int,
        displayName: String,
        startDateTimeIso: String = "2026-06-${20 + order}T09:00"
    ): EventSeriesMember =
        EventSeriesMember(
            seriesId = "series",
            seriesEventId = seriesEventId,
            localRaceId = UUID.randomUUID(),
            eventFilePath = "$seriesEventId.json",
            eventOrder = order,
            displayName = displayName,
            startDateTimeIso = startDateTimeIso,
            formatLabel = "Classic"
        )
}
