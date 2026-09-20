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
    fun negativeReplyWithPartiallyChangedOwnerWordRemainsBlocked() {
        val before = fixture(owner = "Donald;Duck;")
        val longer = request.copy(expectedFirstName = "Donald", firstName = "Penny",
            lastName = "Popandrolopoulos-J")
        val block0 = decode(before.blocks.single { it.blockNumber == 0 })
        "Penn".forEachIndexed { index, char -> block0[32 + index] = char.code.toByte() }
        (36..39).forEach { block0[it] = 0xEA.toByte() }
        val fresh = SportIdentOwnerReadVerification.captureRaw(before.stationNumber,
            listOf(SportIdentCardBlock(0, block0),
                SportIdentCardBlock(1, decode(before.blocks.single { it.blockNumber == 1 }))))

        val assessment = SportIdentSi8OwnerWordWritePlanner.assessInterruption(longer, before, 2, fresh)

        assertTrue(assessment.matchingWordPrefixes.isEmpty())
        assertTrue(assessment.changesOutsideOwnerWords.isEmpty())
        assertFalse(assessment.consistentWithRecordedAttempt)
        assertFailsWith<IllegalArgumentException> {
            SportIdentSi8OwnerWordWritePlanner.planBaselineRestoreAfterInterruption(longer, before, 2, fresh)
        }
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
    fun proposesRawBaselineRestoreWithoutTrustingPartialOwnerNames() {
        val original = fixture(owner = "A;B;")
        val change = request.copy(expectedFirstName = "A", expectedLastName = "B",
            firstName = "Jack", lastName = "Smiths")
        val firstWord = SportIdentSi8OwnerWordWritePlanner.plan(change, original).first()
        val partialBlock0 = decode(original.blocks.single { it.blockNumber == 0 })
        firstWord.copyOfRange(5, 9).copyInto(partialBlock0, 32)
        val partial = SportIdentOwnerReadVerification.captureRaw(original.stationNumber,
            listOf(SportIdentCardBlock(0, partialBlock0),
                SportIdentCardBlock(1, decode(original.blocks.single { it.blockNumber == 1 }))))
        val partialNames = SportIdentOwnerReadVerification.nativeRead(partial)
        assertFalse(partialNames.firstName == change.firstName && partialNames.lastName == change.lastName)
        assertFalse(partialNames.firstName == change.expectedFirstName &&
            partialNames.lastName == change.expectedLastName)

        val restore = SportIdentSi8OwnerWordWritePlanner.planBaselineRestoreAfterInterruption(
            change, original, 1, partial)
        assertEquals(3, restore.size)
        restore.forEachIndexed { index, frame ->
            val parsed = requireNotNull(SportIdentFrameParser.firstFrame(frame))
            assertEquals(SportIdentProtocol.WRITE_SI_CARD_WORD, parsed.command)
            assertTrue(parsed.extended && parsed.crcValid == true)
            assertEquals(8 + index, parsed.data[0].toInt() and 0xff)
            parsed.data.copyOfRange(1, 5).copyInto(partialBlock0, 32 + index * 4)
        }
        assertContentEquals(decode(original.blocks.single { it.blockNumber == 0 }), partialBlock0)
        assertTrue(SportIdentSi8OwnerWordWritePlanner.planBaselineRestoreAfterInterruption(
            change, original, 0, original).isEmpty())
    }

    @Test
    fun refusesRawRestoreWithoutACompatibleFreshFullCardImage() {
        val before = fixture()
        val target = fixture(owner = "Donald;Duck;")
        assertFailsWith<IllegalArgumentException> {
            SportIdentSi8OwnerWordWritePlanner.planBaselineRestoreAfterInterruption(request, before, 1, target)
        }
        val changedBlock1 = decode(target.blocks.single { it.blockNumber == 1 }).also { it[8] = 99 }
        val changed = SportIdentOwnerReadVerification.captureRaw(target.stationNumber,
            listOf(SportIdentCardBlock(0, decode(target.blocks.single { it.blockNumber == 0 })),
                SportIdentCardBlock(1, changedBlock1)))
        assertFailsWith<IllegalArgumentException> {
            SportIdentSi8OwnerWordWritePlanner.planBaselineRestoreAfterInterruption(request, before, 3, changed)
        }
    }

    @Test
    fun refusesStaleIdentityAndNormalizedOwnerBytes() {
        val before = fixture()
        listOf(
            request.copy(stationNumber = 593928),
            request.copy(cardNumber = 2450663),
            request.copy(expectedFirstName = "Mickey"),
            request.copy(acceptPossiblePunchLoss = false),
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
    }

    @Test
    fun plansShortAndMaximumNamesWithoutTouchingOtherCardBytes() {
        val before = fixture(owner = "Donald;Duck;se;\u00ee")
        val changes = listOf(
            "Huey" to "Duck",
            "" to "",
            "ABCDEFGHIJKLMNOPQRSTUVW" to ""
        )
        changes.forEach { (first, last) ->
            val target = request.copy(expectedFirstName = "Donald", firstName = first, lastName = last)
            val frames = SportIdentSi8OwnerWordWritePlanner.plan(target, before)
            val textLength = first.length + last.length + 2
            assertEquals((textLength + 3) / 4, frames.size)
            assertTrue(frames.size in 1..7)
            val afterBlock0 = decode(before.blocks.single { it.blockNumber == 0 })
            frames.forEachIndexed { index, bytes ->
                val frame = requireNotNull(SportIdentFrameParser.firstFrame(bytes))
                assertEquals(0x08 + index, frame.data[0].toInt() and 0xff)
                frame.data.copyOfRange(1, 5).copyInto(afterBlock0, 32 + index * 4)
            }
            val after = SportIdentOwnerReadVerification.capture(before.stationNumber,
                listOf(SportIdentCardBlock(0, afterBlock0),
                    SportIdentCardBlock(1, decode(before.blocks.single { it.blockNumber == 1 }))))
            val read = SportIdentOwnerReadVerification.nativeRead(after)
            assertEquals(first, read.firstName)
            assertEquals(last, read.lastName)
            assertTrue(SportIdentSi8OwnerWordWritePlanner.compareToObserved(target, before, after).matches)
        }
    }

    @Test
    fun reproducesAllSevenFramesCapturedFromConfigPlusMaximumNameWrite() {
        val before = fixture(owner = "Donald;Duck;rolopoulos-J;")
        val longer = request.copy(expectedFirstName = "Donald", firstName = "Penny",
            lastName = "Popandrolopoulos-J")
        val frames = SportIdentSi8OwnerWordWritePlanner.plan(longer, before)

        assertEquals(listOf(
            "ff 02 ea 05 08 50 65 6e 6e a4 5e 03",
            "ff 02 ea 05 09 79 3b 50 6f f7 eb 03",
            "ff 02 ea 05 0a 70 61 6e 64 1c dd 03",
            "ff 02 ea 05 0b 72 6f 6c 6f 27 f9 03",
            "ff 02 ea 05 0c 70 6f 75 6c c8 84 03",
            "ff 02 ea 05 0d 6f 73 2d 4a b2 d8 03",
            "ff 02 ea 05 0e 3b ee ee ee 62 48 03"
        ), frames.map { frame -> frame.joinToString(" ") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        } })

        val block0 = decode(before.blocks.single { it.blockNumber == 0 })
        frames.forEach { frame -> frame.copyOfRange(5, 9).copyInto(block0, (frame[4].toInt() and 0xff) * 4) }
        val observed = SportIdentOwnerReadVerification.capture(before.stationNumber,
            listOf(SportIdentCardBlock(0, block0),
                SportIdentCardBlock(1, decode(before.blocks.single { it.blockNumber == 1 }))))
        assertEquals("Penny", SportIdentOwnerReadVerification.nativeRead(observed).firstName)
        assertEquals("Popandrolopoulos-J", SportIdentOwnerReadVerification.nativeRead(observed).lastName)
        assertTrue(SportIdentSi8OwnerWordWritePlanner.compareToObserved(longer, before, observed).matches)
        assertFalse(SportIdentSi8OwnerWordWritePlanner.hasPreviouslyVerifiedDirectShape(longer, before))
    }

    @Test
    fun liveDirectGateRejectsTheFailedSevenWordShapeWhileKeepingOfflinePlans() {
        val before = fixture(owner = "Donald;Duck;")
        val eleven = request.copy(expectedFirstName = "Donald", firstName = "Daisy")
        val sevenWords = request.copy(expectedFirstName = "Donald", firstName = "Penny",
            lastName = "Popandrolopoulos-J")
        assertTrue(SportIdentSi8OwnerWordWritePlanner.hasPreviouslyVerifiedDirectShape(request, fixture()))
        assertTrue(SportIdentSi8OwnerWordWritePlanner.hasPreviouslyVerifiedDirectShape(eleven, before))
        assertFalse(SportIdentSi8OwnerWordWritePlanner.hasPreviouslyVerifiedDirectShape(
            request.copy(expectedFirstName = "Donald", firstName = "Huey"), before))
        assertFalse(SportIdentSi8OwnerWordWritePlanner.hasPreviouslyVerifiedDirectShape(
            request.copy(expectedFirstName = "Donald", firstName = "Dónald"), before))
        assertEquals(7, SportIdentSi8OwnerWordWritePlanner.plan(sevenWords, before).size)
        assertFalse(SportIdentSi8OwnerWordWritePlanner.hasPreviouslyVerifiedDirectShape(sevenWords, before))
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
