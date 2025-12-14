package org.shotrush.atom

import com.mojang.serialization.DataResult
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
import net.momirealms.craftengine.bukkit.api.CraftEngineItems
import net.momirealms.craftengine.bukkit.nms.FastNMS
import net.momirealms.craftengine.bukkit.plugin.reflection.minecraft.CoreReflections
import net.momirealms.craftengine.bukkit.plugin.reflection.minecraft.MRegistryOps
import net.momirealms.craftengine.core.item.CustomItem
import net.momirealms.craftengine.core.world.BlockPos
import net.momirealms.craftengine.core.world.CEWorld
import net.momirealms.craftengine.libraries.nbt.CompoundTag
import net.momirealms.craftengine.libraries.nbt.Tag
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import org.shotrush.atom.api.BlockRef
import org.shotrush.atom.api.SingleItemRef
import org.shotrush.atom.item.isItem
import org.shotrush.atom.util.Key
import org.shotrush.atom.util.asAtomKey
import kotlin.jvm.optionals.getOrElse
import kotlin.time.Duration
import net.momirealms.craftengine.core.util.Key as CEKey

fun ItemStack.isCustomItem() = CraftEngineItems.isCustomItem(this) && CraftEngineItems.getCustomItemId(this) != null
fun ItemStack.getNamespacedKey(): String = if (isCustomItem()) {
    CraftEngineItems.getCustomItemId(this)?.toString() ?: type.key.toString()
} else {
    type.key.toString()
}

fun ItemStack.getNamespacedPath(): String = if (isCustomItem()) {
    CraftEngineItems.getCustomItemId(this)?.value ?: type.key.value()
} else {
    type.key.value()
}

fun ItemStack.matches(regex: Regex) = getNamespacedKey().matches(regex)
fun ItemStack.matches(key: Key) = getNamespacedKey() == key.toString()
fun ItemStack.matches(key: CEKey) = getNamespacedKey() == key.toString()
fun ItemStack.matches(key: String) = getNamespacedKey() == key
fun ItemStack.matches(namespace: String, path: String) = getNamespacedKey() == "$namespace:$path"
fun ItemStack.matches(item: CustomItem<ItemStack>) = item.isItem(this)

fun ItemStack.asReference() = CraftEngineItems.getCustomItemId(this)?.let { SingleItemRef.keyed(it.asAtomKey()) }
    ?: SingleItemRef.MaterialRef(type)

fun Block.getNamespacedKey(): String = if (CraftEngineBlocks.isCustomBlock(this)) {
    CraftEngineBlocks.getCustomBlockState(this)?.owner()?.value()?.id()?.toString() ?: type.key.toString()
} else {
    type.key.toString()
}

fun Block.matches(key: Key) =
    CraftEngineBlocks.getCustomBlockState(this)?.owner()?.matchesKey(key.toCEKey()) ?: (type.key.toString() == key.toString())
fun Block.matches(key: CEKey) =
    CraftEngineBlocks.getCustomBlockState(this)?.owner()?.matchesKey(key) ?: (type.key.toString() == key.toString())
fun Block.matches(key: String) = matches(CEKey.of(key))
fun Block.matches(namespace: String, path: String) = matches(CEKey.of(namespace, path))

fun Block.asReference() =
    CraftEngineBlocks.getCustomBlockState(this)?.owner()?.value()?.id()?.let { BlockRef.custom(it.asAtomKey()) }
        ?: BlockRef.MaterialRef(this.type)

fun CompoundTag.getItemStack(key: String): ItemStack {
    if (getTagType(key).toInt() != 10) return ItemStack.empty()
    return getCompound(key)?.let { tag ->
        CoreReflections.`instance$ItemStack$CODEC`.parse(MRegistryOps.SPARROW_NBT, tag).resultOrPartial { err ->
            Atom.instance.logger.severe("Tried to load invalid item: '$tag'. $err")
        }.map { result -> FastNMS.INSTANCE.`method$CraftItemStack$asCraftMirror`(result) }
            .getOrElse { ItemStack.empty() }
    } ?: ItemStack.empty()
}

fun CompoundTag.putItemStack(key: String, item: ItemStack) {
    if (item.isEmpty) return
    CoreReflections.`instance$ItemStack$CODEC`.encodeStart(
        MRegistryOps.SPARROW_NBT,
        FastNMS.INSTANCE.`field$CraftItemStack$handle`(item)
    ).ifSuccess { success: Tag? ->
        val itemTag = success as CompoundTag
        this.put(key, itemTag)
    }.ifError { error: DataResult.Error<Tag> ->
        Atom.instance.logger.severe("Error while saving storage item: $error")
    }
}

val Duration.inWholeTicks: Long
    get() = inWholeSeconds * 20


fun format(string: String) = MiniMessage.miniMessage().deserialize(string)
fun format(vararg strings: String) = strings.map { format(it) }

fun Audience.sendMiniMessage(message: String) = sendMessage(format(message))
fun Audience.sendMiniMessages(vararg message: String) = message.forEach { sendMiniMessage(it) }

fun BlockPos.toBukkitLocation(ceWorld: CEWorld?): Location {
    return Location(ceWorld?.world?.platformWorld() as? World, x().toDouble(), y().toDouble(), z().toDouble())
}

fun Location.toVec3i() = BlockPos(x.toInt(), y.toInt(), z.toInt())