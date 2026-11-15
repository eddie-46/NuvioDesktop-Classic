package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.focus.Focusability
import androidx.compose.ui.focus.getFocusedRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    /** How often (ms) the ring position is refreshed while the keyboard is in use. */
    const val RING_REFRESH_MS = 16L

    /**
     * A focused item covering more than this share of the window is treated as a container
     * (a page or a scrolling area), and arrows step into it instead of past it.
     */
    const val CONTAINER_AREA_FRACTION = 0.25f

    /** F9 shows a small on-screen readout of what the keyboard navigation is doing. */
    const val DEBUG_OVERLAY_KEY_ENABLED = true
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun KeyboardFocusHost(content: @Composable () -> Unit) {
    if (!isDesktop || !KeyboardNavigationConfig.ENABLED) {
        content()
        return
    }

    val focusManager = LocalFocusManager.current
    val hostState = remember { KeyboardFocusHostState() }
    var keyboardMode by remember { mutableStateOf(false) }
    var focusedRect by remember { mutableStateOf<Rect?>(null) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var debugVisible by remember { mutableStateOf(false) }
    var debugLine by remember { mutableStateOf("no arrow key seen yet") }
    val ringColor = MaterialTheme.colorScheme.onBackground

    // Keep focus on the host whenever nothing else holds it, so the first arrow press
    // always has somewhere to start from.
    LaunchedEffect(hostState.hasFocus) {
        while (!hostState.hasFocus) {
            runCatching { hostState.node?.requestFocus() }
            delay(250)
        }
    }

    fun currentRect(): Rect? = runCatching { hostState.node?.getFocusedRect() }.getOrNull()

    fun isContainer(rect: Rect?): Boolean {
        if (hostState.selfFocused) return true
        val hostArea = hostSize.width.toFloat() * hostSize.height.toFloat()
        if (rect == null || hostArea <= 0f) return false
        return rect.width * rect.height > hostArea * KeyboardNavigationConfig.CONTAINER_AREA_FRACTION
    }

    // Compose's own arrow-key search never steps *into* the item that currently holds focus,
    // so a focused page or scrolling area would trap the keyboard. Step into containers here.
    fun moveWithArrows(direction: FocusDirection): Boolean {
        val startedInContainer = isContainer(currentRect())
        var entered = 0
        var moved = false
        if (startedInContainer && focusManager.moveFocus(FocusDirection.Enter)) {
            moved = true
            entered++
        }
        if (!moved) moved = focusManager.moveFocus(direction)
        if (!moved && !startedInContainer && focusManager.moveFocus(FocusDirection.Enter)) {
            moved = true
            entered++
        }
        var guard = 0
        while (moved && guard < 3 && isContainer(currentRect())) {
            if (!focusManager.moveFocus(FocusDirection.Enter)) break
            entered++
            guard++
        }
        debugLine = "dir=$direction moved=$moved entered=$entered startContainer=$startedInContainer"
        return moved
    }

    // Follow the focused item while the keyboard is in use (it moves when lists scroll).
    LaunchedEffect(keyboardMode) {
        if (!keyboardMode) {
            focusedRect = null
            return@LaunchedEffect
        }
        while (true) {
            val rect = runCatching { hostState.node?.getFocusedRect() }.getOrNull()
            if (rect != focusedRect) focusedRect = rect
            delay(KeyboardNavigationConfig.RING_REFRESH_MS)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { hostSize = it }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key.isNavigationKey()) {
                    keyboardMode = true
                }
                if (KeyboardNavigationConfig.DEBUG_OVERLAY_KEY_ENABLED &&
                    event.type == KeyEventType.KeyDown && event.key == Key.F9
                ) {
                    debugVisible = !debugVisible
                    return@onPreviewKeyEvent true
                }
                // Text fields swallow Up/Down for the cursor, which would trap focus inside
                // them. Take those two before the field sees them; Left/Right stay with the field.
                if (KeyboardNavigationConfig.ARROW_KEYS_MOVE_FOCUS &&
                    event.type == KeyEventType.KeyDown &&
                    !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
                ) {
                    val vertical = when (event.key) {
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        else -> null
                    }
                    if (vertical != null && moveWithArrows(vertical)) {
                        return@onPreviewKeyEvent true
                    }
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
                moveWithArrows(direction)
            }
            .then(KeyboardFocusHostElement(hostState))
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
            },
    ) {
        content()
        if (KeyboardNavigationConfig.SHOW_FOCUS_RING && keyboardMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val bounds = focusedRect ?: return@drawBehind
                        if (bounds.isEmpty) return@drawBehind
                        // The host itself holding focus is not something to outline.
                        if (bounds.width >= size.width * 0.95f && bounds.height >= size.height * 0.95f) {
                            return@drawBehind
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
            )
        }
        if (debugVisible) {
            val rect = focusedRect
            Text(
                text = "keyboard nav | hostHasFocus=${hostState.hasFocus} hostSelf=${hostState.selfFocused} " +
                    "keyboardMode=$keyboardMode\n" +
                    "focused=${rect?.let { "${it.left.toInt()},${it.top.toInt()} ${it.width.toInt()}x${it.height.toInt()}" } ?: "none"} " +
                    "window=${hostSize.width}x${hostSize.height}\n" +
                    debugLine,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

private class KeyboardFocusHostState {
    var hasFocus by mutableStateOf(false)
    var selfFocused by mutableStateOf(false)
    var node: FocusTargetModifierNode? = null
}

private data class KeyboardFocusHostElement(
    val state: KeyboardFocusHostState,
) : ModifierNodeElement<KeyboardFocusHostNode>() {
    override fun create(): KeyboardFocusHostNode = KeyboardFocusHostNode(state)

    override fun update(node: KeyboardFocusHostNode) {
        node.updateState(state)
    }
}

private class KeyboardFocusHostNode(
    private var state: KeyboardFocusHostState,
) : DelegatingNode() {
    private val focusTarget = delegate(
        FocusTargetModifierNode(
            focusability = Focusability.Always,
            onFocusChange = { _, current ->
                state.hasFocus = current.hasFocus
                state.selfFocused = current.isFocused
            },
        ),
    )

    override fun onAttach() {
        state.node = focusTarget
    }

    override fun onDetach() {
        if (state.node === focusTarget) state.node = null
        state.hasFocus = false
        state.selfFocused = false
    }

    fun updateState(newState: KeyboardFocusHostState) {
        if (newState === state) return
        if (state.node === focusTarget) state.node = null
        state = newState
        if (isAttached) state.node = focusTarget
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
