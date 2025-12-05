package org.shotrush.atom.listener

import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import com.github.shynixn.mccoroutine.folia.registerSuspendingEvents
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.entity.Player

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

import org.shotrush.atom.Atom
import java.util.UUID
import kotlin.math.max

enum class PlayerAction {
    MINING,
    FARMING,
    CRAFTING,
    FISHING,
    COMBAT
}

data class ExhaustionData(
    var currentAction: PlayerAction,
    var currentStreak: Int = 0
) {
    fun updateAction(action: PlayerAction) {
        if (currentAction == action) currentStreak++
        else currentStreak = 1
        currentAction = action
    }
}

object PlayerExhaustionListener : Listener {
    private val playerExhaustionData = mutableMapOf<UUID, ExhaustionData>()
    fun register(atom: Atom) {
        val eventDispatcher = mapOf(
            eventDef<BlockBreakEvent> { atom.regionDispatcher(it.block.location) },
            eventDef<CraftItemEvent> { atom.entityDispatcher(it.whoClicked)},
            eventDef<PlayerFishEvent> { atom.entityDispatcher(it.player)},
            eventDef<PlayerInteractEvent> { atom.entityDispatcher(it.player) }
        )
        atom.server.pluginManager.registerSuspendingEvents(this, atom, eventDispatcher)
    }
    val ACTION_REPETITION_KEY = NamespacedKey("atom", "action_repetition") // might need a better name
    val LAST_ACTION_KEY = NamespacedKey("atom", "last_action")

    private fun isFarmingTool(item: ItemStack): Boolean {
        return Tag.ITEMS_HOES.isTagged(item.type) || when (item.type) {
            Material.SHEARS -> true
            else -> false
        }
    }

    private fun isFarmingBlock(block: Block): Boolean {
        return when (block.type) {
            Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.MELON, Material.PUMPKIN,
            Material.COCOA, Material.SUGAR_CANE, Material.CACTUS,
            Material.BAMBOO, Material.KELP, Material.SEA_PICKLE,
            Material.SWEET_BERRY_BUSH, Material.GLOW_BERRIES -> true
            else -> false
        }
    }

    @EventHandler
    fun on(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return

        updateCurrentAction(PlayerAction.CRAFTING, player)
    }

    @EventHandler
    fun on(event: PlayerFishEvent) {
        val player = event.player

        if (event.state == PlayerFishEvent.State.REEL_IN) {
            updateCurrentAction(PlayerAction.FISHING, player)
        }
    }

    @EventHandler
    fun on(event: BlockBreakEvent) {
        val player = event.player
        val mainItem = player.inventory.itemInMainHand

        if (isFarmingBlock(event.block) || isFarmingTool(mainItem)) {
            updateCurrentAction(PlayerAction.FARMING, player)
        }
    }

    @EventHandler
    fun on(event: PlayerInteractEvent) {
        val player = event.player
        val mainItem = player.inventory.itemInMainHand
        val block = event.clickedBlock ?: return

        if (isFarmingTool(mainItem) && when (block.type) { Material.DIRT, Material.GRASS_BLOCK -> true else -> false} ) {
            updateCurrentAction(PlayerAction.FARMING, player)
        }
    }

    fun updateCurrentAction(action: PlayerAction, player: Player) {
        val exhaustionData = playerExhaustionData.getOrPut(player.uniqueId) { ExhaustionData(action) }

        exhaustionData.updateAction(action)

        if (exhaustionData.currentStreak % 3 == 1) {
            applyExhaustion(player)
        }
    }

    private fun applyExhaustion(player: Player) {
        if (player.foodLevel < 0) {
            player.damage(1.0)
            return
        }
        player.sendMessage("Exhaustion applied")

       // if (player.saturation > 0) {
       //     player.saturation = max(0f, player.saturation - 0.5f)
       // } else {
        player.foodLevel = max(0, player.foodLevel - 1)
       // }
    }
}