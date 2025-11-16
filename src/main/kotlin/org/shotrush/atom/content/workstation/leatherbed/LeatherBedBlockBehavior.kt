package org.shotrush.atom.content.workstation.leatherbed

import com.github.shynixn.mccoroutine.folia.launch
import com.github.shynixn.mccoroutine.folia.regionDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.momirealms.craftengine.bukkit.api.CraftEngineItems
import net.momirealms.craftengine.core.block.BlockBehavior
import net.momirealms.craftengine.core.block.CustomBlock
import net.momirealms.craftengine.core.block.ImmutableBlockState
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory
import net.momirealms.craftengine.core.block.behavior.EntityBlockBehavior
import net.momirealms.craftengine.core.block.entity.BlockEntity
import net.momirealms.craftengine.core.block.entity.BlockEntityType
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker
import net.momirealms.craftengine.core.entity.player.InteractionResult
import net.momirealms.craftengine.core.item.context.UseOnContext
import net.momirealms.craftengine.core.util.Key
import net.momirealms.craftengine.core.world.BlockPos
import net.momirealms.craftengine.core.world.CEWorld
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.joml.AxisAngle4f
import org.joml.Vector3f
import org.shotrush.atom.Atom
import org.shotrush.atom.content.workstation.Workstations
import org.shotrush.atom.content.workstation.core.InteractiveSurface
import org.shotrush.atom.content.workstation.core.PlacedItem
import org.shotrush.atom.content.workstation.core.WorkstationDataManager
import org.shotrush.atom.core.util.ActionBarManager
import org.shotrush.atom.matches


class LeatherBedBlockBehavior(block: CustomBlock) : InteractiveSurface(block) {

    companion object {
        private val activeProcessing = mutableMapOf<Player, Job>()
        internal val stabilizingLeather = mutableMapOf<BlockPos, Job>()
        internal val curingStartTimes = mutableMapOf<BlockPos, Long>()
        internal var CURING_TIME_MS = 10 * 60 * 1000L // 10 minutes default

        fun isScrapingTool(item: ItemStack): Boolean {
            return item.matches("atom:sharpened_rock") || item.matches("atom:knife")
        }
    }

    object Factory : BlockBehaviorFactory {
        override fun create(
            block: CustomBlock,
            arguments: Map<String?, Any?>,
        ): BlockBehavior = LeatherBedBlockBehavior(block)
    }

    override fun <T : BlockEntity> blockEntityType(state: ImmutableBlockState): BlockEntityType<T> =
        @Suppress("UNCHECKED_CAST")
        Workstations.LEATHER_BED.type as BlockEntityType<T>

    override fun createBlockEntity(
        pos: BlockPos,
        state: ImmutableBlockState,
    ): BlockEntity = LeatherBedBlockEntity(pos, state)

    override fun getMaxItems(): Int = 1

    override fun canPlaceItem(item: ItemStack): Boolean {
        val itemId = CraftEngineItems.getCustomItemId(item)
        val isRawLeather = itemId != null && itemId.value().startsWith("animal_leather_raw_")
        val isCuredLeather = itemId != null && itemId.value().startsWith("animal_leather_cured_")
        val isProcessedLeather = itemId != null && itemId.value().startsWith("animal_leather_") && !itemId.value().startsWith("animal_leather_raw_")

        return isRawLeather || isCuredLeather || isProcessedLeather
    }

    override fun calculatePlacement(player: Player, itemCount: Int): Vector3f {
        return Vector3f(-0.05f, 0.75f, 0.60f)
    }

    override fun getFullMessage(): String = "<red>Leather bed is full!</red>"

    override fun getEmptyMessage(): String = "<red>Place leather first!</red>"

    override fun getItemDisplayRotation(item: PlacedItem): AxisAngle4f {
        // Lay flat on the bed
        return AxisAngle4f((Math.PI / 2).toFloat(), 0f, 0f, 0f)
    }

    override fun getItemDisplayScale(item: PlacedItem): Vector3f {
        // Full size for leather
        return Vector3f(1f, 1f, 1f)
    }

    override fun useOnBlock(
        context: UseOnContext,
        state: ImmutableBlockState,
    ): InteractionResult {
        val player = context.player?.platformPlayer() as? Player ?: return InteractionResult.PASS
        val item = context.item.item as? ItemStack ?: return InteractionResult.PASS
        val pos = context.clickedPos

        val blockEntity = context.level.storageWorld().getBlockEntityAtIfLoaded(pos)

        if (blockEntity !is LeatherBedBlockEntity) return InteractionResult.PASS

        if (isScrapingTool(item)) {
            if (!blockEntity.hasItem()) {
                ActionBarManager.send(player, getEmptyMessage())
                return InteractionResult.SUCCESS
            } else {
                blockEntity.startScraping(player, item)
            }
            return InteractionResult.SUCCESS
        } else {
            return if (player.isSneaking) {
                blockEntity.tryEmptyItems(player, item)
            } else {
                blockEntity.tryPlaceItem(player, item)
            }
        }
    }

