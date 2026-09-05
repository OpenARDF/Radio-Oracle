package org.openardf.radiooracle.desktop

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.domain.*
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException

class DesktopClassicRouteAnalysisTest {
    private val flat = DesktopFrozenElevationSurface(listOf(RouteElevationSource("synthetic-flat-v1", "Synthetic flat terrain", 1.0))) { 100.0 }

    @Test fun backgroundProcessingAndExportWatermarkIgnoreLaterDownloads() = runBlocking {
        withContext(coroutineContext) {
            val dir = Files.createTempDirectory("route-worker-test-")
            val session = DesktopProjectSession(DesktopProjectFiles)
            val captured = fixture()
            session.newProject(captured)
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val state = ClassicRouteAnalysisUi(scope, { flat }, DesktopClassicRouteRecovery(dir))
            state.session = session
            var exported: EventProjectFile? = null
            try {
                state.start(captured, null, "", watch = true, series = false)
                assertTrue(state.shouldWaitForExport(captured))
                assertFalse(state.shouldWaitForExport(captured.copy(raceData = captured.raceData.copy(race = captured.raceData.race.copy(raceType = RaceType.SPRINT)))))
                state.requestExport(captured) { exported = state.exportSnapshot }
                state.waitForExport()
                val extra = captured.raceData.competitorData.single().let { data ->
                    data.copy(readoutData = data.readoutData!!.copy(result = data.readoutData!!.result.copy(id = "later")))
                }
                val latest = session.updateCurrentProject { it.copy(raceData = it.raceData.copy(competitorData = it.raceData.competitorData + extra)) }
                repeat(20) { state.observe(latest, null) }
                withTimeout(10000) { state.awaitIdle() }
                assertEquals(1, exported!!.raceData.competitorData.size)
                assertEquals(1, DesktopClassicRouteAnalysis.projection(exported!!).size)
                assertEquals(2, DesktopClassicRouteAnalysis.projection(session.currentProject!!).size)
                assertTrue(session.hasUnsavedChanges)
                assertTrue(state.continuous)
                assertNull(state.notification)
            } finally { state.cancel(); scope.cancel() }
        }
    }

    @Test fun switchingAwayCancelsWorkAndRefreshesWaitingExport() = runBlocking {
        withContext(coroutineContext) {
            val session = DesktopProjectSession(DesktopProjectFiles)
            val captured = session.newProject(fixture())
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val state = ClassicRouteAnalysisUi(scope, { flat }, DesktopClassicRouteRecovery(Files.createTempDirectory("route-stop-test-")))
            state.session = session
            var exported = false
            try {
                state.start(captured, null, "", watch = true, series = false)
                state.requestExport(captured) { exported = true }
                state.waitForExport()
                session.closeProject(discardUnsavedChanges = true)
                state.observe(null, null)
                withTimeout(10000) { state.awaitIdle() }
                assertFalse(exported)
                assertFalse(state.continuous)
                assertNull(session.currentProject)
            } finally { state.cancel(); scope.cancel() }
        }
    }

    @Test fun continuousProcessingResumesAfterIdleAndDoesNotAutosaveReadoutCorrections() = runBlocking {
        val directory = Files.createTempDirectory("route-continuous-correction-")
        val path = directory.resolve("race.rom.json")
        DesktopProjectFiles.write(path, fixture())
        val session = DesktopProjectSession(DesktopProjectFiles)
        session.open(path)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val state = ClassicRouteAnalysisUi(scope, { flat }, DesktopClassicRouteRecovery(directory.resolve("recovery")))
        state.session = session
        try {
            state.start(session.currentProject!!, path, "", watch = true, series = false)
            withTimeout(10000) { state.awaitIdle() }
            val original = session.currentProject!!
            assertFalse(session.hasUnsavedChanges)
            assertTrue(state.continuous)
            val corrected = session.updateCurrentProject {
                fixture(listOf(32, 31, 99)).copy(desktopRouteAnalysis = original.desktopRouteAnalysis)
            }
            assertTrue(DesktopClassicRouteAnalysis.projection(corrected).isEmpty())
            var exported = false
            state.requestExport(original) { exported = true }
            state.waitForExport()
            state.observe(corrected, path)
            withTimeout(10000) { state.awaitIdle() }
            assertFalse(exported)
            assertEquals("Alternative order", DesktopClassicRouteAnalysis.projection(session.currentProject!!).getValue("result").comparison)
            assertTrue(session.hasUnsavedChanges)
            assertEquals("Ideal order", DesktopClassicRouteAnalysis.projection(DesktopProjectFiles.read(path)).getValue("result").comparison)
            session.save()
            assertEquals(DesktopClassicRouteAnalysis.projection(session.currentProject!!), DesktopClassicRouteAnalysis.projection(DesktopProjectFiles.read(path)))
            val deleted = session.updateCurrentProject { it.copy(raceData = it.raceData.copy(competitorData = emptyList())) }
            state.observe(deleted, path)
            withTimeout(10000) { state.awaitIdle() }
            assertTrue(session.currentProject!!.desktopRouteAnalysis!!.results.isEmpty())
        } finally { state.cancel(); scope.cancel() }
    }

