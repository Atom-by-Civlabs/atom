package org.shotrush.atom.systems.room.face

import net.minecraft.core.Direction
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.MultipleFacing
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.GlassPane
import org.bukkit.block.data.type.TrapDoor
import org.bukkit.craftbukkit.block.CraftBlock
import org.shotrush.atom.systems.physics.engine.nms

object NMSFaceOpenProvider : FaceOpenProvider {
    private fun isAirLike(mat: Material): Boolean {
        return mat == Material.AIR ||
                mat == Material.CAVE_AIR ||
                mat == Material.VOID_AIR
    }

    private fun isDoorLike(data: BlockData): Boolean =
        data is Door || data is TrapDoor

    private fun isLiquid(mat: Material): Boolean {
        return mat == Material.WATER || mat == Material.LAVA
    }
    private fun isPane(data: BlockData): Boolean {
        // Covers glass pane variants; MultipleFacing is used by many "pane-like" blocks too.
        return data is GlassPane || (data is MultipleFacing && isPaneMaterial(data.material))
    }

    private fun isPaneMaterial(mat: Material): Boolean {
        return when (mat) {
            Material.GLASS_PANE,
            Material.IRON_BARS,
            Material.WHITE_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE,
            Material.GRAY_STAINED_GLASS_PANE,
            Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.BROWN_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE -> true
            else -> false
        }
    }

    override fun canOccupy(world: World, x: Int, y: Int, z: Int): Boolean {
        val block = world.getBlockAt(x, y, z)
        if (isAirLike(block.type)) return true
        if (isDoorLike(block.blockData)) return true
        if (isPane(block.blockData)) return true
        val nmsWorld = world.nms()
        val nmsState = block.nms()
        val nmsPos = block.location.nms()
        return !nmsState.isCollisionShapeFullBlock(nmsWorld, nmsPos)
    }

    override fun isOpen(world: World, x: Int, y: Int, z: Int, dir: Direction): Boolean {
        val b = world.getBlockAt(x, y, z)
        val type = b.type

        if (isAirLike(type)) return true
        if (isLiquid(type)) return false
        val data = b.blockData

        // Per-face logic for door-like blocks
        if (data is Door) return isDoorFaceOpen(data, dir)
        if (data is TrapDoor) return isTrapDoorFaceOpen(data, dir)
        if(data is MultipleFacing) {
            if (dir.axis == Direction.Axis.Y) return true
            val connected = data.hasFace(CraftBlock.notchToBlockFace(dir.clockWise)) && data.hasFace(CraftBlock.notchToBlockFace(dir.counterClockWise))
            if(connected) return false
            return true
        }

        val nmsWorld = world.nms()
        val nmsState = b.nms()
        val nmsPos = b.location.nms()
        return !nmsState.isFaceSturdy(nmsWorld, nmsPos, dir)
    }

    private fun isDoorFaceOpen(door: Door, dir: Direction): Boolean {
        // vertical faces always open (cavity above/below isn't sealed by the thin door plane)
        if (dir == Direction.UP || dir == Direction.DOWN) return true

        val facing = CraftBlock.blockFaceToNotch(door.facing)
        val hingeRight = (door.hinge == Door.Hinge.RIGHT)
        val whenOpenDir = if (hingeRight) facing.clockWise else facing.counterClockWise
        if(dir == whenOpenDir) return false
        if(dir == facing.opposite) return false
        return true
    }

    private fun isTrapDoorFaceOpen(tr: TrapDoor, dir: Direction): Boolean {
        if (dir.axis == Direction.Axis.Y) {
            if (tr.half == Bisected.Half.TOP && dir == Direction.UP) return false
            if (tr.half == Bisected.Half.BOTTOM && dir == Direction.DOWN) return false
            return false
        }
        return CraftBlock.blockFaceToNotch(tr.facing) != dir.opposite
    }
}