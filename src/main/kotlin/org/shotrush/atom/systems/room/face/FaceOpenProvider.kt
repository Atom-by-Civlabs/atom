package org.shotrush.atom.systems.room.face

import net.minecraft.core.Direction
import org.bukkit.World

interface FaceOpenProvider {
    fun canOccupy(world: World, x: Int, y: Int, z: Int): Boolean

    fun isOpen(world: World, x: Int, y: Int, z: Int, dir: Direction): Boolean

    companion object Default : FaceOpenProvider by NMSFaceOpenProvider
}
