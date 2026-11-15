package com.nuvio.app.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Classic fork: plain-text status lines shown in the F9 readout, for tracking down problems
 * that cannot be seen from outside the app.
 */
internal object ClassicDiagnostics {
    /** What the last skip-intro lookup decided before asking IntroDB. */
    var skipLookup by mutableStateOf("skip lookup: none yet")

    /** What the last IntroDB request returned. */
    var skipIntroDb by mutableStateOf("IntroDB: no request yet")
}
