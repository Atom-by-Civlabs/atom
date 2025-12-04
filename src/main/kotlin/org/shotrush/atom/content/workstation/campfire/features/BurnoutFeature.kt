package org.shotrush.atom.content.workstation.campfire.features

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.shotrush.atom.content.workstation.campfire.CampfireData
import org.shotrush.atom.content.workstation.campfire.CampfireRegistry

class BurnoutFeature : CampfireRegistry.Listener {
    override fun onCampfireExtinguished(state: CampfireData.CampfireState, reason: String) {
        playEffects(state.location)
    }

    private fun playEffects(location: Location) {
        val center = location.clone().add(0.5, 0.5, 0.5)
        center.world?.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f)
        center.world?.spawnParticle(Particle.SMOKE, center, 30, 0.3, 0.3, 0.3, 0.02)
        center.world?.spawnParticle(Particle.ASH, center, 10, 0.2, 0.2, 0.2, 0.01)
    }
}