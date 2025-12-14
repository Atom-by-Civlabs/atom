package org.shotrush.atom.systems.reinforce

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.shapes.VoxelShape
import org.bukkit.World
import org.shotrush.atom.systems.physics.engine.nms

interface VoxelShapeProvider {
    fun getShape(world: World, x: Int, y: Int, z: Int): VoxelShape

    object Default : VoxelShapeProvider {
        override fun getShape(
            world: World,
            x: Int,
            y: Int,
            z: Int,
        ): VoxelShape {
            return world.getBlockAt(x, y, z).nms().getShape(world.nms(), BlockPos(x,y,z)).move(x.toDouble(), y.toDouble(), z.toDouble())
        }
    }
}