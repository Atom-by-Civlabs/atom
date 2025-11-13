package org.shotrush.atom

import co.aikar.commands.PaperCommandManager
import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIPaperConfig
import net.minecraft.world.item.ToolMaterial
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.shotrush.atom.commands.LivingCommands
import org.shotrush.atom.commands.MoldCommand
import org.shotrush.atom.content.RecipeManagement
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
import org.shotrush.atom.core.workstations.WorkstationManager
import org.shotrush.atom.listener.PlayerDataTrackingListener
import org.shotrush.atom.listener.TestListener

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

        TestListener.register(this)
        PlayerDataTrackingListener.register(this)
        RecipeManagement.handle(this)
        setupCommands()
        logger.info("Atom plugin has been enabled!")
    }

    private fun setupCommands() {
        val commandManager = PaperCommandManager(this)


        AtomAPI.registerCommands(commandManager)


        commandManager.registerCommand(org.shotrush.atom.content.workstation.commands.WorkstationCommands())

        val herdManager = AtomAPI.Systems.getService("herd_manager", HerdManager::class.java)
        if (herdManager != null) {
            commandManager.registerCommand(HerdCommand(herdManager))

            val visualDebugger = VisualDebugger(this)
            commandManager.registerCommand(MobAIDebugCommand(visualDebugger, herdManager))
        }

        MoldCommand.register()
        LivingCommands.register()
    }

    override fun onDisable() {
        CommandAPI.onDisable()
        saveAllPlayerData()


        if (WorkstationManager.instance != null) {
            WorkstationManager.instance.saveWorkstations()
        }


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
                        ItemHeatSystem.instance.saveHeatForSlot(player, slot, item)
                    }
                }
            }

            if (PlayerTemperatureSystem.instance != null) {
                val temp = PlayerTemperatureSystem.instance.getPlayerTemperature(player)
                PlayerDataAPI.setDouble(player, "temperature.body", temp)
            }
        }

        logger.info("Player data saved for " + Bukkit.getOnlinePlayers().size + " players")
    }

    companion object {
        @JvmStatic
        lateinit var instance: Atom
    }
}
