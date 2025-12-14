package org.shotrush.atom.systems.reinforce

import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.withContext
import org.bukkit.Color
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.shotrush.atom.Atom

interface OutlineRenderer {
    suspend fun render(player: Player, geometry: List<OutlineGeometry>, step: Double)
}

class ParticleOutlineRenderer3D(
    private val defaultStep: Double = 0.25,
) : OutlineRenderer {
    override suspend fun render(player: Player, geometry: List<OutlineGeometry>, step: Double) {
        val world = player.world
        for (g in geometry) {
            val dust = org.bukkit.Particle.DustOptions(
                when (g) {
                    is OutlineGeometry.Segments3D -> g.color
                    else -> Color.WHITE
                },
                1.2f
            )
            when (g) {
                is OutlineGeometry.Segments3D -> {
                    for ((a, b) in g.segments) {
                        drawSegment(world, a, b, dust, if (step > 0.0) step else defaultStep)
                    }
                }

                else -> {}
            }
        }
    }

    private fun drawSegment(
        world: World,
        a: Vector,
        b: Vector,
        dust: org.bukkit.Particle.DustOptions,
        step: Double,
    ) {
        val seg = b.clone().subtract(a)
        val len = seg.length()
        val count = maxOf(2, (len / step).toInt())
        val dir = seg.multiply(1.0 / count)
        var p = a.clone()
        repeat(count + 1) {
            world.spawnParticle(
                org.bukkit.Particle.DUST,
                p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0, dust
            )
            p.add(dir)
        }
    }
}