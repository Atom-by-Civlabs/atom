package org.shotrush.atom.systems.reinforce

import com.github.shynixn.mccoroutine.folia.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.bukkit.Location
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.shotrush.atom.Atom
import org.shotrush.atom.api.ChunkKey
import org.shotrush.atom.api.chunkKey
import org.shotrush.atom.listener.AtomListener
import org.shotrush.atom.listener.eventDef
import org.shotrush.atom.sendMiniMessage
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class Vec3i(val x: Int, val y: Int, val z: Int)

@Serializable
data class ChunkCache(
    val map: MutableMap<Vec3i, ReinforceType>,
)

object ReinforcementSystem : AtomListener {
    override val eventDefs = mapOf(
        eventDef<BlockBreakEvent> {
            Atom.instance.regionDispatcher(it.block.location)
        },
        eventDef<ChunkLoadEvent> {
            Atom.instance.regionDispatcher(it.world, it.chunk.x, it.chunk.z)
        },
        eventDef<ChunkUnloadEvent> {
            Atom.instance.regionDispatcher(it.world, it.chunk.x, it.chunk.z)
        },
        eventDef<PlayerInteractEvent> {
            Atom.instance.entityDispatcher(it.player)
        },
        eventDef<PlayerJoinEvent> {
            Atom.instance.entityDispatcher(it.player)
        },
        eventDef<PlayerQuitEvent> {
            Atom.instance.entityDispatcher(it.player)
        }
    )


    private val chunkCache = ConcurrentHashMap<ChunkKey, ChunkCache>()

    fun getChunkMap(key: ChunkKey): ChunkCache =
        chunkCache.computeIfAbsent(key) { ChunkCache(mutableMapOf()) }

    fun getReinforcementLevel(location: Location): ReinforceType? {
        val cache = getChunkMap(location.chunkKey())
        return cache.map[Vec3i(location.blockX, location.blockY, location.blockZ)]
    }

    fun setReinforcementLevel(location: Location, level: ReinforceType?) {
        val ck = location.chunkKey()
        val cache = getChunkMap(ck)
        val vect = Vec3i(location.blockX, location.blockY, location.blockZ)
        if (level != null) {
            cache.map[vect] = level
        } else {
            cache.map.remove(vect)
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val pos = block.location
        val level = getReinforcementLevel(pos) ?: return
        event.isCancelled = true
        pos.world?.dropItemNaturally(pos, level.singleItemRef.createStack())
        setReinforcementLevel(pos, null)
    }

    @EventHandler
    fun onItemUse(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        val item = event.item ?: return
        val requestedLevel = ReinforceType.byItem(item) ?: return
        val currentLevel = getReinforcementLevel(block.location)
        if (requestedLevel.isHigher(currentLevel)) {
            if (currentLevel != null) {
                block.location.world?.dropItemNaturally(
                    block.location.clone().add(
                        event.blockFace.modX.toDouble(),
                        event.blockFace.modY.toDouble(),
                        event.blockFace.modZ.toDouble()
                    ), currentLevel.singleItemRef.createStack()
                )
            }
            setReinforcementLevel(block.location, requestedLevel)
            event.setUseItemInHand(Event.Result.DENY)
            item.amount--
            event.player.sendMiniMessage("<gray>Applied ${requestedLevel.displayName} reinforcement!</gray>")
        }
    }

    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val outlineManager: OutlineManager = OutlineManager(
        SnapshotCollector(VoxelShapeProvider.Default),
        VoxelOutlineBuilder(),
        ParticleOutlineRenderer3D(defaultStep = 0.5),
        defaultYBandHeight = 6,
        defaultHalfSize = 12,
        scope = pluginScope
    )

    private var job: Job? = null

    fun start() {
        job = Atom.instance.launch(Atom.instance.asyncDispatcher) {
            while (!Atom.instance.server.isStopping) {
                delay(1.ticks)
                outlineManager.tick(Atom.instance.server.onlinePlayers.toList())
            }
        }
    }

    fun stop() {
        job?.cancel()
        pluginScope.cancel()
    }

    private val renderingJobs = mutableMapOf<UUID, Job>()
    fun cancelRendering(player: UUID) {
        renderingJobs.remove(player)?.cancel()
    }

    fun startRendering(player: UUID, job: Job) {
        renderingJobs[player] = job
    }

    // Outline per-player settings API
    fun setOutlineSettings(player: UUID, settings: PlayerOutlineSettings) {
        outlineManager.setSettings(player, settings)
    }

    fun updateOutlineSettings(player: UUID, update: PlayerOutlineSettings.() -> PlayerOutlineSettings) {
        outlineManager.updateSettings(player, update)
    }

    fun getOutlineSettings(player: UUID): PlayerOutlineSettings = outlineManager.getSettings(player)

//    @EventHandler
//    fun onPlayerJoin(event: PlayerJoinEvent) {
//        startRendering(event.player.uniqueId, Atom.instance.launch(Atom.instance.asyncDispatcher) {
//            while (event.player.isOnline) {
//                delay(1.ticks)
//                outlineManager.
//            }
//        })
//    }
//
//    @EventHandler
//    fun onPlayerQuit(event: PlayerQuitEvent) {
//        cancelRendering(event.player.uniqueId)
//    }
}