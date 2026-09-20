package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportIdentSi8OwnerWordWritePlannerTest {
    private val request = SportIdentOwnerNameWriteRequest(
        1, 593927, 2450662, "Daisy", "Duck", "Donald", "Duck", true
    )

    @Test
    fun reproducesAllThreeCapturedFramesAndLeavesPunchBlockUntouched() {
        val before = fixture()
        val frames = SportIdentSi8OwnerWordWritePlanner.plan(request, before)
        assertEquals(
            listOf(
                "ff 02 ea 05 08 44 6f 6e 61 96 4e 03",
                "ff 02 ea 05 09 6c 64 3b 44 9e 90 03",
                "ff 02 ea 05 0a 75 63 6b 3b cf 84 03"
            ),
            frames.map { frame -> frame.joinToString(" ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') } }
        )

        val block0 = decode(before.blocks.single { it.blockNumber == 0 })
        val block1 = decode(before.blocks.single { it.blockNumber == 1 })
        frames.forEach { frame ->
            val offset = (frame[4].toInt() and 0xff) * 4
            frame.copyOfRange(5, 9).copyInto(block0, offset)
        }
        val after = SportIdentOwnerReadVerification.capture(
            before.stationNumber, listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, block1))
        )
        val readBack = SportIdentOwnerReadVerification.nativeRead(after)
        assertEquals("Donald", readBack.firstName)
        assertEquals("Duck", readBack.lastName)
        assertEquals(1, readBack.controlPunchCount)
        assertContentEquals(block1, decode(before.blocks.single { it.blockNumber == 1 }))
    }

    @Test
    fun reproducesObservedShorterNameBytesWithoutClearingResidualText() {
        // The paired Mac SDK reads showed a 0xEE twelfth byte while older
        // residual text beyond that word remained unchanged.
        val before = fixture(owner = "Donald;Duck;se;\u00ee")
        val shorter = request.copy(expectedFirstName = "Donald", firstName = "Daisy")
        val frames = SportIdentSi8OwnerWordWritePlanner.plan(shorter, before)
        assertEquals(3, frames.size)
        assertContentEquals(byteArrayOf('c'.code.toByte(), 'k'.code.toByte(), ';'.code.toByte(), 0xEE.toByte()),
            frames[2].copyOfRange(5, 9))

        val block0 = decode(before.blocks.single { it.blockNumber == 0 })
        frames.forEach { frame -> frame.copyOfRange(5, 9).copyInto(block0, (frame[4].toInt() and 0xff) * 4) }
        val replay = SportIdentOwnerReadVerification.capture(before.stationNumber,
            listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, decode(before.blocks.single { it.blockNumber == 1 }))))
        val observedShape = fixture(owner = "Daisy;Duck;\u00eese;\u00ee")
        assertEquals(observedShape.blocks, replay.blocks)
        assertEquals("Daisy", SportIdentOwnerReadVerification.nativeRead(replay).firstName)
    }

    @Test
    fun comparesPlannedWholeCardImageWithIndependentAfterRead() {
        val before = fixture(owner = "Donald;Duck;se;\u00ee")
        val after = fixture(owner = "Daisy;Duck;\u00eese;\u00ee")
        val shorter = request.copy(expectedFirstName = "Donald", firstName = "Daisy")
        val match = SportIdentSi8OwnerWordWritePlanner.compareToObserved(shorter, before, after)
        assertTrue(match.matches)
        assertTrue(match.predictedVersusObserved.byteChanges.isEmpty())
        assertEquals(3, match.plannedFramesHex.size)

        val changedBlock1 = decode(after.blocks.single { it.blockNumber == 1 }).also { it[8] = 99 }
        val changed = SportIdentOwnerReadVerification.capture(after.stationNumber,
            listOf(SportIdentCardBlock(0, decode(after.blocks.single { it.blockNumber == 0 })),
                SportIdentCardBlock(1, changedBlock1)))
        val mismatch = SportIdentSi8OwnerWordWritePlanner.compareToObserved(shorter, before, changed)
        assertFalse(mismatch.matches)
        assertEquals(listOf(SportIdentOwnerReadByteChange(1, 8, 0, 99)), mismatch.predictedVersusObserved.byteChanges)
        assertFalse(SportIdentSi8OwnerWordWritePlanner.compareToObserved(shorter, before,
            after.copy(stationNumber = 593928)).matches)
        assertFailsWith<IllegalArgumentException> {
            SportIdentSi8OwnerWordWritePlanner.compareToObserved(shorter, before, after.copy(blocks = after.blocks.take(1)))
        }
    }

    @Test
    fun classifiesEveryCrashBoundaryFromRawFullCardBytes() {
        val before = fixture()
        val frames = SportIdentSi8OwnerWordWritePlanner.plan(request, before)
        for (count in 0..3) {
            val block0 = decode(before.blocks.single { it.blockNumber == 0 })
            frames.take(count).forEach { frame ->
                frame.copyOfRange(5, 9).copyInto(block0, (frame[4].toInt() and 0xff) * 4)
            }
            val fresh = SportIdentOwnerReadFixture(1, before.stationNumber,
                listOf(SportIdentOwnerReadBlock(0, block0.joinToString("") { "%02x".format(it.toInt() and 0xff) }),
                    before.blocks.single { it.blockNumber == 1 }))
            val assessment = SportIdentSi8OwnerWordWritePlanner.assessInterruption(request, before, count, fresh)
            assertTrue(count in assessment.matchingWordPrefixes)
            assertTrue(assessment.changesOutsideOwnerWords.isEmpty())
        }
        val wrongCard = before.copy(blocks = before.blocks.map { block ->
            if (block.blockNumber == 0) block.copy(hexData = block.hexData.replaceRange(50, 52, "26")) else block
        })
        assertFailsWith<IllegalArgumentException> {
            SportIdentSi8OwnerWordWritePlanner.assessInterruption(request, before, 1, wrongCard)
        }
    }

    @Test
    fun flagsImpossibleProgressAndChangesOutsideOwnerWords() {
        val before = fixture()
        val fullTarget = fixture(owner = "Donald;Duck;")
        val impossible = SportIdentSi8OwnerWordWritePlanner.assessInterruption(
            request, before, 1, fullTarget)
        assertEquals(listOf(3), impossible.matchingWordPrefixes)
        assertTrue(impossible.plausibleWordPrefixes.isEmpty())
        assertFalse(impossible.consistentWithRecordedAttempt)

        val block1 = decode(fullTarget.blocks.single { it.blockNumber == 1 }).also { it[8] = 99 }
        val anomalous = SportIdentOwnerReadVerification.captureRaw(fullTarget.stationNumber,
            listOf(SportIdentCardBlock(0, decode(fullTarget.blocks.single { it.blockNumber == 0 })),
                SportIdentCardBlock(1, block1)))
        val assessment = SportIdentSi8OwnerWordWritePlanner.assessInterruption(
            request, before, 3, anomalous)
        assertTrue(assessment.matchingWordPrefixes.isEmpty())
        assertEquals(listOf(SportIdentOwnerReadByteChange(1, 8, 0, 99)), assessment.changesOutsideOwnerWords)
        assertFalse(assessment.consistentWithRecordedAttempt)
    }

    @Test
    fun identicalLaterWordsLeaveSeveralValidPrefixMatches() {
        val before = fixture(owner = "Donald;Duck;")
        val firstWordOnly = request.copy(expectedFirstName = "Donald", firstName = "Ronald")
        val observed = fixture(owner = "Ronald;Duck;")
        val assessment = SportIdentSi8OwnerWordWritePlanner.assessInterruption(
            firstWordOnly, before, 1, observed)
        assertEquals(listOf(1, 2, 3), assessment.matchingWordPrefixes)
        assertEquals(listOf(1), assessment.plausibleWordPrefixes)
        assertTrue(assessment.consistentWithRecordedAttempt)
    }

    @Test
    fun refusesUnsupportedShapeStaleIdentityAndNormalizedOwnerBytes() {
        val before = fixture()
        listOf(
            request.copy(stationNumber = 593928),
            request.copy(cardNumber = 2450663),
            request.copy(expectedFirstName = "Mickey"),
            request.copy(acceptPossiblePunchLoss = false),
            request.copy(firstName = "Huey"), // Ten bytes; final-word padding is not characterized.
            request.copy(firstName = "Dewey"), // Eleven-to-eleven-byte replacement is not characterized.
            request.copy(firstName = "Minnie", lastName = "Mouse"), // Thirteen bytes need four words.
            request.copy(schemaVersion = 2)
        ).forEach { bad ->
            assertFailsWith<IllegalArgumentException> { SportIdentSi8OwnerWordWritePlanner.plan(bad, before) }
        }
        assertFailsWith<IllegalArgumentException> {
            SportIdentSi8OwnerWordWritePlanner.plan(request, before.copy(blocks = before.blocks.take(1)))
        }
        assertFailsWith<IllegalArgumentException> {
            SportIdentSi8OwnerWordWritePlanner.plan(request, fixture(owner = "Da\u0001isy;Duck;"))
        }
        val longOwner = fixture(owner = "Christopher;Robin;")
        val matchingLongOwner = request.copy(expectedFirstName = "Christopher", expectedLastName = "Robin")
        assertFailsWith<IllegalArgumentException> {
            SportIdentSi8OwnerWordWritePlanner.plan(matchingLongOwner, longOwner)
        }
    }

    private fun fixture(owner: String = "Daisy;Duck;"): SportIdentOwnerReadFixture {
        val block0 = ByteArray(128)
        block0[22] = 1
        block0[24] = 2
        block0[25] = 0x25
        block0[26] = 0x64
        block0[27] = 0xe6.toByte()
        owner.forEachIndexed { index, char -> block0[32 + index] = char.code.toByte() }
        val block1 = ByteArray(128)
        return SportIdentOwnerReadVerification.capture(
            593927, listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, block1))
        )
    }

    private fun decode(block: SportIdentOwnerReadBlock): ByteArray = ByteArray(128) { index ->
        block.hexData.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
