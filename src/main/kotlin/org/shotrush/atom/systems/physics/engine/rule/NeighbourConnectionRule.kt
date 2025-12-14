package org.shotrush.atom.systems.physics.engine.rule

import net.minecraft.core.Direction
import org.bukkit.World
import org.bukkit.block.Block
import org.shotrush.atom.systems.physics.engine.PhysicsHelper
import org.shotrush.atom.systems.physics.engine.nms

interface NeighbourConnectionRule : PhysicsRule {
    suspend fun evaluate(world: World, block: Block, direction: Direction): Boolean

    companion object Noop : NeighbourConnectionRule {
        override suspend fun evaluate(world: World, block: Block, direction: Direction): Boolean = false
        override fun isCacheable(): Boolean = true
    }
}

class BasicNeighbourConnectionRule(val directionTest: (Direction) -> Boolean = { true }) : NeighbourConnectionRule {
    override suspend fun evaluate(
        world: World,
        block: Block,
        direction: Direction,
    ): Boolean {
        if (!directionTest(direction)) return false
        if (!PhysicsHelper.isBlockFaceTouchingNeighbour(world.nms(), block.location.nms(), direction)) {
            return false
        }
        return true
    }

    override fun isCacheable(): Boolean = false
}