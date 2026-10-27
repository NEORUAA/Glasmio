package io.github.neoruaa.glasmio.scaffold.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Lifecycle 2.10 compatibility for the upstream scene lifecycle cap. */
@Composable
internal fun rememberLifecycleOwner(maxLifecycle: Lifecycle.State): LifecycleOwner {
    val parent = LocalLifecycleOwner.current
    val owner = remember(parent) {
        object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
    }
    DisposableEffect(owner, parent, maxLifecycle) {
        fun update() {
            owner.registry.currentState = minOf(parent.lifecycle.currentState, maxLifecycle)
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        parent.lifecycle.addObserver(observer)
        update()
        onDispose { parent.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(owner) { onDispose { owner.registry.currentState = Lifecycle.State.DESTROYED } }
    return owner
}
