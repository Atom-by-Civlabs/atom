package org.shotrush.atom.systems.physics

import org.shotrush.atom.listener.AtomListener
import org.shotrush.atom.listener.EventClass
import org.shotrush.atom.listener.EventRunner

object GravitySystem : AtomListener {
    override val eventDefs = mapOf<EventClass, EventRunner>()
}