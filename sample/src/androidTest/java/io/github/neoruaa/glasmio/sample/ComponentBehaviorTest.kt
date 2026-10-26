package io.github.neoruaa.glasmio.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentBehaviorTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun content(body: @Composable () -> Unit) {
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = { Box(Modifier.fillMaxSize().background(Color.White)) },
                    overlayContent = {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            body()
                        }
                    },
                )
            }
        }
    }

    @Test fun sampleLaunchesAndNavigatesWithItsRealAssets() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rule.waitUntil(30_000) {
                rule.onAllNodesWithText("Glasmio").fetchSemanticsNodes().isNotEmpty()
            }
            val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            rule.onNode(tab and hasContentDescription("材质")).performClick()
            rule.onNodeWithText("Regular Small").assertIsDisplayed()
            rule.onNode(tab and hasContentDescription("关于")).performClick().assertIsSelected()
            rule.onNodeWithText("Apache License 2.0").assertIsDisplayed()
            scenario.recreate()
            rule.waitUntil(30_000) {
                rule.onAllNodesWithText("Apache License 2.0").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNode(tab and hasContentDescription("关于")).assertIsSelected()
            rule.onNodeWithContentDescription("搜索").performClick()
            rule.onNodeWithText("Search Components").assertIsDisplayed()
            pressBack()
            rule.onNode(tab and hasContentDescription("关于")).assertIsSelected()
        }
    }

    @Test fun switchAndDisabledControlsExposeAndRespectTheirState() {
        var disabledCalls = 0
        content {
            var checked by remember { mutableStateOf(false) }
            GlasmioSwitch(checked, { checked = it }, Modifier.testTag("switch"))
            GlasmioSwitch(false, { disabledCalls++ }, Modifier.testTag("disabled-switch"), enabled = false)
            GlasmioButton({ disabledCalls++ }, Modifier.testTag("disabled-button"), enabled = false) { Text("Disabled") }
        }
        rule.onNodeWithTag("switch").assertIsOff().performClick().assertIsOn()
        rule.onNodeWithTag("disabled-switch").assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithTag("disabled-button").assertIsNotEnabled()
            .performTouchInput { click() }
        rule.runOnIdle { assertEquals(0, disabledCalls) }
    }

    @Test fun sliderSupportsAccessibilityAdjustmentAndRtlPointerInput() {
        var value = 0.5f
        var finished = 0
        var disabledCalls = 0
        content {
            var current by remember { mutableFloatStateOf(0.5f) }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                GlasmioSlider(current, { current = it; value = it }, Modifier.testTag("slider"), onValueChangeFinished = { finished++ })
            }
            GlasmioSlider(0.25f, { disabledCalls++ }, Modifier.testTag("disabled-slider"), enabled = false)
        }
        rule.onNodeWithTag("slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        rule.runOnIdle { assertEquals(0.75f, value, 0.001f); assertEquals(1, finished) }
        rule.onNodeWithTag("slider").performTouchInput {
            click(Offset(width * 0.1f, height / 2f))
        }
        rule.runOnIdle { assertTrue(value > 0.8f); assertTrue(finished >= 2) }
        rule.onNodeWithTag("disabled-slider").assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.9f) }
        rule.runOnIdle { assertEquals(0, disabledCalls) }
    }

    @Test fun stepperStopsAtRangeAndIntegerLimits() {
        content {
            var bounded by remember { mutableIntStateOf(0) }
            GlasmioStepper(bounded, { bounded = it }, range = 0..1,
                decreaseContentDescription = "Bounded decrease", increaseContentDescription = "Bounded increase")
            var maximum by remember { mutableIntStateOf(Int.MAX_VALUE - 1) }
            GlasmioStepper(maximum, { maximum = it },
                decreaseContentDescription = "Maximum decrease", increaseContentDescription = "Maximum increase")
            var minimum by remember { mutableIntStateOf(Int.MIN_VALUE + 1) }
            GlasmioStepper(minimum, { minimum = it },
                decreaseContentDescription = "Minimum decrease", increaseContentDescription = "Minimum increase")
        }
        rule.onNodeWithContentDescription("Bounded decrease").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Bounded increase").performClick().assertIsNotEnabled()
        rule.onNodeWithContentDescription("Bounded decrease").performClick().assertIsNotEnabled()
        rule.onNodeWithContentDescription("Maximum increase").performClick().assertIsNotEnabled()
        rule.onNodeWithContentDescription("Minimum decrease").performClick().assertIsNotEnabled()
    }

    @Test fun searchEditsSubmitsAndKeepsDisabledInputInactive() {
        var submitted = ""
        var disabledActivations = 0
        content {
            var query by remember { mutableStateOf(TextFieldValue()) }
            var active by remember { mutableStateOf(true) }
            GlasmioSearchBar(query, { query = it }, active, { active = it }, { active = false },
                "Search", "Close search", Modifier.testTag("search"), onSearch = { submitted = it })
            GlasmioSearchBar(TextFieldValue(), {}, false, { disabledActivations++ }, {},
                "Disabled search", "Close disabled search", Modifier.testTag("disabled-search"), enabled = false)
        }
        val editable = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)
        val input = rule.onNode(hasAnyAncestor(hasTestTag("search")) and editable)
        input.performTextInput("Liquid glass")
        input.performImeAction()
        rule.runOnIdle { assertEquals("Liquid glass", submitted) }
        rule.onNode(hasAnyAncestor(hasTestTag("disabled-search")) and editable).assertIsNotEnabled()
        rule.onNodeWithTag("disabled-search").performTouchInput { click() }
        rule.runOnIdle { assertEquals(0, disabledActivations) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun systemBackDismissesAlertAndSheet() {
        content {
            var stage by remember { mutableIntStateOf(0) }
            if (stage == 0) {
                GlasmioAlertDialog(onDismissRequest = { stage = 1 }, title = "Alert fixture")
            }
            if (stage == 1) {
                GlasmioBottomSheet(onDismissRequest = { stage = 2 }) { Text("Sheet fixture") }
            }
            if (stage == 2) Text("Dismissed fixture")
        }
        rule.onNodeWithText("Alert fixture").assertIsDisplayed()
        pressBack()
        rule.onNodeWithText("Alert fixture").assertDoesNotExist()
        rule.onNodeWithText("Sheet fixture").assertIsDisplayed()
        pressBack()
        rule.onNodeWithText("Sheet fixture").assertDoesNotExist()
        rule.onNodeWithText("Dismissed fixture").assertIsDisplayed()
    }
}
