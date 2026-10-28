package io.github.neoruaa.glasmio.sample

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.scaffold.rememberGlasmioHeaderScrollEdge
import io.github.neoruaa.glasmio.ui.*
import io.github.neoruaa.glasmio.preference.GlasmioEditTextPreference
import io.github.neoruaa.glasmio.preference.GlasmioListPreference
import io.github.neoruaa.glasmio.preference.GlasmioPreferenceEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
        val selectionChanges = mutableListOf<TextRange>()
        var replacements = 0
        val replacement = GlasmioCascadingMenuItem("Replace selection", onClick = {
            val range = value.selection
            value = TextFieldValue(value.text.replaceRange(range.min, range.max, "Welcome"), TextRange(range.min + 7))
            replacements++
        })
        editingContent(additionalItems = listOf(replacement)) {
            GlasmioEditableText(value, {
                selectionChanges += it.selection
                value = it
            }, Modifier.fillMaxWidth().testTag("expanded-editor"))
        }
        longPressWord("expanded-editor")
        rule.onNodeWithContentDescription("More").performClick()
        val selectAll = editingAction("Select All", android.R.string.selectAll)
        rule.onNode(selectAll).assertIsDisplayed().performClick()
        try {
            rule.waitUntil(10_000) {
                value.selection.min == 0 && value.selection.max == value.text.length &&
                    rule.onAllNodesWithText("Replace selection").fetchSemanticsNodes().isEmpty()
            }
        } catch (error: ComposeTimeoutException) {
            val current = value
            val changes = rule.runOnIdle { selectionChanges.toList() }
            val remainingExpandedActions = runCatching {
                rule.onAllNodesWithText("Replace selection")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).size
            }.getOrNull()
            throw AssertionError(
                "Select All did not complete: selection=${current.selection}, " +
                    "textLength=${current.text.length}, remainingExpandedActions=$remainingExpandedActions, " +
                    "selectionChanges=$changes",
                error,
            )
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

    @Test fun multilineEditorWrapsScrollsAndKeepsItsCustomSelectionChrome() {
        val text = (1..24).joinToString("\n") { "Line $it: editable text that wraps within this narrow editor." }
        var value by mutableStateOf(TextFieldValue(text))
        editingContent {
            GlasmioEditableText(
                value, { value = it }, Modifier.width(220.dp).height(160.dp).testTag("multiline-editor"),
                singleLine = false,
                keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
            )
        }
        val layout = longPressWord("multiline-editor")
        assertTrue("Text must wrap as well as honor explicit newlines", layout.lineCount > 24)
        rule.onNodeWithContentDescription("Selection start").assertIsDisplayed()
        rule.onNodeWithContentDescription("Selection end").assertIsDisplayed()
        val field = rule.onNodeWithTag("multiline-editor")
        val scroll = field.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Long text must have a vertical scroll range", scroll.maxValue() > 0f)
        val before = scroll.value()
        field.performTouchInput { swipeUp() }
        rule.waitUntil(5_000) { scroll.value() > before }
        rule.onNodeWithContentDescription("Selection start").assertDoesNotExist()
    }

    @Test fun multilineSelectionHandleCrossesLinesAndTheOppositeEndpoint() {
        var value by mutableStateOf(TextFieldValue("Hello World\nSecond line\nThird line"))
        editingContent {
            GlasmioEditableText(
                value, { value = it }, Modifier.fillMaxWidth().height(200.dp).testTag("multiline-handles"),
                singleLine = false,
                keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
            )
        }
        val layout = longPressWord("multiline-handles")
        val origin = layout.getCursorRect(5).center
        val target = layout.getCursorRect(18).center
        val endHandle = rule.onNodeWithContentDescription("Selection end")
        endHandle.performTouchInput {
            down(center)
            moveBy(target - origin, delayMillis = 160)
            up()
        }
        rule.runOnIdle { assertEquals(TextRange(0, 18), value.selection) }
        val crossing = layout.getCursorRect(2).center
        val startHandle = rule.onNodeWithContentDescription("Selection start")
        startHandle.performTouchInput {
            down(center)
            moveBy(layout.getCursorRect(23).center - layout.getCursorRect(0).center, delayMillis = 160)
            moveBy(crossing - layout.getCursorRect(23).center, delayMillis = 160)
            up()
        }
        rule.runOnIdle { assertEquals(TextRange(2, 18), value.selection) }
        rule.onNodeWithContentDescription("Selection start").assertIsDisplayed()
        rule.onNodeWithContentDescription("Selection end").assertIsDisplayed()
    }

    @Test fun multilineHandleAutoScrollsAtBothVerticalEdges() {
        val text = (1..40).joinToString("\n") { "Line $it editable text" }
        var value by mutableStateOf(TextFieldValue(text))
        editingContent {
            GlasmioEditableText(
                value, { value = it }, Modifier.fillMaxWidth().height(160.dp).testTag("edge-multiline"),
                singleLine = false,
                keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
            )
        }
        val layout = longPressWord("edge-multiline")
        val field = rule.onNodeWithTag("edge-multiline")
        val bounds = field.fetchSemanticsNode().boundsInRoot
        val scroll = field.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        val endHandle = rule.onNodeWithContentDescription("Selection end")
        val origin = layout.getCursorRect(value.selection.end).center
        val bottom = Offset(bounds.width - 1f, bounds.height - 1f)
        rule.mainClock.autoAdvance = false
        try {
            endHandle.performTouchInput {
                down(center)
                moveBy(bottom - origin, delayMillis = 160)
            }
            rule.mainClock.advanceTimeBy(1_800)
            rule.runOnIdle {
                assertEquals("Holding the lower edge must reach the scroll limit", scroll.maxValue(), scroll.value(), 1f)
                assertEquals("The endpoint must reach the final character", text.length, value.selection.max)
            }
            endHandle.performTouchInput { moveBy(Offset(-bounds.width + 2f, -bounds.height + 2f), delayMillis = 160) }
            rule.mainClock.advanceTimeBy(1_800)
            rule.runOnIdle {
                assertEquals("Holding the upper edge must reach the start", 0f, scroll.value(), 1f)
                assertEquals(TextRange.Zero, value.selection)
            }
        } finally {
            endHandle.performTouchInput { up() }
            rule.mainClock.autoAdvance = true
        }
    }

    @Test fun changingLineModeWhileFocusedKeepsCustomEditingChrome() {
        var value by mutableStateOf(TextFieldValue("Hello World\nSecond line"))
        var singleLine by mutableStateOf(true)
        editingContent {
            GlasmioEditableText(
                value, { value = it }, Modifier.fillMaxWidth().height(160.dp).testTag("switching-editor"),
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
            )
        }
        val field = rule.onNodeWithTag("switching-editor")
        field.performClick().assertIsFocused()
        rule.runOnIdle { singleLine = false }
        field.assertIsFocused()
        longPressWord("switching-editor")
        rule.onNodeWithContentDescription("Selection start").assertIsDisplayed()
        rule.onNodeWithContentDescription("Selection end").assertIsDisplayed()
        rule.runOnIdle { singleLine = true }
        field.assertIsFocused()
        longPressWord("switching-editor")
        rule.onNodeWithContentDescription("Selection start").assertIsDisplayed()
        rule.onNodeWithContentDescription("Selection end").assertIsDisplayed()
    }

    @Test fun multilineMaxLinesLimitsTheViewportWithoutTruncatingItsText() {
        var value by mutableStateOf(TextFieldValue("First line\nSecond line\nThird line\nFourth line"))
        var layout: TextLayoutResult? = null
        editingContent {
            GlasmioEditableText(
                value, { value = it }, Modifier.fillMaxWidth().testTag("limited-multiline"),
                singleLine = false, maxLines = 2,
                keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                onTextLayout = { layout = it },
            )
        }
        val field = rule.onNodeWithTag("limited-multiline")
        val bounds = field.fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {
            val result = requireNotNull(layout)
            assertEquals(4, result.lineCount)
            assertEquals(result.getLineBottom(1), bounds.height, 1f)
        }
        val scroll = field.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scroll.maxValue() > 0f)
        field.performClick()
        field.performSemanticsAction(SemanticsActions.SetSelection) { it(value.text.length, value.text.length, false) }
        rule.waitUntil(5_000) { scroll.value() > 0f }
    }

    @Test fun textEditingDemoAcceptsFocusFromItsBlankAreaAndKeepsNewlines() {
        val value = mutableStateOf(TextFieldValue("Hello World"))
        editingContent {
            Box(Modifier.fillMaxSize()) { TextEditingDemo(value, PaddingValues()) }
        }
        val field = rule.onNodeWithContentDescription("Editable text")
        field.performTouchInput { click(Offset(center.x, height - 32f)) }
        field.assertIsFocused().performTextInput("\nSecond paragraph")
        rule.runOnIdle { assertEquals("Hello World\nSecond paragraph", value.value.text) }
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

    @Test fun popUpButtonInheritsHostTypographyChanges() {
        var updatedTypography by mutableStateOf(false)
        content {
            val hostTypography = MaterialTheme.typography
            GlasmioTheme(
                typography = hostTypography.copy(
                    bodyLarge = hostTypography.bodyLarge.copy(
                        fontSize = if (updatedTypography) 21.sp else 17.sp,
                        lineHeight = if (updatedTypography) 28.sp else 22.sp,
                        fontWeight = if (updatedTypography) FontWeight.Medium else FontWeight.Normal,
                        letterSpacing = if (updatedTypography) 1.5.sp else 0.5.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                ),
                appearance = GlassAppearance(reduceTransparency = true),
            ) {
                Text("Body sample", style = MaterialTheme.typography.bodyLarge)
                GlasmioPopUpButton("Default", listOf("Default", "Recent"), {}, { it })
            }
        }

        fun layoutFor(text: String): TextLayoutResult {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single()
        }

        fun assertMatchesBody(text: String) {
            val body = layoutFor("Body sample").layoutInput.style
            val actual = layoutFor(text).layoutInput.style
            assertEquals("Font size must match body text", body.fontSize, actual.fontSize)
            assertEquals("Font weight must match body text", body.fontWeight, actual.fontWeight)
            assertEquals("Letter spacing must match body text", body.letterSpacing, actual.letterSpacing)
            assertEquals("Font family must match body text", body.fontFamily, actual.fontFamily)
            assertEquals("Line height must match body text", body.lineHeight, actual.lineHeight)
        }

        val initialBody = layoutFor("Body sample").layoutInput.style
        assertMatchesBody("Default")
        rule.runOnIdle { updatedTypography = true }
        val updatedBody = layoutFor("Body sample").layoutInput.style
        assertNotEquals(initialBody.fontSize, updatedBody.fontSize)
        assertNotEquals(initialBody.fontWeight, updatedBody.fontWeight)
        assertNotEquals(initialBody.letterSpacing, updatedBody.letterSpacing)
        assertMatchesBody("Default")
        rule.onNodeWithText("Default").performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertMatchesBody("Recent")
        pressSystemBack(rule)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Recent").fetchSemanticsNodes().isEmpty() }
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

    @Test fun sheetDetentsResizeTheViewportAndHandOffScrolling() {
        lateinit var sheet: GlasmioSheetState
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        lateinit var scope: CoroutineScope
        var scrollingExpands by mutableStateOf(true)
        content {
            sheet = rememberGlasmioSheetState(skipPartiallyExpanded = false)
            list = rememberLazyListState()
            scope = rememberCoroutineScope()
            GlasmioBottomSheet(onDismissRequest = {}, sheetState = sheet, modifier = Modifier.testTag("sheet"),
                prefersScrollingExpandsWhenScrolledToEdge = scrollingExpands) {
                Text("Sheet header", Modifier.fillMaxWidth().height(44.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("sheet-list"), state = list) {
                    items(40) { Text("Row $it", Modifier.fillMaxWidth().height(52.dp)) }
                }
                Text("Sheet footer", Modifier.fillMaxWidth().height(44.dp))
            }
        }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Medium && !sheet.isAnimationRunning }
        val mediumTop = rule.onNodeWithText("Sheet header").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("sheet-list").performTouchInput {
            swipe(Offset(centerX, height * 0.85f), Offset(centerX, height * 0.15f), 700)
        }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Large && !sheet.isAnimationRunning }
        assertTrue(rule.onNodeWithText("Sheet header").fetchSemanticsNode().boundsInRoot.top < mediumTop)
        val headerPixels = rule.onNodeWithText("Sheet header").captureToImage().toPixelMap()
        assertTrue((0 until headerPixels.height).any { y ->
            (0 until headerPixels.width).any { x ->
                val pixel = headerPixels[x, y]
                pixel.alpha > 0.9f && maxOf(pixel.red, pixel.green, pixel.blue) < 0.3f
            }
        })
        rule.runOnIdle { scope.launch { list.scrollToItem(12) } }
        rule.waitUntil(10_000) { list.firstVisibleItemIndex == 12 }
        rule.onNodeWithTag("sheet-list").performTouchInput {
            swipe(Offset(centerX, height * 0.3f), Offset(centerX, height * 0.6f), 800)
        }
        rule.runOnIdle {
            assertEquals(GlasmioSheetValue.Large, sheet.currentValue)
            assertTrue(list.firstVisibleItemIndex < 12)
            assertTrue(list.firstVisibleItemIndex > 0)
        }
        rule.runOnIdle { scope.launch { list.scrollToItem(0) } }
        rule.waitUntil(10_000) { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 }
        rule.onNodeWithTag("sheet").performSemanticsAction(SemanticsActions.Collapse) { it() }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Medium && !sheet.isAnimationRunning }
        rule.onNodeWithText("Sheet footer").assertIsDisplayed()
        rule.runOnIdle { scrollingExpands = false }
        rule.onNodeWithTag("sheet-list").performTouchInput {
            swipe(Offset(centerX, height * 0.8f), Offset(centerX, height * 0.2f), 700)
        }
        rule.runOnIdle {
            assertEquals(GlasmioSheetValue.Medium, sheet.currentValue)
            assertTrue(list.firstVisibleItemIndex > 0)
        }
        rule.onNodeWithText("Sheet header").performTouchInput {
            down(center)
            moveBy(Offset(0f, -600f), 300)
            cancel()
        }
        rule.waitUntil(10_000) { !sheet.isAnimationRunning && !sheet.isDragging }
        rule.runOnIdle { assertEquals(GlasmioSheetValue.Medium, sheet.currentValue) }
    }

    @Test fun sheetClosingRetainsItsWindowAndCanReverseBeforeRemoval() {
        lateinit var sheet: GlasmioSheetState
        lateinit var scope: CoroutineScope
        var visible by mutableStateOf(true)
        var dismissals = 0
        content {
            sheet = rememberGlasmioSheetState(skipPartiallyExpanded = false)
            scope = rememberCoroutineScope()
            GlasmioBottomSheet(
                visible = visible,
                sheetState = sheet,
                onDismissRequest = { dismissals++; visible = false },
            ) { Text("Retained sheet", Modifier.height(100.dp)) }
        }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Medium && !sheet.isAnimationRunning }
        rule.runOnIdle { scope.launch { sheet.expand() } }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Large && !sheet.isAnimationRunning }
        val restingBounds = rule.onNodeWithText("Retained sheet").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { visible = false }
        rule.mainClock.advanceTimeBy(80)
        rule.onNodeWithText("Retained sheet").assertExists()
        val closingBounds = rule.onNodeWithText("Retained sheet").fetchSemanticsNode().boundsInRoot
        assertEquals(restingBounds.width, closingBounds.width, 1f)
        assertEquals(restingBounds.height, closingBounds.height, 1f)
        assertTrue(closingBounds.top > restingBounds.top)
        rule.runOnIdle { assertTrue(sheet.isVisible); visible = true }
        rule.mainClock.advanceTimeBy(1_500)
        rule.mainClock.autoAdvance = true
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Large && !sheet.isAnimationRunning }
        rule.onNodeWithText("Retained sheet").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, dismissals); scope.launch { sheet.hide() } }
        rule.waitUntil(10_000) { dismissals == 1 }
        rule.onNodeWithText("Retained sheet").assertDoesNotExist()
    }

    @Test fun sheetHeaderBlursUnderlappingContentAndKeepsNavigationAreaImmersive() {
        lateinit var sheet: GlasmioSheetState
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        lateinit var scope: CoroutineScope
        var reduceTransparency by mutableStateOf(false)
        var density = 1f
        var canvas = Color.Unspecified
        val bodyColor = Color(0xFF004488)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = reduceTransparency)) {
                density = LocalDensity.current.density
                GlasmioBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = { Box(Modifier.fillMaxSize().background(Color.White)) },
                    overlayContent = {
                        sheet = rememberGlasmioSheetState()
                        list = rememberLazyListState()
                        scope = rememberCoroutineScope()
                        GlasmioBottomSheet(
                            onDismissRequest = {},
                            sheetState = sheet,
                            modifier = Modifier.testTag("sheet-chrome"),
                            contentWindowInsets = { WindowInsets(top = 50.dp) },
                            contentNavigationBarInsets = { WindowInsets(bottom = 34.dp) },
                            topBarBlurProgress = rememberGlasmioHeaderScrollEdge(list),
                            topBar = {
                                GlasmioSheetTopToolbar("Pinned", Modifier.testTag("sheet-chrome-header"))
                            },
                        ) {
                            canvas = LocalGlassColors.current.groupedBackground
                            val padding = LocalGlasmioSheetContentPadding.current
                            Column(Modifier.fillMaxSize().background(bodyColor)) {
                                LazyColumn(
                                    Modifier.fillMaxWidth().weight(1f).testTag("sheet-chrome-list"),
                                    state = list,
                                    contentPadding = PaddingValues(top = padding.calculateTopPadding()),
                                ) {
                                    items(40) { index ->
                                        Text("Row $index", color = Color.White,
                                            modifier = Modifier.fillMaxWidth().height(52.dp)
                                                .testTag("sheet-chrome-row-$index").padding(start = 200.dp))
                                    }
                                }
                                Box(Modifier.fillMaxWidth().padding(bottom = padding.calculateBottomPadding())) {
                                    Text("Footer", color = Color.White, modifier = Modifier.height(44.dp).padding(start = 200.dp))
                                }
                            }
                        }
                    },
                )
            }
        }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Large && !sheet.isAnimationRunning }
        val restingHeader = rule.onNodeWithTag("sheet-chrome-header").fetchSemanticsNode().boundsInRoot
        val bounds = rule.onNodeWithTag("sheet-chrome").fetchSemanticsNode().boundsInRoot
        fun pixel(image: androidx.compose.ui.graphics.PixelMap, y: Float): Color =
            image[(bounds.left + bounds.width * 0.25f).toInt(), y.toInt()]
        fun assertColor(expected: Color, actual: Color) {
            assertEquals(expected.red, actual.red, 2f / 255f)
            assertEquals(expected.green, actual.green, 2f / 255f)
            assertEquals(expected.blue, actual.blue, 2f / 255f)
        }
        val upperY = bounds.top + 24f * density
        val middleY = restingHeader.bottom + 10f * density
        val lowerY = restingHeader.bottom + 36f * density
        val restingPixels = rule.onNode(isDialog()).captureToImage().toPixelMap()
        assertColor(bodyColor, pixel(restingPixels, upperY))
        assertColor(bodyColor, pixel(restingPixels, middleY))
        rule.runOnIdle { scope.launch { list.scrollToItem(0, (28f * density).toInt()) } }
        rule.waitUntil(10_000) { list.firstVisibleItemScrollOffset >= (27f * density).toInt() }
        val partialUpper = pixel(rule.onNode(isDialog()).captureToImage().toPixelMap(), upperY)
        assertTrue("The header must begin revealing as content scrolls", partialUpper.red > bodyColor.red + 0.1f)

        rule.runOnIdle { scope.launch { list.scrollToItem(12) } }
        rule.waitUntil(10_000) { list.firstVisibleItemIndex == 12 }
        val header = rule.onNodeWithTag("sheet-chrome-header").fetchSemanticsNode().boundsInRoot
        val underlappingRow = rule.onNodeWithTag("sheet-chrome-row-11").fetchSemanticsNode().boundsInRoot
        assertEquals(restingHeader.top, header.top, 1f)
        assertTrue("Scrolled rows must pass behind the pinned header", underlappingRow.top < header.bottom)
        assertTrue("The list must extend into the header's viewport", underlappingRow.bottom > header.top)
        val pixels = rule.onNode(isDialog()).captureToImage().toPixelMap()
        val upper = pixel(pixels, upperY)
        val middle = pixel(pixels, middleY)
        val lower = pixel(pixels, lowerY)
        assertTrue("The scroll reveal must remain gradual", upper.red > partialUpper.red + 0.1f)
        assertTrue("The header must retain the backdrop rather than draw a solid block", upper.red < canvas.red - 0.05f)
        assertTrue("Header tint must fade through the content", upper.red > middle.red + 0.08f)
        assertTrue("The lower blur edge must approach the unmodified content", middle.red > lower.red + 0.03f)
        assertColor(bodyColor, lower)
        assertColor(bodyColor, pixel(pixels, bounds.bottom - 8f * density))

        rule.runOnIdle { reduceTransparency = true }
        rule.waitForIdle()
        val reduced = rule.onNode(isDialog()).captureToImage().toPixelMap()
        assertColor(canvas, pixel(reduced, upperY))
        assertColor(canvas, pixel(reduced, header.bottom - 4f * density))
        assertColor(bodyColor, pixel(reduced, lowerY))
        assertColor(bodyColor, pixel(reduced, bounds.bottom - 8f * density))
        rule.runOnIdle { scope.launch { list.scrollToItem(0) } }
        rule.waitUntil(10_000) { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 }
        val returned = rule.onNode(isDialog()).captureToImage().toPixelMap()
        assertColor(bodyColor, pixel(returned, upperY))
        assertColor(bodyColor, pixel(returned, middleY))
    }

    @Test fun sheetMaterialRendersOverTheNativeReferenceGradient() {
        lateinit var sheet: GlasmioSheetState
        lateinit var scope: CoroutineScope
        var visible by mutableStateOf(false)
        var dark by mutableStateOf(false)
        var table by mutableStateOf(false)
        var density = 1f
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            GlasmioTheme(isDark = dark) {
                density = LocalDensity.current.density
                val gradient = remember {
                    Brush.verticalGradient(0f to Color(0xFF0088FF), 0.55f to Color(0xFF32ADE6), 1f to Color(0xFFFF8D28))
                }
                GlasmioBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = { Box(Modifier.fillMaxSize().background(gradient)) },
                    overlayContent = {
                        sheet = rememberGlasmioSheetState(skipPartiallyExpanded = false)
                        scope = rememberCoroutineScope()
                        GlasmioBottomSheet(
                            onDismissRequest = {},
                            visible = visible,
                            sheetState = sheet,
                            contentWindowInsets = { WindowInsets(top = 50.dp) },
                            contentNavigationBarInsets = { WindowInsets(bottom = 34.dp) },
                        ) {
                            Column(Modifier.fillMaxSize().then(
                                if (table) Modifier.background(LocalGlassColors.current.groupedBackground) else Modifier,
                            )) {
                                Text("Native material reference", Modifier.fillMaxWidth().height(80.dp).padding(24.dp))
                                if (table) {
                                    GlasmioGroupedList(Modifier.padding(horizontal = 16.dp).testTag("material-reference-rows")) {
                                        repeat(6) { index -> GlasmioListRow("Item ${index + 1}",
                                            subtitle = "UIKit native sheet", showTopSeparator = index > 0) }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
        fun save(name: String, modal: Boolean = true) {
            val bitmap = (if (modal) rule.onNode(isDialog()) else rule.onRoot()).captureToImage().asAndroidBitmap()
            java.io.File(rule.activity.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        fun rowSample(): Offset {
            val row = rule.onNodeWithTag("material-reference-rows").fetchSemanticsNode().boundsInRoot
            return Offset(row.right - 12f * density, row.top + 40f * density)
        }
        fun sample(pixels: androidx.compose.ui.graphics.PixelMap, point: Offset): Color =
            pixels[point.x.toInt().coerceIn(0, pixels.width - 1), point.y.toInt().coerceIn(0, pixels.height - 1)]
        fun assertBias(bare: androidx.compose.ui.graphics.PixelMap, bias: Int) {
            val point = rowSample()
            val original = sample(bare, point)
            val transformed = sample(rule.onNode(isDialog()).captureToImage().toPixelMap(), point)
            listOf(transformed.red - original.red, transformed.green - original.green, transformed.blue - original.blue).forEach {
                // Golden CAColorMatrix bias observed on the physical iPhone, in 8-bit sRGB.
                assertEquals(bias.toFloat(), it * 255f, 3f)
            }
        }
        fun assertOpaqueTable(cell: Color, canvas: Color) {
            val pixels = rule.onNode(isDialog()).captureToImage().toPixelMap()
            val point = rowSample()
            val actualCell = sample(pixels, point)
            val row = rule.onNodeWithTag("material-reference-rows").fetchSemanticsNode().boundsInRoot
            val actualCanvas = sample(pixels, Offset(point.x, row.bottom + 20f * density))
            listOf(actualCell to cell, actualCanvas to canvas).forEach { (actual, expected) ->
                assertEquals(expected.red * 255f, actual.red * 255f, 1f)
                assertEquals(expected.green * 255f, actual.green * 255f, 1f)
                assertEquals(expected.blue * 255f, actual.blue * 255f, 1f)
            }
        }
        rule.waitForIdle()
        save("sheet-reference-baseline.png", modal = false)
        rule.runOnIdle { visible = true }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Medium && !sheet.isAnimationRunning }
        val lightBare = rule.onNode(isDialog()).captureToImage().toPixelMap()
        save("sheet-reference-medium.png")
        rule.runOnIdle { scope.launch { sheet.expand() } }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Large && !sheet.isAnimationRunning }
        rule.waitForIdle()
        rule.onNodeWithText("Native material reference").assertIsDisplayed()
        save("sheet-reference-large.png")
        rule.runOnIdle { scope.launch { sheet.partialExpand() } }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Medium && !sheet.isAnimationRunning }
        rule.runOnIdle { table = true }
        rule.waitForIdle()
        save("sheet-reference-table-medium.png")
        assertBias(lightBare, -15)
        rule.mainClock.autoAdvance = false
        try {
            rule.runOnIdle { scope.launch { sheet.expand() } }
            rule.mainClock.advanceTimeUntil(1_000) { sheet.expansionProgress > 0.5f }
            rule.mainClock.advanceTimeByFrame()
            val carrier = sample(rule.onNode(isDialog()).captureToImage().toPixelMap(), rowSample())
            // UIKit replaces the vibrant carrier at the role edge, before detent settling.
            assertEquals(255f, carrier.red * 255f, 1f)
            assertEquals(255f, carrier.green * 255f, 1f)
            assertEquals(255f, carrier.blue * 255f, 1f)
        } finally {
            rule.mainClock.autoAdvance = true
        }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Large && !sheet.isAnimationRunning }
        save("sheet-reference-table-large.png")
        assertOpaqueTable(Color.White, Color(0xFFF2F2F7))
        rule.runOnIdle { visible = false }
        rule.waitUntil(10_000) { !sheet.isVisible && !sheet.isAnimationRunning }
        rule.waitForIdle()
        rule.runOnIdle { dark = true; table = false; visible = true }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Medium && !sheet.isAnimationRunning }
        save("sheet-reference-dark-medium.png")
        val darkBare = rule.onNode(isDialog()).captureToImage().toPixelMap()
        rule.runOnIdle { table = true }
        rule.waitForIdle()
        save("sheet-reference-dark-table-medium.png")
        assertBias(darkBare, 15)
        rule.runOnIdle { scope.launch { sheet.expand() } }
        rule.waitUntil(10_000) { sheet.currentValue == GlasmioSheetValue.Large && !sheet.isAnimationRunning }
        save("sheet-reference-dark-table-large.png")
        assertOpaqueTable(Color(0xFF2C2C2E), Color(0xFF1C1C1E))
    }

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
