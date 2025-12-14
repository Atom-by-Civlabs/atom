package org.shotrush.atom.systems.room

import com.github.shynixn.mccoroutine.folia.ticks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.bukkit.World
import org.joml.Vector3i
import kotlin.random.Random

object RoomScanner {
    suspend fun scanAt(
        world: World,
        start: Vector3i,
        maxVolume: Int = 800,
        retries: Int = 1,
        scanner: RoomScan = RoomScanFace(world, start, maxVolume = maxVolume),
    ): Room? {
        repeat(retries + 1) { attempt ->
            val ok = withContext(Dispatchers.IO) {
                scanner.scan()
            }
            if (ok) {
                val room = scanner.toRoom() ?: return null
                return if (RoomRegistry.tryRegisterDedup(room)) room else null
            } else if (attempt < retries) {
                delay((4..12).random().ticks)
            }
        }
        return null
    }
}