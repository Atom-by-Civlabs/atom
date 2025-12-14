package org.shotrush.atom.systems.reinforce

import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.bukkit.entity.Player
import org.shotrush.atom.Atom

class SnapshotCollector(
    private val shapes: VoxelShapeProvider
) {
    companion object {
        private fun chunkKey(cx: Int, cz: Int): Long =
            (cx.toLong() shl 32) or (cz.toLong() and 0xFFFF_FFFFL)
    }

    suspend fun collect(
        player: Player,
        halfSize: Int,
        yBand: IntRange
    ): List<CellDTO> {
        val world = player.world
        val baseX = player.location.blockX
        val baseZ = player.location.blockZ
        val size = halfSize * 2
        val startX = baseX - halfSize
        val startZ = baseZ - halfSize

        data class Pos(val x: Int, val z: Int)
        val perChunk = HashMap<Long, MutableList<Pos>>()
        for (dx in 0 until size) for (dz in 0 until size) {
            val x = startX + dx
            val z = startZ + dz
            val cx = x shr 4
            val cz = z shr 4
            perChunk.computeIfAbsent(chunkKey(cx, cz)) { mutableListOf() }.add(Pos(x, z))
        }

        val out = mutableListOf<CellDTO>()
        coroutineScope {
            val tasks = perChunk.map { (pk, list) ->
                val cx = (pk shr 32).toInt()
                val cz = (pk and 0xFFFF_FFFFL).toInt()
                async(Dispatchers.Default) {
                    withContext(Atom.instance.regionDispatcher(world, cx, cz)) {
                        val local = ArrayList<CellDTO>(list.size)
                        for (pos in list) {

                            // choose top-most block of interest within band
                            for (y in yBand.last downTo yBand.first) {
                                val b = world.getBlockAt(pos.x, y, pos.z)
                                val t = ReinforcementSystem.getReinforcementLevel(b.location)
                                if (t != null) {
                                    local.add(CellDTO(pos.x, pos.z, y, t, shapes.getShape(world, pos.x, y, pos.z)))
                                }
                            }
                        }
                        local
                    }
                }
            }
            tasks.forEach { out.addAll(it.await()) }
        }
        return out
    }
}