    override fun <T : BlockEntity?> createSyncBlockEntityTicker(
        level: CEWorld,
        state: ImmutableBlockState,
        blockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T> {
        return EntityBlockBehavior.createTickerHelper { _, _, _, be: LeatherBedBlockEntity ->
            be.tick()
        }
    }

    override fun onRemoved() {
        activeProcessing.values.forEach { it.cancel() }
        activeProcessing.clear()

        // Cancel any ongoing curing processes
        blockPos?.let { pos ->
            stabilizingLeather[pos]?.cancel()
            stabilizingLeather.remove(pos)
            curingStartTimes.remove(pos)

            // Clear persistence
            val data = WorkstationDataManager.getWorkstationData(pos, "leather_bed")
            data.curingStartTime = null
            WorkstationDataManager.saveData()
        }

        super.onRemoved()
    }

    fun startStabilization(pos: BlockPos) {
        // Cancel any existing job
        stabilizingLeather[pos]?.cancel()

        // Get current workstation data
        val workstationData = WorkstationDataManager.getWorkstationData(pos, "leather_bed")
        val currentItem = workstationData.placedItems.lastOrNull()

        // Verify we have processed leather to cure
        if (currentItem == null) {
            Atom.instance.logger.info("StartStabilization: No item found at $pos")
            return
        }
        
        val itemId = CraftEngineItems.getCustomItemId(currentItem.item)
        val isProcessedLeather = itemId != null && itemId.value().startsWith("animal_leather_") && !itemId.value().startsWith("animal_leather_raw_")
        
        if (!isProcessedLeather) {
            Atom.instance.logger.info("StartStabilization: No processed leather found at $pos")
            return
        }

        Atom.instance.logger.info("Starting leather stabilization at $pos")

        // Record start time
        val startTime = System.currentTimeMillis()
        curingStartTimes[pos] = startTime
        workstationData.curingStartTime = startTime
        WorkstationDataManager.saveData()

        // Get location for region dispatcher
        val world = Bukkit.getWorld("world") ?: return
        val location = org.bukkit.Location(
            world,
            pos.x().toDouble() + 0.5,
            pos.y().toDouble() + 0.5,
            pos.z().toDouble() + 0.5
        )

        // Schedule completion using Folia region dispatcher
        val job = Atom.instance.launch(Atom.instance.regionDispatcher(location)) {
            delay(CURING_TIME_MS)

            // Verify leather is still there and complete the process
            val updatedData = WorkstationDataManager.getWorkstationData(pos, "leather_bed")
            val oldLeatherItem = updatedData.placedItems.lastOrNull()

            val oldItemId = oldLeatherItem?.let { CraftEngineItems.getCustomItemId(it.item) }
            val isOldProcessedLeather = oldItemId != null && oldItemId.value().startsWith("animal_leather_") && !oldItemId.value().startsWith("animal_leather_raw_")
            
            if (oldLeatherItem != null && isOldProcessedLeather) {
                Atom.instance.logger.info("Completing leather stabilization at $pos")

                // Create cured leather
                val animals = listOf(
                    "cow", "pig", "sheep", "chicken", "rabbit", "horse", "donkey", "mule",
                    "llama", "goat", "cat", "wolf", "fox", "panda", "polar_bear", "ocelot", "camel"
                )
                val randomAnimal = animals.random()
                val curedLeatherId = "atom:animal_leather_cured_$randomAnimal"

                CraftEngineItems.byId(Key.of(curedLeatherId))?.let { curedItem ->
                    // Create the cured leather item
                    val curedLeather = curedItem.buildItemStack()

                    // Update the placed item
                    updatedData.placedItems.clear()
                    val newPlacedItem = PlacedItem(
                        item = curedLeather,
                        position = oldLeatherItem.position,
                        yaw = oldLeatherItem.yaw,
                        displayUUID = oldLeatherItem.displayUUID
                    )
                    updatedData.placedItems.add(newPlacedItem)

                    // Update the visual display
                    newPlacedItem.displayUUID?.let { uuid ->
                        (Bukkit.getEntity(uuid) as? org.bukkit.entity.ItemDisplay)?.let { display ->
                            display.setItemStack(curedLeather)

                            display.location.world?.playSound(display.location, Sound.ITEM_TRIDENT_HIT, 1.0f, 1.5f)
                            display.location.world?.spawnParticle(
                                Particle.HAPPY_VILLAGER,
                                display.location,
                                10,
                                0.3,
                                0.3,
                                0.3,
                                0.0
                            )
                        }
                    }

                    // Save the updated data
                    WorkstationDataManager.updatePlacedItems(pos, updatedData.placedItems)
                    WorkstationDataManager.saveData()

                    Atom.instance.logger.info("Leather cured successfully at $pos to $curedLeatherId")
                } ?: Atom.instance.logger.warning("Failed to find cured leather item: $curedLeatherId")
            } else {
                Atom.instance.logger.info("Leather no longer present at $pos, cancelling stabilization")
            }

            stabilizingLeather.remove(pos)
            curingStartTimes.remove(pos)

            // Clear persistence
            val data = WorkstationDataManager.getWorkstationData(pos, "leather_bed")
            data.curingStartTime = null
            WorkstationDataManager.saveData()
        }

        stabilizingLeather[pos] = job
    }
}

// Extension functions for managing curing processes
fun LeatherBedBlockBehavior.Companion.accelerateCuring(pos: BlockPos): Boolean {
    val job = stabilizingLeather[pos]
    if (job == null) {
        Atom.instance.logger.info("No stabilization job found for $pos")
        return false
    }

    // Cancel the delayed job
    job.cancel()
    stabilizingLeather.remove(pos)
    curingStartTimes.remove(pos)

    // Complete immediately
    val workstationData = WorkstationDataManager.getWorkstationData(pos, "leather_bed")
    val oldLeatherItem = workstationData.placedItems.lastOrNull()

    Atom.instance.logger.info("Accelerating cure at $pos, item type: ${oldLeatherItem?.item?.type}")

    val oldItemId = oldLeatherItem?.let { CraftEngineItems.getCustomItemId(it.item) }
    val isOldProcessedLeather = oldItemId != null && oldItemId.value().startsWith("animal_leather_") && !oldItemId.value().startsWith("animal_leather_raw_")
    
    if (oldLeatherItem != null && isOldProcessedLeather) {
        // Create cured leather
        val animals = listOf(
            "cow", "pig", "sheep", "chicken", "rabbit", "horse", "donkey", "mule",
            "llama", "goat", "cat", "wolf", "fox", "panda", "polar_bear", "ocelot", "camel"
        )
        val randomAnimal = animals.random()
        val curedLeatherId = "atom:animal_leather_cured_$randomAnimal"

        CraftEngineItems.byId(Key.of(curedLeatherId))?.let { curedItem ->
            val curedLeather = curedItem.buildItemStack()

            // Update the placed item
            workstationData.placedItems.clear()
            val newPlacedItem = PlacedItem(
                item = curedLeather,
                position = oldLeatherItem.position,
                yaw = oldLeatherItem.yaw,
                displayUUID = oldLeatherItem.displayUUID
            )
            workstationData.placedItems.add(newPlacedItem)

            // Update the visual display
            newPlacedItem.displayUUID?.let { uuid ->
                (Bukkit.getEntity(uuid) as? org.bukkit.entity.ItemDisplay)?.let { display ->
                    display.setItemStack(curedLeather)

                    display.location.world?.playSound(display.location, Sound.ITEM_TRIDENT_HIT, 1.0f, 1.5f)
                    display.location.world?.spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        display.location,
                        10,
                        0.3,
                        0.3,
                        0.3,
                        0.0
                    )
                }
            }

            // Save the updated data
            WorkstationDataManager.updatePlacedItems(pos, workstationData.placedItems)
            WorkstationDataManager.saveData()
            return true
        }
    }
    return false
}

