package com.nuvio.app

import androidx.compose.ui.semantics.SemanticsOwner
import java.awt.Component
import java.awt.Container
import java.lang.reflect.Method

/**
 * Classic fork: hands the window's semantics trees to the keyboard navigation.
 *
 * Compose's Swing hosts expose `semanticsOwners`, but which host class backs the window is an
 * implementation detail, so the property is located by name on whichever component has it.
 */
internal class DesktopSemanticsOwnersProvider(
    private val window: Component,
) : () -> Collection<SemanticsOwner> {
    private var source: Any? = null
    private var getter: Method? = null

    override fun invoke(): Collection<SemanticsOwner> {
        if (getter == null) locate(window)
        val method = getter ?: return emptyList()
        val owners = runCatching { method.invoke(source) }.getOrNull() as? Collection<*>
        return owners?.filterIsInstance<SemanticsOwner>() ?: emptyList()
    }

    private fun locate(component: Component): Boolean {
        val method = component.javaClass.methods.firstOrNull {
            it.name.startsWith("getSemanticsOwners") && it.parameterCount == 0
        }
        if (method != null) {
            runCatching { method.isAccessible = true }
            source = component
            getter = method
            return true
        }
        if (component is Container) {
            for (child in component.components) {
                if (locate(child)) return true
            }
        }
        return false
    }
}
