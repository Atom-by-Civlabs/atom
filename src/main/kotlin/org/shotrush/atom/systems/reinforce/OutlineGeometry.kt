package org.shotrush.atom.systems.reinforce

import org.bukkit.Color
import org.bukkit.util.Vector

sealed interface OutlineGeometry {
    val type: ReinforceType

    data class Polyline2D(
        override val type: ReinforceType,
        val polylines: List<List<Vector>>, // world-space points at a flat Y
        val color: Color
    ) : OutlineGeometry

    data class Segments3D(
        override val type: ReinforceType,
        val segments: List<Pair<Vector, Vector>>, // world-space segments
        val color: Color
    ) : OutlineGeometry
}