    @Test fun recoveryRestoresOnlyMatchingPersistedInputs() = runBlocking {
        val captured = fixture()
        val completed = analyzed(captured)
        val recovery = DesktopClassicRouteRecovery(Files.createTempDirectory("route-recovery-test-"))
        recovery.write(captured, null, completed.desktopRouteAnalysis!!)
        assertEquals(DesktopClassicRouteAnalysis.projection(completed), DesktopClassicRouteAnalysis.projection(recovery.recover(captured, null)))
        assertTrue(DesktopClassicRouteAnalysis.projection(recovery.recover(fixture(listOf(32, 31, 99)), null)).isEmpty())
        assertEquals(completed, recovery.recover(completed, null))
    }

    @Test fun nativeSeriesCodecPreservesAnalysisAndUnrelatedMembers() = runBlocking {
        val completed = analyzed(fixture())
        val other = fixture().let { it.copy(raceData = it.raceData.copy(race = it.raceData.race.copy(id = "foxoring", raceType = RaceType.FOXORING))) }
        val archive = EventSeriesArchive(EventSeriesFile(seriesId = "series", name = "Synthetic series", events = listOf(
            EventSeriesEvent("classic", "classic.rom.json", 0, "Classic"), EventSeriesEvent("foxoring", "foxoring.rom.json", 1, "Foxoring"))),
            mapOf("classic" to completed, "foxoring" to other))
        val decoded = EventSeriesArchiveZipCodec.decode(EventSeriesArchiveZipCodec.encode(archive))
        assertEquals(completed.desktopRouteAnalysis, decoded.member("classic").desktopRouteAnalysis)
        assertNull(decoded.member("foxoring").desktopRouteAnalysis)
        assertEquals(other.raceData, decoded.member("foxoring").raceData)
    }

    @Test fun recoverySurvivesSeriesWorkspaceRecreationWithoutSavingUnrelatedEdits() = runBlocking {
        val directory = Files.createTempDirectory("route-series-recovery-")
        val archivePath = directory.resolve("series.roseries")
        val captured = fixture()
        val archive = EventSeriesArchive(EventSeriesFile(seriesId = "series", name = "Synthetic", events = listOf(
            EventSeriesEvent("classic", "classic.rom.json", 0, "Classic"))), mapOf("classic" to captured))
        val recovery = DesktopClassicRouteRecovery(directory.resolve("checkpoints"))
        var workspace = DesktopEventSeriesArchiveWorkspaces.create(archivePath, archive)
        try {
            val firstPath = workspace.memberPaths.single()
            val native = DesktopProjectFiles.read(firstPath)
            val unsaved = native.copy(raceData = native.raceData.copy(race = native.raceData.race.copy(name = "Unsaved rename")))
            recovery.write(unsaved, firstPath, analyzed(unsaved).desktopRouteAnalysis!!)
            DesktopEventSeriesArchiveWorkspaces.close(workspace)
            workspace = DesktopEventSeriesArchiveWorkspaces.open(archivePath)
            val reopenedPath = workspace.memberPaths.single()
            assertNotEquals(firstPath, reopenedPath)
            val reopened = DesktopProjectFiles.read(reopenedPath)
            assertEquals(native.raceData, reopened.raceData)
            assertNull(reopened.desktopRouteAnalysis)
            val recovered = recovery.recover(reopened, reopenedPath)
            assertEquals(1, DesktopClassicRouteAnalysis.projection(recovered).size)
            assertEquals(native.raceData, recovered.raceData)
        } finally { DesktopEventSeriesArchiveWorkspaces.close(workspace) }
    }

