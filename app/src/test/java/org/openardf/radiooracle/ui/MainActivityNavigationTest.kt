package org.openardf.radiooracle.ui

import androidx.navigation.fragment.NavHostFragment
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityNavigationTest {
    @Test
    fun `cold start attaches the navigation controller`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val navHost = activity.supportFragmentManager.findFragmentById(
                R.id.nav_host_fragment_activity_main
            ) as NavHostFragment

            assertNotNull(navHost.navController.currentDestination)
        } finally {
            controller.destroy()
        }
    }
}
