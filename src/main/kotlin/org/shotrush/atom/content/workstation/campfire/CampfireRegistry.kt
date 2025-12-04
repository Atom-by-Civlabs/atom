package org.shotrush.atom.content.workstation.campfire

import com.github.shynixn.mccoroutine.folia.launch
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.Lightable
import org.bukkit.plugin.Plugin
import org.shotrush.atom.Atom
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for tracking active campfires and their burn timers.
 * Data is persisted to the block's PersistentDataContainer (stored with the chunk).
 */
class CampfireRegistry(private val plugin: Plugin) {
    companion object {
        const val BASE_BURN_MS = 2 * 60 * 1000L
    }

    data class ActiveCampfire(
        val location: Location,
        var lit: Boolean,
        var startTime: Long?,
        var endTime: Long?,
        var burnoutJob: Job? = null
    )

    interface Listener {
        fun onCampfirePlaced(state: CampfireData.CampfireState) {}
        fun onCampfireLit(state: CampfireData.CampfireState) {}
        fun onCampfireExtinguished(state: CampfireData.CampfireState, reason: String) {}
        fun onCampfireBroken(state: CampfireData.CampfireState) {}
        fun onFuelAdded(state: CampfireData.CampfireState, addedMs: Long, newEndTimeMs: Long) {}
        fun onResumeTimerScheduled(state: CampfireData.CampfireState, remainingMs: Long) {}
        fun onResumeTimerExpired(state: CampfireData.CampfireState) {}
    }