    @Test fun rendersDesktopReportAndPublicSiteFixturesForVisualAndPrivacyChecks() = runBlocking {
        val completed = analyzed(fixture())
        val dir = Path.of("build", "reports", "classic-route-analysis")
        Files.createDirectories(dir)
        val original = completed.raceData.competitorData.single()
        val snapshot = completed.desktopRouteAnalysis!!.results.getValue("result")
        val copies = (1..18).map { n -> original.copy(
            competitorCategory = original.competitorCategory.copy(competitor = original.competitorCategory.competitor.copy(id = "competitor$n", lastName = "Runner $n", firstName = "Long given name")),
            readoutData = original.readoutData!!.copy(result = original.readoutData!!.result.copy(id = "result$n", competitorId = "competitor$n"))
        ) }
        val project = completed.copy(raceData = completed.raceData.copy(competitorData = copies), desktopRouteAnalysis = completed.desktopRouteAnalysis!!.copy(results = (1..17).associate { "result$it" to snapshot }))
        DesktopSplitResultReportPdf.exportPdf(dir.resolve("split-results.pdf"), project)
        DesktopResultReportPdf.exportPdf(dir.resolve("results-report.pdf"), project)
        DesktopProjectFiles.exportResultsHtml(dir.resolve("results.html"), project)
        DesktopPublicResultSiteExports.export(dir.resolve("public-site"), project)
        val jsonFile = Files.walk(dir.resolve("public-site")).use { paths -> paths.filter { it.fileName.toString() == "public-results.json" }.findFirst().orElseThrow() }
        val publicJson = Files.readString(jsonFile)
        assertTrue(publicJson.contains("estimatedEffectiveRouteLengthMeters"))
        assertFalse(publicJson.contains("courseFingerprint"))
        assertFalse(publicJson.contains("elevationSources"))
    }

    @Test fun idealOrderIsExactlyTheReferenceAndAlternativeIsLonger() = runBlocking {
        val ideal = analyzed(fixture())
        val first = DesktopClassicRouteAnalysis.projection(ideal).getValue("result")
        assertEquals("Ideal order", first.comparison)
        assertEquals(first.idealEffectiveMeters, first.effectiveMeters)
        assertEquals(0, first.climbMeters)
        val alternative = DesktopClassicRouteAnalysis.projection(analyzed(fixture(listOf(32, 31, 99)))).getValue("result")
        assertEquals("Alternative order", alternative.comparison)
        assertEquals(first.idealEffectiveMeters, alternative.idealEffectiveMeters)
        assertTrue(alternative.effectiveMeters > first.effectiveMeters)
        assertEquals(2, ideal.desktopRouteAnalysis!!.contexts.values.single().permutations)
    }

    @Test fun wholeRouteElevationMetricsMatchTheSharedAnalyzerCalculator() = runBlocking {
        val project = fixture()
        val surface = DesktopFrozenElevationSurface(listOf(RouteElevationSource("synthetic-hill-v1", "Hill", 1.0))) {
            100.0 + (it.latitude - 40.0) * 10000.0
        }
        val analyzed = analyzed(project, surface)
        val value = DesktopClassicRouteAnalysis.projection(analyzed).getValue("result")
        val sampled = DesktopCourseRouteSampler.sampledStraightRoutePoints(
            listOf(40.0, 40.001, 40.003, 40.004, 40.005).map { CourseGeoPoint(it, -75.0) }, surface.elevation
        )
        val metric = DesktopCourseRouteMetricsCalculator.metrics(sampled)
        assertEquals(kotlin.math.round(metric.horizontalLengthMeters).toInt(), value.horizontalMeters)
        assertEquals(kotlin.math.round(metric.climbMeters!!).toInt(), value.climbMeters)
        assertEquals(value.horizontalMeters + 10 * value.climbMeters, value.effectiveMeters)
        assertEquals(value.effectiveMeters, value.idealEffectiveMeters)
    }

    @Test fun missingTerrainNeverFallsBackToEndpointElevations() = runBlocking {
        val noTerrain = DesktopFrozenElevationSurface(emptyList()) { null }
        val project = analyzed(fixture(), noTerrain)
        assertTrue(DesktopClassicRouteAnalysis.projection(project).isEmpty())
        assertTrue(project.desktopRouteAnalysis!!.results.getValue("result").unavailableReason!!.contains("Elevation"))
    }

