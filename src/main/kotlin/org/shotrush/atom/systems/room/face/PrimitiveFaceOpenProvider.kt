package org.shotrush.atom.systems.room.face

import net.minecraft.core.Direction
import org.bukkit.World

/**
 * Replicates the behaviour of the original room scanner
 */
object PrimitiveFaceOpenProvider : FaceOpenProvider {
    override fun canOccupy(world: World, x: Int, y: Int, z: Int): Boolean {
        val block = world.getBlockAt(x, y, z)
        return !block.isSolid
    }

    override fun isOpen(world: World, x: Int, y: Int, z: Int, dir: Direction): Boolean {
        val block = world.getBlockAt(x, y, z)
        return !block.isSolid
    }
}