package org.shotrush.atom.systems.room

import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.minecraft.core.Direction
import org.bukkit.World
import org.joml.Vector3i
import org.shotrush.atom.Atom
import org.shotrush.atom.systems.room.face.FaceOpenProvider
import org.shotrush.atom.util.LocationUtil
import java.util.UUID
import kotlin.collections.ArrayDeque

class RoomScanFace(
    private val world: World,
    private val startPosition: Vector3i,
    private val maxVolume: Int = 200,
    private val epochCheckInterval: Int = 64,
    private val nodesPerChunkHop: Int = 64,
    private val faceProvider: FaceOpenProvider = FaceOpenProvider.Default
) : RoomScan {
    private val scanId = UUID.randomUUID()
    private var hasScanned = false

    val scannedPositions = mutableSetOf<Long>()
    val volume: Int get() = scannedPositions.size

    private fun inRangeY(y: Int): Boolean =
        y in world.minHeight..<world.maxHeight

    private fun tryPack(x: Int, y: Int, z: Int): Long? =
        try {
            LocationUtil.pack(x, y, z)
        } catch (_: IllegalArgumentException) {
            null
        }

    private val touchedChunks = HashMap<Long, Long>()
    private fun packChunk(cx: Int, cz: Int): Long =
        (cx.toLong() shl 32) or (cz.toLong() and 0xFFFF_FFFFL)

    private fun rememberEpoch(cx: Int, cz: Int) {
        val pk = packChunk(cx, cz)
        touchedChunks.computeIfAbsent(pk) {
            WorldEpochs.get(world.uid, cx, cz)
        }
    }

    private fun epochsStable(): Boolean {
        for ((packed, epoch) in touchedChunks) {
            val cx = (packed shr 32).toInt()
            val cz = (packed and 0xFFFF_FFFFL).toInt()
            val now = WorldEpochs.get(world.uid, cx, cz)
            if (now != epoch) return false
        }
        return true
    }

    private data class ChunkQueues(
        val active: ArrayDeque<Vector3i> = ArrayDeque(),
        val pending: ArrayDeque<Vector3i> = ArrayDeque()
    )

    override suspend fun scan(): Boolean {
        val ok = try {
            runBatchedScan()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
        hasScanned = true
        ScanReservations.releaseCells(scannedPositions, scanId)
        return ok
    }

    private suspend fun runBatchedScan(): Boolean = coroutineScope {
        val sx = startPosition.x()
        val sy = startPosition.y()
        val sz = startPosition.z()
        if (!inRangeY(sy)) return@coroutineScope false
        if (!faceProvider.canOccupy(world, sx, sy, sz)) return@coroutineScope false

        val startKey = tryPack(sx, sy, sz) ?: return@coroutineScope false
        if (!ScanReservations.tryReserve(startKey, scanId)) return@coroutineScope false
        scannedPositions.add(startKey)

        val startChunkX = sx shr 4
        val startChunkZ = sz shr 4
        rememberEpoch(startChunkX, startChunkZ)

        val frontier = HashMap<Long, ChunkQueues>()
        fun queues(cx: Int, cz: Int) =
            frontier.computeIfAbsent(packChunk(cx, cz)) { ChunkQueues() }

        queues(startChunkX, startChunkZ).active.add(Vector3i(sx, sy, sz))

        var stepsSinceEpochCheck = 0

        while (frontier.isNotEmpty()) {
            val keys = frontier.keys.toList()

            for (pk in keys) {
                val cx = (pk shr 32).toInt()
                val cz = (pk and 0xFFFF_FFFFL).toInt()
                val q = frontier[pk] ?: continue

                val keepBucket = withContext(Atom.instance.regionDispatcher(world, cx, cz)) {
                    rememberEpoch(cx, cz)

                    var processed = 0
                    val budget = nodesPerChunkHop.coerceAtLeast(1)
                    val drainBudget = (budget / 2).coerceAtLeast(1)
                    val expandBudget = budget - drainBudget

                    // Drain pending into active, bounded
                    var drained = 0
                    while (q.pending.isNotEmpty() && drained < drainBudget && processed < budget) {
                        val p = q.pending.removeFirst()
                        val px = p.x(); val py = p.y(); val pz = p.z()
                        if (!inRangeY(py)) {
                            drained++; processed++
                            continue
                        }

                        val key = tryPack(px, py, pz)
                        if (key == null) {
                            drained++; processed++
                            continue
                        }

                        if (!faceProvider.canOccupy(world, px, py, pz)) {
                            ScanReservations.releaseCells(listOf(key), scanId)
                        } else if (!scannedPositions.contains(key)) {
                            scannedPositions.add(key)
                            if (volume > maxVolume) return@withContext false
                            q.active.add(Vector3i(px, py, pz))
                        }
                        drained++; processed++
                    }

                    // Expand within this chunk, bounded
                    var expanded = 0
                    while (q.active.isNotEmpty()
                        && expanded < expandBudget
                        && processed < budget
                    ) {
                        if (volume > maxVolume) return@withContext false

                        stepsSinceEpochCheck++
                        if (stepsSinceEpochCheck >= epochCheckInterval) {
                            stepsSinceEpochCheck = 0
                            if (!epochsStable()) return@withContext false
                        }

                        val p = q.active.removeFirst()
                        val px = p.x(); val py = p.y(); val pz = p.z()

                        if (!faceProvider.canOccupy(world, px, py, pz)) {
                            expanded++; processed++
                            continue
                        }

                        for (dir in Direction.entries) {
                            val nx = px + dir.stepX
                            val ny = py + dir.stepY
                            val nz = pz + dir.stepZ
                            if (!inRangeY(ny)) continue

                            val key = tryPack(nx, ny, nz) ?: continue
                            if (scannedPositions.contains(key)) continue

                            // Reserve neighbor before any heavy checks
                            if (!ScanReservations.tryReserve(key, scanId)) return@withContext false

                            // Neighbor must be occupiable
                            if (!faceProvider.canOccupy(world, nx, ny, nz)) {
                                ScanReservations.releaseCells(listOf(key), scanId)
                                continue
                            }

                            // Mutual face openness: p->n AND n->p
                            val openPN = faceProvider.isOpen(world, px, py, pz, dir)
                            val openNP = faceProvider.isOpen(world, nx, ny, nz, dir.opposite)
                            if (!openPN || !openNP) {
                                ScanReservations.releaseCells(listOf(key), scanId)
                                continue
                            }

                            val nChunkX = nx shr 4
                            val nChunkZ = nz shr 4

                            // Accept immediately
                            scannedPositions.add(key)
                            if (volume > maxVolume) return@withContext false

                            if (nChunkX == cx && nChunkZ == cz) {
                                q.active.add(Vector3i(nx, ny, nz))
                            } else {
                                rememberEpoch(nChunkX, nChunkZ)
                                // Place directly into target ACTIVE (already validated)
                                val tgt = queues(nChunkX, nChunkZ)
                                tgt.active.add(Vector3i(nx, ny, nz))
                            }
                        }
                        expanded++; processed++
                    }

                    true
                }

                if (!keepBucket) return@coroutineScope false

                val q2 = frontier[pk]
                if (q2 != null && q2.active.isEmpty() && q2.pending.isEmpty()) {
                    frontier.remove(pk)
                }
            }
        }

        return@coroutineScope epochsStable()
    }

    override fun toRoom(): Room? {
        if (!hasScanned) return null
        if (volume == 0 || volume > maxVolume) return null

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        for (cell in scannedPositions) {
            val (x, y, z) = LocationUtil.unpack(cell)
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
        }

        return Room(
            id = UUID.randomUUID(),
            world = world,
            minX = minX, minY = minY, minZ = minZ,
            maxX = maxX, maxY = maxY, maxZ = maxZ,
            blocks = scannedPositions.toSet()
        )
    }
}