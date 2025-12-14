package org.shotrush.atom.systems.reinforce

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import java.time.Instant
import java.util.*

class PlayerOutlineSession(
    val playerId: UUID,
    private val buildScope: CoroutineScope,
) {
    @Volatile
    var cache: PlayerOutlineCache? = null

    @Volatile
    private var building = false

    fun isBuilding(): Boolean = building

    fun startBuild(
        player: Player,
        halfSize: Int,
        yBandHeight: Int,
        snapshotCollector: SnapshotCollector,
        builder: VoxelOutlineBuilder,
    ) {
        if (building) return
        building = true

        val baseX = player.location.blockX
        val baseZ = player.location.blockZ
        val feetY = player.location.blockY
        val yBand = if (yBandHeight <= 0) feetY..feetY else (feetY - yBandHeight)..(feetY + yBandHeight)

        buildScope.launch {
            try {
                val cells = snapshotCollector.collect(player, halfSize, yBand)
                val geometry = builder.build(cells)
                cache = PlayerOutlineCache(baseX, baseZ, halfSize, Instant.now(), geometry)
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                building = false
            }
        }
    }
}