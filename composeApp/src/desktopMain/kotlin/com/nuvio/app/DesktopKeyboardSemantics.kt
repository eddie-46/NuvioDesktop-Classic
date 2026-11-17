package com.nuvio.app

import androidx.compose.ui.semantics.SemanticsOwner
import java.awt.Component
import java.awt.Container
import java.lang.reflect.Method

/**
 * Classic fork: hands the window's semantics trees to the keyboard navigation.
 *
 * Compose's Swing hosts expose `semanticsOwners`, but which host class backs the window is an
 * implementation detail, so the property is located by name. More than one component (and more
 * than one method on a component) can match, and reflection does not return them in a stable
 * order, so every match is asked and the answers are combined rather than trusting the first.
 */
internal class DesktopSemanticsOwnersProvider(
    private val window: Component,
) : () -> Collection<SemanticsOwner> {
    private class Source(val target: Any, val getter: Method)

    private var sources: List<Source> = emptyList()

    override fun invoke(): Collection<SemanticsOwner> {
        var owners = collect()
        if (owners.isEmpty()) {
            // Nothing yet, or the component tree changed: look again.
            sources = locate()
            owners = collect()
        }
        return owners
    }

    private fun collect(): List<SemanticsOwner> {
        val owners = LinkedHashSet<SemanticsOwner>()
        for (source in sources) {
            val result = runCatching { source.getter.invoke(source.target) }.getOrNull() as? Collection<*>
            result?.forEach { if (it is SemanticsOwner) owners += it }
        }
        return owners.toList()
    }

    private fun locate(): List<Source> {
        val found = ArrayList<Source>()
        visit(window, found)
        return found
    }

    private fun visit(component: Component, found: MutableList<Source>) {
        component.javaClass.methods
            .filter { it.name.startsWith("getSemanticsOwners") && it.parameterCount == 0 }
            .sortedBy { it.name }
            .forEach { method ->
                runCatching { method.isAccessible = true }
                found += Source(component, method)
            }
        if (component is Container) {
            for (child in component.components) visit(child, found)
        }
    }
}

/** Classic fork: gives the interface-size setting somewhere to save itself. */
internal fun attachClassicSettingsStore() {
    val store = com.nuvio.app.core.storage.DesktopStorage.store("nuvio_classic_settings")
    com.nuvio.app.core.ui.ClassicUiScale.attach(
        object : com.nuvio.app.core.ui.ClassicUiScale.Store {
            override fun getString(key: String): String? = store.getString(key)
            override fun putString(key: String, value: String) {
                store.putString(key, value)
            }
        },
    )
}