    @Test fun unknownControlsAreOmittedWithoutChangingDownloadedReadoutsOrScoring() = runBlocking {
        for (codes in listOf(listOf(134, 31, 135, 32, 99, 136), listOf(31, 134, 99), listOf(134))) {
            val input = fixture(codes)
            val completed = analyzed(input)
            val expected = analyzed(fixture(codes.filter { it in listOf(31, 32, 99) }))
            assertEquals(input.raceData, completed.raceData)
            assertEquals(DesktopClassicRouteAnalysis.projection(expected), DesktopClassicRouteAnalysis.projection(completed))
            val saved = completed.desktopRouteAnalysis!!.results.getValue("result")
            assertEquals(DesktopClassicRouteAnalysis.PUNCH_POLICY, saved.punchPolicy)
            assertEquals(codes.indices.filter { codes[it] !in listOf(31, 32, 99) }, saved.ignoredControlPunchIndexes)
            val reloaded = EventProjectFileJson.decode(EventProjectFileJson.encode(completed))
            assertEquals(completed.desktopRouteAnalysis, reloaded.desktopRouteAnalysis)
            assertEquals(DesktopClassicRouteAnalysis.projection(completed), DesktopClassicRouteAnalysis.projection(reloaded))
            assertFalse(HtmlResultExports.results(completed.raceData, routeLengths = DesktopClassicRouteAnalysis.projection(completed)).contains("ignoredControlPunchIndexes"))
        }
    }

    @Test fun ambiguousDownloadedIdentityAndMissingOrAmbiguousExtraLocationsAreIgnored() = runBlocking {
        val original = fixture(listOf(31, 134, 32, 99))
        val extra = EventControl("extra", "race", "Extra", 134, ControlPointType.CONTROL)
        val known = original.copy(raceData = original.raceData.copy(controls = original.raceData.controls + extra))
        val ambiguousCode = known.copy(raceData = known.raceData.copy(controls = known.raceData.controls + extra.copy(id = "other")))
        val ambiguousLocation = known.copy(raceData = known.raceData.copy(categories = known.raceData.categories.map { c ->
            c.copy(category = c.category.copy(courseInfo = c.category.courseInfo!!.let { info ->
                info.copy(controlPoints = info.controlPoints + listOf(40.002, 40.004).map { lat ->
                    ProtectedCourseControlPoint(extra.id, extra.label, lat, -75.0, extra.type, 100.0)
                })
            }))
        }))
        val expected = DesktopClassicRouteAnalysis.projection(analyzed(fixture()))
        for (input in listOf(known, ambiguousCode, ambiguousLocation)) {
            val completed = analyzed(input)
            assertEquals(expected, DesktopClassicRouteAnalysis.projection(completed))
            assertEquals(listOf(1), completed.desktopRouteAnalysis!!.results.getValue("result").ignoredControlPunchIndexes)
            assertEquals(input.raceData, completed.raceData)
        }
    }

    @Test fun ignoredPunchTimesDoNotBlockCalculationAndResolvingControlInvalidatesSavedEstimate() = runBlocking {
        val input = fixture(listOf(31, 134, 32, 99)).let { project ->
            project.copy(raceData = project.raceData.copy(competitorData = project.raceData.competitorData.map { data ->
                data.copy(readoutData = data.readoutData!!.let { readout -> readout.copy(punches = readout.punches.map {
                    if (it.punch.siCode == 134) it.copy(punch = it.punch.copy(siTimeSeconds = 999999)) else it
                }) })
            }))
        }
        val completed = analyzed(input)
        assertEquals("Ideal order", DesktopClassicRouteAnalysis.projection(completed).getValue("result").comparison)
        val corrected = completed.copy(raceData = completed.raceData.copy(controls = completed.raceData.controls +
            EventControl("extra", "race", "Extra", 134, ControlPointType.CONTROL)))
        assertTrue(DesktopClassicRouteAnalysis.projection(corrected).isEmpty())
        val invalidPolicy = completed.desktopRouteAnalysis!!.let { metadata -> metadata.copy(results = metadata.results.mapValues { (_, s) -> s.copy(punchPolicy = "future") }) }
        assertTrue(DesktopClassicRouteAnalysis.projection(completed.copy(desktopRouteAnalysis = invalidPolicy)).isEmpty())
    }