fun LeatherBedBlockBehavior.Companion.getCuringTimeRemaining(pos: BlockPos): Long? {
    if (!stabilizingLeather.containsKey(pos)) return null
    val startTime = curingStartTimes[pos] ?: return null
    val elapsed = System.currentTimeMillis() - startTime
    val remaining = CURING_TIME_MS - elapsed
    return if (remaining > 0) remaining else 0
}

fun LeatherBedBlockBehavior.Companion.setCuringTime(timeMs: Long) {
    CURING_TIME_MS = timeMs
}

fun LeatherBedBlockBehavior.Companion.resumeCuringProcesses() {
    Atom.instance.logger.info("Resuming leather curing processes...")

    // Get world reference safely
    val world = Bukkit.getWorld("world")
    if (world == null) {
        Atom.instance.logger.warning("World not loaded yet, cannot resume leather curing")
        return
    }

    WorkstationDataManager.getAllWorkstations().forEach { (_, data) ->
        if (data.type == "leather_bed" && data.curingStartTime != null) {
            val pos = data.position

            // Verify block is still a leather bed before resuming
            val block = world.getBlockAt(pos.x(), pos.y(), pos.z())
            val state = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.getCustomBlockState(block)
            if (state == null || !state.owner()
                    .matchesKey(net.momirealms.craftengine.core.util.Key.of("atom:leather_bed"))
            ) {
                Atom.instance.logger.info("Block at $pos is no longer a leather bed, skipping resume")
                data.curingStartTime = null
                WorkstationDataManager.saveData()
                return@forEach
            }

            val elapsedTime = System.currentTimeMillis() - data.curingStartTime!!
            val remainingTime = CURING_TIME_MS - elapsedTime

            if (remainingTime > 0) {
                Atom.instance.logger.info("Resuming curing at $pos with ${remainingTime / 1000}s remaining")
                curingStartTimes[pos] = data.curingStartTime!!

                val job = GlobalScope.launch {
                    delay(remainingTime)

                    val updatedData = WorkstationDataManager.getWorkstationData(pos, "leather_bed")
                    val oldLeatherItem = updatedData.placedItems.lastOrNull()

                    val oldItemId = oldLeatherItem?.let { CraftEngineItems.getCustomItemId(it.item) }
                    val isOldProcessedLeather = oldItemId != null && oldItemId.value().startsWith("animal_leather_") && !oldItemId.value().startsWith("animal_leather_raw_")
                    
                    if (oldLeatherItem != null && isOldProcessedLeather) {
                        completeCuring(pos, updatedData, oldLeatherItem)
                    }

                    stabilizingLeather.remove(pos)
                    curingStartTimes.remove(pos)
                    updatedData.curingStartTime = null
                    WorkstationDataManager.saveData()
                }

                stabilizingLeather[pos] = job
            } else {
                Atom.instance.logger.info("Completing overdue curing at $pos")
                val oldLeatherItem = data.placedItems.lastOrNull()
                val oldItemId = oldLeatherItem?.let { CraftEngineItems.getCustomItemId(it.item) }
                val isOldProcessedLeather = oldItemId != null && oldItemId.value().startsWith("animal_leather_") && !oldItemId.value().startsWith("animal_leather_raw_")
                
                if (oldLeatherItem != null && isOldProcessedLeather) {
                    completeCuring(pos, data, oldLeatherItem)
                }
                data.curingStartTime = null
                WorkstationDataManager.saveData()
            }
        }
    }
}

