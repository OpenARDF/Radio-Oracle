package org.openardf.radiooracle.files

import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.EventSeriesImport
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.shared.event.EVENT_SERIES_PACKAGE_CONTENT_TYPE
import org.openardf.radiooracle.shared.event.EventFileTransferPayloads
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.EventSeriesFileJson
import org.openardf.radiooracle.shared.event.EventSeriesLink
import org.openardf.radiooracle.shared.event.EventSeriesPackageContents
import org.openardf.radiooracle.shared.event.EventSeriesPackageEventFile
import org.openardf.radiooracle.shared.event.isEventSeriesFileName
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class EventSeriesTransferRoundTripTests {
    @Before
    fun initializeBackend() {
        val context = RuntimeEnvironment.getApplication()
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
    }

    @Test
    fun androidReexportsFullSeriesPackageAfterImportingDesktopPackage() = runBlocking {
        val desktopPackage = desktopSeriesPackageBytes(
            seriesId = "series-2026",
            seriesName = "Championship",
            eventIdsAndNames = listOf("day-1" to "Day 1", "day-2" to "Day 2")
        )
        val androidImport = EventSeriesImport.prepareZipPackage(ByteArrayInputStream(desktopPackage))
        DataProcessor.get().saveEventSeriesImport(androidImport)
        val selectedMember = androidImport.members.last()

        val upload = DataProcessor.get().desktopUploadForRaceOrSeries(selectedMember.localRaceId)
        val uploadBytes = upload.bytes

        assertTrue(
            "Selected member must be one of the local Android race ids.",
            androidImport.races.any { it.race.id == selectedMember.localRaceId }
        )
        assertEquals("Championship.zip", upload.fileName)
        assertEquals(EVENT_SERIES_PACKAGE_CONTENT_TYPE, upload.contentType)
        assertTrue(EventFileTransferPayloads.isSeriesPackage(upload.fileName, upload.contentType))
        assertTrue(
            "Android upload must not be a single Event File.",
            uploadBytes.first() != '{'.code.toByte()
        )
        val uploadEntries = unzipTextEntries(uploadBytes)
        val returnedManifest = uploadEntries.entries.single { (path, _) ->
            isEventSeriesFileName(path.substringAfterLast('/'))
        }.value
        val returnedSeries = EventSeriesFileJson.decode(returnedManifest)
        val returnedEventFiles = returnedSeries.sortedEvents().map { event ->
            EventProjectFileJson.decode(requireNotNull(uploadEntries[event.eventFilePath]))
        }

        assertEquals("series-2026", returnedSeries.seriesId)
        assertEquals(listOf("day-1", "day-2"), returnedSeries.sortedEvents().map { it.seriesEventId })
        assertEquals(listOf("Day 1", "Day 2"), returnedEventFiles.map { it.raceData.race.name })
        assertEquals(
            listOf(EventSeriesLink("series-2026", "day-1"), EventSeriesLink("series-2026", "day-2")),
            returnedEventFiles.map { it.seriesLink }
        )
    }

    @Test
    fun androidReexportsKnownSingleMemberSeriesAsSeriesPackage() = runBlocking {
        val desktopPackage = desktopSeriesPackageBytes(
            seriesId = "solo-series",
            seriesName = "Solo Series",
            eventIdsAndNames = listOf("solo-day" to "Solo Day")
        )
        val androidImport = EventSeriesImport.prepareZipPackage(ByteArrayInputStream(desktopPackage))
        DataProcessor.get().saveEventSeriesImport(androidImport)
        val selectedMember = androidImport.members.single()

        val upload = DataProcessor.get().desktopUploadForRaceOrSeries(selectedMember.localRaceId)
        val uploadEntries = unzipTextEntries(upload.bytes)
        val returnedManifest = uploadEntries.entries.single { (path, _) ->
            isEventSeriesFileName(path.substringAfterLast('/'))
        }.value
        val returnedSeries = EventSeriesFileJson.decode(returnedManifest)
        val returnedEventFile = EventProjectFileJson.decode(requireNotNull(uploadEntries["events/solo-day.rom.json"]))

        assertEquals("Solo Series.zip", upload.fileName)
        assertEquals(EVENT_SERIES_PACKAGE_CONTENT_TYPE, upload.contentType)
        assertTrue(EventFileTransferPayloads.isSeriesPackage(upload.fileName, upload.contentType))
        assertEquals("solo-series", returnedSeries.seriesId)
        assertEquals(listOf("solo-day"), returnedSeries.sortedEvents().map { it.seriesEventId })
        assertEquals(EventSeriesLink("solo-series", "solo-day"), returnedEventFile.seriesLink)
    }

    private fun desktopSeriesPackageBytes(
        seriesId: String,
        seriesName: String,
        eventIdsAndNames: List<Pair<String, String>>
    ): ByteArray {
        val seriesFile = EventSeriesFile(
            seriesId = seriesId,
            name = seriesName,
            events = eventIdsAndNames.mapIndexed { index, (eventId, displayName) ->
                EventSeriesEvent(eventId, "events/$eventId.rom.json", index, displayName)
            }
        )
        val content = EventSeriesPackageContents.build(
            seriesFile = seriesFile,
            eventFiles = seriesFile.events.map { event ->
                EventSeriesPackageEventFile(
                    event = event,
                    projectFile = projectFile(
                        seriesId = seriesId,
                        raceId = "race-${event.seriesEventId}",
                        raceName = event.displayName,
                        seriesEventId = event.seriesEventId
                    )
                )
            },
            manifestEntryPath = "$seriesName.series.radio-oracle.json"
        )
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                content.entries.forEach { entry ->
                    zip.putNextEntry(ZipEntry(entry.path))
                    zip.write(entry.text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }

    private fun projectFile(
        seriesId: String,
        raceId: String,
        raceName: String,
        seriesEventId: String
    ): EventProjectFile =
        EventProjectFile(
            seriesLink = EventSeriesLink(seriesId, seriesEventId),
            raceData = EventRaceData(
                race = EventRace(
                    id = raceId,
                    name = raceName,
                    apiKey = "",
                    startDateTimeIso = "2026-06-20T09:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = emptyList(),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList()
            )
        )

    private fun unzipTextEntries(bytes: ByteArray): Map<String, String> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            buildMap {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                    }
                    zip.closeEntry()
                }
            }
        }
}