    @Test fun previousUnavailableSnapshotIsRetriedAndPreviouslyValidStrictSnapshotIsReusable() = runBlocking {
        val completed = analyzed(fixture())
        val strict = completed.copy(desktopRouteAnalysis = completed.desktopRouteAnalysis!!.let { metadata ->
            metadata.copy(results = metadata.results.mapValues { (_, snapshot) -> snapshot.copy(punchPolicy = "strict-v1") })
        })
        assertEquals(DesktopClassicRouteAnalysis.projection(completed), DesktopClassicRouteAnalysis.projection(strict))
        val unknown = fixture(listOf(31, 134, 32, 99))
        val oldFailure = unknown.copy(desktopRouteAnalysis = strict.desktopRouteAnalysis!!.let { metadata ->
            metadata.copy(contexts = metadata.contexts.mapValues { (_, ref) -> ref.copy(courseFingerprint = DesktopClassicRouteAnalysis.courseFingerprint(unknown)) },
                results = metadata.results.mapValues { (_, snapshot) -> snapshot.copy(inputFingerprint = DesktopClassicRouteAnalysis.inputFingerprint(unknown.raceData.competitorData.single()),
                    length = null, unavailableReason = "Unknown or ambiguous SI control 134.") })
        })
        val retried = analyzed(oldFailure)
        assertEquals("Ideal order", DesktopClassicRouteAnalysis.projection(retried).getValue("result").comparison)
        assertNull(retried.desktopRouteAnalysis!!.results.getValue("result").unavailableReason)
    }

    @Test fun missingOrRepeatedKnownControlsAreNoncomparable() = runBlocking {
        for (codes in listOf(listOf(31, 99), listOf(31, 31, 32, 99), listOf(31, 32))) {
            val value = DesktopClassicRouteAnalysis.projection(analyzed(fixture(codes))).getValue("result")
            assertEquals("Different control set", value.comparison)
        }
        assertEquals("Beacon not terminal", DesktopClassicRouteAnalysis.projection(analyzed(fixture(listOf(99, 31, 32)))).getValue("result").comparison)
    }

    @Test fun missingEndpointsAreUnavailableAndScoringStatusDoesNotInventAPunch() = runBlocking {
        val project = fixture()
        val noStart = changeResult(project) { it.copy(startTimeSeconds = null) }
        assertTrue(DesktopClassicRouteAnalysis.projection(analyzed(noStart)).isEmpty())
        val disqualified = changeResult(project) { it.copy(resultStatus = ResultStatus.DISQUALIFIED) }
        assertEquals(DesktopClassicRouteAnalysis.projection(analyzed(project)), DesktopClassicRouteAnalysis.projection(analyzed(disqualified)))
    }

    @Test fun correctedPunchesHideStoredMetricsAndRejectLateCompletion() = runBlocking {
        val original = fixture()
        val completed = analyzed(original)
        val corrected = fixture(listOf(32, 31, 99)).copy(desktopRouteAnalysis = completed.desktopRouteAnalysis)
        assertTrue(DesktopClassicRouteAnalysis.projection(corrected).isEmpty())
        val merged = mergeClassicRouteAnalysis(corrected, original, completed.desktopRouteAnalysis!!)
        assertTrue(DesktopClassicRouteAnalysis.projection(merged).isEmpty())
        assertEquals(corrected.raceData, merged.raceData)
    }

    @Test fun backwardsPunchTimesAreUnavailableEvenWhenAllPunchesAreInsideStartAndFinish() = runBlocking {
        val original = fixture()
        val data = original.raceData.competitorData.single()
        val readout = data.readoutData!!
        val reversed = readout.punches.reversed().mapIndexed { index, punch -> punch.copy(punch = punch.punch.copy(order = index)) }
        val corrected = original.copy(raceData = original.raceData.copy(competitorData = listOf(data.copy(readoutData = readout.copy(punches = reversed)))))
        val completed = analyzed(corrected)
        assertTrue(DesktopClassicRouteAnalysis.projection(completed).isEmpty())
        assertTrue(completed.desktopRouteAnalysis!!.results.getValue("result").unavailableReason!!.contains("run backwards"))
    }

    @Test fun courseEditsHideStoredMetricsButNamesAndPointsDoNot() = runBlocking {
        val completed = analyzed(fixture())
        val renamed = changeResult(completed) { it.copy(points = 0, resultStatus = ResultStatus.DISQUALIFIED) }
            .let { it.copy(raceData = it.raceData.copy(race = it.raceData.race.copy(name = "Renamed"))) }
        assertEquals(DesktopClassicRouteAnalysis.projection(completed), DesktopClassicRouteAnalysis.projection(renamed))
        val edited = completed.copy(raceData = completed.raceData.copy(controls = completed.raceData.controls.map { it.copy(siCode = it.siCode + 1) }))
        assertTrue(DesktopClassicRouteAnalysis.projection(edited).isEmpty())
        assertEquals(edited, mergeClassicRouteAnalysis(edited, completed, completed.desktopRouteAnalysis!!))
    }

