package org.shotrush.atom.content.workstation.campfire.features

import net.momirealms.craftengine.bukkit.item.DataComponentTypes
import net.momirealms.craftengine.core.plugin.CraftEngine
import net.momirealms.craftengine.libraries.nbt.CompoundTag
import net.momirealms.craftengine.libraries.nbt.NumericTag
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.shotrush.atom.content.workstation.campfire.CampfireData
import org.shotrush.atom.content.workstation.campfire.CampfireRegistry

/**
 * Fuel system that uses CraftEngine's custom_data component to determine burn time.
 * Items with `campfire_burn_time_seconds` in their custom_data can be used as fuel.
 * 
 * Fuel queue is stored in the block's PDC (PersistentDataContainer).
 */
class UniversalFuelFeature : CampfireRegistry.Listener {

    companion object {
        private const val MAX_QUEUE_SIZE = 4
        private const val BURN_TIME_KEY = "campfire_burn_time_seconds"
    }

    data class QueuedFuel(
        val burnTimeSeconds: Int,
        val addedAt: Long
    )

    // In-memory cache of fuel queues (restored from PDC on resume)
    private val fuelQueues = mutableMapOf<Location, MutableList<QueuedFuel>>()

    /**
     * Try to add fuel to the campfire from an item stack.
     * Returns the new end time in milliseconds if successful, null otherwise.
     */
    fun tryAddFuel(registry: CampfireRegistry, loc: Location, item: ItemStack): Long? {
        if (item.isEmpty) return null

        val queue = fuelQueues.getOrPut(loc) { mutableListOf() }
        if (queue.size >= MAX_QUEUE_SIZE) return null

        val burnTimeSeconds = getBurnTimeFromItem(item) ?: return null
        val burnTimeMs = burnTimeSeconds * 1000L

        queue.add(QueuedFuel(burnTimeSeconds, System.currentTimeMillis()))
        persistFuelQueue(loc)

        return registry.addFuel(loc, burnTimeMs)
    }

    /**
     * Extract burn time in seconds from an item's custom_data component.
     */
    private fun getBurnTimeFromItem(item: ItemStack): Int? {
        if (item.isEmpty) return null

        return try {
            val wrappedItem = CraftEngine.instance().itemManager<ItemStack>().wrap(item)
            val customData = wrappedItem.getSparrowNBTComponent(DataComponentTypes.CUSTOM_DATA)
            
            if (customData is CompoundTag) {
                val burnTimeTag = customData.get(BURN_TIME_KEY)
                if (burnTimeTag is NumericTag) {
                    val burnTime = burnTimeTag.asInt
                    if (burnTime > 0) return burnTime
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    override fun onCampfireExtinguished(state: CampfireData.CampfireState, reason: String) {
        fuelQueues.remove(state.location)
        clearFuelQueuePersistence(state.location)
    }

    override fun onCampfireBroken(state: CampfireData.CampfireState) {
        fuelQueues.remove(state.location)
        // PDC is automatically cleared when block is broken
    }

    override fun onResumeTimerScheduled(state: CampfireData.CampfireState, remainingMs: Long) {
        // Restore fuel queue from PDC
        val queue = loadFuelQueue(state.location)
        if (queue.isNotEmpty()) {
            fuelQueues[state.location] = queue.toMutableList()
        }
    }

    /**
     * Process the fuel queue, removing consumed items.
     */
    fun processFuelQueue(loc: Location, registry: CampfireRegistry) {
        val queue = fuelQueues[loc] ?: return
        if (queue.isEmpty()) return

        val now = System.currentTimeMillis()
        var hasChanges = false

        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val fuel = iterator.next()
            val elapsed = now - fuel.addedAt
            if (elapsed >= fuel.burnTimeSeconds * 1000L) {
                iterator.remove()
                hasChanges = true
            }
        }

        if (hasChanges) {
            persistFuelQueue(loc)
        }
    }

    fun getFuelQueue(loc: Location): List<QueuedFuel> = fuelQueues[loc]?.toList() ?: emptyList()

    fun isQueueFull(loc: Location): Boolean = (fuelQueues[loc]?.size ?: 0) >= MAX_QUEUE_SIZE

    /**
     * Persist fuel queue to the block's PDC via CampfireData.
     */
    private fun persistFuelQueue(loc: Location) {
        val state = CampfireData.getState(loc) ?: return
        state.fuelQueue = fuelQueues[loc]?.joinToString(";") { "${it.burnTimeSeconds},${it.addedAt}" } ?: ""
        CampfireData.saveState(state)
    }

    /**
     * Load fuel queue from PDC.
     */
    private fun loadFuelQueue(loc: Location): List<QueuedFuel> {
        val state = CampfireData.getState(loc) ?: return emptyList()
        val dataString = state.fuelQueue
        if (dataString.isEmpty()) return emptyList()

        return dataString.split(";")
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                val parts = entry.split(",")
                if (parts.size == 2) {
                    try {
                        QueuedFuel(parts[0].toInt(), parts[1].toLong())
                    } catch (e: NumberFormatException) {
                        null
                    }
                } else null
            }
    }

    private fun clearFuelQueuePersistence(loc: Location) {
        val state = CampfireData.getState(loc) ?: return
        state.fuelQueue = ""
        CampfireData.saveState(state)
    }
}
