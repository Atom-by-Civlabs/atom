package org.shotrush.atom.content.workstation.campfire

import com.github.shynixn.mccoroutine.folia.launch
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import com.github.shynixn.mccoroutine.folia.ticks
import kotlinx.coroutines.delay
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Campfire
import org.bukkit.block.data.Lightable
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.shotrush.atom.Atom
import org.shotrush.atom.content.workstation.campfire.features.BurnoutFeature
// import org.shotrush.atom.content.workstation.campfire.features.MoldFiringFeature
import org.shotrush.atom.content.workstation.campfire.features.UniversalFuelFeature
import org.shotrush.atom.core.api.annotation.RegisterSystem
import org.shotrush.atom.core.util.ActionBarManager
import org.shotrush.atom.item.MoldType
import org.shotrush.atom.item.Molds
import org.shotrush.atom.matches
import kotlin.random.Random

@RegisterSystem(
    id = "campfire_system",
    priority = 5,
    toggleable = true,
    description = "Unified campfire lifecycle (lighting, burnout, universal fuel, mold firing)",
    enabledByDefault = true
)
class CampfireSystem(private val plugin: Plugin) : Listener {

    private val registry = CampfireRegistry(plugin)
    private val burnout = BurnoutFeature()
    private val universalFuel = UniversalFuelFeature()