    private val atom get() = Atom.instance
    private val listeners = mutableListOf<Listener>()
    private val active = ConcurrentHashMap<Location, ActiveCampfire>()

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)

    fun isTracked(loc: Location) = active.containsKey(fix(loc))
    
    fun getState(loc: Location): CampfireData.CampfireState? {
        return CampfireData.getState(fix(loc))
    }

    fun getAllActiveLocations(): Collection<Location> = active.keys.toList()

    fun trackOnPlace(loc: Location, lit: Boolean) {
        val fixed = fix(loc)
        val now = System.currentTimeMillis()
        
        val state = CampfireData.CampfireState(
            location = fixed,
            startTime = if (lit) now else null,
            endTime = if (lit) now + BASE_BURN_MS else null,
            fuelQueue = ""
        )
        
        CampfireData.saveState(state)
        
        if (lit) {
            active[fixed] = ActiveCampfire(fixed, true, now, now + BASE_BURN_MS)
            scheduleBurnout(fixed)
        }
        
        listeners.forEach { it.onCampfirePlaced(state) }
    }

    fun lightAt(loc: Location) {
        val fixed = fix(loc)
        val block = fixed.block
        val data = block.blockData
        
        if (data is Lightable && !data.isLit) {
            data.isLit = true
            block.blockData = data
        }
        
        val now = System.currentTimeMillis()
        val endTime = now + BASE_BURN_MS
        
        val state = CampfireData.getState(fixed) ?: CampfireData.CampfireState(fixed, null, null, "")
        state.startTime = now
        state.endTime = endTime
        CampfireData.saveState(state)
        
        active[fixed] = ActiveCampfire(fixed, true, now, endTime)
        scheduleBurnout(fixed)
        
        listeners.forEach { it.onCampfireLit(state) }
        atom.logger.info("Campfire lit at ${xyz(fixed)}")
    }

    fun extinguishAt(loc: Location, reason: String) {
        val fixed = fix(loc)
        val activeCampfire = active.remove(fixed)
        
        activeCampfire?.burnoutJob?.cancel()
        
        setLit(fixed, false)
        
        val state = CampfireData.getState(fixed)
        if (state != null) {
            state.startTime = null
            state.endTime = null
            CampfireData.saveState(state)
            listeners.forEach { it.onCampfireExtinguished(state, reason) }
        }
        
        atom.logger.info("Campfire extinguished at ${xyz(fixed)} ($reason)")
    }

    fun brokenAt(loc: Location) {
        val fixed = fix(loc)
        val activeCampfire = active.remove(fixed)
        
        activeCampfire?.burnoutJob?.cancel()
        
        val state = CampfireData.getState(fixed)
        if (state != null) {
            listeners.forEach { it.onCampfireBroken(state) }
        }
        
        // PDC is automatically cleared when block is broken
        atom.logger.info("Campfire broken at ${xyz(fixed)}")
    }

    fun addFuel(loc: Location, addedMs: Long): Long? {
        val fixed = fix(loc)
        val state = CampfireData.getState(fixed) ?: return null
        
        if (!state.lit || state.endTime == null) return null
        
        val now = System.currentTimeMillis()
        val currentEnd = state.endTime!!.coerceAtLeast(now)
        val newEndTime = currentEnd + addedMs
        
        state.endTime = newEndTime
        CampfireData.saveState(state)
        
        // Update in-memory tracking and reschedule burnout
        val activeCampfire = active[fixed]
        if (activeCampfire != null) {
            activeCampfire.endTime = newEndTime
            activeCampfire.burnoutJob?.cancel()
            scheduleBurnout(fixed)
        }
        
        listeners.forEach { it.onFuelAdded(state, addedMs, newEndTime) }
        atom.logger.info("Fuel added at ${xyz(fixed)}: +${addedMs / 1000}s, new end in ${(newEndTime - now) / 1000}s")
        
        return newEndTime
    }

    /**
     * Resume tracking for campfires in a world that was just loaded.
     * Scans for lit campfires and restores their timers from PDC.
     * Uses Folia's region scheduler to access chunks on the correct thread.
     */
    fun resumeFromDisk(targetWorld: org.bukkit.World) {
        atom.logger.info("=== Resuming campfires for world ${targetWorld.name} ===")
        
        // Get chunk locations first (safe from any thread)
        val chunkLocations = targetWorld.loadedChunks.map { chunk ->
            Location(targetWorld, (chunk.x * 16).toDouble(), 64.0, (chunk.z * 16).toDouble())
        }
        
        // Schedule each chunk scan on its region's thread
        for (chunkLoc in chunkLocations) {
            org.bukkit.Bukkit.getServer().regionScheduler.run(plugin, chunkLoc) { _ ->
                val chunk = chunkLoc.chunk
                for (entity in chunk.tileEntities) {
                    if (entity is org.bukkit.block.Campfire) {
                        val loc = entity.location
                        val state = CampfireData.getState(loc) ?: continue
                        
                        if (state.endTime != null && state.lit) {
                            val now = System.currentTimeMillis()
                            val remaining = state.endTime!! - now
                            
                            if (remaining > 0) {
                                active[fix(loc)] = ActiveCampfire(
                                    fix(loc), true, state.startTime, state.endTime
                                )
                                scheduleBurnout(fix(loc), remaining)
                                listeners.forEach { it.onResumeTimerScheduled(state, remaining) }
                                atom.logger.info("  ✓ Resume ${xyz(loc)} ${remaining / 1000}s remaining")
                            } else {
                                // Timer expired while offline
                                setLit(loc, false)
                                state.startTime = null
                                state.endTime = null
                                CampfireData.saveState(state)
                                listeners.forEach { it.onResumeTimerExpired(state) }
                                atom.logger.info("  ✗ Expired ${xyz(loc)}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleBurnout(loc: Location, remainingOverride: Long? = null) {
        val fixed = fix(loc)
        val activeCampfire = active[fixed] ?: return
        val endTime = activeCampfire.endTime ?: return
        
        val now = System.currentTimeMillis()
        val remaining = remainingOverride ?: (endTime - now).coerceAtLeast(0)
        
        if (remaining <= 0) {
            extinguishAt(fixed, "burnout")
            return
        }

        val job = atom.launch(atom.regionDispatcher(fixed)) {
            delay(remaining)
            
            val lightable = fixed.block.blockData as? Lightable
            if (lightable != null && lightable.isLit) {
                setLit(fixed, false)
                active.remove(fixed)
                
                val state = CampfireData.getState(fixed)
                if (state != null) {
                    state.startTime = null
                    state.endTime = null
                    CampfireData.saveState(state)
                    listeners.forEach { it.onCampfireExtinguished(state, "burnout") }
                }
                
                atom.logger.info("Campfire burned out at ${xyz(fixed)}")
            }
        }
        
        activeCampfire.burnoutJob = job
    }

    private fun setLit(loc: Location, value: Boolean) {
        val block = loc.block
        val data = block.blockData
        if (data is Lightable && data.isLit != value) {
            data.isLit = value
            block.blockData = data
        }
    }

    private fun fix(l: Location) = Location(l.world, l.blockX.toDouble(), l.blockY.toDouble(), l.blockZ.toDouble())
    private fun xyz(l: Location) = "${l.blockX},${l.blockY},${l.blockZ}"
}
