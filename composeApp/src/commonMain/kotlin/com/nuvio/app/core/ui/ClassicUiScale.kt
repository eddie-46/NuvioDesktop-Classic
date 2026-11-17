package com.nuvio.app.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Classic fork: user-adjustable interface size for the desktop app, as a percentage applied on
 * top of the app's own window-based scaling. Saved between runs through [store], which the
 * desktop entry point supplies.
 */
internal object ClassicUiScale {
    const val MIN_PERCENT = 60
    const val MAX_PERCENT = 130
    const val DEFAULT_PERCENT = 100
    const val STEP_PERCENT = 5

    private const val KEY = "classic_ui_scale_percent"

    interface Store {
        fun getString(key: String): String?
        fun putString(key: String, value: String)
    }

    private var store: Store? = null

    var percent by mutableIntStateOf(DEFAULT_PERCENT)
        private set

    val factor: Float
        get() = percent / 100f

    fun attach(newStore: Store) {
        store = newStore
        percent = newStore.getString(KEY)?.toIntOrNull()?.coerceIn(MIN_PERCENT, MAX_PERCENT) ?: DEFAULT_PERCENT
    }

    fun set(newPercent: Int) {
        val snapped = ((newPercent + STEP_PERCENT / 2) / STEP_PERCENT * STEP_PERCENT)
            .coerceIn(MIN_PERCENT, MAX_PERCENT)
        if (snapped == percent) return
        percent = snapped
        store?.putString(KEY, snapped.toString())
    }
}
