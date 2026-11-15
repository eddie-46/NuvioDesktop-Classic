package com.nuvio.app.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop
import kotlinx.coroutines.delay

/**
 * Classic fork: keyboard / remote navigation for the desktop app.
 *
 * - Arrow keys move focus between anything clickable (posters, buttons, tabs, list rows).
 * - Enter activates the focused item (Compose's clickable already does this once it has focus).
 * - A focus ring is drawn around the focused item, but only while the keyboard is being used;
 *   moving the mouse or touchpad hides it again so mouse users see no change.
 *
 * The host keeps focus on itself whenever nothing else is focused, so the first arrow press
 * always has somewhere to start from.
 */
internal object KeyboardNavigationConfig {
    /** Master switch for the whole feature. */
    const val ENABLED = true

    /** Draw the focus ring around the focused item. */
    const val SHOW_FOCUS_RING = true

    /** Arrow keys move focus. Tab / Shift+Tab always work regardless. */
    const val ARROW_KEYS_MOVE_FOCUS = true

    /** Pointer travel (px) that switches back from keyboard mode to mouse mode. */
    const val POINTER_MOVE_THRESHOLD_PX = 4f

    const val RING_WIDTH_DP = 3
    const val RING_RADIUS_DP = 8
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KeyboardFocusHost(content: @Composable () -> Unit) {
    if (!isDesktop || !KeyboardNavigationConfig.ENABLED) {
        content()
        return
    }

    val focusManager = LocalFocusManager.current
    val hostFocus = remember { FocusRequester() }
    var keyboardMode by remember { mutableStateOf(false) }
    var needsHostFocus by remember { mutableStateOf(true) }
    var hostCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var focusedCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    val ringColor = MaterialTheme.colorScheme.onBackground

    LaunchedEffect(needsHostFocus) {
        if (!needsHostFocus) return@LaunchedEffect
        // The host may not be laid out yet on the first pass, so retry briefly.
        repeat(10) {
            if (runCatching { hostFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(100)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { hostCoordinates = it }
            .onFocusChanged { needsHostFocus = !it.hasFocus }
            .onFocusedBoundsChanged { coordinates ->
                focusedCoordinates = coordinates
                focusTick++
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key.isNavigationKey()) {
                    keyboardMode = true
                }
                false
            }
            .onKeyEvent { event ->
                if (!KeyboardNavigationConfig.ARROW_KEYS_MOVE_FOCUS) return@onKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return@onKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> null
                } ?: return@onKeyEvent false
                focusManager.moveFocus(direction)
            }
            .focusRequester(hostFocus)
            .focusable()
            .pointerInput(Unit) {
                var lastPosition: Offset? = null
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val position = event.changes.firstOrNull()?.position
                        when (event.type) {
                            PointerEventType.Press, PointerEventType.Scroll -> keyboardMode = false
                            PointerEventType.Move -> {
                                // Layout changes under a resting pointer can re-send its position,
                                // so only real travel counts as "the mouse is being used".
                                val last = lastPosition
                                if (position != null && last != null &&
                                    (position - last).getDistance() >
                                    KeyboardNavigationConfig.POINTER_MOVE_THRESHOLD_PX
                                ) {
                                    keyboardMode = false
                                }
                            }
                            else -> Unit
                        }
                        if (position != null) lastPosition = position
                    }
                }
            }
            .drawWithContent {
                drawContent()
                // Reading the tick redraws the ring when the focused item moves (e.g. scrolling).
                @Suppress("UNUSED_VARIABLE")
                val tick = focusTick
                if (!KeyboardNavigationConfig.SHOW_FOCUS_RING || !keyboardMode) return@drawWithContent
                val host = hostCoordinates?.takeIf { it.isAttached } ?: return@drawWithContent
                val target = focusedCoordinates?.takeIf { it.isAttached } ?: return@drawWithContent
                val bounds = runCatching { host.localBoundingBoxOf(target, clipBounds = false) }
                    .getOrNull() ?: return@drawWithContent
                if (bounds.isEmpty) return@drawWithContent
                // The host itself holding focus is not something to outline.
                if (bounds.width >= size.width * 0.95f && bounds.height >= size.height * 0.95f) {
                    return@drawWithContent
                }
                val ringWidth = KeyboardNavigationConfig.RING_WIDTH_DP.dp.toPx()
                val radius = CornerRadius(KeyboardNavigationConfig.RING_RADIUS_DP.dp.toPx())
                val outer = bounds.inflate(ringWidth * 1.5f)
                val inner = bounds.inflate(ringWidth * 0.5f)
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.55f),
                    topLeft = outer.topLeft,
                    size = outer.size,
                    cornerRadius = radius,
                    style = Stroke(width = ringWidth),
                )
                drawRoundRect(
                    color = ringColor,
                    topLeft = inner.topLeft,
                    size = inner.size,
                    cornerRadius = radius,
                    style = Stroke(width = ringWidth),
                )
            },
    ) {
        content()
    }
}

private fun Key.isNavigationKey(): Boolean =
    this == Key.DirectionUp ||
        this == Key.DirectionDown ||
        this == Key.DirectionLeft ||
        this == Key.DirectionRight ||
        this == Key.Tab ||
        this == Key.Enter ||
        this == Key.NumPadEnter
