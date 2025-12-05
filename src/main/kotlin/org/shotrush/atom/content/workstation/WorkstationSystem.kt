package org.shotrush.atom.content.workstation

import org.bukkit.plugin.Plugin
import org.shotrush.atom.core.api.annotation.RegisterSystem


@RegisterSystem(
    id = "workstation_system",
    priority = 1,
    toggleable = false,
    description = "Initializes CraftEngine workstation block behaviors"
)
class WorkstationSystem(private val plugin: Plugin) {

    init {
        Workstations.init()
        plugin.logger.info("Workstation System initialized")
    }
}
