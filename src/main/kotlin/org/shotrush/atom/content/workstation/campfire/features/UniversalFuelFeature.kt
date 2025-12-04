package org.shotrush.atom.content.workstation.campfire.features

import net.momirealms.craftengine.bukkit.api.CraftEngineItems
import net.momirealms.craftengine.bukkit.item.DataComponentTypes
import net.momirealms.craftengine.core.item.ItemManager
import net.momirealms.craftengine.core.plugin.CraftEngine
import net.momirealms.craftengine.libraries.nbt.CompoundTag
import net.momirealms.craftengine.libraries.nbt.NumericTag
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Campfire
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.shotrush.atom.content.workstation.campfire.CampfireRegistry
import org.shotrush.atom.matches

/**
 * Modern fuel system that uses data components to determine burn time.
 * Supports any item with a campfire burn time component, allowing for different fuel types.
 * Implements a queue system (max 4 items) without visual item displays.
 */
class UniversalFuelFeature : CampfireRegistry.Listener {

    // Queue of fuel items waiting to be consumed, limited to 4 items
    private val fuelQueues = mutableMapOf<Location, MutableList<QueuedFuel>>()
    
    data class QueuedFuel(
        val burnTimeSeconds: Int,
        val addedAt: Long
    )

    /**
     * Try to add fuel to the campfire from an item stack.
     * Returns the new end time in milliseconds if successful, null otherwise.
     */
    fun tryAddFuel(registry: CampfireRegistry, loc: Location, item: ItemStack): Long? {
        val campfire = loc.block.state as? Campfire ?: return null

        // Get current queue or create new one
        val queue = fuelQueues.getOrPut(loc) { mutableListOf() }

        // Check if queue is full (max 4 items)
        if (queue.size >= 4) return null

        // Extract burn time from item component
        val burnTimeSeconds = getBurnTimeFromItem(item) ?: return null
        val burnTimeMs = burnTimeSeconds * 1000L

        // Add to queue
        queue.add(QueuedFuel(burnTimeSeconds, System.currentTimeMillis()))
        persistFuelQueue(loc)

        // Call the registry to extend the campfire timer properly
        val endTime = registry.addFuel(loc, burnTimeMs)
        return endTime
    }

    /**
     * Extract burn time in seconds from an item's data component.
     * Returns null if the item doesn't have a valid burn time component.
     */
    private fun getBurnTimeFromItem(item: ItemStack): Int? {
        // Wrap the ItemStack in CraftEngine's Item wrapper
        val wrappedItem = CraftEngine.instance().itemManager<ItemStack>().wrap(item)

        // Try to read from custom_data component
        try {
            val customData = wrappedItem.getSparrowNBTComponent(DataComponentTypes.CUSTOM_DATA)
            if (customData is CompoundTag) {
                val burnTimeTag = customData.get("campfire_burn_time_seconds")
                if (burnTimeTag is NumericTag) {
                    val burnTime = burnTimeTag.asInt
                    if (burnTime > 0) {
                        return burnTime
                    }
                }
            }
        } catch (e: Exception) {
            // If component reading fails, fall back to legacy logic
        }

        // Fallback: legacy items that don't have the component yet
        return when {
            item.matches("atom:straw") -> 90
            else -> null
        }
    }

    override fun onCampfireExtinguished(state: CampfireRegistry.CampfireState, reason: String) {
        // Clear the fuel queue when extinguished
        fuelQueues.remove(state.location)
        clearPersistence(state.location)
    }

    override fun onCampfireBroken(state: CampfireRegistry.CampfireState) {
        fuelQueues.remove(state.location)?.clear()
        clearPersistence(state.location)
    }

    override fun onResumeTimerScheduled(state: CampfireRegistry.CampfireState, remainingMs: Long) {
        // On server restart, try to restore fuel queue
        val queue = loadFuelQueue(state.location)
        if (queue.isNotEmpty()) {
            fuelQueues[state.location] = queue.toMutableList()
        }
    }

    /**
     * Process the fuel queue, consuming items as time passes.
     * Called periodically to manage fuel consumption.
     */
    fun processFuelQueue(loc: Location, registry: CampfireRegistry) {
        val queue = fuelQueues[loc] ?: return
        if (queue.isEmpty()) return
        
        val now = System.currentTimeMillis()
        var hasChanges = false
        
        // Process queue from oldest to newest
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val fuel = iterator.next()
            val elapsed = now - fuel.addedAt
            
            if (elapsed >= fuel.burnTimeSeconds * 1000L) {
                // This fuel item has been fully consumed
                iterator.remove()
                hasChanges = true
            }
        }
        
        if (hasChanges) {
            persistFuelQueue(loc)
            
            // Let the registry's burnout timer handle extinguishing, don't force it here
        }
    }

    /**
     * Get the current fuel queue for display purposes.
     */
    fun getFuelQueue(loc: Location): List<QueuedFuel> {
        return fuelQueues[loc]?.toList() ?: emptyList()
    }

    /**
     * Check if the fuel queue is full.
     */
    fun isQueueFull(loc: Location): Boolean {
        return (fuelQueues[loc]?.size ?: 0) >= 4
    }

    private fun persistFuelQueue(loc: Location) {
        val pos = net.momirealms.craftengine.core.world.BlockPos(loc.blockX, loc.blockY, loc.blockZ)
        val data = org.shotrush.atom.content.workstation.core.WorkstationDataManager.getWorkstationData(pos, "campfire")
        data.fuelQueue = fuelQueues[loc]?.map { "${it.burnTimeSeconds},${it.addedAt}" }?.joinToString(";") ?: ""
    }

    private fun loadFuelQueue(loc: Location): List<QueuedFuel> {
        val pos = net.momirealms.craftengine.core.world.BlockPos(loc.blockX, loc.blockY, loc.blockZ)
        val dataString = org.shotrush.atom.content.workstation.core.WorkstationDataManager.getAllWorkstations()
            .values.find { it.position == pos }?.fuelQueue ?: return emptyList()
        
        return dataString.split(";").filter { it.isNotEmpty() }.map { entry ->
            val parts = entry.split(",")
            if (parts.size == 2) {
                QueuedFuel(parts[0].toInt(), parts[1].toLong())
            } else null
        }.filterNotNull()
    }

    private fun clearPersistence(loc: Location) {
        val pos = net.momirealms.craftengine.core.world.BlockPos(loc.blockX, loc.blockY, loc.blockZ)
        val data = org.shotrush.atom.content.workstation.core.WorkstationDataManager.getWorkstationData(pos, "campfire")
        data.fuelQueue = ""
    }

    /**
     * Helper function to create an item with campfire burn time component.
     * This can be used to create new fuel items with different burn times.
     */
    companion object {
        fun createFuelItem(baseItem: ItemStack, burnTimeSeconds: Int): ItemStack {
            val item = baseItem.clone()
            
            // Use persistent data container like in Molds.kt
            item.editPersistentDataContainer { container ->
                container.set(NamespacedKey("atom", "campfire_burn_time_seconds"), PersistentDataType.STRING, burnTimeSeconds.toString())
            }
            
            return item
        }
    }
}