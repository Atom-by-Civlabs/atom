package org.shotrush.atom.content.workstation.campfire

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Campfire
import org.bukkit.persistence.PersistentDataType
import org.shotrush.atom.Atom

/**
 * Helper object for storing campfire data in the block's PersistentDataContainer.
 * This stores data directly on the vanilla campfire TileEntity, which persists with the chunk.
 */
object CampfireData {
    private val START_TIME_KEY = NamespacedKey(Atom.instance, "campfire_start_time")
    private val END_TIME_KEY = NamespacedKey(Atom.instance, "campfire_end_time")
    private val FUEL_QUEUE_KEY = NamespacedKey(Atom.instance, "campfire_fuel_queue")

    data class CampfireState(
        val location: Location,
        var startTime: Long?,
        var endTime: Long?,
        var fuelQueue: String
    ) {
        val lit: Boolean
            get() {
                val data = location.block.blockData
                return data is org.bukkit.block.data.Lightable && data.isLit
            }
    }

    /**
     * Get the campfire state from a block's PDC.
     * Returns null if the block is not a campfire.
     */
    fun getState(location: Location): CampfireState? {
        val block = location.block
        if (block.type != Material.CAMPFIRE && block.type != Material.SOUL_CAMPFIRE) {
            return null
        }

        val campfire = block.state as? Campfire ?: return null
        val pdc = campfire.persistentDataContainer

        val startTime = pdc.get(START_TIME_KEY, PersistentDataType.LONG)
        val endTime = pdc.get(END_TIME_KEY, PersistentDataType.LONG)
        val fuelQueue = pdc.get(FUEL_QUEUE_KEY, PersistentDataType.STRING) ?: ""

        return CampfireState(location, startTime, endTime, fuelQueue)
    }

    /**
     * Save the campfire state to the block's PDC.
     */
    fun saveState(state: CampfireState) {
        val block = state.location.block
        if (block.type != Material.CAMPFIRE && block.type != Material.SOUL_CAMPFIRE) {
            return
        }

        val campfire = block.state as? Campfire ?: return
        val pdc = campfire.persistentDataContainer

        state.startTime?.let { pdc.set(START_TIME_KEY, PersistentDataType.LONG, it) }
            ?: pdc.remove(START_TIME_KEY)
        
        state.endTime?.let { pdc.set(END_TIME_KEY, PersistentDataType.LONG, it) }
            ?: pdc.remove(END_TIME_KEY)
        
        if (state.fuelQueue.isNotEmpty()) {
            pdc.set(FUEL_QUEUE_KEY, PersistentDataType.STRING, state.fuelQueue)
        } else {
            pdc.remove(FUEL_QUEUE_KEY)
        }

        campfire.update()
    }

    /**
     * Clear all campfire data from a block's PDC.
     */
    fun clearState(location: Location) {
        val block = location.block
        if (block.type != Material.CAMPFIRE && block.type != Material.SOUL_CAMPFIRE) {
            return
        }

        val campfire = block.state as? Campfire ?: return
        val pdc = campfire.persistentDataContainer

        pdc.remove(START_TIME_KEY)
        pdc.remove(END_TIME_KEY)
        pdc.remove(FUEL_QUEUE_KEY)

        campfire.update()
    }

    /**
     * Check if a campfire has any stored data.
     */
    fun hasData(location: Location): Boolean {
        val block = location.block
        if (block.type != Material.CAMPFIRE && block.type != Material.SOUL_CAMPFIRE) {
            return false
        }

        val campfire = block.state as? Campfire ?: return false
        val pdc = campfire.persistentDataContainer

        return pdc.has(START_TIME_KEY) || pdc.has(END_TIME_KEY) || pdc.has(FUEL_QUEUE_KEY)
    }
}
