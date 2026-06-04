package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopNavigationTest {
    @Test
    fun exposesWorkflowGroupsInBottomNavigationOrder() {
        assertEquals(
            listOf("Preparation/Setup", "Race Operations", "Results/File Export", "Help/About/App Settings"),
            DesktopWorkflow.entries.map { it.label }
        )
    }

    @Test
    fun placesCurrentDesktopSectionsUnderWorkflowGroups() {
        assertEquals(
            listOf("Event File", "Categories", "Competitors", "Start List", "Aliases", "Imports", "Setup Exports", "Utils"),
            DesktopNavigation.rootItems(DesktopWorkflow.Setup).map { it.label }
        )
        assertEquals(
            listOf("Readouts", "SI Readout", "In Forest", "Unmatched Readouts", "Finish Tickets", "Hardware Status"),
            DesktopNavigation.rootItems(DesktopWorkflow.RaceOps).map { it.label }
        )
        assertEquals(
            listOf("Results", "Live Results", "Exports"),
            DesktopNavigation.rootItems(DesktopWorkflow.ResultsExport).map { it.label }
        )
        assertEquals(
            listOf("App Settings", "Hardware Preferences", "Help"),
            DesktopNavigation.rootItems(DesktopWorkflow.SettingsHelp).map { it.label }
        )
    }

    @Test
    fun buildsBreadcrumbForSectionAndSubmenuSelection() {
        val state = DesktopNavState()
            .enter(DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Start List" })
            .enter(DesktopNavigation.currentItems(DesktopNavState(submenuStack = listOf("setup.start-list")))
                .first { it.label == "Exports" })

        assertEquals(
            "Preparation/Setup > Start List > Exports",
            DesktopNavigation.breadcrumb(state)
        )
    }

    @Test
    fun submenuStackStopsAtTwoLevels() {
        val startList = DesktopNavigation.rootItems(DesktopWorkflow.Setup).first { it.label == "Start List" }
        val firstLevel = DesktopNavState().enter(startList)
        val exports = DesktopNavigation.currentItems(firstLevel).first { it.label == "Exports" }
        val secondLevel = firstLevel.enter(exports)
        val blocked = secondLevel.enter(
            DesktopNavItem(
                id = "test.third",
                label = "Third",
                workflow = DesktopWorkflow.Setup,
                children = listOf(DesktopNavItem("test.leaf", "Leaf", DesktopWorkflow.Setup))
            )
        )

        assertEquals(listOf("setup.start-list", "setup.start-list.exports"), secondLevel.submenuStack)
        assertEquals(secondLevel, blocked)
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
    fun defaultStartupStateShowsWorkflowHome() {
        val state = DesktopNavState()

        assertEquals(DesktopWorkflow.Setup, state.workflow)
        assertEquals(DesktopSection.WorkflowHome, state.selectedSection)
        assertEquals("setup.home", state.selectedItemId)
        assertEquals("Preparation/Setup", DesktopNavigation.selectedLabel(state))
        assertEquals("Preparation/Setup", DesktopNavigation.breadcrumb(state))
    }

    @Test
    fun backFromFirstLevelSubmenuReturnsToWorkflowHome() {
        val eventFile = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
            .first { it.label == "Event File" }
        val state = DesktopNavState().enter(eventFile).back()

        assertTrue(state.submenuStack.isEmpty())
        assertEquals(DesktopSection.WorkflowHome, state.selectedSection)
        assertEquals("setup.home", state.selectedItemId)
        assertEquals("Preparation/Setup", DesktopNavigation.breadcrumb(state))
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
        assertEquals("Preparation/Setup > Start List", DesktopNavigation.breadcrumb(state))
        assertEquals(
            listOf("Start List", "Import Starts CSV...", "Exports"),
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
        assertEquals("Preparation/Setup > Event File", DesktopNavigation.breadcrumb(state))
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
        assertFalse(eventFileActions.first { it.action == DesktopNavAction.ImportAndroidRaceBackup }.requiresEventFile)
        assertTrue(eventFileActions.first { it.action == DesktopNavAction.ExportAndroidRaceBackupJson }.requiresEventFile)
        assertTrue(eventFileActions.first { it.action == DesktopNavAction.SaveEventFile }.requiresEventFile)
        assertEquals(
            listOf("New Event File", "Open...", "Import Android Event File...", "Export Android Event File...", "Save"),
            eventFileActions.map { it.label }
        )
    }

    @Test
    fun setupRowsDeclareWhichButtonsRequireAnOpenEventFile() {
        val setupItems = DesktopNavigation.rootItems(DesktopWorkflow.Setup)

        assertFalse(setupItems.first { it.label == "Event File" }.requiresEventFile)
        assertTrue(setupItems.first { it.label == "Categories" }.requiresEventFile)
        assertTrue(setupItems.first { it.label == "Competitors" }.requiresEventFile)
        assertTrue(setupItems.first { it.label == "Start List" }.requiresEventFile)
        assertTrue(setupItems.first { it.label == "Aliases" }.requiresEventFile)
        assertTrue(setupItems.first { it.label == "Imports" }.requiresEventFile)
        assertTrue(setupItems.first { it.label == "Setup Exports" }.requiresEventFile)
        assertTrue(setupItems.first { it.label == "Utils" }.requiresEventFile)
    }

    @Test
    fun settingsAndHardwareRowsRemainAvailableWithoutAnOpenEventFile() {
        val raceOpsItems = DesktopNavigation.rootItems(DesktopWorkflow.RaceOps)
        val settingsItems = DesktopNavigation.rootItems(DesktopWorkflow.SettingsHelp)

        assertTrue(raceOpsItems.first { it.label == "Readouts" }.requiresEventFile)
        assertFalse(raceOpsItems.first { it.label == "Hardware Status" }.requiresEventFile)
        assertFalse(settingsItems.first { it.label == "App Settings" }.requiresEventFile)
        assertFalse(settingsItems.first { it.label == "Hardware Preferences" }.requiresEventFile)
        assertFalse(settingsItems.first { it.label == "Help" }.requiresEventFile)
    }

    @Test
    fun androidEventFileExportLivesWithEventFileActions() {
        val eventFileActions = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
            .first { it.label == "Event File" }
            .children
        val resultJsonActions = DesktopNavigation.rootItems(DesktopWorkflow.ResultsExport)
            .first { it.label == "Exports" }
            .children
            .first { it.label == "JSON/XML" }
            .children

        assertEquals(
            "Export Android Event File...",
            eventFileActions.first { it.action == DesktopNavAction.ExportAndroidRaceBackupJson }.label
        )
        assertFalse(resultJsonActions.any { it.action == DesktopNavAction.ExportAndroidRaceBackupJson })
    }

    @Test
    fun enteringEventFileMenuSelectsEventDetailsWorkspace() {
        val eventFile = DesktopNavigation.rootItems(DesktopWorkflow.Setup)
            .first { it.label == "Event File" }

        val state = DesktopNavState().enter(eventFile)

        assertEquals(DesktopSection.Races, state.selectedSection)
        assertEquals("setup.event-file", state.selectedItemId)
        assertEquals("Preparation/Setup > Event File", DesktopNavigation.breadcrumb(state))
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
        assertEquals("Preparation/Setup > Event File > New Event File", DesktopNavigation.breadcrumb(state))
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
    fun helpMenuExposesConcreteActions() {
        val helpActions = DesktopNavigation.rootItems(DesktopWorkflow.SettingsHelp)
            .first { it.label == "Help" }
            .children

        assertEquals(DesktopSection.Settings, helpActions.first { it.label == "Beta Scope" }.section)
        assertEquals(DesktopNavAction.ShowDebugLogHelp, helpActions.first { it.label == "Logs" }.action)
        assertEquals(DesktopNavAction.ShowAbout, helpActions.first { it.label == "About Radio-Oracle" }.action)
        assertFalse(helpActions.first { it.label == "Logs" }.requiresEventFile)
        assertFalse(helpActions.first { it.label == "About Radio-Oracle" }.requiresEventFile)
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
            ).first { it.label == "About Radio-Oracle" })

        assertEquals(DesktopSection.Settings, state.selectedSection)
        assertEquals("settings.about", state.selectedItemId)
        assertEquals("About Radio-Oracle", DesktopNavigation.selectedLabel(state))
        assertEquals("Help/About/App Settings > Help > About Radio-Oracle", DesktopNavigation.breadcrumb(state))
    }
}
