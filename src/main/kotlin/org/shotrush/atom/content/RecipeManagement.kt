package org.shotrush.atom.content

import org.bukkit.NamespacedKey
import org.bukkit.event.Listener
import org.shotrush.atom.Atom

object RecipeManagement : Listener {
    fun handle(atom: Atom) {
        val server = atom.server
        val toolMaterials = listOf(
            "wood",
            "stone",
            "iron",
            "gold",
            "diamond",
            "netherite"
        )
        val tools = listOf(
            "shovel",
            "pickaxe",
            "axe",
            "hoe",
            "sword"
        )
        tools.forEach { tool ->
            toolMaterials.forEach { material ->
                server.removeRecipe(NamespacedKey("minecraft", "${material}_$tool"))
            }
        }
    }
}