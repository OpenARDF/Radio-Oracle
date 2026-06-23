package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceLevel

class DesktopPracticeRaceOpsSiReadoutTest {
    @Test
    fun practiceRaceOpsWithConnectedStationProvidesAutoStartContext() {
        assertEquals(
            "race:SI station 554896 connected in SI MASTER mode",
            practiceRaceOpsSiReadoutContextKey(
                raceId = "race",
                raceLevel = RaceLevel.PRACTICE,
                workflow = DesktopWorkflow.RaceOps,
                isSiReaderConnected = true,
                siReaderStatusText = "SI station 554896 connected in SI MASTER mode"
            )
        )
    }

    @Test
    fun practiceAutoStartContextRequiresPracticeRaceOpsAndConnectedStation() {
        assertNull(
            practiceRaceOpsSiReadoutContextKey(
                raceId = null,
                raceLevel = RaceLevel.PRACTICE,
                workflow = DesktopWorkflow.RaceOps,
                isSiReaderConnected = true,
                siReaderStatusText = "SI station 554896 connected in SI MASTER mode"
            )
        )
        assertNull(
            practiceRaceOpsSiReadoutContextKey(
                raceId = "race",
                raceLevel = RaceLevel.PRACTICE,
                workflow = DesktopWorkflow.Setup,
                isSiReaderConnected = true,
                siReaderStatusText = "SI station 554896 connected in SI MASTER mode"
            )
        )
        assertNull(
            practiceRaceOpsSiReadoutContextKey(
                raceId = "race",
                raceLevel = RaceLevel.REGIONAL,
                workflow = DesktopWorkflow.RaceOps,
                isSiReaderConnected = true,
                siReaderStatusText = "SI station 554896 connected in SI MASTER mode"
            )
        )
        assertNull(
            practiceRaceOpsSiReadoutContextKey(
                raceId = "race",
                raceLevel = RaceLevel.PRACTICE,
                workflow = DesktopWorkflow.RaceOps,
                isSiReaderConnected = false,
                siReaderStatusText = "SI station disconnected"
            )
        )
    }
}
