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
            listOf("Event File", "Race", "Categories", "Competitors", "Start List", "Aliases", "Imports", "Setup Exports", "Utils"),
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
        assertEquals(DesktopSection.Readouts, state.selectedSection)
        assertEquals("race.readouts", state.selectedItemId)
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
        assertTrue(eventFileActions.first { it.action == DesktopNavAction.SaveEventFile }.requiresEventFile)
        assertTrue(eventFileActions.first { it.action == DesktopNavAction.CloseEventFile }.requiresEventFile)
    }
}
