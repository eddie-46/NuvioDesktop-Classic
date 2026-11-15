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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.nuvio.app.isDesktop
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Classic fork: keyboard / remote navigation for the desktop app.
 *
 * - Arrow keys move focus between anything clickable (posters, buttons, tabs, list rows).
 * - Enter activates the focused item (Compose's clickable already does this once it has focus).
 * - A focus ring is drawn around the focused item, but only while the keyboard is being used;
 *   moving the mouse or touchpad hides it again so mouse users see no change.
 *
 * Compose's built-in arrow-key search cannot reach items inside a scrolling page from a button
 * floating on top of that page, so the search is done here instead: every focusable item on
 * screen is read from the window's semantics tree and the nearest one in the pressed direction
 * is focused.
 */
internal object KeyboardNavigationConfig {
    /** Master switch for the whole feature. */
    const val ENABLED = true

    /** Draw the focus ring around the focused item. */
    const val SHOW_FOCUS_RING = true

    /** Arrow keys move focus. Tab / Shift+Tab always work regardless. */
    const val ARROW_KEYS_MOVE_FOCUS = true

    /** When nothing lies in the pressed direction, scroll the surrounding list instead. */
    const val SCROLL_WHEN_NOTHING_TO_FOCUS = true

    /** Share of the surrounding list scrolled by one arrow press in that case. */
    const val SCROLL_STEP_FRACTION = 0.6f

    /** After such a scroll, wait this long (ms) for it to settle, then look again for a target. */
    const val SCROLL_SETTLE_MS = 350L

    /** Items covering more than this share of the window are page backgrounds, not targets. */
    const val MAX_TARGET_AREA_FRACTION = 0.6f

    /** How much sideways distance counts against a target compared with forward distance. */
    const val SIDEWAYS_DISTANCE_WEIGHT = 2f

    /** Pointer travel (px) that switches back from keyboard mode to mouse mode. */
    const val POINTER_MOVE_THRESHOLD_PX = 4f

    const val RING_WIDTH_DP = 3
    const val RING_RADIUS_DP = 8

    /** How often (ms) the ring position is refreshed while the keyboard is in use. */
    const val RING_REFRESH_MS = 16L

    /** F9 shows a small on-screen readout of what the keyboard navigation is doing. */
    const val DEBUG_OVERLAY_KEY_ENABLED = true
}

/**
 * Supplies the semantics trees of the window hosting the app (main content first, pop-ups and
 * dialogs after it). Provided by the desktop entry point; empty elsewhere.
 */
internal val LocalKeyboardSemanticsOwners =
    staticCompositionLocalOf<() -> Collection<SemanticsOwner>> { { emptyList() } }

@Composable
internal fun KeyboardFocusHost(content: @Composable () -> Unit) {
    if (!isDesktop || !KeyboardNavigationConfig.ENABLED) {
        content()
        return
    }

    val focusManager = LocalFocusManager.current
    val semanticsOwners = LocalKeyboardSemanticsOwners.current
    val hostState = remember { KeyboardFocusHostState() }
    var keyboardMode by remember { mutableStateOf(false) }
    var focusedRect by remember { mutableStateOf<Rect?>(null) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var debugVisible by remember { mutableStateOf(false) }
    var debugLine by remember { mutableStateOf("no arrow key seen yet") }
    val ringColor = MaterialTheme.colorScheme.onBackground

    // Keep focus on the host whenever nothing else holds it, so key presses always arrive here.
    LaunchedEffect(hostState.hasFocus) {
        while (!hostState.hasFocus) {
            runCatching { hostState.node?.requestFocus() }
            delay(250)
        }
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

    val scope = rememberCoroutineScope()
    val retryHolder = remember { RetryHolder() }
    // Where focus last landed. If that item then disappears (a section that hides itself,
    // a list that reloads), the next arrow press carries on from here instead of starting over.
    val lastLanding = remember { LastLanding() }

    fun searchAndMove(direction: FocusDirection, allowScroll: Boolean): SemanticsMoveOutcome {
        val windowArea = hostSize.width.toFloat() * hostSize.height.toFloat()
        // Some items (text boxes inside a wrapper, for one) do not report themselves as focused
        // in the semantics tree, so the focused box known to the host is the fallback origin.
        val hostFocusedRect = if (hostState.selfFocused) {
            null
        } else {
            runCatching { hostState.node?.getFocusedRect() }.getOrNull()
        }
        val outcome = runCatching {
            moveFocusBySemantics(
                owners = semanticsOwners(),
                direction = direction,
                windowArea = windowArea,
                fallbackSource = hostFocusedRect,
                rememberedSource = lastLanding.rect,
                allowScroll = allowScroll,
            )
        }.getOrElse { error ->
            SemanticsMoveOutcome(moved = false, how = "error:${error::class.simpleName}")
        }
        if (outcome.to != null) lastLanding.rect = outcome.to
        return outcome
    }

    fun describeOutcome(direction: FocusDirection, outcome: SemanticsMoveOutcome, how: String, moved: Boolean) =
        "dir=$direction how=$how moved=$moved targets=${outcome.targets} " +
            "layers=${outcome.layers} from=${describeRect(outcome.from)} to=${describeRect(outcome.to)}"

    fun moveWithArrows(direction: FocusDirection): Boolean {
        retryHolder.job?.cancel()
        val outcome = searchAndMove(direction, allowScroll = KeyboardNavigationConfig.SCROLL_WHEN_NOTHING_TO_FOCUS)
        var moved = outcome.moved
        var how = outcome.how
        if (!moved && focusManager.moveFocus(direction)) {
            moved = true
            how += "+compose"
        }
        debugLine = describeOutcome(direction, outcome, how, moved)
        if (outcome.scrolled) {
            // The next row was out of view: once the scroll settles, land on it.
            retryHolder.job = scope.launch {
                delay(KeyboardNavigationConfig.SCROLL_SETTLE_MS)
                val retry = searchAndMove(direction, allowScroll = false)
                debugLine = describeOutcome(direction, retry, "scrolled-then-${retry.how}", retry.moved)
            }
        }
        return moved
    }

    fun arrowDirection(key: Key): FocusDirection? = when (key) {
        Key.DirectionUp -> FocusDirection.Up
        Key.DirectionDown -> FocusDirection.Down
        Key.DirectionLeft -> FocusDirection.Left
        Key.DirectionRight -> FocusDirection.Right
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { hostSize = it }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key.isNavigationKey()) keyboardMode = true
                if (KeyboardNavigationConfig.DEBUG_OVERLAY_KEY_ENABLED && event.key == Key.F9) {
                    debugVisible = !debugVisible
                    return@onPreviewKeyEvent true
                }
                // Text fields swallow Up/Down for the cursor, which would trap focus inside
                // them. Take those two before the field sees them; Left/Right stay with the field.
                if (KeyboardNavigationConfig.ARROW_KEYS_MOVE_FOCUS &&
                    !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed &&
                    (event.key == Key.DirectionUp || event.key == Key.DirectionDown)
                ) {
                    val direction = arrowDirection(event.key)
                    if (direction != null && moveWithArrows(direction)) {
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .onKeyEvent { event ->
                if (!KeyboardNavigationConfig.ARROW_KEYS_MOVE_FOCUS) return@onKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return@onKeyEvent false
                val direction = arrowDirection(event.key) ?: return@onKeyEvent false
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
            Text(
                text = "keyboard nav | hostHasFocus=${hostState.hasFocus} hostSelf=${hostState.selfFocused} " +
                    "keyboardMode=$keyboardMode\n" +
                    "ring=${describeRect(focusedRect)} window=${hostSize.width}x${hostSize.height}\n" +
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

// ---------------------------------------------------------------------------------------------
// Arrow-key search over the semantics tree
// ---------------------------------------------------------------------------------------------

private class RetryHolder {
    var job: Job? = null
}

private class LastLanding {
    var rect: Rect? = null
}

private class SemanticsMoveOutcome(
    val moved: Boolean,
    val how: String,
    val targets: Int = 0,
    val layers: Int = 0,
    val from: Rect? = null,
    val to: Rect? = null,
    val scrolled: Boolean = false,
)

private class FocusTarget(
    val node: SemanticsNode,
    /** The visible part of the item. */
    val bounds: Rect,
    /** The whole item, including any part scrolled out of view. */
    val fullBounds: Rect,
    val layer: Int,
    val focused: Boolean,
)

private class ScrollArea(
    val node: SemanticsNode,
    val bounds: Rect,
    val layer: Int,
)

private fun moveFocusBySemantics(
    owners: Collection<SemanticsOwner>,
    direction: FocusDirection,
    windowArea: Float,
    fallbackSource: Rect?,
    rememberedSource: Rect?,
    allowScroll: Boolean,
): SemanticsMoveOutcome {
    if (owners.isEmpty()) return SemanticsMoveOutcome(moved = false, how = "no-semantics")

    val all = ArrayList<FocusTarget>()
    val scrollAreas = ArrayList<ScrollArea>()
    owners.forEachIndexed { layer, owner ->
        owner.getAllSemanticsNodes(mergingEnabled = true).forEach { node ->
            val config = node.config
            val visible = node.boundsInWindow
            if (config.getOrNull(SemanticsActions.ScrollBy)?.action != null &&
                visible.width >= 2f && visible.height >= 2f
            ) {
                scrollAreas += ScrollArea(node = node, bounds = visible, layer = layer)
            }
            val focused = config.getOrNull(SemanticsProperties.Focused) ?: return@forEach
            if (config.contains(SemanticsProperties.Disabled)) return@forEach
            if (config.getOrNull(SemanticsActions.RequestFocus)?.action == null) return@forEach
            // Items scrolled fully out of view report an empty visible box. Keep the focused
            // one anyway: it is where the search starts from.
            if (!focused && (visible.width < 2f || visible.height < 2f)) return@forEach
            all += FocusTarget(
                node = node,
                bounds = visible,
                fullBounds = Rect(node.positionInWindow, node.size.toSize()),
                layer = layer,
                focused = focused,
            )
        }
    }
    if (all.isEmpty()) return SemanticsMoveOutcome(moved = false, how = "no-targets", layers = owners.size)

    // A dialog or pop-up sits in a later layer; while one is open, stay inside it.
    val activeLayer = all.maxOf { it.layer }
    val focusedTarget = all.lastOrNull { it.focused && it.layer == activeLayer }
    val liveSource: Rect? = focusedTarget?.fullBounds ?: fallbackSource
    // Nothing holds focus right now: carry on from where focus last was, if known.
    val source: Rect? = liveSource ?: rememberedSource
    val pool = all.filter { target ->
        target.layer == activeLayer &&
            !target.focused &&
            target.bounds.width >= 2f && target.bounds.height >= 2f &&
            // A wrapper drawn exactly where the focused item is counts as the same item.
            (source == null || !sameBox(source, target.fullBounds)) &&
            (windowArea <= 0f ||
                target.bounds.width * target.bounds.height <=
                windowArea * KeyboardNavigationConfig.MAX_TARGET_AREA_FRACTION)
    }

    val directional: List<FocusTarget> = if (source == null) {
        emptyList()
    } else {
        pool.mapNotNull { target ->
            directionalScore(source, target.bounds, direction)?.let { score -> target to score }
        }.sortedBy { it.second }.map { it.first }
    }
    // With no live focus and nothing that way from the remembered spot (or no remembered spot
    // at all), start from the item nearest the top-left corner.
    val startOver = liveSource == null && directional.isEmpty()
    val ranked: List<FocusTarget> = if (startOver) {
        pool.sortedBy { it.bounds.top + it.bounds.left }
    } else {
        directional
    }

    for (target in ranked.take(3)) {
        val focusedNow = target.node.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke() == true
        if (focusedNow) {
            return SemanticsMoveOutcome(
                moved = true,
                how = if (startOver) "first" else if (liveSource == null) "nearest-from-last" else "nearest",
                targets = pool.size,
                layers = owners.size,
                from = source,
                to = target.bounds,
            )
        }
    }

    if (liveSource != null && allowScroll) {
        val areas = scrollAreas.filter { it.layer == activeLayer }
        if (scrollTowards(areas, liveSource, direction)) {
            return SemanticsMoveOutcome(
                moved = true,
                how = "scrolled",
                targets = pool.size,
                layers = owners.size,
                from = source,
                scrolled = true,
            )
        }
    }

    return SemanticsMoveOutcome(
        moved = false,
        how = if (ranked.isEmpty()) "nothing-that-way" else "focus-refused",
        targets = pool.size,
        layers = owners.size,
        from = source,
    )
}

private fun sameBox(a: Rect, b: Rect): Boolean =
    abs(a.left - b.left) <= 3f && abs(a.top - b.top) <= 3f &&
        abs(a.right - b.right) <= 3f && abs(a.bottom - b.bottom) <= 3f

/**
 * Lower is better; null means the target is not in that direction at all. A target must start
 * past the middle of the current item, so neighbours in the same row never count as "below".
 */
private fun directionalScore(source: Rect, target: Rect, direction: FocusDirection): Float? {
    val forward: Float
    val sideways: Float
    when (direction) {
        FocusDirection.Down -> {
            if (target.top < source.center.y || target.bottom <= source.bottom) return null
            forward = max(0f, target.top - source.bottom)
            sideways = rangeGap(source.left, source.right, target.left, target.right)
        }
        FocusDirection.Up -> {
            if (target.bottom > source.center.y || target.top >= source.top) return null
            forward = max(0f, source.top - target.bottom)
            sideways = rangeGap(source.left, source.right, target.left, target.right)
        }
        FocusDirection.Right -> {
            if (target.left < source.center.x || target.right <= source.right) return null
            forward = max(0f, target.left - source.right)
            sideways = rangeGap(source.top, source.bottom, target.top, target.bottom)
        }
        FocusDirection.Left -> {
            if (target.right > source.center.x || target.left >= source.left) return null
            forward = max(0f, source.left - target.right)
            sideways = rangeGap(source.top, source.bottom, target.top, target.bottom)
        }
        else -> return null
    }
    return forward + sideways * KeyboardNavigationConfig.SIDEWAYS_DISTANCE_WEIGHT
}

/** Distance between two ranges on one axis; zero when they overlap. */
private fun rangeGap(aStart: Float, aEnd: Float, bStart: Float, bEnd: Float): Float = when {
    bStart > aEnd -> bStart - aEnd
    aStart > bEnd -> aStart - bEnd
    else -> 0f
}

/**
 * Scrolls the innermost list around [source] that runs along the pressed direction and still
 * has somewhere to go. A sideways row is skipped for Up/Down, and the page for Left/Right.
 */
private fun scrollTowards(areas: List<ScrollArea>, source: Rect, direction: FocusDirection): Boolean {
    val vertical = direction == FocusDirection.Down || direction == FocusDirection.Up
    val forward = direction == FocusDirection.Down || direction == FocusDirection.Right
    val usable = areas.filter { area ->
        val range = area.node.config.getOrNull(
            if (vertical) SemanticsProperties.VerticalScrollAxisRange else SemanticsProperties.HorizontalScrollAxisRange,
        ) ?: return@filter false
        val value = range.value()
        val towardsEnd = if (range.reverseScrolling) !forward else forward
        if (towardsEnd) value < range.maxValue() else value > 0f
    }
    if (usable.isEmpty()) return false
    val around = usable.filter { it.bounds.overlaps(source) }
    val chosen = (around.ifEmpty { usable }).minByOrNull { it.bounds.width * it.bounds.height } ?: return false
    val scrollBy = chosen.node.config.getOrNull(SemanticsActions.ScrollBy)?.action ?: return false
    val step = (if (vertical) chosen.bounds.height else chosen.bounds.width) *
        KeyboardNavigationConfig.SCROLL_STEP_FRACTION
    return when (direction) {
        FocusDirection.Down -> scrollBy(0f, step)
        FocusDirection.Up -> scrollBy(0f, -step)
        FocusDirection.Right -> scrollBy(step, 0f)
        FocusDirection.Left -> scrollBy(-step, 0f)
        else -> false
    }
}

private fun describeRect(rect: Rect?): String =
    rect?.let { "${it.left.toInt()},${it.top.toInt()} ${it.width.toInt()}x${it.height.toInt()}" } ?: "none"

// ---------------------------------------------------------------------------------------------
// Host focus target
// ---------------------------------------------------------------------------------------------

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
