package org.shotrush.atom.systems.physics.engine

import net.minecraft.core.Direction
import org.bukkit.Location
import org.bukkit.World

sealed interface PhysicsEngine {
    suspend fun isBlockConnectableToNeighbour(world: World, pos: Location, direction: Direction)
}