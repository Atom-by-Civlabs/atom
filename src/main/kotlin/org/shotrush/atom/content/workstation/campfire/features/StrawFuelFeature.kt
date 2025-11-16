package org.shotrush.atom.content.workstation.campfire.features

import com.github.shynixn.mccoroutine.folia.launch
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import io.papermc.paper.registry.entry.RegistryEntryMeta
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import net.momirealms.craftengine.bukkit.api.CraftEngineItems
import net.momirealms.craftengine.core.util.Key
import org.bukkit.Location
import org.bukkit.Tag
import org.bukkit.block.Campfire
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.shotrush.atom.Atom
import org.shotrush.atom.content.workstation.campfire.CampfireRegistry

class StrawFuelFeature : CampfireRegistry.Listener {

    data class FuelType(
        val check: (ItemStack) -> Boolean,
        val burnTimeMs: Long,
        val displayName: String
    )

    companion object {
        const val MAX_FUEL_SLOTS = 4 // Maximum number of fuel slots

        // Check if item is straw
        private fun isStraw(item: ItemStack): Boolean {
            return if (CraftEngineItems.isCustomItem(item)) {
                CraftEngineItems.getCustomItemId(item).toString() == "atom:straw"
            } else {
                false // Straw is a custom item, not vanilla
            }
        }

        // Check if item is a log using vanilla tag
        private fun isLog(item: ItemStack): Boolean {
            return Tag.LOGS.isTagged(item.type)
        }

        // Check if item is coal or charcoal
        private fun isCoal(item: ItemStack): Boolean {
            return item.type == Material.COAL || item.type == Material.CHARCOAL
        }

        val FUEL_TYPES = listOf(
            FuelType({ isStraw(it) }, 2 * 60 * 1000L, "Straw"), // 2 minutes
            FuelType({ isLog(it) }, 8 * 60 * 1000L, "Log"), // 8 minutes for any log
            FuelType({ isCoal(it) }, 5 * 60 * 1000L, "Coal") // 5 minutes for coal/charcoal
        )
    }

    private data class FuelSlot(
        val slotIndex: Int,
        val burnJob: Job,
        val fuelType: FuelType
    )
    
    private val activeFuelSlots = mutableMapOf<Location, MutableList<FuelSlot>>()
    private val atom get() = Atom.instance

    fun tryAddFuel(registry: CampfireRegistry, loc: Location, item: ItemStack): Pair<Long, String>? {
        val campfire = loc.block.state as? Campfire ?: return null

        // Find the fuel type that matches the item
        val fuelType = FUEL_TYPES.find { it.check(item) } ?: return null

        // Check if we have available fuel slots
        val currentFuelSlots = activeFuelSlots[loc] ?: mutableListOf()
        if (currentFuelSlots.size >= MAX_FUEL_SLOTS) {
            // Fully fueled -> do not extend time
            return null
        }

        // Extend timer
        val end = registry.addFuel(loc, fuelType.burnTimeMs) ?: return null

        // Find an available slot (not used for cooking)
        val availableSlot = findAvailableFuelSlot(campfire)
        if (availableSlot == -1) {
            // No available slots for fuel
            return null
        }

        // Schedule this fuel slot to burn out
        scheduleFuelBurn(loc, availableSlot, fuelType)

        return Pair(end, fuelType.displayName)
    }

    private fun scheduleFuelBurn(loc: Location, slotIndex: Int, fuelType: FuelType) {
        val job = atom.launch(atom.regionDispatcher(loc)) {
            delay(fuelType.burnTimeMs)
            // Fuel burned out, remove from tracking
            val slots = activeFuelSlots[loc]
            slots?.removeAll { it.slotIndex == slotIndex }
            if (slots?.isEmpty() == true) {
                activeFuelSlots.remove(loc)
            }
        }
        
        val slots = activeFuelSlots.getOrPut(loc) { mutableListOf() }
        slots.add(FuelSlot(slotIndex, job, fuelType))
    }

    override fun onCampfireExtinguished(state: CampfireRegistry.CampfireState, reason: String) {
        // Cancel all active fuel burn jobs for this campfire
        val slots = activeFuelSlots.remove(state.location)
        slots?.forEach { it.burnJob.cancel() }
    }

    override fun onCampfireBroken(state: CampfireRegistry.CampfireState) {
        // Cancel all active fuel burn jobs for this campfire
        val slots = activeFuelSlots.remove(state.location)
        slots?.forEach { it.burnJob.cancel() }
    }

    private fun findAvailableFuelSlot(campfire: Campfire): Int {
        // Find a slot that's empty (not used for cooking)
        for (i in 0 until campfire.size) {
            val item = campfire.getItem(i)
            if (item == null || item.type.isAir) {
                return i
            }
        }
        return -1
    }
}