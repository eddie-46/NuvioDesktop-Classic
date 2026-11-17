package com.nuvio.app.core.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.nuvio.app.isDesktop
import com.nuvio.app.navigation.AppRoute

/**
 * Classic fork: shows the list of sources in a panel on the details page instead of opening
 * a separate sources page.
 *
 * The details page registers itself as the host while it is on screen. When something asks to
 * open the sources page and the host is the page currently showing, the request is kept here
 * and the details page draws it in a side panel.
 */
internal class ClassicInlineSourcesRequest(
    val host: AppRoute,
    val launchId: Long,
    val title: String,
)

internal object ClassicInlineSources {
    private var hostRoute: AppRoute? by mutableStateOf(null)

    var request: ClassicInlineSourcesRequest? by mutableStateOf(null)
        private set

    /** Where the show page's episode column sits in the window, while it is showing. */
    var episodeColumnBounds: Rect? by mutableStateOf(null)

    /** Where the panel sits in the window, while it is showing. Used by keyboard navigation. */
    var panelBounds: Rect? by mutableStateOf(null)

    val enabled: Boolean
        get() = isDesktop && ClassicLayoutConfig.INLINE_SOURCES_ENABLED

    private var pendingRoute: AppRoute? = null
    private var pendingAction: (() -> Unit)? = null

    /**
     * Runs [action] as soon as the details page for [route] is on screen and hosting. Used to
     * open a page with its sources panel already showing.
     */
    fun runWhenHosted(route: AppRoute, action: () -> Unit) {
        pendingRoute = route
        pendingAction = action
    }

    fun attach(route: AppRoute) {
        hostRoute = route
        if (pendingRoute == route) {
            val action = pendingAction
            pendingRoute = null
            pendingAction = null
            action?.invoke()
        }
    }

    /** [stillOnBackStack] is false once the page has been closed for good, not just covered. */
    fun detach(route: AppRoute, stillOnBackStack: Boolean) {
        if (hostRoute == route) hostRoute = null
        if (!stillOnBackStack) close(route)
    }

    /** Returns true when the sources were sent to the details page's panel. */
    fun tryShow(currentRoute: AppRoute?, launchId: Long, title: String): Boolean {
        if (!enabled) return false
        val host = hostRoute ?: return false
        if (currentRoute != host) return false
        request = ClassicInlineSourcesRequest(host = host, launchId = launchId, title = title)
        return true
    }

    fun close(route: AppRoute) {
        if (request?.host == route) request = null
    }

    /** Closes whichever panel is open. */
    fun closeActive() {
        request = null
    }
}

/** Set inside the panel so the sources list's back button closes the panel. */
internal val LocalClassicInlineSourcesClose = compositionLocalOf<(() -> Unit)?> { null }
