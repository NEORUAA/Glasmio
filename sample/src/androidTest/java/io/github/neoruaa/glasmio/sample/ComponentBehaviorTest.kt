package io.github.neoruaa.glasmio.sample

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.ui.*
import io.github.neoruaa.glasmio.preference.GlasmioEditTextPreference
import io.github.neoruaa.glasmio.preference.GlasmioListPreference
import io.github.neoruaa.glasmio.preference.GlasmioPreferenceEntry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deliver an actual Back key to the focused window without Espresso root selection. */
private fun pressSystemBack(rule: ComposeContentTestRule) {
    rule.waitForIdle()
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    rule.waitForIdle()
}

@RunWith(AndroidJUnit4::class)
class ComponentBehaviorTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun content(body: @Composable () -> Unit) {
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = { Box(Modifier.fillMaxSize().background(Color.White)) },
                    overlayContent = {
                        GlasmioTextSelectionHost {
                            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                body()
                            }
                        }
                    },
                )
            }
        }
        // Dialog fixtures can own focus before the activity's window does.
        rule.waitUntil(10_000) {
            rule.onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun editingContent(
        additionalItems: List<GlasmioCascadingMenuItem> = emptyList(),
        body: @Composable () -> Unit,
    ) {
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = { Box(Modifier.fillMaxSize().background(Color.White)) },
                    overlayContent = {
                        GlasmioTextSelectionHost(additionalItems = additionalItems) {
                            Column(
                                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 160.dp),
                                verticalArrangement = Arrangement.spacedBy(48.dp),
                            ) { body() }
                        }
                    },
                )
            }
        }
        rule.waitUntil(10_000) { rule.activity.hasWindowFocus() }
    }

    private fun editingAction(english: String, resource: Int): SemanticsMatcher =
        (hasText(english) or hasText(rule.activity.getString(resource))) and hasClickAction()

    private fun longPressWord(tag: String, character: Int = 1): TextLayoutResult {
        val field = rule.onNodeWithTag(tag)
        val layouts = mutableListOf<TextLayoutResult>()
        field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val point = layouts.single().getBoundingBox(character).center
        field.performTouchInput { longClick(point) }
        val copy = editingAction("Copy", android.R.string.copy)
        rule.waitUntil(10_000) { rule.onAllNodes(copy).fetchSemanticsNodes().isNotEmpty() }
        return layouts.single()
    }

    @Test fun selectionMenuCopiesCutsAndPastesThroughTheRealClipboard() {
        var source by mutableStateOf(TextFieldValue("Hello World"))
        var destination by mutableStateOf(TextFieldValue("Replace me"))
        val clipboard = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var previousClip: ClipData? = null
        rule.runOnIdle {
            previousClip = clipboard.primaryClip
            clipboard.setPrimaryClip(ClipData.newPlainText("Editing fixture", "Seed"))
        }
        try {
            editingContent {
                GlasmioEditableText(source, { source = it }, Modifier.fillMaxWidth().testTag("copy-source"))
                GlasmioEditableText(destination, { destination = it }, Modifier.fillMaxWidth().testTag("paste-target"))
            }
            val sourceLayout = longPressWord("copy-source")
            rule.runOnIdle { assertEquals(TextRange(0, 5), source.selection) }
            val sourceBounds = rule.onNodeWithTag("copy-source").fetchSemanticsNode().boundsInRoot
            val cutBounds = rule.onNode(editingAction("Cut", android.R.string.cut)).fetchSemanticsNode().boundsInRoot
            assertEquals(sourceBounds.left + with(rule.density) { 20.dp.toPx() }, cutBounds.left, 1f)
            assertEquals(sourceBounds.top + sourceLayout.getCursorRect(0).bottom + with(rule.density) { 24.dp.toPx() }, cutBounds.top, 1f)
            rule.onNode(editingAction("Copy", android.R.string.copy)).performClick()
            rule.runOnIdle { assertEquals("Hello", clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }

            longPressWord("copy-source")
            rule.onNode(editingAction("Cut", android.R.string.cut)).performClick()
            rule.waitUntil(10_000) { source.text == " World" }
            rule.runOnIdle { assertEquals("Hello", clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }

            longPressWord("paste-target")
            rule.onNode(editingAction("Paste", android.R.string.paste)).performClick()
            rule.waitUntil(10_000) { destination.text == "Hello me" }
            rule.runOnIdle {
                assertEquals(TextRange(5), destination.selection)
                assertEquals(" World", source.text)
            }
        } finally {
            rule.runOnIdle {
                previousClip?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip()
            }
        }
    }

    @Test fun expandedSelectionMenuKeepsSelectAllHandlesAndRunsItsEditingCallback() {
        var value by mutableStateOf(TextFieldValue("Hello World"))
        var replacements = 0
        val replacement = GlasmioCascadingMenuItem("Replace selection", onClick = {
            val range = value.selection
            value = TextFieldValue(value.text.replaceRange(range.min, range.max, "Welcome"), TextRange(range.min + 7))
            replacements++
        })
        editingContent(additionalItems = listOf(replacement)) {
            GlasmioEditableText(value, { value = it }, Modifier.fillMaxWidth().testTag("expanded-editor"))
        }
        longPressWord("expanded-editor")
        rule.onNodeWithContentDescription("More").performClick()
        val selectAll = editingAction("Select All", android.R.string.selectAll)
        rule.onNode(selectAll).assertIsDisplayed().performClick()
        rule.waitUntil(10_000) {
            value.selection.min == 0 && value.selection.max == value.text.length &&
                rule.onAllNodesWithText("Replace selection").fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithContentDescription("Selection start").assertIsDisplayed()
        rule.onNodeWithContentDescription("Selection end").assertIsDisplayed()
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Replace selection").assertIsDisplayed().performClick()
        rule.waitUntil(10_000) { value.text == "Welcome" }
        rule.runOnIdle { assertEquals(1, replacements) }
    }

    @Test fun selectionHandlesChangeTheRealRangeAndSurviveCrossingTheOtherEndpoint() {
        var value by mutableStateOf(TextFieldValue("Hello World"))
        editingContent {
            GlasmioEditableText(value, { value = it }, Modifier.fillMaxWidth().testTag("handle-editor"))
        }
        longPressWord("handle-editor")
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("handle-editor").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.single()
        val startX = text.getCursorRect(0).left
        val endX = text.getCursorRect(5).left
        val fullX = text.getCursorRect(11).left
        val endHandle = rule.onNodeWithContentDescription("Selection end").assertIsDisplayed()
        // Cross the start endpoint, then move out again in the same real pointer stream.
        endHandle.performTouchInput {
            down(center)
            moveBy(Offset(startX - endX, 0f), delayMillis = 160)
            moveBy(Offset(fullX - startX, 0f), delayMillis = 240)
            up()
        }
        rule.waitUntil(10_000) { value.selection.min == 0 && value.selection.max == 11 }
        rule.onNodeWithContentDescription("Selection start").assertIsDisplayed()
        rule.onNodeWithContentDescription("Selection end").assertIsDisplayed()
        rule.runOnIdle { assertEquals("Hello World", value.text) }
    }

    @Test fun selectionMenuFitsBelowTopAndAboveBottomSafeDrawingEditors() {
        var upper by mutableStateOf(TextFieldValue("Upper edge"))
        var lower by mutableStateOf(TextFieldValue("Lower edge"))
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = { Box(Modifier.fillMaxSize().background(Color.White)) },
                    overlayContent = {
                        GlasmioTextSelectionHost {
                            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                                Box(Modifier.fillMaxSize().testTag("safe-editor-viewport")) {
                                    GlasmioEditableText(
                                        upper, { upper = it },
                                        Modifier.align(Alignment.TopStart).fillMaxWidth().testTag("upper-edge-editor"),
                                        keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                                    )
                                    GlasmioEditableText(
                                        lower, { lower = it },
                                        Modifier.align(Alignment.BottomStart).fillMaxWidth().testTag("lower-edge-editor"),
                                        keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
        rule.waitUntil(10_000) { rule.activity.hasWindowFocus() }
        val clipboard = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var previousClip: ClipData? = null
        rule.runOnIdle { previousClip = clipboard.primaryClip }
        try {
            val copy = editingAction("Copy", android.R.string.copy)
            longPressWord("upper-edge-editor")
            val upperBounds = rule.onNodeWithTag("upper-edge-editor").fetchSemanticsNode().boundsInRoot
            val upperCopy = rule.onNode(copy).assertIsDisplayed().assertHasClickAction()
            assertTrue("The top-edge selection menu must fit below its editor",
                upperCopy.fetchSemanticsNode().boundsInRoot.top >= upperBounds.bottom - 1f)
            rule.runOnIdle { assertEquals(TextRange(0, 5), upper.selection) }
            upperCopy.performClick()
            rule.runOnIdle { assertEquals("Upper", clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }

            longPressWord("lower-edge-editor")
            val lowerBounds = rule.onNodeWithTag("lower-edge-editor").fetchSemanticsNode().boundsInRoot
            val viewport = rule.onNodeWithTag("safe-editor-viewport").fetchSemanticsNode().boundsInRoot
            val lowerCopy = rule.onNode(copy).assertIsDisplayed().assertHasClickAction()
            val menuBounds = lowerCopy.fetchSemanticsNode().boundsInRoot
            assertTrue("The bottom-edge selection menu must fit above its editor", menuBounds.bottom <= lowerBounds.top + 1f)
            assertTrue("The copy action must remain inside the safe drawing viewport",
                menuBounds.top >= viewport.top - 1f && menuBounds.bottom <= viewport.bottom + 1f)
            rule.runOnIdle { assertEquals(TextRange(0, 5), lower.selection) }
            lowerCopy.performClick()
            rule.runOnIdle {
                assertEquals("Lower", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                assertEquals("Upper edge", upper.text)
                assertEquals("Lower edge", lower.text)
            }
        } finally {
            rule.runOnIdle { previousClip?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip() }
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

    @Test fun alertRetainsItsContentUntilTheExitAnimationFinishes() {
        var visible by mutableStateOf(false)
        var mounts = 0
        var disposals = 0
        content {
            GlasmioAlertDialog(
                visible = visible,
                onDismissRequest = { visible = false },
                title = "Retained alert",
                buttons = listOf(GlasmioAlertButtonSpec("Close retained alert", { visible = false })),
            ) {
                DisposableEffect(Unit) {
                    mounts++
                    onDispose { disposals++ }
                }
            }
        }
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.runOnIdle { visible = true }
        rule.onNodeWithText("Retained alert").assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, mounts); assertEquals(0, disposals) }

        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithText("Close retained alert").performClick()
            rule.mainClock.advanceTimeBy(160)
            rule.onNode(isDialog()).assertExists()
            rule.onNodeWithText("Close retained alert").assertDoesNotExist()
            rule.runOnIdle { assertEquals(0, disposals) }

            rule.mainClock.advanceTimeBy(1_000)
            rule.onNode(isDialog()).assertDoesNotExist()
            rule.runOnIdle { assertEquals(1, mounts); assertEquals(1, disposals) }
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    @Test fun reopeningAlertDuringExitKeepsTheSameContentAndCancelsRemoval() {
        var visible by mutableStateOf(false)
        var mounts = 0
        var disposals = 0
        var dismissals = 0
        content {
            GlasmioAlertDialog(
                visible = visible,
                onDismissRequest = { dismissals++; visible = false },
                title = "Interruptible alert",
                buttons = listOf(GlasmioAlertButtonSpec("Close interruptible alert", {
                    dismissals++
                    visible = false
                })),
            ) {
                DisposableEffect(Unit) {
                    mounts++
                    onDispose { disposals++ }
                }
            }
        }
        rule.runOnIdle { visible = true }
        rule.onNodeWithText("Interruptible alert").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithText("Close interruptible alert").performClick()
            rule.mainClock.advanceTimeBy(128)
            rule.runOnIdle { visible = true }
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithText("Interruptible alert").assertExists()
            rule.onNodeWithText("Close interruptible alert").assertIsEnabled()
            rule.mainClock.advanceTimeBy(1_000)
            rule.onNodeWithText("Interruptible alert").assertIsDisplayed()
            rule.runOnIdle {
                assertEquals(1, mounts)
                assertEquals(0, disposals)
                assertEquals(1, dismissals)
            }

            rule.runOnIdle { visible = false }
            rule.mainClock.advanceTimeBy(1_000)
            rule.onNode(isDialog()).assertDoesNotExist()
            rule.runOnIdle { assertEquals(1, disposals); assertEquals(1, dismissals) }
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    @Test fun preferenceEditorSubmitsOncePerOpeningEvenWhileItsExitIsRetained() {
        var stored = "Initial name"
        val commits = mutableListOf<String>()
        content {
            var value by remember { mutableStateOf(stored) }
            GlasmioEditTextPreference(
                title = { Text("Repeated editor") },
                value = value,
                onValueChange = { value = it; stored = it; commits += it },
            )
        }
        val editable = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)
        fun editor() = rule.onNode(editable and hasAnyAncestor(isDialog()))

        rule.onNodeWithText("Repeated editor").performClick()
        editor().assertTextEquals("Initial name").performTextReplacement("First committed name")
        val firstSubmit = checkNotNull(editor().fetchSemanticsNode().config[SemanticsActions.OnImeAction].action)
        rule.mainClock.autoAdvance = false
        try {
            rule.runOnIdle { firstSubmit(); firstSubmit() }
            rule.mainClock.advanceTimeBy(128)
            rule.onNode(isDialog()).assertExists()
            rule.runOnIdle { assertEquals(listOf("First committed name"), commits) }
            rule.mainClock.advanceTimeBy(1_000)
            rule.onNode(isDialog()).assertDoesNotExist()
        } finally {
            rule.mainClock.autoAdvance = true
        }

        rule.onNodeWithText("Repeated editor").performClick()
        editor().assertTextEquals("First committed name").performTextReplacement("Second committed name")
        val secondSubmit = checkNotNull(editor().fetchSemanticsNode().config[SemanticsActions.OnImeAction].action)
        rule.mainClock.autoAdvance = false
        try {
            rule.runOnIdle { secondSubmit(); secondSubmit() }
            rule.mainClock.advanceTimeBy(1_000)
            rule.onNode(isDialog()).assertDoesNotExist()
            rule.runOnIdle {
                assertEquals("Second committed name", stored)
                assertEquals(listOf("First committed name", "Second committed name"), commits)
            }
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    @Test fun preferenceEditorKeepsItsDraftUntilValidConfirmation() {
        var stored = "Initial name"
        var commits = 0
        content {
            var value by remember { mutableStateOf(stored) }
            GlasmioEditTextPreference(
                title = { Text("Display name") },
                value = value,
                onValueChange = { value = it; stored = it; commits++ },
                isInputValid = { it.isNotBlank() },
            )
        }
        val cancel = rule.activity.getString(android.R.string.cancel)
        val confirm = rule.activity.getString(android.R.string.ok)
        val editable = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)
        fun editor() = rule.onNode(editable and hasAnyAncestor(isDialog()))

        rule.onNodeWithText("Display name").performClick()
        rule.onNode(hasText("Display name") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        editor().assertIsDisplayed().performTextReplacement("Uncommitted draft")
        rule.onNodeWithText(cancel).performClick()
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.runOnIdle { assertEquals("Initial name", stored); assertEquals(0, commits) }

        rule.onNodeWithText("Display name").performClick()
        editor().assertTextEquals("Initial name").performTextReplacement("")
        rule.onNodeWithText(confirm).assertIsNotEnabled()
        editor().performTextReplacement("Updated name")
        rule.onNodeWithText(confirm).assertIsEnabled().performClick()
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.runOnIdle { assertEquals("Updated name", stored); assertEquals(1, commits) }

        rule.onNodeWithText("Display name").performClick()
        editor().performTextReplacement("Keyboard confirmation")
        editor().performImeAction()
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.runOnIdle { assertEquals("Keyboard confirmation", stored); assertEquals(2, commits) }
    }

    @Test fun preferenceChoicesHaveATitleAndCancelWithoutSelection() {
        var selected = "System"
        var selections = 0
        content {
            var value by remember { mutableStateOf(selected) }
            GlasmioListPreference(
                title = { Text("Appearance choice") },
                selectedValue = value,
                values = listOf("System", "Light", "Dark"),
                valueText = { it },
                onValueSelected = { value = it; selected = it; selections++ },
            )
        }
        rule.onNodeWithText("Appearance choice").performClick()
        rule.onNode(hasText("Appearance choice") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        rule.onNode(hasText("System") and hasAnyAncestor(isDialog())).assertIsSelected()
        rule.onNodeWithText(rule.activity.getString(android.R.string.cancel)).performClick()
        rule.runOnIdle { assertEquals("System", selected); assertEquals(0, selections) }
        rule.onNodeWithText("Appearance choice").performClick()
        rule.onNode(hasText("Dark") and hasAnyAncestor(isDialog())).performClick()
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.runOnIdle { assertEquals("Dark", selected); assertEquals(1, selections) }
    }

    @Test fun alertFieldLeavesTheSpecifiedGapBeforeItsButtons() {
        content {
            var value by remember { mutableStateOf(TextFieldValue("Name")) }
            GlasmioAlertDialog(
                onDismissRequest = {},
                title = "Input geometry",
                buttons = listOf(GlasmioAlertButtonSpec("Confirm geometry", {})),
            ) {
                GlasmioAlertFieldGroup {
                    GlasmioAlertTextField(value, { value = it }, modifier = Modifier.testTag("alert-field"))
                }
            }
        }
        val field = rule.onNodeWithTag("alert-field").fetchSemanticsNode().boundsInRoot
        val button = rule.onNodeWithText("Confirm geometry").fetchSemanticsNode().boundsInRoot
        val expectedGap = with(rule.density) { 19.dp.toPx() }
        assertEquals(expectedGap, button.top - field.bottom, 1f)
        val fieldInset = with(rule.density) { 16.dp.toPx() }
        assertEquals(button.left + fieldInset, field.left, 1f)
        assertEquals(button.right - fieldInset, field.right, 1f)
    }

    @Test fun stepperKeepsIts92dpCapsuleWhilePressed() {
        content {
            Box(Modifier.testTag("stepper-region").background(Color.White).padding(8.dp)) {
                GlasmioStepper(
                    1, {}, Modifier.testTag("stepper-geometry"), range = 0..2,
                    decreaseContentDescription = "Geometry decrease", increaseContentDescription = "Geometry increase",
                )
            }
        }
        rule.onNodeWithTag("stepper-geometry").assertWidthIsEqualTo(92.dp).assertHeightIsEqualTo(32.dp)
        val increase = rule.onNodeWithContentDescription("Geometry increase")
        increase.performTouchInput { down(center) }
        try {
            rule.mainClock.advanceTimeBy(160)
            val pixels = rule.onNodeWithTag("stepper-region").captureToImage().toPixelMap()
            val inset = with(rule.density) { 8.dp.toPx() }
            val corner = with(rule.density) { 1.dp.toPx() }
            val width = with(rule.density) { 92.dp.toPx() }
            val height = with(rule.density) { 32.dp.toPx() }
            // These four pixels are inside the control's rectangle and outside its capsule.
            for (x in listOf(inset + corner, inset + width - corner)) {
                for (y in listOf(inset + corner, inset + height - corner)) {
                    val pixel = pixels[x.toInt(), y.toInt()]
                    assertEquals(1f, pixel.red, 0.01f)
                    assertEquals(1f, pixel.green, 0.01f)
                    assertEquals(1f, pixel.blue, 0.01f)
                }
            }
        } finally {
            increase.performTouchInput { up() }
        }
    }

    @Test fun groupedPreferenceSeparatorIsVisibleAndKeepsTheOuterGutterClear() {
        var secondEnabled by mutableStateOf(true)
        content {
            GlasmioGroupedList(Modifier.testTag("preference-separator-group")) {
                GlasmioPreferenceEntry(title = { Text("First preference") })
                GlasmioPreferenceEntry(
                    title = { Text("Second preference") },
                    modifier = Modifier.testTag("second-preference"),
                    isEnabled = secondEnabled,
                    showTopSeparator = true,
                )
            }
        }
        val groupNode = rule.onNodeWithTag("preference-separator-group")
        val groupBounds = groupNode.fetchSemanticsNode().boundsInRoot
        val secondBounds = rule.onNodeWithTag("second-preference").fetchSemanticsNode().boundsInRoot
        val pixels = groupNode.captureToImage().toPixelMap()
        val y = ((secondBounds.top - groupBounds.top) * pixels.height / groupBounds.height).toInt()
        val sampleOffset = with(rule.density) { 4.dp.roundToPx() }
        fun contrastAt(x: Int): Float {
            val reference = pixels[x, (y + sampleOffset).coerceAtMost(pixels.height - 1)]
            return (-2..2).maxOf { delta ->
                val actual = pixels[x, (y + delta).coerceIn(0, pixels.height - 1)]
                (kotlin.math.abs(actual.red - reference.red) +
                    kotlin.math.abs(actual.green - reference.green) +
                    kotlin.math.abs(actual.blue - reference.blue)) / 3f
            }
        }
        assertTrue("The row boundary must contain a visible separator", contrastAt((pixels.width * 0.8f).toInt()) > 0.02f)
        val gutterX = with(rule.density) { 8.dp.roundToPx() }
        assertTrue("The separator must not extend through the outer gutter", contrastAt(gutterX) < 0.01f)
        rule.runOnIdle { secondEnabled = false }
        val disabledPixels = groupNode.captureToImage().toPixelMap()
        val dividerX = (pixels.width * 0.8f).toInt()
        for (delta in -1..1) {
            val row = (y + delta).coerceIn(0, pixels.height - 1)
            val before = pixels[dividerX, row]
            val after = disabledPixels[dividerX, row]
            assertEquals("Disabling content cannot fade a structural separator", before.red, after.red, 0.01f)
            assertEquals(before.green, after.green, 0.01f)
            assertEquals(before.blue, after.blue, 0.01f)
        }
    }

    @Test fun popUpButtonSelectsOnceAndKeepsTheSelectionAfterDismissal() {
        var commits = 0
        content {
            var selected by remember { mutableStateOf("Default") }
            GlasmioPopUpButton(selected, listOf("Default", "Recent"), {
                selected = it
                commits++
            }, { it })
        }
        rule.onNodeWithText("Default").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("Recent").assertIsDisplayed().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Default").fetchSemanticsNodes().isEmpty() }
        rule.runOnIdle { assertEquals(1, commits) }
        rule.onNodeWithText("Recent").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("Default").assertIsDisplayed()
        pressSystemBack(rule)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Default").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Recent").assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, commits) }
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
        pressSystemBack(rule)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Alert fixture").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithText("Sheet fixture").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Alert fixture").assertDoesNotExist()
        rule.onNodeWithText("Sheet fixture").assertIsDisplayed()
        pressSystemBack(rule)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Sheet fixture").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithText("Dismissed fixture").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Sheet fixture").assertDoesNotExist()
        rule.onNodeWithText("Dismissed fixture").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class SampleNavigationBehaviorTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun sampleLaunchesAndNavigatesWithItsRealAssets() {
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText("Glasmio").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        rule.onNode(tab and hasContentDescription("材质")).performClick()
        rule.onNodeWithText("Regular Small").assertIsDisplayed()
        rule.onNode(tab and hasContentDescription("关于")).performClick().assertIsSelected()
        rule.onNodeWithText("Apache License 2.0").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText("Apache License 2.0").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNode(tab and hasContentDescription("关于")).assertIsSelected()
        rule.onNodeWithContentDescription("搜索").performClick()
        rule.onNodeWithText("Search Components").assertIsDisplayed()
        rule.waitUntil(10_000) {
            var focused = false
            rule.activityRule.scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        pressSystemBack(rule)
        rule.waitUntil(10_000) { !imeVisible() }
        if (rule.onAllNodesWithContentDescription("搜索").fetchSemanticsNodes().isEmpty()) {
            rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertIsDisplayed()
            pressSystemBack(rule)
        }
        val selectedAboutTab = tab and hasContentDescription("关于") and isSelected()
        rule.waitUntil(10_000) {
            rule.onAllNodes(selectedAboutTab).fetchSemanticsNodes().size == 1 &&
                rule.onAllNodesWithText("Search Components").fetchSemanticsNodes().isEmpty()
        }
        rule.onNode(tab and hasContentDescription("关于")).assertIsSelected()
        rule.onNodeWithText("Search Components").assertDoesNotExist()
    }

    private fun imeVisible(): Boolean {
        var visible = false
        rule.activityRule.scenario.onActivity {
            visible = it.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
        }
        return visible
    }

    @Test fun floatingSearchFiltersCancelsAndStartsEmptyOnReentry() {
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Glasmio").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        rule.onNodeWithContentDescription("搜索").performClick()
        val editor = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)
        val input = rule.onNode(editor).assertIsDisplayed()
        val screen = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val inputBounds = input.fetchSemanticsNode().boundsInRoot
        assertTrue("Search must float in the lower half of the screen", inputBounds.top > screen.height * 0.4f)
        input.performTextInput("Controls")
        rule.onNode(hasText("Controls") and hasClickAction() and !editor).assertIsDisplayed()
        rule.onNodeWithContentDescription("Cancel").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("搜索").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(editor).assertDoesNotExist()
        rule.onNodeWithContentDescription("搜索").performClick()
        rule.onNode(editor).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithContentDescription("Cancel").performClick()
    }

    @Test fun dynamicTypePickerChangesTheRenderedSemanticStyle() {
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Glasmio").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        rule.onNodeWithText("Text Styles and Dynamic Type").performScrollTo().performClick()
        val initialHeight = rule.onAllNodesWithText("Large Title")[0]
            .fetchSemanticsNode().boundsInRoot.height
        rule.onNode(hasText("Large") and hasClickAction()).performClick()
        rule.onNodeWithText("AX1").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Large").fetchSemanticsNodes().isEmpty() }
        val title = rule.onAllNodesWithText("Large Title")[0].performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val style = layouts.single().layoutInput.style
        assertEquals(with(rule.density) { 44.dp.toSp() }, style.fontSize)
        assertEquals(with(rule.density) { 52.dp.toSp() }, style.lineHeight)
        // Android preserves the loaded font's rounded ascent/descent outside the
        // requested line box. Verify growth without forcing the glyphs to clip.
        assertTrue(title.fetchSemanticsNode().boundsInRoot.height > initialHeight)
    }

    @Test fun rootNavigationCompactsOnScrollAndExpandsWithoutChangingTheTab() {
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Glasmio").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        rule.onAllNodes(tab).assertCountEquals(4)
        rule.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performTouchInput {
            swipeUp(startY = height * 0.75f, endY = height * 0.3f, durationMillis = 400)
        }
        rule.waitUntil(10_000) { rule.onAllNodes(tab).fetchSemanticsNodes().size == 1 }
        rule.onNode(tab and hasContentDescription("主页")).assertIsSelected()
        rule.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performTouchInput {
            swipeDown(startY = height * 0.3f, endY = height * 0.75f, durationMillis = 400)
        }
        rule.waitUntil(10_000) { rule.onAllNodes(tab).fetchSemanticsNodes().size == 4 }
        rule.onNode(tab and hasContentDescription("主页")).assertIsSelected()
        rule.onNode(tab and hasContentDescription("材质")).performClick().assertIsSelected()
        rule.onNodeWithText("Regular Small").assertIsDisplayed()
        rule.onAllNodes(tab).assertCountEquals(4)
    }

    @Test fun toolbarMenuOpensAndClosesFromItsRealAnchor() {
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Glasmio").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        rule.onNodeWithText("Menus").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Menu").assertIsDisplayed().performClick()
        rule.onNodeWithText("Favorite").assertIsDisplayed()
        rule.onNodeWithText("Unavailable").assertIsNotEnabled()
        rule.onNodeWithText("Favorite").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Favorite").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("Menu").assertIsDisplayed().performClick()
        rule.onNodeWithText("Favorite").assertIsDisplayed()
        pressSystemBack(rule)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Favorite").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("Menu").assertIsDisplayed()
    }


    @Test fun settingsScrollShowsTheCenteredTitleAndSurvivesRootTabSwitches() {
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Glasmio").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        rule.onNode(tab and hasContentDescription("设置")).performClick().assertIsSelected()
        val scrollable = hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)
        val titleInBody = hasText("Settings") and hasAnyAncestor(scrollable)
        val toolbarTitle = hasText("Settings") and !hasAnyAncestor(scrollable)
        val initialLarge = rule.onAllNodes(titleInBody).fetchSemanticsNodes().maxByOrNull { it.boundsInRoot.height }
            ?: error("Settings must initially show its large title")
        assertTrue(initialLarge.boundsInRoot.height > with(rule.density) { 28.dp.toPx() })
        val initialContrast = visibleContrast(rule.onNode(toolbarTitle))

        // Exercise the real LazyColumn action. A page shorter than its viewport cannot satisfy this transition.
        rule.onNode(scrollable).performScrollToIndex(1)
        rule.waitForIdle()
        val compactTitle = rule.onNode(toolbarTitle).assertIsDisplayed()
        val titleBounds = compactTitle.fetchSemanticsNode().boundsInRoot
        val viewport = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.center.x, titleBounds.center.x, with(rule.density) { 1.dp.toPx() })
        val afterContrast = visibleContrast(compactTitle)
        assertTrue("The toolbar title must actually become visible after scrolling", afterContrast > 0.25f && afterContrast > initialContrast + 0.15f)
        val oldLarge = rule.onAllNodes(SemanticsMatcher("original Settings large title") { it.id == initialLarge.id })
            .fetchSemanticsNodes()
        val toolbarBottom = titleBounds.bottom + with(rule.density) { 20.dp.toPx() }
        assertTrue("The large title must leave the body viewport", oldLarge.isEmpty() || oldLarge.all { it.boundsInRoot.bottom <= toolbarBottom })

        rule.onNode(tab and hasContentDescription("关于")).performClick().assertIsSelected()
        rule.onNode(tab and hasContentDescription("设置")).performClick().assertIsSelected()
        rule.onAllNodes(tab).assertCountEquals(4)
        assertTrue("Returning to Settings must retain its scrolled title state", visibleContrast(rule.onNode(toolbarTitle)) > 0.25f)
    }

    private fun visibleContrast(node: SemanticsNodeInteraction): Float {
        val pixels = node.captureToImage().toPixelMap()
        var darkest = 1f
        var lightest = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            val brightness = (pixel.red + pixel.green + pixel.blue) / 3f * pixel.alpha + 0.5f * (1f - pixel.alpha)
            darkest = minOf(darkest, brightness)
            lightest = maxOf(lightest, brightness)
        }
        return lightest - darkest
    }

}
