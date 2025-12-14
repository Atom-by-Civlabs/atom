package org.shotrush.atom.systems.physics.engine

import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.block.CraftBlockState
import org.shotrush.atom.Atom
import net.minecraft.world.level.block.state.BlockState as NMSBlockState

object PhysicsHelper {

    /**
     * Checks whether any collision AABB of the block at `pos` is touching
     * any collision AABB of the neighbor in the given `face` direction.
     *
     * @param level          ServerLevel (Paper exposes NMS)
     * @param pos            Base block position
     * @param face           Direction to check (neighbor direction)
     * @param allowDiagonal  If false, returns false for diagonal axes (if you use custom directions)
     * @param allowLiquids   If false, liquids short-circuit to true if either is liquid (matching original)
     */
    suspend fun isBlockFaceTouchingNeighbour(
        level: ServerLevel,
        pos: BlockPos,
        face: Direction,
        allowLiquids: Boolean = false
    ): Boolean {
        val state = level.getBlockState(pos)
        val nPos = pos.relative(face)
        val nState = withContext(Atom.instance.regionDispatcher(level.world, nPos.x shr 4, nPos.z shr 4)) {
            level.getBlockState(nPos)
        }

        if (!allowLiquids && (isLiquid(state) || isLiquid(nState))) {
            return true
        }

        val shape = state.getCollisionShape(level, pos)
        val nShape = nState.getCollisionShape(level, nPos)

        val boxes = shapeToAabbs(shape)
        val nBoxes = shapeToAabbs(nShape)

        val margin = 0.02

        for (b1 in boxes) {
            val expanded = b1.inflate(margin)

            for (b2 in nBoxes) {
                if (expanded.intersects(b2)) {
                    return true
                }
            }
        }

        return false
    }

    private fun isLiquid(state: NMSBlockState): Boolean = state.liquid()

    private fun shapeToAabbs(shape: VoxelShape): List<AABB> = shape.toAabbs()

    fun canBlockBeFallenInto(block: Block, ignoreLiquids: Boolean = false): Boolean {
        if(!ignoreLiquids && block.isLiquid) return true
        if(isPassableBlock(block)) return true
        val world = block.world.nms()
        val shape = block.nms().getCollisionShape(world, block.location.nms())
        return shape.isEmpty
    }

    fun isPassableBlock(block: Block): Boolean = FallingBlock.isFree(block.nms())
}

fun Block.nms(): NMSBlockState = (this.state as CraftBlockState).handle

fun Location.nms(): BlockPos = BlockPos(blockX, blockY, blockZ)

fun World.nms(): ServerLevel = (this as? CraftWorld)?.handle ?: error("World is not a CraftWorld, ${this.javaClass.simpleName}")