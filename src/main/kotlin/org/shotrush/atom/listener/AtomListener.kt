package org.shotrush.atom.listener

import com.github.shynixn.mccoroutine.folia.registerSuspendingEvents
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.shotrush.atom.Atom
import kotlin.coroutines.CoroutineContext

typealias EventClass = Class<out Event>
typealias EventRunner = (event: Event) -> CoroutineContext
inline fun <reified T : Event> eventDef(noinline runner: (event: T) -> CoroutineContext): Pair<EventClass, EventRunner> {
    return (T::class.java to runner) as Pair<EventClass, EventRunner>
}

interface AtomListener : Listener {
    val eventDefs: Map<EventClass, EventRunner>
}


fun AtomListener.register() = Atom.instance.server.pluginManager.registerSuspendingEvents(this, Atom.instance, eventDefs)