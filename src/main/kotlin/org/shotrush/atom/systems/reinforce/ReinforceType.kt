package org.shotrush.atom.systems.reinforce

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.shotrush.atom.api.SingleItemRef

enum class ReinforceType(val displayName: String, val singleItemRef: SingleItemRef, val speedMultiplier: Double) {
    LIGHT("Light", SingleItemRef.material(Material.COPPER_INGOT), 1.25),
    MEDIUM("Medium", SingleItemRef.material(Material.IRON_INGOT), 1.75),
    HEAVY("Heavy", SingleItemRef.atom("steel_ingot"), 2.5);

    fun isHigher(type: ReinforceType?): Boolean {
        if(type == null) return true
        return ordinal > type.ordinal
    }

    companion object {
        fun byItem(item: ItemStack): ReinforceType? {
            return entries.find { it.singleItemRef.matches(item) }
        }
    }
}