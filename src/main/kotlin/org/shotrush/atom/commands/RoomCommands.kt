package org.shotrush.atom.commands

import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import dev.jorel.commandapi.kotlindsl.*
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.minecraft.core.Direction
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.entity.Player
import org.joml.Vector3i
import org.shotrush.atom.Atom
import org.shotrush.atom.sendMiniMessage
import org.shotrush.atom.core.api.scheduler.SchedulerAPI
import org.shotrush.atom.systems.room.face.FaceOpenProvider
import org.shotrush.atom.systems.room.RoomRegistry
import org.shotrush.atom.systems.room.RoomScanner
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

object RoomCommands {
    // Debug: FaceOpen visualizer state
    private val faceOpenActive = ConcurrentHashMap<UUID, ScheduledTask>()
    private val COLOR_OPEN = Particle.DustOptions(Color.fromRGB(20, 220, 60), 0.9f)
    private val COLOR_CLOSED = Particle.DustOptions(Color.fromRGB(220, 20, 60), 0.9f)
    private val COLOR_OCCUPY = Particle.DustOptions(Color.fromRGB(120, 120, 255), 0.9f)
    // Debug: Room outline visualizer state
    private val roomOutlineActive = ConcurrentHashMap<UUID, ScheduledTask>()
    private val roomOutlineFacesCache = ConcurrentHashMap<UUID, Pair<UUID, List<BoundaryFace>>>() // playerId -> (roomId, faces)
    private val roomOutlineIndex = ConcurrentHashMap<UUID, Int>()
    private val COLOR_ROOM = Particle.DustOptions(Color.fromRGB(50, 200, 255), 0.9f)

