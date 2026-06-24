package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType

class DesktopNavigationTest {
    private val eventFileMenuLabels = listOf(
        "New Event File",
        "Load Event File...",
        "Import EventReg Website...",
        "Android...",
        "Settings",
        "Save Event",
        "Close Event File"
    )

    private val androidEventFileMenuLabels = listOf(
        "Send Event to Android",
        "Receive File from Android",
        "Save Android Event File..."
    )

    @Test
    fun exposesVisibleWorkflowGroupsInBottomNavigationOrder() {
        assertEquals(
            listOf("Setup", "Race Operations", "Results/File Export"),
            DesktopWorkflow.bottomBarEntries.map { it.label }
        )
    }

    @Test
    fun bottomBarAddsSeriesOnlyWhenEventHasSeriesContext() {
        assertEquals(
            listOf(DesktopWorkflow.Setup, DesktopWorkflow.RaceOps, DesktopWorkflow.ResultsExport),
            DesktopWorkflow.bottomBarEntries(DesktopNavigationReadiness(hasEventFile = true))
        )
        assertEquals(
            listOf(DesktopWorkflow.Series, DesktopWorkflow.Setup, DesktopWorkflow.RaceOps, DesktopWorkflow.ResultsExport),
            DesktopWorkflow.bottomBarEntries(DesktopNavigationReadiness(hasEventFile = true, hasSeriesContext = true))
        )
    }

    @Test
    fun bottomBarPrefixesEventWorkflowLabelsWithFormatOnlyForSeriesEvents() {
        val standaloneClassic = DesktopNavigationReadiness(
            hasEventFile = true,
            raceType = RaceType.CLASSIC,
            raceBand = RaceBand.M80
        )
        val seriesClassic = standaloneClassic.copy(hasSeriesContext = true)

        assertEquals("Setup", DesktopWorkflow.bottomBarLabel(DesktopWorkflow.Setup, standaloneClassic))
        assertEquals("80m Classic\nSetup", DesktopWorkflow.bottomBarLabel(DesktopWorkflow.Setup, seriesClassic))
        assertEquals("80m Classic\nRace Ops", DesktopWorkflow.bottomBarLabel(DesktopWorkflow.RaceOps, seriesClassic))
        assertEquals("80m Classic\nResults", DesktopWorkflow.bottomBarLabel(DesktopWorkflow.ResultsExport, seriesClassic))
        assertEquals("Series", DesktopWorkflow.bottomBarLabel(DesktopWorkflow.Series, seriesClassic))
    }

    @Test
    fun bottomBarFormatPrefixDistinguishesSeriesEventFormats() {
        assertEquals(
            "Sprint\nSetup",
            DesktopWorkflow.bottomBarLabel(
                DesktopWorkflow.Setup,
                DesktopNavigationReadiness(hasEventFile = true, hasSeriesContext = true, raceType = RaceType.SPRINT)
            )
        )
        assertEquals(
            "Foxoring\nRace Ops",
            DesktopWorkflow.bottomBarLabel(
                DesktopWorkflow.RaceOps,
                DesktopNavigationReadiness(hasEventFile = true, hasSeriesContext = true, raceType = RaceType.FOXORING)
            )
        )
        assertEquals(
            "2m Classic\nResults",
            DesktopWorkflow.bottomBarLabel(
                DesktopWorkflow.ResultsExport,
                DesktopNavigationReadiness(
                    hasEventFile = true,
                    hasSeriesContext = true,
                    raceType = RaceType.CLASSIC,
                    raceBand = RaceBand.M2
                )
            )
        )
    }

    @Test
    fun seriesEventsMenuIncludesAddEventAction() {
        val events = DesktopNavigation.rootItems(DesktopWorkflow.Series)
            .first { it.label == "Events" }

        assertEquals(
            listOf("Add Event to Series..."),
            events.children.map { it.label }
        )
        assertEquals(DesktopNavAction.AddEventToSeries, events.children.first { it.label == "Add Event to Series..." }.action)
    }

    @Test
    fun seriesEventsMenuDoesNotDuplicateRowSpecificOpenAction() {
        val events = DesktopNavigation.rootItems(DesktopWorkflow.Series)
            .first { it.label == "Events" }

        assertFalse(events.children.any { it.label == "Open Series Event..." })
    }

    @Test
    fun seriesSubmenusUseDistinctDestinationLabels() {
        val roots = DesktopNavigation.rootItems(DesktopWorkflow.Series)

        assertEquals(
            listOf("Balance Open Event for Series"),
            roots.first { it.label == "Start Fairness" }.children.map { it.label }
        )
        assertEquals(
            emptyList<String>(),
            roots.first { it.label == "Series Validation" }.children.map { it.label }
        )
        assertEquals(
            listOf("Export Series..."),
            roots.first { it.label == "Series Settings" }.children.map { it.label }
        )
    }

    @Test
    fun seriesActionsAvoidDuplicateMenuAndScreenButtons() {
        val roots = DesktopNavigation.rootItems(DesktopWorkflow.Series)

        assertTrue(roots.first { it.label == "Events" }.children.any { it.label == "Add Event to Series..." })
        assertFalse(roots.first { it.label == "Series Validation" }.children.any { it.label == "Validate Series" })
        assertFalse(roots.first { it.label == "Events" }.children.any { it.label == "Open Series Event..." })
    }

    @Test
    fun seriesSubmenuGroupsDoNotRepeatTheirParentLabel() {
        assertNoRepeatedParentChildLabels(DesktopNavigation.rootItems(DesktopWorkflow.Series))
    }

    @Test
    fun menuGroupsDoNotContainViewOnlyChildrenForTheirOwnSection() {
        DesktopWorkflow.entries.forEach { workflow ->
            assertNoRedundantViewOnlyChildren(DesktopNavigation.rootItems(workflow))
        }
    }

    @Test
    fun bottomNavigationDisablesWorkflowsThatNeedAnOpenEventFile() {
        assertFalse(DesktopWorkflow.Setup.requiresEventFileInBottomBar)
        assertTrue(DesktopWorkflow.RaceOps.requiresEventFileInBottomBar)
        assertTrue(DesktopWorkflow.Series.requiresEventFileInBottomBar)
        assertTrue(DesktopWorkflow.ResultsExport.requiresEventFileInBottomBar)
    }

