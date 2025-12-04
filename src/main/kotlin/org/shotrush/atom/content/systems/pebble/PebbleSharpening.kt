package org.shotrush.atom.content.systems.pebble

import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import com.github.shynixn.mccoroutine.folia.ticks
import kotlinx.coroutines.delay
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
import net.momirealms.craftengine.bukkit.api.CraftEngineItems
import net.momirealms.craftengine.core.block.properties.Property
import net.momirealms.craftengine.core.util.Key
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.data.Lightable
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.shotrush.atom.Atom
import org.shotrush.atom.core.api.annotation.RegisterSystem
import org.shotrush.atom.core.util.ActionBarManager
import org.shotrush.atom.matches
import kotlin.random.Random

@RegisterSystem(
    id = "pebble_sharpening",
    priority = 5,
    toggleable = true,
    description = "",
    enabledByDefault = true
)
class PebbleSharpening(private val plugin: Plugin) : Listener {
    companion object {
        private val allStoneBlocks = listOf("minecraft:stone", "minecraft:mossy_cobblestone", "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff", "minecraft:deepslate")
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return

        val player = event.player
        val item = player.inventory.itemInMainHand
        if (!item.matches("atom:pebble")) return
        if (allStoneBlocks.none { block.matches(it) }) return

        val strikesNeeded = 6 + Random.nextInt(8)
        Atom.instance.launch(Atom.instance.entityDispatcher(player)) {
            var strikes = 0
            ActionBarManager.send(player, "stone_sharpen", "<gray>Bashing Rocks Together</gray>")
            while (strikes < strikesNeeded) {
                delay(7.ticks)
                // Basic validations
                if (!player.isOnline ||
                    player.location.distance(block.location) > 5.0 || !player.hasActiveItem()
                ) {
                    ActionBarManager.send(player, "stone_sharpen", "<red>Bashing cancelled</red>")
                    return@launch
                }
                // Feedback
                player.world.playSound(block.location.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_STONE_HIT, 1.0f, 1.5f)
                player.world.spawnParticle(
                    Particle.DUST_PLUME,
                    block.location.clone().add(0.5, 0.5, 0.5),
                    3,
                    0.2,
                    0.2,
                    0.2,
                    0.02
                )
                strikes++
                val progress = (strikes.toFloat() / strikesNeeded * 100).toInt()

                val str = buildString {
                    append("<yellow>$progress%</yellow> ")
                    append("<dark_gray>[")
                    append("<green>")
                    val total = 20
                    val bars = (total * (progress / 100.0)).toInt()
                    repeat(bars) {
                        append("|")
                    }
                    append("</green><gray>")
                    repeat(total - bars) {
                        append("|")
                    }
                    append("</gray>")
                    append("]")
                    append("<dark_gray>")
                }
                ActionBarManager.send(player, "stone_sharpen", str)
            }
            // Chance of success
            val center = block.location.clone().add(0.5, 0.5, 0.5)
            item.amount--
            if (Random.nextDouble() < 0.7) {
                player.inventory.addItem(
                    CraftEngineItems.byId(Key.of("atom:sharpened_rock"))?.buildItemStack() ?: ItemStack.empty()
                )
                center.world?.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f)
                center.world?.spawnParticle(Particle.CRIT, center, 20, 0.3, 0.3, 0.3, 0.02)
                Atom.instance.launch(Atom.instance.regionDispatcher(block.location)) {
                    block.type = Material.COBBLESTONE
                }
                ActionBarManager.send(player, "stone_sharpen" ,"<green>Stone sharpened!</green>")
            } else {
                player.inventory.addItem(ItemStack(Material.GUNPOWDER))
                center.world?.playSound(center, Sound.BLOCK_STONE_BREAK, 0.5f, 2.0f)
                center.world?.spawnParticle(Particle.SMOKE, center, 10, 0.2, 0.2, 0.2, 0.02)
                ActionBarManager.send(player, "stone_sharpen" ,"<red>The pebble crumbled to dust!</red>")
            }
        }
        return
    }
}