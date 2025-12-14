package org.shotrush.atom.systems.physics.engine.rule

import org.bukkit.World
import org.bukkit.block.Block
import org.shotrush.atom.systems.physics.engine.PhysicsHelper
import org.shotrush.atom.systems.physics.engine.nms

interface SupportPillarRule : PhysicsRule {
    fun evaluate(world: World, block: Block): Boolean

    companion object Noop : SupportPillarRule {
        override fun evaluate(world: World, block: Block): Boolean = false
        override fun isCacheable(): Boolean = true
    }
}

class BasicSupportPillarRule : SupportPillarRule {
    override fun evaluate(
        world: World,
        block: Block,
    ): Boolean {
        val pos = block.location.nms()
        for (i in pos.y - 1 downTo world.minHeight) {
            val nBlock = world.getBlockAt(pos.x, i, pos.z)
            if (PhysicsHelper.canBlockBeFallenInto(nBlock))
                return false
        }
        return true
    }

    override fun isCacheable(): Boolean = false
}