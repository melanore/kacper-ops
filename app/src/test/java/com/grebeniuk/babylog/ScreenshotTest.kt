package com.grebeniuk.babylog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.MainScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the main screens to PNGs (CI uploads them) so the UI can be reviewed without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun sample(withData: Boolean = true): AppController {
        val c = AppController(null, MainScope())
        if (!withData) return c
        c.setMe("p-dad")
        c.renameProfile("p-dad", "Max")
        val n = System.currentTimeMillis()
        fun add(type: String, minutesAgo: Long, by: String = "p-dad", side: String? = null, ml: Int? = null) {
            val e = c.log(type, floor10(n - minutesAgo * MIN), side, ml)
            c.update(e.copy(by = by))
        }
        add(Kind.FEED, 25, side = "L")
        add(Kind.PEE, 40, by = "p-mom")
        add(Kind.BOTH, 130)
        add(Kind.FEED, 190, by = "p-mom", side = "bottle", ml = 60)
        add(Kind.PEE, 260)
        add(Kind.FEED, 340, side = "R")
        add(Kind.POOP, 1500, by = "p-mom")
        add(Kind.FEED, 1560, side = "LR")
        c.now = n
        return c
    }

    private fun shot(name: String, dark: Boolean, lang: String = "en", content: @Composable () -> Unit) {
        rule.setContent {
            AppTheme(dark = dark, dynamic = false) {
                CompositionLocalProvider(LocalStrings provides Strings(lang)) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun mainLight() { val c = sample(); shot("1_home_light", false) { MainScreen(c, {}, {}) } }
    @Test fun mainDark() { val c = sample(); shot("2_home_dark", true) { MainScreen(c, {}, {}) } }
    @Test fun logPee() { val c = sample(); shot("3_step2_pee_dark", true) { LogScreen(c, Kind.PEE, {}) } }
    @Test fun logFeedCustomPl() { val c = sample(); shot("4_step2_feed_custom_pl", false, "pl") { LogScreen(c, Kind.FEED, {}, startCustom = true) } }
    @Test fun settingsDark() { val c = sample(); shot("5_settings_dark", true) { SettingsScreen(c) {} } }
    @Test fun welcomeLight() { val c = sample(false); shot("6_welcome_uk", false, "uk") { WelcomeScreen(c) } }
}