    @Test fun corruptedOrFutureMetadataIsNotExportable() = runBlocking {
        val completed = analyzed(fixture())
        assertTrue(DesktopClassicRouteAnalysis.projection(completed.copy(desktopRouteAnalysis = completed.desktopRouteAnalysis!!.copy(version = 99))).isEmpty())
        val snapshot = completed.desktopRouteAnalysis!!.results.getValue("result")
        val invalid = snapshot.copy(length = snapshot.length!!.copy(effectiveMeters = -1))
        assertTrue(DesktopClassicRouteAnalysis.projection(completed.copy(desktopRouteAnalysis = completed.desktopRouteAnalysis!!.copy(results = mapOf("result" to invalid)))).isEmpty())
    }

    @Test fun everyNonClassicRaceIsExcluded() = runBlocking {
        for (type in RaceType.entries.filter { it != RaceType.CLASSIC }) {
            val p = fixture().let { it.copy(raceData = it.raceData.copy(race = it.raceData.race.copy(raceType = type))) }
            assertTrue(DesktopClassicRouteAnalysis.projection(p).isEmpty())
            try { analyzed(p); fail("Non-Classic calculation accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun cancellationIsPropagatedWithoutAnUnavailableSnapshot() = runBlocking {
        val project = fixture()
        try {
            DesktopClassicRouteAnalysis.calculate(project, infos(project), flat, checkCancelled = { throw CancellationException() })
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { }
        assertNull(project.desktopRouteAnalysis)
    }

    @Test fun completedEstimatesResumeWithoutTerrainLookupsAndKeepTheirTimestamp() = runBlocking {
        val completed = analyzed(fixture())
        val noLookup = DesktopFrozenElevationSurface(flat.sources) { error("Should reuse stored result") }
        assertEquals(completed.desktopRouteAnalysis, analyzed(completed, noLookup).desktopRouteAnalysis)
    }

    @Test fun nativeSaveReopenRetainsMetadataAndLegacyBackupDoesNotAcquireIt() = runBlocking {
        val project = analyzed(fixture())
        val dir = Files.createTempDirectory("classic-route-test-")
        val path = dir.resolve("race.rom.json")
        try {
            DesktopProjectFiles.write(path, project)
            val reopened = DesktopProjectFiles.read(path)
            assertEquals(project.desktopRouteAnalysis, reopened.desktopRouteAnalysis)
            assertEquals(DesktopClassicRouteAnalysis.projection(project), DesktopClassicRouteAnalysis.projection(reopened))
            assertFalse(EventProjectFileJson.encode(fixture()).contains("desktopRouteAnalysis"))
            assertFalse(RaceBackupJsonExports.race(project.raceData).contains("desktopRouteAnalysis"))
            val deleted = project.copy(raceData = project.raceData.copy(competitorData = emptyList()))
            val savedAfterDeletion = EventProjectFileJson.decode(EventProjectFileJson.encode(deleted))
            assertTrue(savedAfterDeletion.desktopRouteAnalysis!!.results.isEmpty())
            assertTrue(savedAfterDeletion.desktopRouteAnalysis!!.contexts.isEmpty())
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(dir) }
    }

    @Test fun routeLengthWordingUsesTwoDecimalsAndSignedDifference() {
        val length = ResultRouteLength(1234, 0, 1234, 1234, "Ideal order")
        assertEquals("1.23 km (ideal order)", length.text)
        assertEquals("1.23 km (missing punches)", length.copy(missingAssignedPunches = true).text)
        assertEquals("1.23 km (difference from ideal order: 0.00 km)", length.copy(comparison = "Alternative order").text)
        assertEquals("1.24 km (difference from ideal order: 0.11 km)", length.copy(effectiveMeters = 1235, idealEffectiveMeters = 1130, comparison = "Alternative order").text)
        assertEquals("1.23 km (difference from ideal order: -0.12 km)", length.copy(idealEffectiveMeters = 1350, comparison = "Different control set").text)
        assertEquals("0.00", ResultRouteLength.kilometers(-4))
        assertEquals(" - Ideal route: Fox2-Fox3-Fox1 (EL: 1.23 km)", length.copy(idealRoute = "Fox2-Fox3-Fox1").categoryHeadingSuffix)
    }

    @Test fun missingAssignedPunchesOverrideComparisonTextOnReloadAndEveryHumanExport() = runBlocking {
        for (codes in listOf(listOf(31, 99), listOf(31, 31, 99), listOf(31, 32), listOf(134, 99), emptyList())) {
            val completed = analyzed(fixture(codes))
            val native = EventProjectFileJson.encode(completed)
            val reloaded = EventProjectFileJson.decode(native)
            val lengths = DesktopClassicRouteAnalysis.projection(reloaded)
            val length = lengths.getValue("result")
            assertTrue(length.missingAssignedPunches)
            assertTrue(length.text.endsWith(" km (missing punches)"))
            assertFalse(length.text.contains("difference from ideal"))
            assertFalse(native.contains("missingAssignedPunches"))
            assertEquals(completed.desktopRouteAnalysis, reloaded.desktopRouteAnalysis)
            val label = "Estimated effective route length: ${length.text}"
            val race = reloaded.raceData
            assertTrue(HtmlResultExports.results(race, routeLengths = lengths).contains(label))
            assertTrue(TextResultExports.results(race, routeLengths = lengths).contains(label))
            assertTrue(ResultReportExports.html(race, routeLengths = lengths).contains(label))
            assertTrue(String(SplitResultPdfExports.pdf(race, routeLengths = lengths), Charsets.ISO_8859_1).contains("missing punches"))
            assertTrue(String(DesktopResultReportPdf.pdfBytes(reloaded), Charsets.ISO_8859_1).contains("missing punches"))
            val site = DesktopPublicResultSiteExports.export(Files.createTempDirectory("missing-punch-result-site-"), reloaded)
            assertTrue(Files.readString(site.publicResultsJson).contains(label))
        }
        for (codes in listOf(listOf(31, 32, 99), listOf(31, 31, 32, 99), listOf(32, 31, 99), listOf(31, 134, 32, 99))) {
            val length = DesktopClassicRouteAnalysis.projection(analyzed(fixture(codes))).getValue("result")
            assertFalse(length.missingAssignedPunches)
            assertFalse(length.text.contains("missing punches"))
        }
    }

    @Test fun headingRecoversCertifiedOrderWithoutTerrainAndDoesNotTrustStoredDesignOrder() = runBlocking {
        val input = fixture().let { it.copy(raceData = it.raceData.copy(categories = it.raceData.categories.map { c ->
            c.copy(category = c.category.copy(idealOrder = "2-1"))
        })) }
        val project = analyzed(input)
        val reloaded = EventProjectFileJson.decode(EventProjectFileJson.encode(project))
        val length = DesktopClassicRouteAnalysis.projection(reloaded).getValue("result")
        assertEquals("1-2", length.idealRoute)
        assertEquals("M21 (1) - Ideal route: 1-2 (EL: 0.56 km)", SplitResultExports.model(reloaded.raceData, routeLengths = mapOf("result" to length)).categories.single().displayName)
        assertNull(project.desktopRouteAnalysis!!.results.getValue("result").length!!.idealRoute)
        // Even accidental serialization of the public projection cannot persist the recovered order.
        val withProjection = project.copy(desktopRouteAnalysis = project.desktopRouteAnalysis!!.let { metadata ->
            metadata.copy(results = metadata.results.mapValues { (_, snapshot) -> snapshot.copy(length = length) })
        })
        assertFalse(EventProjectFileJson.encode(withProjection).contains("\"idealRoute\""))
        val ref = project.desktopRouteAnalysis!!.contexts.values.single()
        assertNull(DesktopClassicRouteHeading.order(project, ref.copy(idealOrderFingerprint = "invalid")))
        val protected = project.copy(raceData = project.raceData.copy(categories = project.raceData.categories.map { c ->
            c.copy(category = c.category.copy(encryptedIdealOrder = "protected"))
        }))
        assertNull(DesktopClassicRouteHeading.order(protected, ref))
    }

    @Test fun exportOptInAddsOnlyPublicNumbersAndLeavesDefaultAndroidPathsUnchanged() = runBlocking {
        val project = analyzed(fixture())
        val projection = DesktopClassicRouteAnalysis.projection(project)
        val race = project.raceData
        assertEquals(SplitResultExports.csv(race), SplitResultExports.csv(race, routeLengths = emptyMap()))
        assertFalse(SplitResultExports.csv(race).contains("effective route"))
        val csv = SplitResultExports.csv(race, routeLengths = projection)
        assertTrue(csv.contains("Estimated effective route length (m)"))
        val html = HtmlResultExports.results(race, routeLengths = projection)
        val txt = TextResultExports.results(race, routeLengths = projection)
        val xml = ResultReportExports.xml(race, routeLengths = projection)
        val heading = "M21 (1) - Ideal route: 1-2 (EL: 0.56 km)"
        assertTrue(html.contains(heading))
        assertTrue(txt.contains(heading))
        assertTrue(ResultReportExports.html(race, routeLengths = projection).contains(heading))
        assertEquals(heading, ResultReportExports.model(race, routeLengths = projection).categories.single().displayName)
        assertFalse(HtmlResultExports.results(race).contains("Ideal route"))
        assertTrue(html.contains("Estimated effective route length: 0.56 km (ideal order)"))
        assertNull(ResultReportExports.model(race).routeLengthCoverage)
        assertEquals(ResultRouteLength.coverage(1, 1), ResultReportExports.model(race, routeLengths = projection).routeLengthCoverage)
        assertTrue(xml.contains("RouteLengthCoverage"))
        for (output in listOf(csv, html, txt, xml)) {
            assertFalse(output.contains("synthetic-flat-v1"))
            assertFalse(output.contains("40.001"))
            assertTrue(output.contains(projection.getValue("result").effectiveMeters.toString()) || output.contains(projection.getValue("result").text))
        }
        assertTrue(xml.contains("EstimatedEffectiveRouteLengthMeters"))
        assertTrue(String(SplitResultPdfExports.pdf(race, routeLengths = projection), Charsets.ISO_8859_1).contains("Estimated effective route length"))
    }

    private fun changeResult(project: EventProjectFile, transform: (EventResult) -> EventResult): EventProjectFile =
        project.copy(raceData = project.raceData.copy(competitorData = project.raceData.competitorData.map { data ->
            data.copy(readoutData = data.readoutData?.let { it.copy(result = transform(it.result)) })
        }))

    private suspend fun analyzed(project: EventProjectFile, surface: DesktopFrozenElevationSurface = flat) =
        project.copy(desktopRouteAnalysis = DesktopClassicRouteAnalysis.calculate(project, infos(project), surface))

    private fun infos(project: EventProjectFile) = project.raceData.categories.associate { it.category.id to it.category.courseInfo!! }

    internal fun fixture(codes: List<Int> = listOf(31, 32, 99)): EventProjectFile {
        val controls = listOf(EventControl("one", "race", "1", 31, ControlPointType.CONTROL),
            EventControl("two", "race", "2", 32, ControlPointType.CONTROL), EventControl("beacon", "race", "Beacon", 99, ControlPointType.BEACON))
        val info = ProtectedCourseInfo(controlPoints = controls.zip(listOf(40.001, 40.003, 40.004)).map { (c, lat) ->
            ProtectedCourseControlPoint(c.id, c.label, lat, -75.0, c.type, 100.0)
        }, courseObjects = listOf(ProtectedCourseObjectPoint("start", "Start", ProtectedCourseObjectType.START, 40.0, -75.0),
            ProtectedCourseObjectPoint("finish", "Finish", ProtectedCourseObjectType.FINISH, 40.005, -75.0)))
        val category = EventCategory("category", "race", "M21", true, 21, 0, 0, 1, false, null, null, null, "", courseInfo = info)
        val competitor = EventCompetitor("competitor", "race", "category", "Test", "Runner", "Club", "", true, 2000, 12345, false, drawnStartTimeSeconds = null)
        val result = EventResult("result", "race", "competitor", 12345, 0, null, 0, 100, "2026-09-04T12:00:00", true, ResultStatus.OK, 2, 100, false, false, categoryId = "category")
        val punches = codes.mapIndexed { index, code -> EventAliasPunch(EventPunch("p$index", "race", "result", 12345, code, (index + 1) * 10L, (index + 1) * 10L, SIRecordType.CONTROL, index, PunchStatus.VALID, 10), null) }
        return EventProjectFile(raceData = EventRaceData(EventRace("race", "Synthetic Classic", "", "2026-09-04T10:00:00", RaceType.CLASSIC, RaceLevel.PRACTICE, RaceBand.M80, 7200),
            listOf(EventCategoryData(category, controls.mapIndexed { index, c -> EventControlPoint("cp$index", category.id, c.siCode, c.type, index, c.id) }, listOf(competitor))),
            emptyList(), listOf(EventCompetitorData(EventCompetitorCategory(competitor, category), EventReadoutData(result, punches))), emptyList(), controls))
    }
}
