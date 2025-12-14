package org.shotrush.atom.systems.reinforce

import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.CoroutineScope
import org.bukkit.entity.Player
import org.shotrush.atom.Atom
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class PlayerOutlineSettings(
    val halfSize: Int = 4,
    val yBandHeight: Int = 0,
    val buildCooldownTicks: Long = 10L,
    val renderIntervalTicks: Long = 5L,
    val particleStep: Double = 0.25,
)

class OutlineManager(
    private val snapshotCollector: SnapshotCollector,
    private val builder: VoxelOutlineBuilder,
    private val renderer: OutlineRenderer,
    private val scope: CoroutineScope,
    // Defaults used for players without overrides
    private val defaultBuildCooldownTicks: Long = 10L,
    private val defaultRenderIntervalTicks: Long = 5L,
    private val defaultHalfSize: Int = 4,
    private val defaultYBandHeight: Int = 0,
    private val defaultParticleStep: Double = 0.25,
) {
    private val sessions = ConcurrentHashMap<UUID, PlayerOutlineSession>()
    private val lastBuildTick = ConcurrentHashMap<UUID, Long>()
    private val lastRenderTick = ConcurrentHashMap<UUID, Long>()
    private val playerSettings = ConcurrentHashMap<UUID, PlayerOutlineSettings>()
    private var tickCounter: Long = 0

    private fun ensureSession(player: Player): PlayerOutlineSession =
        sessions.computeIfAbsent(player.uniqueId) { PlayerOutlineSession(it, scope) }

    fun shouldShowPlayer(player: Player): Boolean {
        val mainHand = player.inventory.itemInMainHand
        val offHand = player.inventory.itemInOffHand
        return ReinforceType.byItem(mainHand) != null || ReinforceType.byItem(offHand) != null
    }

    fun tick(players: Collection<Player>) {
        tickCounter++

        players.forEach { player ->
            Atom.instance.launch(Atom.instance.entityDispatcher(player)) {
                if (!shouldShowPlayer(player)) return@launch
                val session = ensureSession(player)
                val settings = getSettings(player.uniqueId)
                val needsNewBuild = shouldRebuild(player, session, settings)
                if (needsNewBuild && !session.isBuilding()) {
                    val last = lastBuildTick[player.uniqueId] ?: 0L
                    if (tickCounter - last >= settings.buildCooldownTicks) {
                        lastBuildTick[player.uniqueId] = tickCounter
                        session.startBuild(
                            player = player,
                            halfSize = settings.halfSize,
                            yBandHeight = settings.yBandHeight,
                            snapshotCollector = snapshotCollector,
                            builder = builder
                        )
                    }
                }
            }
        }

        players.forEach { player ->
            val settings = getSettings(player.uniqueId)
            val last = lastRenderTick[player.uniqueId] ?: 0L
            if (tickCounter - last >= settings.renderIntervalTicks) {
                lastRenderTick[player.uniqueId] = tickCounter
                val cache = sessions[player.uniqueId]?.cache ?: return@forEach
                Atom.instance.launch(Atom.instance.entityDispatcher(player)) {
                    if (shouldShowPlayer(player))
                        renderer.render(player, cache.geometry, settings.particleStep)
                }
            }
        }
    }

    private fun shouldRebuild(player: Player, session: PlayerOutlineSession, settings: PlayerOutlineSettings): Boolean {
        val cache = session.cache ?: return true
        val bx = player.location.blockX
        val bz = player.location.blockZ
        val movedCell = (bx != cache.originX) || (bz != cache.originZ)
        val sizeChanged = cache.halfSize != settings.halfSize
        return movedCell || sizeChanged
    }

    private fun defaultSettings(): PlayerOutlineSettings = PlayerOutlineSettings(
        halfSize = defaultHalfSize,
        yBandHeight = defaultYBandHeight,
        buildCooldownTicks = defaultBuildCooldownTicks,
        renderIntervalTicks = defaultRenderIntervalTicks,
        particleStep = defaultParticleStep
    )

    fun getSettings(playerId: UUID): PlayerOutlineSettings =
        playerSettings.computeIfAbsent(playerId) { defaultSettings() }

    fun setSettings(playerId: UUID, settings: PlayerOutlineSettings) {
        playerSettings[playerId] = settings
    }

    fun updateSettings(playerId: UUID, update: PlayerOutlineSettings.() -> PlayerOutlineSettings) {
        val current = getSettings(playerId)
        setSettings(playerId, current.update())
    }
}