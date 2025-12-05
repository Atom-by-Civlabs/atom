package org.shotrush.atom.listener

import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import com.github.shynixn.mccoroutine.folia.registerSuspendingEvents
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.civlabs.atom.core.listener.eventDef
import org.shotrush.atom.Atom
import org.shotrush.atom.core.util.ActionBarManager
import org.civlabs.atom.core.util.getNamespacedKey
import org.civlabs.atom.core.util.getNamespacedPath
import org.shotrush.atom.item.Material
import org.shotrush.atom.item.MoldShape

object PlayerMiningListener : Listener {

    fun register(atom: Atom) {
        val eventDispatcher = mapOf(
            eventDef<BlockBreakEvent> { atom.regionDispatcher(it.block.location) },
            eventDef<PlayerInteractEvent> {
                if (it.clickedBlock != null) atom.regionDispatcher(it.clickedBlock!!.location)
                else atom.entityDispatcher(it.player)
            }
        )
        atom.server.pluginManager.registerSuspendingEvents(this, atom, eventDispatcher)
    }

    private val miningRequirements: Map<String, Pair<MoldShape, Material>> = buildMap {
        // Dirt/Grass
        put("minecraft:dirt", MoldShape.Shovel to Material.Stone)
        put("minecraft:grass_block", MoldShape.Shovel to Material.Stone)
        put("minecraft:mud", MoldShape.Shovel to Material.Stone)
        put("minecraft:podzol", MoldShape.Shovel to Material.Stone)
        put("minecraft:coarse_dirt", MoldShape.Shovel to Material.Stone)
        put("minecraft:rooted_dirt", MoldShape.Shovel to Material.Stone)

        // Stone
        put("minecraft:stone", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:cobblestone", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:andesite", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:diorite", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:granite", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:tuff", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:calcite", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:dripstone_block", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:packed_mud", MoldShape.Pickaxe to Material.Stone)

        // Deepslate
        put("minecraft:deepslate", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:cobbled_deepslate", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:polished_deepslate", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:deepslate_bricks", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:deepslate_tiles", MoldShape.Pickaxe to Material.Steel)

        // Ores (stone)
        put("minecraft:coal_ore", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:copper_ore", MoldShape.Pickaxe to Material.Stone)
        put("atom:tin_ore", MoldShape.Pickaxe to Material.Copper)
        put("minecraft:iron_ore", MoldShape.Pickaxe to Material.Bronze)
        put("minecraft:gold_ore", MoldShape.Pickaxe to Material.Iron)
        put("minecraft:redstone_ore", MoldShape.Pickaxe to Material.Iron)
        put("minecraft:lapis_ore", MoldShape.Pickaxe to Material.Copper)
        put("minecraft:diamond_ore", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:emerald_ore", MoldShape.Pickaxe to Material.Steel)

        // Ores (deepslate variants)
        put("minecraft:deepslate_coal_ore", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:deepslate_copper_ore", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:deepslate_iron_ore", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:deepslate_gold_ore", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:deepslate_redstone_ore", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:deepslate_lapis_ore", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:deepslate_diamond_ore", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:deepslate_emerald_ore", MoldShape.Pickaxe to Material.Steel)

        // Raw ore blocks
        put("minecraft:raw_iron_block", MoldShape.Pickaxe to Material.Copper)
        put("minecraft:raw_copper_block", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:raw_gold_block", MoldShape.Pickaxe to Material.Iron)

        // Metal/valuable blocks
        put("minecraft:iron_block", MoldShape.Pickaxe to Material.Copper)
        put("minecraft:copper_block", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:gold_block", MoldShape.Pickaxe to Material.Iron)
        put("minecraft:lapis_block", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:diamond_block", MoldShape.Pickaxe to Material.Iron)
        put("minecraft:emerald_block", MoldShape.Pickaxe to Material.Iron)
        put("minecraft:redstone_block", MoldShape.Pickaxe to Material.Iron)
        put("minecraft:netherite_block", MoldShape.Pickaxe to Material.Steel)

        // Obsidian and similar
        put("minecraft:obsidian", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:crying_obsidian", MoldShape.Pickaxe to Material.Steel)
        put("minecraft:ancient_debris", MoldShape.Pickaxe to Material.Steel)

        // Overworld building stones/bricks
        put("minecraft:stone_bricks", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:mossy_stone_bricks", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:smooth_stone", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:cobblestone_wall", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:cobblestone_stairs", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:cobblestone_slab", MoldShape.Pickaxe to Material.Stone)

        // Terracotta and glazed terracotta
        put("minecraft:terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:white_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:orange_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:magenta_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:light_blue_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:yellow_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:lime_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:pink_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:gray_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:light_gray_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:cyan_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:purple_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:blue_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:brown_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:green_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:red_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:black_glazed_terracotta", MoldShape.Pickaxe to Material.Stone)

        // Bricks
        put("minecraft:bricks", MoldShape.Pickaxe to Material.Stone)

        // Misc hard blocks
        put("minecraft:prismarine", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:packed_ice", MoldShape.Pickaxe to Material.Stone)
        put("minecraft:blue_ice", MoldShape.Pickaxe to Material.Stone)
    }

    private fun isRelevantBlock(blockKey: String): Boolean =
        miningRequirements.containsKey(blockKey)

    private fun requiredFor(blockKey: String): Pair<MoldShape, Material>? =
        miningRequirements[blockKey]

    // Compare tiers; ensure enum order reflects progression
    private fun isTierAtLeast(actual: Material, required: Material): Boolean =
        actual.ordinal >= required.ordinal

    // Tool parsing using "<tier>_<shape>" convention
    private fun parseTool(stack: ItemStack): Pair<Material, MoldShape>? {
        val path = stack.getNamespacedPath()
        val shape = MoldShape.entries.firstOrNull { path.endsWith("_${it.id}") } ?: return null
        val tier = Material.entries.firstOrNull { path.startsWith("${it.id}_") } ?: return null
        return tier to shape
    }

    // Optional early feedback on left-click
    @EventHandler
    suspend fun on(event: PlayerInteractEvent) {
        if (event.action != Action.LEFT_CLICK_BLOCK) return
        val player = event.player
        if (player.gameMode == GameMode.CREATIVE) return
        val block = event.clickedBlock ?: return

        val blockKey = block.getNamespacedKey()
        if (!isRelevantBlock(blockKey)) return

        val item = event.item ?: ItemStack.empty()
        val tool = parseTool(item)

        val (requiredShape, requiredTier) = requiredFor(blockKey) ?: return

        if (tool == null) {
            player.feedback(needTool = true, blockKey = blockKey)
            return
        }

        val (tier, shape) = tool
        if (shape != requiredShape) {
            player.feedback(wrongShape = true, shape = shape, blockKey = blockKey)
            return
        }

        if (!isTierAtLeast(tier, requiredTier)) {
            player.feedback(
                tooLowTier = true,
                requiredTier = requiredTier,
                shape = requiredShape,
                blockKey = blockKey
            )
        }
    }

    @EventHandler
    suspend fun on(event: BlockBreakEvent) {
        val player = event.player
        if (player.gameMode == GameMode.CREATIVE) return

        val block = event.block
        val blockKey = block.getNamespacedKey()
        if (!isRelevantBlock(blockKey)) return

        val item = player.inventory.itemInMainHand
        val tool = parseTool(item)

        val (requiredShape, requiredTier) = requiredFor(blockKey) ?: return

        if (tool == null || tool.second == MoldShape.Ingot) {
            event.isCancelled = true
            player.feedback(needTool = true, blockKey = blockKey)
            return
        }

        val (tier, shape) = tool
        if (shape != requiredShape) {
            event.isCancelled = true
            player.feedback(wrongShape = true, shape = shape, blockKey = blockKey)
            return
        }

        if (!isTierAtLeast(tier, requiredTier)) {
            event.isCancelled = true
            player.feedback(
                tooLowTier = true,
                requiredTier = requiredTier,
                shape = requiredShape,
                blockKey = blockKey
            )
        }
        PlayerExhaustionListener.updateCurrentAction(PlayerAction.MINING, player)
    }

    // Simple feedback helper (actionbar + sound)
    private fun Player.feedback(
        needTool: Boolean = false,
        wrongShape: Boolean = false,
        tooLowTier: Boolean = false,
        requiredTier: Material? = null,
        shape: MoldShape? = null,
        blockKey: String,
    ) {
        val split = blockKey.split(":")
        val translatableKey = "<lang:block.${split[0]}.${split[1]}>"
        val msg = when {
            needTool -> "You need a proper tool to mine $translatableKey."
            wrongShape -> "Wrong tool type${shape?.let { " ($it)" } ?: ""} for $translatableKey."
            tooLowTier -> "Your tool is too weak. Need <l10n:material.${requiredTier?.id}.name> or better."
            else -> null
        }
        msg?.let { ActionBarManager.send(this, "mining", it, 4) }
        playSound(location, Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.7f)
    }
}