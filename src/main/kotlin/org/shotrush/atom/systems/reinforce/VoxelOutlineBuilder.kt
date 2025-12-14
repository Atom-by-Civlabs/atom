package org.shotrush.atom.systems.reinforce

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import org.bukkit.Color
import org.bukkit.util.Vector


class VoxelOutlineBuilder {
    suspend fun build(
        cells: List<CellDTO>,
    ): List<OutlineGeometry> = withContext(Dispatchers.Default) {
        // one geometry set per ReinforceType of interest
        val out = ArrayList<OutlineGeometry>(2)
        for (t in ReinforceType.entries) {
            val segs = buildOutlineFromVoxelShape(cells, t)
            if (segs.isNotEmpty()) {
                val color = when (t) {
                    ReinforceType.LIGHT -> Color.fromRGB(184, 115, 51) // copper-ish
                    ReinforceType.MEDIUM -> Color.fromRGB(200, 200, 200) // iron-ish
                    else -> Color.WHITE
                }
                out.add(OutlineGeometry.Segments3D(t, segs, color))
            }
        }
        out
    }

    enum class Axis { X, Y, Z }

    fun buildOutlineFromVoxelShape(
        cells: List<CellDTO>,
        type: ReinforceType,
    ): List<Pair<Vector, Vector>> {
        // 1) Union into a VoxelShape
        var shape = Shapes.empty()
        for (c in cells) {
            if (c.type != type) continue
            shape = Shapes.joinUnoptimized(shape, c.shape, BooleanOp.OR)
        }
        shape = shape.optimize()

        // 2) Iterate merged boxes and emit edges
        val segs = ArrayList<Pair<Vector, Vector>>()

        shape.forAllEdges { x1, y1, z1, x2, y2, z2 ->
            // If you want a uniform tiny epsilon instead of face-based:
            val a = Vector(x1, y1, z1)
            val b = Vector(x2, y2, z2)
            segs.add(a to b)
        }
        return segs
    }
}