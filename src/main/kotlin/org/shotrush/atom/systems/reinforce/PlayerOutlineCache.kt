package org.shotrush.atom.systems.reinforce

import java.time.Instant

data class PlayerOutlineCache(
    val originX: Int,
    val originZ: Int,
    val halfSize: Int,
    val builtAt: Instant,
    val geometry: List<OutlineGeometry>,
)