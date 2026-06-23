package org.shotrush.atom.listener

import com.github.shynixn.mccoroutine.folia.entityDispatcher
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.plugin.java.JavaPlugin
import org.civlabs.atom.core.listener.AtomListener
import org.civlabs.atom.core.listener.eventDef
import org.shotrush.atom.Atom
import org.shotrush.atom.content.systems.ThirstSystem

class DeathPenaltyListener(val plugin: JavaPlugin): AtomListener {
    override val eventDefs = mapOf(eventDef<InventoryCloseEvent> {
        Atom.instance.entityDispatcher(it.player)
    })

    @EventHandler
    fun on(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (event.inventory
                .type != InventoryType.CRAFTING || !player.isDead || !player.isConnected || player.health > 0
        ) return

        val thirstSystem = ThirstSystem.instance
        thirstSystem.setThirst(player, 10.0)
        player.scheduler.runDelayed(plugin, {
            player.foodLevel = 10
            player.saturation = 0.0f
        },null, 10L)

        // #TODO add skill penalty
    }
}