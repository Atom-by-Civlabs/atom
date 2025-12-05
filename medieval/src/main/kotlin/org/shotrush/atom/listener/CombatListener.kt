package org.shotrush.atom.listener

import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.registerSuspendingEvents
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.shotrush.atom.Atom
import org.shotrush.atom.core.util.ActionBarManager

object CombatListener : Listener {
    fun register(atom: Atom) {
        val eventDispatcher = mapOf(
            eventDef<EntityDamageByEntityEvent> { atom.entityDispatcher(it.entity) }
        )
        atom.server.pluginManager.registerSuspendingEvents(this, atom, eventDispatcher)
    }

    private fun isTooHungry(event: EntityDamageByEntityEvent, player: Player): Boolean {
        if (player.foodLevel <= 0) {
            event.isCancelled = true
            ActionBarManager.send(player, "You're too hungry to fight!")
            return true
        }
        return false
    }

    @EventHandler
    fun onPlayerAttack(event: EntityDamageByEntityEvent) {
        val entity = event.damager

        if (entity is Player) {
            if (isTooHungry(event, entity)) return

            PlayerExhaustionListener.updateCurrentAction(PlayerAction.COMBAT, entity)
        }

        if (entity is Projectile) {
            val shooter = entity.shooter
            if (shooter is Player) {
                if (isTooHungry(event, shooter)) return

                PlayerExhaustionListener.updateCurrentAction(PlayerAction.COMBAT, shooter)
            }
        }
    }
}