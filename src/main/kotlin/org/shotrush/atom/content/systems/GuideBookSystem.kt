package org.shotrush.atom.content.systems

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.plugin.Plugin
import org.shotrush.atom.core.api.annotation.RegisterSystem

@RegisterSystem(
    id = "guide_book_system",
    priority = 10,
    toggleable = true,
    description = "Gives players an essential guide book on join/respawn"
)
class GuideBookSystem(private val plugin: Plugin) : Listener {

    companion object {
        private const val GUIDE_TITLE = "Atom Survival Guide"
        private const val GUIDE_AUTHOR = "Atom Dev Team"

        private val mm: MiniMessage = MiniMessage.miniMessage()

        private fun mmc(text: String): Component = mm.deserialize(text)

        private fun nl(n: Int = 1): String = "<newline>".repeat(n)

        private fun createGuideBook(): ItemStack {
            val book = ItemStack(Material.WRITTEN_BOOK)
            val meta = book.itemMeta as BookMeta

            // Title and author via MiniMessage to be consistent
            meta.title(mmc("<gold>$GUIDE_TITLE</gold>"))
            meta.author(mmc("<yellow>$GUIDE_AUTHOR</yellow>"))
            meta.generation = BookMeta.Generation.ORIGINAL

            val pWelcome = mmc(
                buildString {
                    append("<dark_red><bold>Welcome to Atom</bold></dark_red>")
                    append(nl(2))
                    append("<black>This world operates differently.</black>")
                    append(nl())
                    append(
                        "<black>Survival requires understanding temperature, crafting, and metallurgy.</black>"
                    )
                    append(nl(2))
                    append("<dark_gray>Read this guide carefully to survive.</dark_gray>")
                }
            )

            val pPhysical = mmc(
                buildString {
                    append("<dark_red><bold>Physical Limits</bold></dark_red>")
                    append(nl(2))
                    append("<black>Your body is frail.</black>")
                    append(nl())
                    append("<black>- <blue>Movement</blue> is 20% slower.</black>")
                    append(nl())
                    append("<black>- <blue>Mining</blue> is 75% harder.</black>")
                    append(nl())
                    append("<black>- <blue>Combat</blue> is exhausting.</black>")
                    append(nl(2))
                    append("<dark_gray>Plan every action.</dark_gray>")
                }
            )

            val pTemp = mmc(
                buildString {
                    append("<blue><bold>Temperature</bold></blue>")
                    append(nl(2))
                    append("<black>- <red>Sprinting</red> warms you.</black>")
                    append(nl())
                    append("<black>- <blue>Water/Rain</blue> cools you down rapidly.</black>")
                    append(nl())
                    append("<black>- <gold>Campfires</gold> dry and warm you.</black>")
                    append(nl(2))
                    append("<dark_red>Beware of hypothermia and heatstroke.</dark_red>")
                }
            )

            val pThirst = mmc(
                buildString {
                    append("<aqua><bold>Thirst & Water</bold></aqua>")
                    append(nl(2))
                    append("<black>Drinking </black><dark_green>Raw Water</dark_green><black> causes sickness (Hunger).</black>")
                    append(nl(2))
                    append("<gold>Purification:</gold>")
                    append(nl())
                    append("<black>Boil a </black><white>Water Bottle</white><black> on a </black><gold>Campfire</gold><black> until hot.</black>")
                    append(nl())
                    append("<black>It becomes </black><aqua>Purified Water</aqua><black> (+Regen).</black>")
                }
            )

            val pTools = mmc(
                buildString {
                    append("<dark_green><bold>Early Tools</bold></dark_green>")
                    append(nl(2))
                    append("<black>1. Find </black><gray>Pebbles</gray><black> on the ground.</black>")
                    append(nl())
                    append("<black>2. Right-click a Pebble on </black><dark_gray>Stone/Rock</dark_gray><black> blocks repeatedly.</black>")
                    append(nl())
                    append("<black>3. Use </black><gray>Sharpened Rocks</gray><black> to craft basic tools.</black>")
                }
            )

            val pKnapping = mmc(
                buildString {
                    append("<gold><bold>Knapping</bold></gold>")
                    append(nl(2))
                    append("<black>Use materials on the </black><dark_aqua>Knapping Station</dark_aqua><black>.</black>")
                    append(nl(2))
                    append("<dark_green>Ingredients:</dark_green>")
                    append(nl())
                    append("<black>1. </black><gray>Clay Ball</gray><black>: Add clay to match pattern (Molds).</black>")
                    append(nl())
                    append("<black>2. </black><gray>Pebble</gray><black>: Remove stone to match pattern (Tools).</black>")
                    append(nl())
                    append("<black>3. </black><gold>Honeycomb</gold><black>: Wax Molds.</black>")
                    append(nl(2))
                    append("<black>Check the </black><green>Recipe Book</green><black> in the station for patterns.</black>")
                }
            )

            val pRoomsIntro = mmc(
                buildString {
                    append("<gold><bold>Rooms</bold></gold>")
                    append(nl(2))
                    append("<black>Rooms are areas where air can move from block to block.</black>")
                    append(nl(2))
                    append("<black>If two blocks touch and their sides are open, air can pass, and they count as the same room.</black>")
                }
            )

            val pRoomRules = mmc(
                buildString {
                    append("<gold><bold>Rooms: </bold></gold><dark_gray>Rules (Simple)</dark_gray>")
                    append(nl(2))
                    append("<black>• <dark_aqua>Doors</dark_aqua> & <dark_aqua>Trapdoors</dark_aqua> seal rooms completely, </black><gray>open or closed</gray><black>.</black>")
                    append(nl())
                    append("<black>• </black><dark_aqua>Full blocks</dark_aqua><black> (like <dark_aqua>stone</dark_aqua>) block air and split rooms.</black>")
                    append(nl())
                    append("<black>• <dark_aqua>Non-solid blocks</dark_aqua> (like <dark_aqua>slabs</dark_aqua>) can have open sides that let air through (depending on their shape).</black>")
                }
            )

            val pRoomTips = mmc(
                buildString {
                    append("<gold><bold>Rooms: </bold></gold><dark_gray>Tips</dark_gray>")
                    append(nl(2))
                    append("<black>• Want a sealed room?</black> <gray>Use doors/trapdoors (they always seal) or fill openings with full blocks.</gray>")
                    append(nl())
                    append("<black>• Want rooms to connect?</black> <gray>Leave clear gaps or shape slabs/stairs so their open sides face the opening.</gray>")
                }
            )

            val pFiring = mmc(
                buildString {
                    append("<red><bold>Firing Molds</bold></red>")
                    append(nl(2))
                    append("<black>Unfired Clay Molds are useless.</black>")
                    append(nl(2))
                    append("<black>Place the </black><gray>Unfired Mold</gray><black> directly onto a </black><gold>LIT Campfire</gold><black>.</black>")
                    append(nl())
                    append("<dark_gray>Wait 60s for it to fire.</dark_gray>")
                }
            )

            val pCasting = mmc(
                buildString {
                    append("<dark_aqua><bold>Casting</bold></dark_aqua>")
                    append(nl(2))
                    append("<black>1. Place a </black><gray>Clay Cauldron</gray><black> directly ABOVE a Lit Campfire.</black>")
                    append(nl())
                    append("<black>2. Add </black><gold>Raw Ore</gold><black> (Copper/Iron).</black>")
                    append(nl())
                    append("<black>3. Wait for it to melt.</black>")
                }
            )

            val pCasting2 = mmc(
                buildString {
                    append("<dark_aqua><bold>Casting Cont.</bold></dark_aqua>")
                    append(nl(2))
                    append("<black>4. Right-click the cauldron with an </black><red>Empty Fired Mold</red><black> to fill it.</black>")
                    append(nl())
                    append("<black>5. </black><gold>Throw the filled mold</gold><black> on the ground.</black>")
                    append(nl())
                    append("<black>6. Wait 30 seconds for it to cool and break open.</black>")
                }
            )

            val pLeather = mmc(
                buildString {
                    append("<gold><bold>Leather Working</bold></gold>")
                    append(nl(2))
                    append("<black>1. Craft a </black><yellow>Leather Bed</yellow><black>.</black>")
                    append(nl())
                    append("<black>2. Place </black><gold>Leather/Hide</gold><black> on it.</black>")
                    append(nl())
                    append("<black>3. Use a </black><gray>Sharpened Rock</gray><black> or </black><gray>Knife</gray><black> to scrape and process it.</black>")
                }
            )

            val pDomestication = mmc(
                buildString {
                    append("<green><bold>Domestication</bold></green>")
                    append(nl(2))
                    append("<black>Animals are wild.</black>")
                    append(nl())
                    append("<black>Breeding animals increases their </black><green>Domestication Level</green><black>.</black>")
                    append(nl(2))
                    append("<gold>Level 5+</gold><black> animals are fully domesticated and will follow herd commands.</black>")
                }
            )

            val pMechanical = mmc(
                buildString {
                    append("<gray><bold>Mechanics</bold></gray>")
                    append(nl(2))
                    append("<black>Use </black><gray>Cogs</gray><black> to transmit power.</black>")
                    append(nl(2))
                    append("<black>To create a </black><red>Power Source</red><black>:</black>")
                    append(nl())
                    append("<black>Right-click a Cog with a </black><blue>Wrench</blue><black>.</black>")
                }
            )

            meta.addPages(pWelcome, pPhysical, pTemp, pThirst, pTools, pKnapping, pRoomsIntro, pRoomRules, pRoomTips, pFiring, pCasting, pCasting2, pLeather, pDomestication, pMechanical)
            book.itemMeta = meta
            return book
        }
    }

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        ensureGuideBook(event.player)
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        ensureGuideBook(event.player)
    }

    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        if (isGuideBook(item)) {
            event.isCancelled = true
            event.player.sendMessage(mmc("<red>You cannot drop the guide book.</red>"))
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val item = event.currentItem ?: return
        if (isGuideBook(item)) {
            if (event.clickedInventory != event.whoClicked.inventory) {
                if (event.clickedInventory?.type != InventoryType.PLAYER) {
                    if (
                        event.clickedInventory != null &&
                        event.clickedInventory!!.type != InventoryType.PLAYER &&
                        event.clickedInventory!!.type != InventoryType.CRAFTING
                    ) {
                        event.isCancelled = true
                    }
                }
            }
            if (event.isShiftClick && event.view.topInventory.type != InventoryType.CRAFTING) {
                event.isCancelled = true
            }
        }
    }

    private fun ensureGuideBook(player: Player) {
        val inventory = player.inventory
        for (item in inventory.contents) {
            if (item != null && isGuideBook(item)) return
        }
        inventory.addItem(createGuideBook())
        player.sendMessage(mmc("<green>You have received the Atom Survival Guide.</green>"))
    }

    private fun isGuideBook(item: ItemStack): Boolean {
        if (item.type != Material.WRITTEN_BOOK) return false
        val meta = item.itemMeta as? BookMeta ?: return false
        return meta.title() == mmc("<gold>$GUIDE_TITLE</gold>") &&
                meta.author() == mmc("<yellow>$GUIDE_AUTHOR</yellow>")
    }
}