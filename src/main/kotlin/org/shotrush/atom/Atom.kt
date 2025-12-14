package org.shotrush.atom

import co.aikar.commands.PaperCommandManager
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIPaperConfig
import org.bukkit.Bukkit
import org.bukkit.Material
import org.shotrush.atom.commands.Commands
import org.shotrush.atom.compat.AtomPlaceholder
import org.shotrush.atom.content.mobs.ai.debug.MobAIDebugCommand
import org.shotrush.atom.content.mobs.ai.debug.VisualDebugger
import org.shotrush.atom.content.mobs.commands.HerdCommand
import org.shotrush.atom.content.mobs.herd.HerdManager
import org.shotrush.atom.content.systems.ItemHeatSystem
import org.shotrush.atom.content.systems.PlayerTemperatureSystem
import org.shotrush.atom.content.systems.ThirstSystem
import org.shotrush.atom.content.workstation.Workstations
import org.shotrush.atom.core.age.AgeManager
import org.shotrush.atom.core.api.AtomAPI
import org.shotrush.atom.core.api.player.PlayerDataAPI
import org.shotrush.atom.core.blocks.CustomBlockManager
import org.shotrush.atom.core.items.CustomItemRegistry
import org.shotrush.atom.core.storage.DataStorage
import org.shotrush.atom.listener.EventListeners
import org.shotrush.atom.systems.reinforce.ReinforcementSystem
import org.shotrush.atom.systems.room.RoomRegistry

class Atom : SuspendingJavaPlugin() {
    var blockManager: CustomBlockManager? = null
        private set

    var itemRegistry: CustomItemRegistry? = null
        private set

    var dataStorage: DataStorage? = null
        private set

    var ageManager: AgeManager? = null
        private set

    override fun onLoad() {
        super.onLoad()
        CommandAPI.onLoad(CommandAPIPaperConfig(this))
    }

    override fun onEnable() {
        super.onEnable()
        CommandAPI.onEnable()
        instance = this

        RoomRegistry.readAllFromDisk()

        AtomPlaceholder.register()


        AtomAPI.initialize(this)


        dataStorage = AtomAPI.dataStorage
        ageManager = AtomAPI.ageManager
        itemRegistry = AtomAPI.itemRegistry
        blockManager = AtomAPI.blockManager


        AtomAPI.registerAges()
        AtomAPI.registerItems()
        AtomAPI.registerBlocks()
        AtomAPI.registerSystems()

        Workstations.init()
        org.shotrush.atom.content.carcass.CarcassConfigs.load()
        org.shotrush.atom.content.carcass.CarcassBlock.init()
        org.shotrush.atom.content.carcass.CarcassGui.init()

        EventListeners.register(this)

        setupCommands()

        ReinforcementSystem.start()
        logger.info("Atom plugin has been enabled!")
    }

    private fun setupCommands() {
        val commandManager = PaperCommandManager(this)

        AtomAPI.registerCommands(commandManager)

        val herdManager = AtomAPI.Systems.getService("herd_manager", HerdManager::class.java)
        if (herdManager != null) {
            commandManager.registerCommand(HerdCommand(herdManager))

            val visualDebugger = VisualDebugger(this)
            commandManager.registerCommand(MobAIDebugCommand(visualDebugger, herdManager))
        }

        Commands.register(commandManager)
    }

    override fun onDisable() {
        ReinforcementSystem.stop()
        CommandAPI.onDisable()
        RoomRegistry.saveAllToDisk()
        saveAllPlayerData()

        AtomAPI.shutdown()

        logger.info("Atom plugin has been disabled!")
    }

    private fun saveAllPlayerData() {
        logger.info("Saving all player data...")

        for (player in Bukkit.getOnlinePlayers()) {
            if (ThirstSystem.instance != null) {
                val thirst = ThirstSystem.instance.getThirst(player)
                PlayerDataAPI.setInt(player, "thirst.level", thirst)
            }

            if (ItemHeatSystem.instance != null) {
                for (slot in 0..8) {
                    val item = player.inventory.getItem(slot)
                    if (item != null && item.type != Material.AIR) {
                        ItemHeatSystem.instance!!.saveHeatForSlot(player, slot, item)
                    }
                }
            }

            val temp = PlayerTemperatureSystem.instance.getPlayerTemperature(player)
            PlayerDataAPI.setDouble(player, "temperature.body", temp)
        }

        logger.info("Player data saved for " + Bukkit.getOnlinePlayers().size + " players")
    }

    companion object {
        @JvmStatic
        lateinit var instance: Atom
    }
}