    @Test
    fun placesCurrentDesktopSectionsUnderWorkflowGroups() {
        assertEquals(
            listOf("Event File", "Controls", "Categories", "Competitors", "Start List", "Tools"),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).map { it.label }
        )
        assertEquals(
            listOf("Readouts", "SI Readout", "In Forest", "Unmatched Readouts", "Finish Tickets"),
            DesktopNavigation.rootItems(DesktopWorkflow.RaceOps).map { it.label }
        )
        assertEquals(
            listOf("Events", "Start Fairness", "Competitor Matching", "Series Validation", "Series Settings"),
            DesktopNavigation.rootItems(DesktopWorkflow.Series).map { it.label }
        )
        assertEquals(
            listOf("Live Results", "Exports"),
            DesktopNavigation.rootItems(DesktopWorkflow.ResultsExport).map { it.label }
        )
        assertEquals(
            listOf("App Settings", "Hardware Preferences", "Help"),
            DesktopNavigation.rootItems(DesktopWorkflow.SettingsHelp).map { it.label }
        )
    }

    @Test
    fun seriesWorkflowRequiresSeriesContext() {
        assertEquals(
            "Series is available after this Event File is linked to an Event Series.",
            DesktopNavigation.disabledWorkflowReason(
                DesktopWorkflow.Series,
                DesktopNavigationReadiness(hasEventFile = true, hasSeriesContext = false)
            )
        )
        assertEquals(
            null,
            DesktopNavigation.disabledWorkflowReason(
                DesktopWorkflow.Series,
                DesktopNavigationReadiness(hasEventFile = true, hasSeriesContext = true)
            )
        )
    }

    @Test
    fun featureSettingsUseSpecificSections() {
        val setupSiSettings = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
            .first { it.label == "Event File" }
            .children
            .first { it.label == "Settings" }
            .children
            .first { it.label == "SI Readout Settings" }
        val resultsLiveSettings = DesktopNavigation.rootItems(DesktopWorkflow.ResultsExport)
            .first { it.label == "Live Results" }

        assertEquals(DesktopSection.SiReadoutSettings, setupSiSettings.section)
        assertEquals(DesktopSection.LiveResultsOverview, resultsLiveSettings.section)
    }

    @Test
    fun buildsBreadcrumbForSectionAndSubmenuSelection() {
        val state = DesktopNavState()
            .enter(DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Start List" })
            .enter(DesktopNavigation.currentItems(DesktopNavState(submenuStack = listOf("setup.start-list")))
                .first { it.label == "Exports" })

        assertEquals(
            "Setup > Start List > Exports",
            DesktopNavigation.breadcrumb(state)
        )
    }

    @Test
    fun eventSeriesSettingsDrillIntoThirdLevelMenu() {
        val eventFile = DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Event File" }
        val eventFileState = DesktopNavState().enter(eventFile)
        val settings = DesktopNavigation.currentItems(eventFileState).first { it.label == "Settings" }
        val settingsState = eventFileState.enter(settings)
        val eventSeries = DesktopNavigation.currentItems(settingsState).first { it.label == "Event Series" }
        val eventSeriesState = settingsState.enter(eventSeries)

        assertTrue(DesktopNavigation.usesSeriesNavigationColor(settingsState, eventSeries))
        assertTrue(
            DesktopNavigation.currentItems(eventSeriesState)
                .all { DesktopNavigation.usesSeriesNavigationColor(eventSeriesState, it) }
        )
        assertFalse(
            DesktopNavigation.usesSeriesNavigationColor(
                settingsState,
                DesktopNavigation.currentItems(settingsState).first { it.label == "App Settings" }
            )
        )
        assertEquals(
            listOf("setup.event-file", "setup.event-file.settings", "setup.event-file.series-settings"),
            eventSeriesState.submenuStack
        )
        assertEquals(
            listOf(
                "Create New Series with This Event",
                "Link to Existing Series...",
                "Change Series Link...",
                "Remove from Series...",
                "Validate Series Link"
            ),
            DesktopNavigation.currentItems(eventSeriesState).map { it.label }
        )
    }

    @Test
    fun switchingWorkflowClearsSubmenuStackAndSelectsDefaultSection() {
        val state = DesktopNavState(submenuStack = listOf("setup.start-list"), selectedSection = DesktopSection.StartList)
            .switchWorkflow(DesktopWorkflow.RaceOps)

        assertEquals(DesktopWorkflow.RaceOps, state.workflow)
        assertTrue(state.submenuStack.isEmpty())
        assertEquals(DesktopSection.WorkflowHome, state.selectedSection)
        assertEquals("race.home", state.selectedItemId)
        assertEquals("Race Operations", DesktopNavigation.selectedLabel(state))
    }

    @Test
    fun switchingToResultsFileExportShowsResultsWithoutBack() {
        val state = DesktopNavState()
            .switchWorkflow(DesktopWorkflow.ResultsExport)

        assertEquals(DesktopWorkflow.ResultsExport, state.workflow)
        assertTrue(state.submenuStack.isEmpty())
        assertEquals(DesktopSection.Results, state.selectedSection)
        assertEquals("results.home", state.selectedItemId)
        assertEquals("Results", DesktopNavigation.selectedLabel(state))
        assertEquals("Results/File Export", DesktopNavigation.breadcrumb(state))
        assertFalse(DesktopNavigation.canGoBack(state))
        assertEquals(
            listOf("Live Results", "Exports"),
            DesktopNavigation.currentItems(state).map { it.label }
        )
        assertTrue(DesktopNavigation.selectedDescription(state).contains("review scored finishers by category"))
        assertTrue(DesktopNavigation.selectedDescription(state).contains("Use Live Results"))
        assertTrue(DesktopNavigation.selectedDescription(state).contains("use Exports"))
    }

    @Test
    fun defaultStartupStateShowsWorkflowHome() {
        val state = DesktopNavState()

        assertEquals(DesktopWorkflow.Setup, state.workflow)
        assertEquals(DesktopSection.WorkflowHome, state.selectedSection)
        assertEquals("setup.home", state.selectedItemId)
        assertEquals("Setup", DesktopNavigation.selectedLabel(state))
        assertEquals("Setup", DesktopNavigation.breadcrumb(state))
        assertTrue(DesktopNavigation.selectedDescription(state).contains("create or open an Event File"))
    }

    @Test
    fun describesWorkflowHomesAndNavigationDestinations() {
        DesktopWorkflow.entries.forEach { workflow ->
            val workflowState = DesktopNavState().switchWorkflow(workflow)
            val workflowDescription = DesktopNavigation.selectedDescription(workflowState)

            assertTrue("Missing description for ${workflow.name}", workflowDescription.length > 40)
            flatten(DesktopNavigation.rootItems(workflow)).forEach { item ->
                val itemState = workflowState.copy(
                    selectedSection = item.section ?: workflowState.selectedSection,
                    selectedItemId = item.id
                )
                val itemDescription = DesktopNavigation.selectedDescription(itemState)

                assertTrue("Missing description for ${item.id}", itemDescription.length > 40)
                assertFalse("Generic workflow description used for ${item.id}", itemDescription == workflowDescription)
            }
        }
    }

    @Test
    fun menuIndicatorsAndEllipsesFollowNavigationSemantics() {
        val items = DesktopWorkflow.entries.flatMap { workflow -> flatten(DesktopNavigation.rootItems(workflow)) }

        items.forEach { item ->
            assertEquals("Wrong menu indicator state for ${item.id}", item.action == null, DesktopNavigation.showsMenuIndicator(item))
            assertFalse("Stored labels should not include rendered indicator for ${item.id}", item.label.contains(">"))
            if (item.children.isNotEmpty() && item.id != "setup.event-file.android") {
                assertFalse("Submenu labels should not use ellipses for ${item.id}", item.label.contains("..."))
            }
        }
        assertTrue(
            DesktopNavigation.showsMenuIndicator(
                DesktopNavigation.rootItems(DesktopWorkflow.Setup)
                    .first { it.label == "Controls" }
                    .children
                    .first { it.label == "Elevation Data" }
            )
        )
    }

    @Test
    fun backFromFirstLevelSubmenuReturnsToWorkflowHome() {
        val eventFile = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
            .first { it.label == "Event File" }
        val state = DesktopNavState().enter(eventFile).back()

        assertTrue(state.submenuStack.isEmpty())
        assertEquals(DesktopSection.WorkflowHome, state.selectedSection)
        assertEquals("setup.home", state.selectedItemId)
        assertEquals("Setup", DesktopNavigation.breadcrumb(state))
    }

    @Test
    fun backFromRootLeafScreensReturnsToWorkflowHome() {
        val cases = listOf(
            DesktopWorkflow.RaceOps to "Readouts",
            DesktopWorkflow.RaceOps to "In Forest",
            DesktopWorkflow.RaceOps to "Unmatched Readouts",
            DesktopWorkflow.RaceOps to "Finish Tickets"
        )

        cases.forEach { (workflow, label) ->
            val workflowState = DesktopNavState().switchWorkflow(workflow)
            val selectedState = DesktopNavigation.selectItem(
                workflowState,
                DesktopNavigation.rootItems(workflow).first { it.label == label }
            ).state

            assertTrue("Expected Back for $label", DesktopNavigation.canGoBack(selectedState))
            assertEquals(emptyList<String>(), DesktopNavigation.currentItems(selectedState).map { it.label })
            assertEquals(workflowState, selectedState.back())
        }
    }

    @Test
    fun workflowHomeDoesNotShowBack() {
        DesktopWorkflow.entries.forEach { workflow ->
            assertFalse(DesktopNavigation.canGoBack(DesktopNavState().switchWorkflow(workflow)))
        }
    }

    @Test
    fun backFromSecondLevelSubmenuReturnsToPreviousMenu() {
        val startList = DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Start List" }
        val firstLevel = DesktopNavState().enter(startList)
        val exports = DesktopNavigation.currentItems(firstLevel).first { it.label == "Exports" }

        val state = firstLevel.enter(exports).back()

        assertEquals(listOf("setup.start-list"), state.submenuStack)
        assertEquals("setup.start-list", state.selectedItemId)
        assertEquals(DesktopSection.StartList, state.selectedSection)
        assertEquals("Setup > Start List", DesktopNavigation.breadcrumb(state))
        assertEquals(
            listOf("Import Starts CSV...", "Exports"),
            DesktopNavigation.currentItems(state).map { it.label }
        )
    }

    @Test
    fun backFromNewEventFileActionReturnsToEventFileMenu() {
        val eventFileState = DesktopNavigation.selectItem(
            DesktopNavState(),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Event File" }
        ).state
        val newEventState = DesktopNavigation.selectItem(
            eventFileState,
            DesktopNavigation.currentItems(eventFileState).first { it.action == DesktopNavAction.NewEventFile }
        ).state

        val state = newEventState.back()

        assertEquals(listOf("setup.event-file"), state.submenuStack)
        assertEquals(DesktopSection.Races, state.selectedSection)
        assertEquals("setup.event-file", state.selectedItemId)
        assertEquals("Setup > Event File", DesktopNavigation.breadcrumb(state))
    }

    @Test
    fun openEventFileActionFocusesLoadFileContext() {
        val eventFileState = DesktopNavigation.selectItem(
            DesktopNavState(),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Event File" }
        ).state

        val selection = DesktopNavigation.selectItem(
            eventFileState,
            DesktopNavigation.currentItems(eventFileState).first { it.action == DesktopNavAction.OpenEventFile }
        )

        assertEquals(DesktopNavAction.OpenEventFile, selection.action)
        assertEquals(listOf("setup.event-file"), selection.state.submenuStack)
        assertEquals(DesktopSection.Races, selection.state.selectedSection)
        assertEquals("setup.event-file.open", selection.state.selectedItemId)
        assertEquals("Setup > Event File > Load Event File...", DesktopNavigation.breadcrumb(selection.state))
        assertEquals(emptyList<String>(), DesktopNavigation.currentItems(selection.state).map { it.label })
    }

    @Test
    fun completedTransientActionReturnsToParentMenu() {
        val eventFileState = DesktopNavigation.selectItem(
            DesktopNavState(),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Event File" }
        ).state
        val selection = DesktopNavigation.selectItem(
            eventFileState,
            DesktopNavigation.currentItems(eventFileState).first { it.action == DesktopNavAction.OpenEventFile }
        )

        val completedState = DesktopNavigation.returnToParentMenuAfterAction(
            selection.state,
            DesktopNavAction.OpenEventFile
        )

        assertEquals(listOf("setup.event-file"), completedState.submenuStack)
        assertEquals("setup.event-file", completedState.selectedItemId)
        assertEquals(DesktopSection.Races, completedState.selectedSection)
        assertEquals("Setup > Event File", DesktopNavigation.breadcrumb(completedState))
        assertEquals(eventFileMenuLabels, DesktopNavigation.currentItems(completedState).map { it.label })
    }

    @Test
    fun completedNewEventFileActionKeepsEditingWorkspaceSelected() {
        val eventFileState = DesktopNavigation.selectItem(
            DesktopNavState(),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Event File" }
        ).state
        val selection = DesktopNavigation.selectItem(
            eventFileState,
            DesktopNavigation.currentItems(eventFileState).first { it.action == DesktopNavAction.NewEventFile }
        )

        val completedState = DesktopNavigation.returnToParentMenuAfterAction(
            selection.state,
            DesktopNavAction.NewEventFile
        )

        assertEquals("setup.event-file.new", completedState.selectedItemId)
        assertEquals(DesktopSection.Races, completedState.selectedSection)
        assertEquals("Setup > Event File > New Event File", DesktopNavigation.breadcrumb(completedState))
        assertEquals(emptyList<String>(), DesktopNavigation.currentItems(completedState).map { it.label })
    }

    @Test
    fun unsavedNewEventDraftGuardBlocksNavigationAway() {
        val eventFileState = DesktopNavigation.selectItem(
            DesktopNavState(),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Event File" }
        ).state
        val newEventState = DesktopNavigation.selectItem(
            eventFileState,
            DesktopNavigation.currentItems(eventFileState).first { it.action == DesktopNavAction.NewEventFile }
        ).state

        assertTrue(
            DesktopNavigation.shouldGuardUnsavedNewEventDraft(
                currentState = newEventState,
                nextState = newEventState.back(),
                hasEditedUnsavedNewEventDraft = true
            )
        )
        assertFalse(
            DesktopNavigation.shouldGuardUnsavedNewEventDraft(
                currentState = newEventState,
                nextState = newEventState.back(),
                hasEditedUnsavedNewEventDraft = false
            )
        )
    }

    @Test
    fun dirtySubmenuGuardBlocksLeavingSubmenuWhenEventHasUnsavedChanges() {
        val startListState = DesktopNavigation.selectItem(
            DesktopNavState(),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Start List" }
        ).state

        assertTrue(
            DesktopNavigation.shouldGuardDirtySubmenuExit(
                currentState = startListState,
                nextState = startListState.back(),
                hasUnsavedChanges = true
            )
        )
        assertTrue(
            DesktopNavigation.shouldGuardDirtySubmenuExit(
                currentState = startListState,
                nextState = startListState.switchWorkflow(DesktopWorkflow.RaceOps),
                hasUnsavedChanges = true
            )
        )
        assertFalse(
            DesktopNavigation.shouldGuardDirtySubmenuExit(
                currentState = startListState,
                nextState = startListState.back(),
                hasUnsavedChanges = false
            )
        )
        assertFalse(
            DesktopNavigation.shouldGuardDirtySubmenuExit(
                currentState = startListState,
                nextState = DesktopNavigation.selectItem(
                    startListState,
                    DesktopNavigation.currentItems(startListState).first { it.label == "Exports" }
                ).state,
                hasUnsavedChanges = true
            )
        )
    }

    @Test
    fun dirtySubmenuGuardBlocksBackingOutOfProtectedCourseOrder() {
        val categoriesState = DesktopNavigation.selectItem(
            DesktopNavState(),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Categories" }
        ).state
        val protectedCourseOrderState = DesktopNavigation.selectItem(
            categoriesState,
            DesktopNavigation.currentItems(categoriesState).first { it.label == "Course Order" }
        ).state

        assertTrue(
            DesktopNavigation.shouldGuardDirtySubmenuExit(
                currentState = protectedCourseOrderState,
                nextState = protectedCourseOrderState.back(),
                hasUnsavedChanges = true
            )
        )
    }

    @Test
    fun preservesSelectedMenuItemWhenMultipleItemsShareASection() {
        val finishTickets = DesktopNavigation.rootItems(DesktopWorkflow.RaceOps)
            .first { it.label == "Finish Tickets" }
        val state = DesktopNavState().switchWorkflow(DesktopWorkflow.RaceOps).enter(finishTickets)

        assertEquals(DesktopSection.Readouts, state.selectedSection)
        assertEquals("race.finish-tickets", state.selectedItemId)
        assertEquals("Finish Tickets", DesktopNavigation.selectedLabel(state))
        assertEquals("Race Operations > Finish Tickets", DesktopNavigation.breadcrumb(state))
    }

    @Test
    fun eventFileActionsDeclareOpenEventFileRequirements() {
        val eventFileActions = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
            .first { it.label == "Event File" }
            .children

        assertFalse(eventFileActions.first { it.action == DesktopNavAction.NewEventFile }.requiresEventFile)
        assertFalse(eventFileActions.first { it.action == DesktopNavAction.OpenEventFile }.requiresEventFile)
        assertFalse(eventFileActions.first { it.action == DesktopNavAction.ImportEventRegWebsite }.requiresEventFile)
        assertTrue(eventFileActions.first { it.action == DesktopNavAction.SaveEventFile }.requiresEventFile)
        assertTrue(eventFileActions.first { it.action == DesktopNavAction.CloseEventFile }.requiresEventFile)
        val androidActions = eventFileActions.first { it.label == "Android..." }.children
        assertFalse(eventFileActions.first { it.label == "Android..." }.requiresEventFile)
        assertFalse(androidActions.first { it.action == DesktopNavAction.ReceiveFileFromAndroid }.requiresEventFile)
        assertTrue(androidActions.first { it.action == DesktopNavAction.ExportAndroidRaceBackupJson }.requiresEventFile)
        assertTrue(androidActions.first { it.action == DesktopNavAction.SendEventFileToAndroid }.requiresEventFile)
        assertFalse(eventFileActions.first { it.label == "Settings" }.requiresEventFile)
        assertEquals(eventFileMenuLabels, eventFileActions.map { it.label })
        assertEquals(androidEventFileMenuLabels, androidActions.map { it.label })
        assertEquals(
            listOf(
                "SI Readout Settings",
                "Live Results",
                "Display Settings",
                "App Settings",
                "Event Series",
                "Readiness"
            ),
            eventFileActions.first { it.label == "Settings" }.children.map { it.label }
        )
    }

    @Test
    fun setupRowsDeclareWhichButtonsRequireAnOpenEventFile() {
        val setupItems = DesktopNavigation.rootItems(DesktopWorkflow.Setup)

        assertFalse(setupItems.first { it.label == "Event File" }.requiresEventFile)
        assertTrue(setupItems.first { it.label == "Controls" }.requiresEventFile)
        assertTrue(setupItems.first { it.label == "Categories" }.requiresEventFile)
        assertTrue(setupItems.first { it.label == "Competitors" }.requiresEventFile)
        assertTrue(setupItems.first { it.label == "Start List" }.requiresEventFile)
        assertFalse(setupItems.first { it.label == "Tools" }.requiresEventFile)
    }

    @Test
    fun setupMenusAreEnabledInWorkflowOrder() {
        val setupItems = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
        val controls = setupItems.first { it.label == "Controls" }
        val categories = setupItems.first { it.label == "Categories" }
        val competitors = setupItems.first { it.label == "Competitors" }
        val startList = setupItems.first { it.label == "Start List" }
        val tools = setupItems.first { it.label == "Tools" }

        val noEvent = DesktopNavigationReadiness()
        assertFalse(DesktopNavigation.isItemEnabled(controls, noEvent))
        assertFalse(DesktopNavigation.isItemEnabled(categories, noEvent))
        assertFalse(DesktopNavigation.isItemEnabled(competitors, noEvent))
        assertFalse(DesktopNavigation.isItemEnabled(startList, noEvent))
        assertTrue(DesktopNavigation.isItemEnabled(tools, noEvent))

        val eventOnly = DesktopNavigationReadiness(hasEventFile = true)
        assertTrue(DesktopNavigation.isItemEnabled(controls, eventOnly))
        assertFalse(DesktopNavigation.isItemEnabled(categories, eventOnly))
        assertFalse(DesktopNavigation.isItemEnabled(competitors, eventOnly))
        assertFalse(DesktopNavigation.isItemEnabled(startList, eventOnly))

        val controlsEntered = eventOnly.copy(hasControls = true)
        assertTrue(DesktopNavigation.isItemEnabled(categories, controlsEntered))
        assertFalse(DesktopNavigation.isItemEnabled(competitors, controlsEntered))
        assertFalse(DesktopNavigation.isItemEnabled(startList, controlsEntered))

        val categoriesEntered = controlsEntered.copy(hasCategories = true)
        assertTrue(DesktopNavigation.isItemEnabled(competitors, categoriesEntered))
        assertFalse(DesktopNavigation.isItemEnabled(startList, categoriesEntered))

        val competitorsAssigned = categoriesEntered.copy(
            hasCompetitors = true,
            hasAssignedCompetitors = true
        )
        assertTrue(DesktopNavigation.isItemEnabled(startList, competitorsAssigned))
    }

    @Test
    fun workflowTabsAreEnabledAfterTheirPrerequisites() {
        val setupComplete = DesktopNavigationReadiness(
            hasEventFile = true,
            hasControls = true,
            hasCategories = true,
            hasCompetitors = true,
            hasAssignedCompetitors = true,
            hasStartList = true
        )

        assertTrue(DesktopNavigation.isWorkflowEnabled(DesktopWorkflow.Setup, DesktopNavigationReadiness()))
        assertFalse(DesktopNavigation.isWorkflowEnabled(DesktopWorkflow.RaceOps, setupComplete.copy(hasStartList = false)))
        assertTrue(DesktopNavigation.isWorkflowEnabled(DesktopWorkflow.RaceOps, setupComplete))
        assertFalse(DesktopNavigation.isWorkflowEnabled(DesktopWorkflow.ResultsExport, setupComplete))
        assertTrue(
            DesktopNavigation.isWorkflowEnabled(
                DesktopWorkflow.ResultsExport,
                setupComplete.copy(hasRaceOpsData = true)
            )
        )
    }

    @Test
    fun practiceEventsEnableRaceOpsWithoutCompetitorsOrStartList() {
        val practiceEvent = DesktopNavigationReadiness(
            hasEventFile = true,
            raceLevel = RaceLevel.PRACTICE
        )
        val regionalEvent = practiceEvent.copy(raceLevel = RaceLevel.REGIONAL)

        assertTrue(DesktopNavigation.isWorkflowEnabled(DesktopWorkflow.RaceOps, practiceEvent))
        assertFalse(DesktopNavigation.isWorkflowEnabled(DesktopWorkflow.RaceOps, regionalEvent))
    }

    @Test
    fun disabledWorkflowOverrideHintAppliesToRaceOpsAndResults() {
        val noEvent = DesktopNavigationReadiness()

        assertTrue(DesktopNavigation.canLongClickOverrideDisabledWorkflow(DesktopWorkflow.RaceOps, noEvent))
        assertTrue(DesktopNavigation.canLongClickOverrideDisabledWorkflow(DesktopWorkflow.ResultsExport, noEvent))
        assertEquals(
            "Race Ops disabled: open or create an Event File first. Long-click for 3 seconds to explore this workflow.",
            DesktopNavigation.disabledWorkflowReasonWithOverrideHint(DesktopWorkflow.RaceOps, noEvent)
        )
        assertEquals(
            "Results need at least one SI-card readout or unmatched readout. Long-click for 3 seconds to explore this workflow.",
            DesktopNavigation.disabledWorkflowReasonWithOverrideHint(DesktopWorkflow.ResultsExport, noEvent)
        )
    }

    @Test
    fun disabledRaceOpsReasonIdentifiesMissingDrawnStartTimes() {
        val readiness = DesktopNavigationReadiness(
            hasEventFile = true,
            hasControls = true,
            hasCategories = true,
            hasCompetitors = true,
            hasAssignedCompetitors = true,
            hasStartList = false,
            competitorCount = 16,
            unscheduledCompetitorCount = 7
        )

        assertEquals(
            "Race Ops disabled: generate a Start List; 7 competitors have no drawn start time.",
            DesktopNavigation.disabledWorkflowReason(DesktopWorkflow.RaceOps, readiness)
        )
        assertEquals(
            "Race Ops disabled: generate a Start List; 7 competitors have no drawn start time.",
            DesktopNavigation.primaryDisabledSummary(readiness)
        )
    }

    @Test
    fun disabledStartListReasonIdentifiesUnassignedCompetitors() {
        val startList = DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Start List" }
        val readiness = DesktopNavigationReadiness(
            hasEventFile = true,
            hasControls = true,
            hasCategories = true,
            hasCompetitors = true,
            hasAssignedCompetitors = false,
            unassignedCompetitorCount = 2
        )

        assertEquals(
            "Assign 2 competitors to categories before drawing a Start List.",
            DesktopNavigation.disabledItemReason(startList, readiness)
        )
    }

    @Test
    fun disabledMenuOverrideAppliesOnlyToDisabledMenuEntries() {
        val setupItems = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
        val categories = setupItems.first { it.label == "Categories" }
        val eventFile = setupItems.first { it.label == "Event File" }
        val noEvent = DesktopNavigationReadiness()
        val eventOnly = DesktopNavigationReadiness(hasEventFile = true)

        assertTrue(DesktopNavigation.canLongClickOverrideDisabledMenu(categories, noEvent))
        assertFalse(DesktopNavigation.canLongClickOverrideDisabledMenu(eventFile, noEvent))
        assertFalse(DesktopNavigation.canLongClickOverrideDisabledMenu(categories, eventOnly.copy(hasControls = true)))
    }

    @Test
    fun disabledMenuOverrideHintIsNotAddedToDisabledActions() {
        val disabledAction = flatten(DesktopNavigation.rootItems(DesktopWorkflow.Setup))
            .first { it.action != null && it.requiresEventFile }
        val categories = DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Categories" }
        val noEvent = DesktopNavigationReadiness()

        assertEquals(
            "Open or create an Event File first.",
            DesktopNavigation.disabledItemReasonWithMenuOverrideHint(disabledAction, noEvent)
        )
        assertEquals(
            "Open or create an Event File first. Long-click for 3 seconds to explore this menu.",
            DesktopNavigation.disabledItemReasonWithMenuOverrideHint(categories, noEvent)
        )
    }

    @Test
    fun disabledResultsReasonIdentifiesMissingRaceOpsData() {
        val setupComplete = DesktopNavigationReadiness(
            hasEventFile = true,
            hasControls = true,
            hasCategories = true,
            hasCompetitors = true,
            hasAssignedCompetitors = true,
            hasStartList = true,
            hasRaceOpsData = false
        )

        assertEquals(
            "Results need at least one SI-card readout or unmatched readout.",
            DesktopNavigation.disabledWorkflowReason(DesktopWorkflow.ResultsExport, setupComplete)
        )
        assertEquals(
            "Results need at least one SI-card readout or unmatched readout.",
            DesktopNavigation.primaryDisabledSummary(setupComplete)
        )
    }

    @Test
    fun eventFileMenuOwnsDiagnostics() {
        val eventFileItems = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
            .first { it.label == "Event File" }
            .children

        assertEquals(eventFileMenuLabels, eventFileItems.map { it.label })
        assertFalse(eventFileItems.first { it.label == "Settings" }.requiresEventFile)
        assertEquals(
            DesktopSection.LiveResultsOverview,
            eventFileItems.first { it.label == "Settings" }
                .children
                .first { it.label == "Live Results" }
                .section
        )
        assertFalse(DesktopNavigation.rootItems(DesktopWorkflow.Setup).any { it.label == "Utils" })
    }

    @Test
    fun controlsCategoriesAndCompetitorCsvActionsLiveUnderTheirSetupSections() {
        val setupItems = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
        val controlItems = setupItems.first { it.label == "Controls" }.children
        val categoryItems = setupItems.first { it.label == "Categories" }.children
        val competitorItems = setupItems.first { it.label == "Competitors" }.children

        assertEquals(
            listOf(
                "Elevation Data",
                "Import/Export",
                "Delete All Controls..."
            ),
            controlItems.map { it.label }
        )
        assertEquals(
            listOf(
                "Course Order",
                "Import Categories CSV...",
                "Export Categories CSV...",
                "Delete All Control Assignments...",
                "Delete All Categories...",
            ),
            categoryItems.map { it.label }
        )
        assertEquals(DesktopNavAction.DeleteAllControls, controlItems.last { it.label == "Delete All Controls..." }.action)
        assertEquals(
            DesktopNavAction.ImportControlsKmlKmz,
            controlItems.first { it.label == "Import/Export" }.children.first { it.label == "Import Controls KML/KMZ..." }.action
        )
        assertEquals(
            DesktopNavAction.ImportControlsGpx,
            controlItems.first { it.label == "Import/Export" }.children.first { it.label == "Import Controls GPX..." }.action
        )
        assertEquals(DesktopSection.ElevationCache, controlItems.first { it.label == "Elevation Data" }.section)
        assertEquals(
            listOf("Import Elevation Data", "Import DEM File..."),
            controlItems.first { it.label == "Elevation Data" }.children.map { it.label }
        )
        assertEquals(
            DesktopSection.ElevationCacheImport,
            controlItems.first { it.label == "Elevation Data" }.children.first { it.label == "Import Elevation Data" }.section
        )
        assertEquals(
            DesktopNavAction.ImportDemFile,
            controlItems.first { it.label == "Elevation Data" }.children.first { it.label == "Import DEM File..." }.action
        )
        assertEquals(DesktopSection.ControlsImportExport, controlItems.first { it.label == "Import/Export" }.section)
        assertEquals(
            listOf(
                "Import Controls CSV...",
                "Export Controls CSV...",
                "Import Controls KML/KMZ...",
                "Export Controls KML/KMZ...",
                "Import Controls GPX...",
                "Export Controls GPX...",
                "Export Course Overlays..."
            ),
            controlItems.first { it.label == "Import/Export" }.children.map { it.label }
        )
        assertEquals(DesktopSection.ProtectedCourseOrder, categoryItems.first { it.label == "Course Order" }.section)
        assertEquals(
            listOf(
                "Import Competitors CSV...",
                "Import EventReg Website...",
                "Export Competitors CSV...",
                "Delete All Competitors..."
            ),
            competitorItems.map { it.label }
        )
        assertEquals(DesktopNavAction.ImportCategoriesCsv, categoryItems.first { it.label == "Import Categories CSV..." }.action)
        assertEquals(DesktopNavAction.ExportCategoriesCsv, categoryItems.first { it.label == "Export Categories CSV..." }.action)
        assertEquals(
            DesktopNavAction.DeleteAllCategoryAssignedControls,
            categoryItems.first { it.label == "Delete All Control Assignments..." }.action
        )
        assertEquals(
            DesktopNavAction.DeleteAllCategories,
            categoryItems.first { it.label == "Delete All Categories..." }.action
        )
        assertEquals(DesktopNavAction.ImportCompetitorsCsv, competitorItems.first { it.label == "Import Competitors CSV..." }.action)
        assertEquals(DesktopNavAction.ImportEventRegCompetitorsCsv, competitorItems.first { it.label == "Import EventReg Website..." }.action)
        assertTrue(competitorItems.first { it.label == "Import EventReg Website..." }.requiresEventFile)
        assertEquals(DesktopNavAction.ExportCompetitorsCsv, competitorItems.first { it.label == "Export Competitors CSV..." }.action)
        assertEquals(DesktopNavAction.DeleteAllCompetitors, competitorItems.first { it.label == "Delete All Competitors..." }.action)
        assertFalse(setupItems.any { it.label == "Imports" })
        assertFalse(setupItems.any { it.label == "Setup Exports" })
    }

    @Test
    fun setupToolsOwnCourseToolsAndCourseAnalyzer() {
        val tools = DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Tools" }
        val courseTools = tools.children.first { it.label == "Course Tools" }
        val courseAnalyzer = courseTools.children.first { it.label == "Course Analyzer" }

        assertEquals(DesktopSection.Tools, tools.section)
        assertEquals(DesktopSection.KmlTools, courseTools.section)
        assertFalse(tools.requiresEventFile)
        assertFalse(courseTools.requiresEventFile)
        assertEquals(
            listOf("Course Analyzer", "Move Course", "Classic Course Generator", "Foxoring Course Generator", "Sprint Course Generator"),
            courseTools.children.map { it.label }
        )
        assertEquals(DesktopSection.CourseAnalysis, courseAnalyzer.section)
        assertTrue(courseAnalyzer.requiresEventFile)
        assertEquals(
            listOf("Import Course KML/KMZ...", "Import Course GPX..."),
            courseAnalyzer.children.map { it.label }
        )
        val courseAnalyzerState = DesktopNavState()
            .enter(tools)
            .enter(courseTools)
            .enter(courseAnalyzer)
        assertEquals(
            listOf(
                "setup.tools",
                "setup.tools.course-tools",
                "setup.tools.course-tools.course-analysis"
            ),
            courseAnalyzerState.submenuStack
        )
        assertEquals(DesktopSection.CourseAnalysis, courseAnalyzerState.selectedSection)
        assertEquals(
            listOf("Import Course KML/KMZ...", "Import Course GPX..."),
            DesktopNavigation.currentItems(courseAnalyzerState).map { it.label }
        )
        assertEquals(DesktopNavAction.ImportCourseKmlKmz, courseAnalyzer.children.first().action)
        assertEquals(DesktopNavAction.ImportCourseGpx, courseAnalyzer.children.last().action)
        assertEquals(DesktopSection.KmlMoveCourse, courseTools.children.first { it.label == "Move Course" }.section)
        assertFalse(courseTools.children.first { it.label == "Move Course" }.requiresEventFile)
        assertEquals(
            DesktopSection.KmlClassicCourseGenerator,
            courseTools.children.first { it.label == "Classic Course Generator" }.section
        )
        assertFalse(courseTools.children.first { it.label == "Classic Course Generator" }.requiresEventFile)
        assertEquals(
            DesktopSection.KmlFoxoringCourseGenerator,
            courseTools.children.first { it.label == "Foxoring Course Generator" }.section
        )
        assertFalse(courseTools.children.first { it.label == "Foxoring Course Generator" }.requiresEventFile)
        assertEquals(
            DesktopSection.KmlSprintCourseGenerator,
            courseTools.children.first { it.label == "Sprint Course Generator" }.section
        )
        assertFalse(courseTools.children.first { it.label == "Sprint Course Generator" }.requiresEventFile)
    }

    @Test
    fun settingsRowsRemainAvailableWithoutAnOpenEventFile() {
        val raceOpsItems = DesktopNavigation.rootItems(DesktopWorkflow.RaceOps)
        val settingsItems = DesktopNavigation.rootItems(DesktopWorkflow.SettingsHelp)

        assertTrue(raceOpsItems.first { it.label == "Readouts" }.requiresEventFile)
        assertFalse(raceOpsItems.any { it.label == "Hardware Status" })
        assertFalse(settingsItems.first { it.label == "App Settings" }.requiresEventFile)
        assertFalse(settingsItems.first { it.label == "Hardware Preferences" }.requiresEventFile)
        assertFalse(settingsItems.first { it.label == "Help" }.requiresEventFile)
    }

    @Test
    fun androidEventFileActionsLiveInEventFileAndroidSubmenu() {
        val eventFileActions = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
            .first { it.label == "Event File" }
            .children
            .first { it.label == "Android..." }
            .children
        val resultJsonActions = DesktopNavigation.rootItems(DesktopWorkflow.ResultsExport)
            .first { it.label == "Exports" }
            .children
            .first { it.label == "JSON/XML" }
            .children

        assertEquals(
            "Save Android Event File...",
            eventFileActions.first { it.action == DesktopNavAction.ExportAndroidRaceBackupJson }.label
        )
        assertEquals(
            "Send Event to Android",
            eventFileActions.first { it.action == DesktopNavAction.SendEventFileToAndroid }.label
        )
        assertEquals(
            "Receive File from Android",
            eventFileActions.first { it.action == DesktopNavAction.ReceiveFileFromAndroid }.label
        )
        assertFalse(resultJsonActions.any { it.action == DesktopNavAction.ExportAndroidRaceBackupJson })
    }

    @Test
    fun androidEventFileSubmenuSupportsBackNavigation() {
        val eventFileState = DesktopNavigation.selectItem(
            DesktopNavState(),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Event File" }
        ).state
        val androidState = DesktopNavigation.selectItem(
            eventFileState,
            DesktopNavigation.currentItems(eventFileState).first { it.label == "Android..." }
        ).state

        assertEquals("Setup > Event File > Android...", DesktopNavigation.breadcrumb(androidState))
        assertEquals(
            listOf("setup.event-file", "setup.event-file.android"),
            androidState.submenuStack
        )
        assertEquals(androidEventFileMenuLabels, DesktopNavigation.currentItems(androidState).map { it.label })
        assertTrue(DesktopNavigation.canGoBack(androidState))

        val backState = androidState.back()

        assertEquals("Setup > Event File", DesktopNavigation.breadcrumb(backState))
        assertEquals(eventFileMenuLabels, DesktopNavigation.currentItems(backState).map { it.label })
    }

    @Test
    fun enteringEventFileMenuSelectsEventDetailsWorkspace() {
        val eventFile = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
            .first { it.label == "Event File" }

        val state = DesktopNavState().enter(eventFile)

        assertEquals(DesktopSection.Races, state.selectedSection)
        assertEquals("setup.event-file", state.selectedItemId)
        assertEquals("Setup > Event File", DesktopNavigation.breadcrumb(state))
    }

    @Test
    fun newEventFileActionSelectsEventDetailsWorkspaceForImmediateEditing() {
        val eventFileState = DesktopNavState().enter(
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Event File" }
        )
        val newEventFile = DesktopNavigation.currentItems(eventFileState)
            .first { it.action == DesktopNavAction.NewEventFile }

        val state = eventFileState.enter(newEventFile)

        assertEquals(DesktopSection.Races, state.selectedSection)
        assertEquals("setup.event-file.new", state.selectedItemId)
        assertEquals("Setup > Event File > New Event File", DesktopNavigation.breadcrumb(state))
        assertEquals(emptyList<String>(), DesktopNavigation.currentItems(state).map { it.label })
    }

    @Test
    fun selectingNewEventFileDispatchesCreateAction() {
        val eventFileState = DesktopNavigation.selectItem(
            DesktopNavState(),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Event File" }
        ).state
        val newEventFile = DesktopNavigation.currentItems(eventFileState)
            .first { it.action == DesktopNavAction.NewEventFile }

        val selection = DesktopNavigation.selectItem(eventFileState, newEventFile)

        assertEquals(DesktopNavAction.NewEventFile, selection.action)
        assertEquals(DesktopSection.Races, selection.state.selectedSection)
        assertEquals("setup.event-file.new", selection.state.selectedItemId)
    }

    @Test
    fun selectingNewEventFileHidesUnrelatedEventFileButtonsUntilBack() {
        val eventFileState = DesktopNavigation.selectItem(
            DesktopNavState(),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Event File" }
        ).state
        val state = DesktopNavigation.selectItem(
            eventFileState,
            DesktopNavigation.currentItems(eventFileState).first { it.label == "New Event File" }
        ).state

        assertEquals("Setup > Event File > New Event File", DesktopNavigation.breadcrumb(state))
        assertEquals(emptyList<String>(), DesktopNavigation.currentItems(state).map { it.label })

        val backState = state.back()

        assertEquals("Setup > Event File", DesktopNavigation.breadcrumb(backState))
        assertEquals(eventFileMenuLabels, DesktopNavigation.currentItems(backState).map { it.label })
    }

    @Test
    fun selectingSetupSectionLeafHidesSiblingButtonsUntilBack() {
        val categoriesState = DesktopNavigation.selectItem(
            DesktopNavState(),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Categories" }
        ).state
        val courseOrderState = DesktopNavigation.selectItem(
            categoriesState,
            DesktopNavigation.currentItems(categoriesState).first { it.label == "Course Order" }
        ).state

        assertEquals("Setup > Categories > Course Order", DesktopNavigation.breadcrumb(courseOrderState))
        assertEquals(emptyList<String>(), DesktopNavigation.currentItems(courseOrderState).map { it.label })

        val backState = courseOrderState.back()

        assertEquals("Setup > Categories", DesktopNavigation.breadcrumb(backState))
        assertEquals(
            listOf(
                "Course Order",
                "Import Categories CSV...",
                "Export Categories CSV...",
                "Delete All Control Assignments...",
                "Delete All Categories..."
            ),
            DesktopNavigation.currentItems(backState).map { it.label }
        )
    }

    @Test
    fun selectingNestedExportActionHidesSiblingExportButtonsUntilBack() {
        val resultsState = DesktopNavState().switchWorkflow(DesktopWorkflow.ResultsExport)
        val exportsState = DesktopNavigation.selectItem(
            resultsState,
            DesktopNavigation.currentItems(resultsState).first { it.label == "Exports" }
        ).state
        val jsonXmlState = DesktopNavigation.selectItem(
            exportsState,
            DesktopNavigation.currentItems(exportsState).first { it.label == "JSON/XML" }
        ).state
        val selectedExportState = DesktopNavigation.selectItem(
            jsonXmlState,
            DesktopNavigation.currentItems(jsonXmlState).first { it.label == "Export Live Results JSON..." }
        ).state

        assertEquals(
            "Results/File Export > Exports > JSON/XML > Export Live Results JSON...",
            DesktopNavigation.breadcrumb(selectedExportState)
        )
        assertEquals(emptyList<String>(), DesktopNavigation.currentItems(selectedExportState).map { it.label })

        val backState = selectedExportState.back()

        assertEquals("Results/File Export > Exports > JSON/XML", DesktopNavigation.breadcrumb(backState))
        assertEquals(
            listOf(
                "Export Live Results JSON...",
                "Export Final Results JSON...",
                "Export IOF Result List XML...",
                "Export ARDF JSON..."
            ),
            DesktopNavigation.currentItems(backState).map { it.label }
        )
    }

    @Test
    fun everyNavigationActionIsReachableFromMenuOrNamedPanelControl() {
        val reachableActions = DesktopWorkflow.entries
            .flatMap { workflow -> flatten(DesktopNavigation.rootItems(workflow)) }
            .mapNotNull { it.action }
            .toSet()
        val panelLocalActions = setOf(
            // Series Validation owns the results display, so its command belongs in that panel
            // instead of duplicating the same action in the left menu.
            DesktopNavAction.ValidateEventSeries
        )

        assertEquals(DesktopNavAction.entries.toSet(), reachableActions + panelLocalActions)
    }

    @Test
    fun liveResultsMenuExposesLocalWebServerWorkflow() {
        val liveResults = DesktopNavigation.rootItems(DesktopWorkflow.ResultsExport)
            .first { it.label == "Live Results" }
        val localWebServer = liveResults.children.first { it.label == "Local Web Server" }
        val robis = liveResults.children.first { it.label == "ROBIS" }

        assertEquals(
            listOf(
                "Local Web Server",
                "ROBIS"
            ),
            liveResults.children.map { it.label }
        )
        assertEquals(DesktopSection.LiveResultsOverview, liveResults.section)
        assertEquals(DesktopSection.LocalResultsWebServer, localWebServer.section)
        assertEquals(
            listOf(
                "Open Web Page",
                "Preview Web Page",
                "Start Web Server",
                "Stop Web Server"
            ),
            localWebServer.children.map { it.label }
        )
        assertEquals(
            listOf(
                DesktopNavAction.OpenLocalResultsWebPage,
                DesktopNavAction.PreviewLocalResultsWebPage,
                DesktopNavAction.StartLocalResultsWebServer,
                DesktopNavAction.StopLocalResultsWebServer
            ),
            localWebServer.children.mapNotNull { it.action }
        )
        assertEquals(DesktopSection.RobisLiveResults, robis.section)
        assertEquals(listOf("Send ROBIS"), robis.children.map { it.label })
        assertEquals(listOf(DesktopNavAction.SendRobis), robis.children.mapNotNull { it.action })
    }

    @Test
    fun cloudflareWebsiteMenuExposesPublicSiteWorkflow() {
        val exports = DesktopNavigation.rootItems(DesktopWorkflow.ResultsExport)
            .first { it.label == "Exports" }
            .children
        val resultFiles = exports
            .first { it.label == "Result Files" }
            .children
        val cloudflareWebsite = exports
            .first { it.label == "Cloudflare Website" }

        assertFalse(
            resultFiles
                .mapNotNull { it.action }
                .any {
                    it in setOf(
                        DesktopNavAction.GeneratePublicResultsSite,
                        DesktopNavAction.PublishPublicResultsSite,
                        DesktopNavAction.OpenPublicResultsSitePreview,
                        DesktopNavAction.StopPublicResultsSitePreview
                    )
                }
        )
        assertEquals(
            listOf(
                "Generate Public Results Site...",
                "Publish Public Results Site",
                "Public Site Preview",
                "View Public Results",
                "Cloudflare Settings"
            ),
            cloudflareWebsite.children.map { it.label }
        )
        assertEquals(
            listOf(
                "Open Public Site Preview",
                "Stop Public Site Preview"
            ),
            cloudflareWebsite.children.first { it.label == "Public Site Preview" }.children.map { it.label }
        )
        assertEquals(DesktopSection.PublicResultsSite, cloudflareWebsite.section)
        assertEquals(
            DesktopSection.PublicResultsLink,
            cloudflareWebsite.children.first { it.label == "View Public Results" }.section
        )
        assertEquals(
            listOf(
                DesktopNavAction.GeneratePublicResultsSite,
                DesktopNavAction.PublishPublicResultsSite,
                DesktopNavAction.OpenPublicResultsSitePreview,
                DesktopNavAction.StopPublicResultsSitePreview
            ),
            flatten(cloudflareWebsite.children)
                .mapNotNull { it.action }
                .filter {
                    it in setOf(
                        DesktopNavAction.GeneratePublicResultsSite,
                        DesktopNavAction.PublishPublicResultsSite,
                        DesktopNavAction.OpenPublicResultsSitePreview,
                        DesktopNavAction.StopPublicResultsSitePreview
                    )
                }
        )
        assertEquals(DesktopSection.Settings, cloudflareWebsite.children.first { it.label == "Cloudflare Settings" }.section)
        assertTrue(
            DesktopNavigation.selectedDescription(
                DesktopNavState(
                    workflow = DesktopWorkflow.ResultsExport,
                    submenuStack = listOf("results.exports", "results.exports.cloudflare-website"),
                    selectedSection = DesktopSection.PublicResultsSite,
                    selectedItemId = "results.exports.cloudflare-website"
                )
            ).contains("generate the public results site")
        )
    }

    @Test
    fun viewPublicResultsReturnsToCloudflareWebsiteMenuOnBack() {
        val resultsState = DesktopNavState().switchWorkflow(DesktopWorkflow.ResultsExport)
        val exportsState = DesktopNavigation.selectItem(
            resultsState,
            DesktopNavigation.currentItems(resultsState).first { it.label == "Exports" }
        ).state
        val cloudflareWebsiteState = DesktopNavigation.selectItem(
            exportsState,
            DesktopNavigation.currentItems(exportsState).first { it.label == "Cloudflare Website" }
        ).state
        val viewPublicResultsState = DesktopNavigation.selectItem(
            cloudflareWebsiteState,
            DesktopNavigation.currentItems(cloudflareWebsiteState).first { it.label == "View Public Results" }
        ).state

        assertEquals(
            "Results/File Export > Exports > Cloudflare Website > View Public Results",
            DesktopNavigation.breadcrumb(viewPublicResultsState)
        )
        assertEquals(DesktopSection.PublicResultsLink, viewPublicResultsState.selectedSection)
        assertEquals(emptyList<String>(), DesktopNavigation.currentItems(viewPublicResultsState).map { it.label })

        val backState = viewPublicResultsState.back()

        assertEquals("Results/File Export > Exports > Cloudflare Website", DesktopNavigation.breadcrumb(backState))
        assertEquals(DesktopSection.PublicResultsSite, backState.selectedSection)
        assertEquals(
            listOf(
                "Generate Public Results Site...",
                "Publish Public Results Site",
                "Public Site Preview",
                "View Public Results",
                "Cloudflare Settings"
            ),
            DesktopNavigation.currentItems(backState).map { it.label }
        )
    }

    @Test
    fun helpMenuExposesConcreteActions() {
        val helpActions = DesktopNavigation.rootItems(DesktopWorkflow.SettingsHelp)
            .first { it.label == "Help" }
            .children

        assertEquals(DesktopSection.Settings, helpActions.first { it.label == "Beta Scope" }.section)
        assertEquals(DesktopNavAction.ShowDebugLogHelp, helpActions.first { it.label == "Logs..." }.action)
        assertEquals(DesktopNavAction.ShowAbout, helpActions.first { it.label == "About Radio-Oracle..." }.action)
        assertFalse(helpActions.first { it.label == "Logs..." }.requiresEventFile)
        assertFalse(helpActions.first { it.label == "About Radio-Oracle..." }.requiresEventFile)
    }

    @Test
    fun actionSelectionUpdatesBreadcrumbWithoutChangingContentSection() {
        val state = DesktopNavState()
            .switchWorkflow(DesktopWorkflow.SettingsHelp)
            .enter(DesktopNavigation.rootItems(DesktopWorkflow.SettingsHelp).first { it.label == "Help" })
            .enter(DesktopNavigation.currentItems(
                DesktopNavState(
                    workflow = DesktopWorkflow.SettingsHelp,
                    submenuStack = listOf("settings.help"),
                    selectedSection = DesktopSection.Settings,
                    selectedItemId = "settings.app"
                )
            ).first { it.label == "About Radio-Oracle..." })

        assertEquals(DesktopSection.Settings, state.selectedSection)
        assertEquals("settings.about", state.selectedItemId)
        assertEquals("About Radio-Oracle...", DesktopNavigation.selectedLabel(state))
        assertEquals("Help/About/App Settings > Help > About Radio-Oracle...", DesktopNavigation.breadcrumb(state))
    }

    private fun assertNoRepeatedParentChildLabels(items: List<DesktopNavItem>) {
        items.forEach { item ->
            assertFalse(
                "Submenu '${item.label}' should not contain a child with the same label.",
                item.children.any { child -> child.label == item.label }
            )
            assertNoRepeatedParentChildLabels(item.children)
        }
    }

    private fun assertNoRedundantViewOnlyChildren(items: List<DesktopNavItem>) {
        items.forEach { item ->
            assertFalse(
                "Submenu '${item.label}' should not contain a view-only child for its own section.",
                item.id != "setup.event-file.settings" &&
                    item.section != null &&
                    item.children.any { child ->
                        child.action == null &&
                            child.children.isEmpty() &&
                            child.section == item.section
                    }
            )
            assertNoRedundantViewOnlyChildren(item.children)
        }
    }

    private fun flatten(items: List<DesktopNavItem>): List<DesktopNavItem> =
        items + items.flatMap { flatten(it.children) }
}
