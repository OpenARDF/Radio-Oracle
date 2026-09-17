package org.openardf.radiooracle.files

import java.io.ByteArrayOutputStream
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.backend.files.processors.CsvProcessor
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.backend.room.enums.ControlPointType
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.sportident.SITime
import org.openardf.radiooracle.shared.files.CsvCodec
import org.openardf.radiooracle.shared.files.EventCsvImports

class CsvProcessorTest {
    @Test
    fun androidCategoryExportRoundTripsCommaNamesControlListsAndZeroLengths() = runBlocking {
        val category = Category("Open, practice")
        val controls = listOf(ControlPoint(79).apply { type = ControlPointType.BEACON }, ControlPoint(71))
        val out = ByteArrayOutputStream()
        CsvProcessor.exportCategories(out, listOf(CategoryData(category, controls, emptyList())))
        val csv = out.toString("UTF-8")
        assertTrue(csv.startsWith("category,is_man,max_age,length_m,climb_m,"))
        val parsed = EventCsvImports.parseAndroidCategoryRows(csv)
        assertEquals(emptyList<Any>(), parsed.invalidLines)
        assertEquals("Open, practice", parsed.rows.single().name)
        assertEquals("71 79B", parsed.rows.single().controlPointsText)
        assertEquals(0, parsed.rows.single().lengthMeters)
    }

    @Test
    fun androidCompetitorAndStartsExportsPreserveQuotedIdentityFields() = runBlocking {
        val category = Category("Open, practice")
        val competitor = Competitor().apply {
            firstName = "Jo, Jane"
            lastName = "O\"Neil"
            club = "Club; East\nTeam"
            siNumber = 123456
            startNumber = 7
            index = "ID,1"
        }
        val data = listOf(CompetitorData(CompetitorCategory(competitor, category), null))
        val competitorsOut = ByteArrayOutputStream()
        CsvProcessor.exportCompetitors(competitorsOut, data)
        val competitors = EventCsvImports.parseAndroidCompetitorRows(competitorsOut.toString("UTF-8"))
        assertEquals(emptyList<Any>(), competitors.invalidLines)
        assertEquals(competitor.firstName, competitors.rows.single().firstName)
        assertEquals(competitor.lastName, competitors.rows.single().lastName)
        assertEquals(competitor.club, competitors.rows.single().club)
        val startsOut = ByteArrayOutputStream()
        CsvProcessor.exportStarts(startsOut, data, Race())
        val starts = EventCsvImports.parseAndroidCompetitorStartRows(startsOut.toString("UTF-8"))
        assertEquals(emptyList<Any>(), starts.invalidLines)
        assertEquals("ID,1", starts.rows.single().personId)
        assertEquals(123456, starts.rows.single().siNumber)
    }

    @Test
    fun androidReadoutExportSortsPunchesAndPadsRowsUnderNamedColumns() = runBlocking {
        val first = Result().apply { siNumber = 123456 }
        val later = Punch(72, SITime(LocalTime.of(10, 5)), SIRecordType.CONTROL, 2)
        val earlier = Punch(71, SITime(LocalTime.of(10, 0)), SIRecordType.CONTROL, 1)
        earlier.siTime.setDayOfWeek(3)
        earlier.siTime.setWeek(1)
        val data = listOf(ResultData(first, listOf(AliasPunch(later), AliasPunch(earlier)), null),
            ResultData(Result().apply { siNumber = 123457 }, emptyList(), null))
        val out = ByteArrayOutputStream()
        CsvProcessor.exportReadoutData(out, data)
        val records = CsvCodec.records(out.toString("UTF-8"))
        assertEquals(3, records.size)
        assertTrue(records.all { it.error == null && it.fields.size == 9 })
        assertEquals("control_1_code", records[0].fields[5])
        assertEquals("71", records[1].fields[5])
        assertEquals("10:00:00", records[1].fields[6])
        assertEquals("72", records[1].fields[7])
        assertEquals(listOf("0", "", "", "", ""), records[2].fields.drop(4))
    }

    @Test
    fun emptyAndroidExportsStillHaveHeaders() = runBlocking {
        val categories = ByteArrayOutputStream()
        val competitors = ByteArrayOutputStream()
        val starts = ByteArrayOutputStream()
        val readouts = ByteArrayOutputStream()
        CsvProcessor.exportCategories(categories, emptyList())
        CsvProcessor.exportCompetitors(competitors, emptyList())
        CsvProcessor.exportStarts(starts, emptyList(), Race())
        CsvProcessor.exportReadoutData(readouts, emptyList())
        listOf(categories, competitors, starts, readouts).forEach {
            val records = CsvCodec.records(it.toString("UTF-8"))
            assertEquals(1, records.size)
            assertTrue(records.single().fields.size >= 5)
        }
    }
}