    fun register() {
        commandTree("room") {
            literalArgument("current", true) {
                withPermission("atom.command.room.current")
                playerExecutor { player, arguments ->
                    val room = RoomRegistry.roomAt(player.location)
                    if (room == null) {
                        player.sendMiniMessage("<red>You are not in a room</red>")
                    } else {
                        player.sendMiniMessage("<green>You are in room ${room.id}</green>")
                    }
                }
            }
            // Debug subcommand: visualize FaceOpenProvider around the player
            literalArgument("faceopen", true) {
                withPermission("atom.debug.faceopen")
                playerExecutor { player, _ ->
                    toggleFaceOpenDebug(player)
                }
            }
            // Debug subcommand: outline the current room geometry (boundary faces)
            literalArgument("outline", true) {
                withPermission("atom.debug.room")
                playerExecutor { player, _ ->
                    toggleRoomOutline(player)
                }
            }
            literalArgument("scan") {
                withPermission("atom.command.room.scan")
                integerArgument("maxVolume", 10, Short.MAX_VALUE.toInt(), true) {
                    playerExecutor { player, arguments ->
                        val maxVolume = arguments["maxVolume"] as? Int ?: 800
                        Atom.instance.launch(Atom.instance.entityDispatcher(player)) {
                            player.sendMiniMessage("<green>Scanning for rooms...</green>")
                            val scan = RoomScanner.scanAt(
                                player.world,
                                Vector3i(player.location.blockX, player.location.blockY, player.location.blockZ),
                                maxVolume = maxVolume,
                                retries = 0
                            )
                            player.sendMiniMessage(
                                "<green>Scanned room ${scan?.id ?: "<red>failed</red>"}</green>"
                            )
                        }
                    }
                }
            }
            literalArgument("save") {
                withPermission("atom.command.room.save")
                anyExecutor { executor, arguments ->
                    Atom.instance.launch {
                        executor.sendMiniMessage("<green>Saving rooms...</green>")
                        try {
                            RoomRegistry.saveAllToDisk()
                            executor.sendMiniMessage("<green>Saved rooms</green>")
                        } catch (e: Exception) {
                            executor.sendMiniMessage("<red>Failed to save rooms</red>")
                            e.printStackTrace()
                        }
                    }
                }
            }
            literalArgument("load") {
                withPermission("atom.command.room.load")
                anyExecutor { executor, arguments ->
                    Atom.instance.launch {
                        executor.sendMiniMessage("<green>Loading rooms...</green>")
                        try {
                            RoomRegistry.readAllFromDisk()
                            executor.sendMiniMessage("<green>Loaded rooms</green>")
                        } catch (e: Exception) {
                            executor.sendMiniMessage("<red>Failed to load rooms</red>")
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun toggleFaceOpenDebug(player: Player) {
        val id = player.uniqueId
        val existing = faceOpenActive.remove(id)
        if (existing != null) {
            existing.cancel()
            player.sendMiniMessage("<yellow>[FaceOpen]</yellow> <gray>Debug visualization</gray> <red>disabled</red>.")
            return
        }

        val task = SchedulerAPI.runTaskTimer(player, { task ->
            if (!player.isOnline) {
                task?.cancel()
                faceOpenActive.remove(id)
                return@runTaskTimer
            }
            try {
                renderFaceOpenAround(player)
            } catch (_: Throwable) {
                task?.cancel()
                faceOpenActive.remove(id)
            }
        }, 1L, 6L)

        faceOpenActive[id] = task
        player.sendMiniMessage("<yellow>[FaceOpen]</yellow> <gray>Debug visualization</gray> <green>enabled</green>. <gray>Run again to disable.</gray>")
    }

    // =====================
    // Room outline debug
    // =====================
    private fun toggleRoomOutline(player: Player) {
        val id = player.uniqueId
        val existing = roomOutlineActive.remove(id)
        if (existing != null) {
            existing.cancel()
            roomOutlineFacesCache.remove(id)
            roomOutlineIndex.remove(id)
            player.sendMiniMessage("<yellow>[Room]</yellow> <gray>Outline debug</gray> <red>disabled</red>.")
            return
        }

        val task = SchedulerAPI.runTaskTimer(player, { task ->
            if (!player.isOnline) {
                task?.cancel()
                roomOutlineActive.remove(id)
                roomOutlineFacesCache.remove(id)
                roomOutlineIndex.remove(id)
                return@runTaskTimer
            }
            try {
                renderRoomOutlineTick(player)
            } catch (_: Throwable) {
                task?.cancel()
                roomOutlineActive.remove(id)
                roomOutlineFacesCache.remove(id)
                roomOutlineIndex.remove(id)
            }
        }, 1L, 6L)

        roomOutlineActive[id] = task
        player.sendMiniMessage("<yellow>[Room]</yellow> <gray>Outline debug</gray> <green>enabled</green>. <gray>Run again to disable.</gray>")
    }

    private data class BoundaryFace(val x: Int, val y: Int, val z: Int, val dir: Direction)

    private fun renderRoomOutlineTick(player: Player, maxFacesPerTick: Int = 1500) {
        val pId = player.uniqueId
        val room = RoomRegistry.roomAt(player.location) ?: run {
            // Not in a room; nothing to display this tick
            return
        }

        val cached = roomOutlineFacesCache[pId]
        val faces: List<BoundaryFace> = if (cached == null || cached.first != room.id) {
            val built = buildBoundaryFaces(room)
            roomOutlineFacesCache[pId] = room.id to built
            roomOutlineIndex[pId] = 0
            built
        } else {
            cached.second
        }

        if (faces.isEmpty()) return

        val startIndex = roomOutlineIndex.getOrDefault(pId, 0)
        var i = 0
        var idx = startIndex
        val world = player.world
        val total = faces.size
        while (i < maxFacesPerTick) {
            val f = faces[idx]
            drawFaceOutline(world, f.x, f.y, f.z, f.dir, COLOR_ROOM, segments = 2)
            i++
            idx++
            if (idx >= total) idx = 0
            if (idx == startIndex) break // completed a cycle
        }
        roomOutlineIndex[pId] = idx
    }

    private fun buildBoundaryFaces(room: org.shotrush.atom.systems.room.Room): List<BoundaryFace> {
        val set = room.blocks
        // Pre-size roughly: many internal faces cancel; boundary faces usually ~surface area
        val faces = ArrayList<BoundaryFace>(set.size)

        // Direction deltas
        val dirs = arrayOf(
            Direction.EAST to intArrayOf(1, 0, 0),
            Direction.WEST to intArrayOf(-1, 0, 0),
            Direction.UP to intArrayOf(0, 1, 0),
            Direction.DOWN to intArrayOf(0, -1, 0),
            Direction.SOUTH to intArrayOf(0, 0, 1),
            Direction.NORTH to intArrayOf(0, 0, -1),
        )

        for (packed in set) {
            val (x, y, z) = org.shotrush.atom.util.LocationUtil.unpack(packed)
            for ((dir, d) in dirs) {
                val nx = x + d[0]
                val ny = y + d[1]
                val nz = z + d[2]
                val neighborPacked = try {
                    org.shotrush.atom.util.LocationUtil.pack(nx, ny, nz)
                } catch (_: IllegalArgumentException) {
                    // Out of allowed range counts as boundary
                    faces.add(BoundaryFace(x, y, z, dir))
                    continue
                }
                if (!set.contains(neighborPacked)) {
                    faces.add(BoundaryFace(x, y, z, dir))
                }
            }
        }
        return faces
    }

    private fun isAirLike(mat: Material): Boolean {
        return mat == Material.AIR ||
                mat == Material.CAVE_AIR ||
                mat == Material.VOID_AIR
    }
    private fun renderFaceOpenAround(player: Player, radius: Int = 3) {
        val world = player.world
        val bx = player.location.blockX
        val by = player.location.blockY
        val bz = player.location.blockZ

        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val x = bx + dx
                    val y = by + dy
                    val z = bz + dz

                    val mat = world.getBlockAt(x, y, z).type
                    if (isAirLike(mat)) continue

                    // Skip if not occupiable to reduce clutter
                    if (!FaceOpenProvider.canOccupy(world, x, y, z)) {
                        // for (dir in Direction.entries) drawFaceOutline(world, x, y, z, dir, COLOR_OCCUPY)
                        continue
                    }

                    // Draw per-face outline
                    for (dir in Direction.entries) {
                        val open = FaceOpenProvider.isOpen(world, x, y, z, dir)
                        drawFaceOutline(world, x, y, z, dir, if (open) COLOR_OPEN else COLOR_CLOSED)
                    }
                }
            }
        }
    }

    private fun drawFaceOutline(
        world: World,
        x: Int,
        y: Int,
        z: Int,
        dir: Direction,
        color: Particle.DustOptions,
        segments: Int = 2,
    ) {
        val ox = x + 0.5
        val oy = y + 0.5
        val oz = z + 0.5
        val step = 1.0 / max(1, segments)
        val outward = 0.52
        val half = 0.5
        fun spawn(px: Double, py: Double, pz: Double) {
            world.spawnParticle(Particle.DUST, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0, color)
        }

        when (dir) {
            Direction.UP, Direction.DOWN -> {
                val yFace = oy + if (dir == Direction.UP) half + 0.02 else -half - 0.02
                // four edges of the square
                for (t in 0..segments) {
                    val s = -half + t * step
                    spawn(ox - half, yFace, oz + s)
                    spawn(ox + half, yFace, oz + s)
                    spawn(ox + s, yFace, oz - half)
                    spawn(ox + s, yFace, oz + half)
                }
            }
            Direction.NORTH, Direction.SOUTH -> {
                val zFace = oz + if (dir == Direction.SOUTH) half + 0.02 else -half - 0.02
                for (t in 0..segments) {
                    val s = -half + t * step
                    spawn(ox - half, oy + s, zFace)
                    spawn(ox + half, oy + s, zFace)
                    spawn(ox + s, oy - half, zFace)
                    spawn(ox + s, oy + half, zFace)
                }
            }
            Direction.EAST, Direction.WEST -> {
                val xFace = ox + if (dir == Direction.EAST) half + 0.02 else -half - 0.02
                for (t in 0..segments) {
                    val s = -half + t * step
                    spawn(xFace, oy + s, oz - half)
                    spawn(xFace, oy + s, oz + half)
                    spawn(xFace, oy - half, oz + s)
                    spawn(xFace, oy + half, oz + s)
                }
            }
        }
    }
}