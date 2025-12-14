package org.shotrush.atom.systems.reinforce

import net.minecraft.world.phys.shapes.VoxelShape

data class CellDTO(
    val x: Int,
    val z: Int,
    val y: Int,
    val type: ReinforceType,
    val shape: VoxelShape
)