private fun LeatherBedBlockBehavior.Companion.completeCuring(
    pos: BlockPos,
    workstationData: WorkstationDataManager.WorkstationData,
    oldLeatherItem: PlacedItem
) {
    val animals = listOf(
        "cow", "pig", "sheep", "chicken", "rabbit", "horse", "donkey", "mule",
        "llama", "goat", "cat", "wolf", "fox", "panda", "polar_bear", "ocelot", "camel"
    )
    val randomAnimal = animals.random()
    val curedLeatherId = "atom:animal_leather_cured_$randomAnimal"

    CraftEngineItems.byId(Key.of(curedLeatherId))?.let { curedItem ->
        val curedLeather = curedItem.buildItemStack()

        // Update the placed item
        workstationData.placedItems.clear()
        val newPlacedItem = PlacedItem(
            item = curedLeather,
            position = oldLeatherItem.position,
            yaw = oldLeatherItem.yaw,
            displayUUID = oldLeatherItem.displayUUID
        )
        workstationData.placedItems.add(newPlacedItem)

        // Update the visual display
        newPlacedItem.displayUUID?.let { uuid ->
            (Bukkit.getEntity(uuid) as? org.bukkit.entity.ItemDisplay)?.let { display ->
                display.setItemStack(curedLeather)

                display.location.world?.playSound(display.location, Sound.ITEM_TRIDENT_HIT, 1.0f, 1.5f)
                display.location.world?.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    display.location,
                    10,
                    0.3,
                    0.3,
                    0.3,
                    0.0
                )
            }
        }

        // Save the updated data
        WorkstationDataManager.updatePlacedItems(pos, workstationData.placedItems)
        WorkstationDataManager.saveData()

        Atom.instance.logger.info("Leather cured successfully at $pos to $curedLeatherId")
    }
}