    // Prevent duplicate fuel additions within 100ms
    private val recentFuelAdditions = java.util.concurrent.ConcurrentHashMap<String, Long>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        registry.addListener(burnout)
        registry.addListener(universalFuel)
    }

    private val resumedWorlds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    
    @EventHandler
    fun onWorldLoad(event: org.bukkit.event.world.WorldLoadEvent) {
        if (resumedWorlds.add(event.world.name)) {
            Atom.instance.launch {
                delay(100L)
                registry.resumeFromDisk(event.world)
            }
        }
    }
    
    @EventHandler
    fun onPlayerJoin(event: org.bukkit.event.player.PlayerJoinEvent) {
        val world = event.player.world
        if (resumedWorlds.add(world.name)) {
            Atom.instance.launch {
                delay(2000L)
                registry.resumeFromDisk(world)
            }
        }
    }

    // Ensure campfires placed are unlit by default but tracked
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val b = event.blockPlaced
        if (b.type != Material.CAMPFIRE && b.type != Material.SOUL_CAMPFIRE) return

        val data = b.blockData
        if (data is Lightable) {
            data.isLit = false
            b.blockData = data
        }
        registry.trackOnPlace(b.location, lit = false)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.CAMPFIRE && block.type != Material.SOUL_CAMPFIRE) return

        val data = block.blockData as? Lightable ?: return
        val player = event.player
        val item = player.inventory.itemInMainHand

        if (data.isLit) {
            val key = "${player.uniqueId}:${block.location}"
            val now = System.currentTimeMillis()
            val lastAddition = recentFuelAdditions[key] ?: 0L
            if (now - lastAddition < 100L) {
                return
            }

            if (Molds.isMold(item) && Molds.getMoldType(item) == MoldType.Clay) {
                val campfire = block.state as? Campfire ?: return

                for (i in 0 until 4) {
                    if (campfire.getItem(i) == null) {
                        val shape = Molds.getMoldShape(item)
                        val canonicalItem = Molds.getMold(shape, MoldType.Clay).buildItemStack()
                        canonicalItem.amount = 1
                        
                        campfire.setItem(i, canonicalItem)
                        campfire.setCookTime(i, 0)
                        campfire.setCookTimeTotal(i, 60 * 20) // 60 seconds
                        campfire.update()
                        
                        if (player.gameMode != org.bukkit.GameMode.CREATIVE) {
                            item.subtract(1)
                        }
                        recentFuelAdditions[key] = now
                        
                        event.isCancelled = true
                        return
                    }
                }
                return
            }

            val end = universalFuel.tryAddFuel(registry, block.location, item)
            if (end != null) {
                event.isCancelled = true
                recentFuelAdditions[key] = now
                item.subtract(1)
                val remaining = (end - System.currentTimeMillis()).coerceAtLeast(0)
                val min = (remaining / 60000L).toInt()
                val sec = ((remaining % 60000L) / 1000L).toInt()
                val queueInfo = universalFuel.getFuelQueue(block.location)
                val queueStatus = if (queueInfo.size >= 4) " (Queue Full!)" else " (${queueInfo.size}/4 in queue)"
                ActionBarManager.send(player, "campfire", "<green>Added fuel! Time remaining:</green> <yellow>${min}m ${sec}s</yellow>${queueStatus}")
            } else {
                if (universalFuel.isQueueFull(block.location)) {
                    ActionBarManager.send(player, "campfire","<red>Fuel queue is full (max 4 items).</red>")
                } else {
                    ActionBarManager.send(player, "campfire","<red>Item is not valid fuel.</red>")
                }
            }
            return
        }

        // Lighting with pebble when unlit
        if (item.matches("atom:fire_starter") && !data.isLit) {
            // simulate multiple strikes with progress
            val strikesNeeded = 10 + Random.nextInt(6)
            startStrikeTask(player, block.location, strikesNeeded) {
                registry.lightAt(block.location)
                ActionBarManager.send(player, "campfire","<green>Successfully lit the campfire!</green>")
            }
            return
        }

        // Manual extinguish if player toggles and it becomes unlit
        Atom.instance.launch(Atom.instance.regionDispatcher(block.location)) {
            delay(50L)
            val updated = block.location.block.blockData as? Lightable ?: return@launch
            if (!updated.isLit) {
                registry.extinguishAt(block.location, "player")
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        val b = event.block
        if (b.type != Material.CAMPFIRE && b.type != Material.SOUL_CAMPFIRE) return
        
        registry.brokenAt(b.location)
        
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCampfireCook(event: org.bukkit.event.block.BlockCookEvent) {
        val source = event.source
        if (Molds.isMold(source) && Molds.getMoldType(source) == MoldType.Clay) {
            val shape = Molds.getMoldShape(source)
            val result = Molds.getMold(shape, MoldType.Fired).buildItemStack()
            event.result = result
        }
    }

    init {
        // Use global timer only for cleanup of in-memory cache (no block access)
        org.shotrush.atom.core.api.scheduler.SchedulerAPI.runGlobalTaskTimer({
            val now = System.currentTimeMillis()
            recentFuelAdditions.entries.removeIf { now - it.value > 1000L }
            
            // Schedule fuel queue processing on each campfire's region thread
            registry.getAllActiveLocations().forEach { location ->
                org.bukkit.Bukkit.getServer().regionScheduler.run(plugin, location) { _ ->
                    val state = registry.getState(location)
                    if (state != null && state.lit) {
                        universalFuel.processFuelQueue(location, registry)
                    }
                }
            }
        }, 100L, 100L)
    }

    private fun startStrikeTask(
        player: Player,
        campfireLoc: Location,
        strikesNeeded: Int,
        onSuccess: () -> Unit
    ) {
        val atom = Atom.instance
        atom.launch(atom.regionDispatcher(campfireLoc)) {
            var strikes = 0
            ActionBarManager.sendStatus(player, "<gray>Lighting campfire... Strike repeatedly</gray>")
            while (strikes < strikesNeeded) {
                delay(5.ticks)
                val data = campfireLoc.block.blockData
                if (data is Lightable && data.isLit) {
                    return@launch
                }
                // Basic validations
                if (!player.isOnline || player.location.world != campfireLoc.world ||
                    player.location.distance(campfireLoc) > 5.0 || !player.hasActiveItem()
                ) {
                    ActionBarManager.sendStatus(player, "<red>Lighting cancelled</red>")
                    delay(800)
                    ActionBarManager.clearStatus(player)
                    return@launch
                }
                // Feedback
                player.world.playSound(campfireLoc.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_STONE_HIT, 1.0f, 1.5f)
                player.world.spawnParticle(Particle.DUST_PLUME, campfireLoc.clone().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.02)
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
                ActionBarManager.sendStatus(player, str)
            }
            ActionBarManager.clearStatus(player)
            // Chance of success
            if (Random.nextDouble() < 0.7) {
                onSuccess()
                val center = campfireLoc.clone().add(0.5, 0.5, 0.5)
                center.world?.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f)
                center.world?.spawnParticle(Particle.FLAME, center, 20, 0.3, 0.3, 0.3, 0.02)
            } else {
                val center = campfireLoc.clone().add(0.5, 0.5, 0.5)
                center.world?.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 2.0f)
                center.world?.spawnParticle(Particle.SMOKE, center, 10, 0.2, 0.2, 0.2, 0.02)
                ActionBarManager.send(player, "campfire", "<red>Failed to light the campfire. Try again!</red>")
            }
        }
    }
}