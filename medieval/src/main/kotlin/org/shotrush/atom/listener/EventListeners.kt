package org.shotrush.atom.listener

import org.civlabs.atom.core.listener.register
import org.shotrush.atom.Atom
import org.shotrush.atom.systems.blockbreak.BlockBreakSystem
import org.shotrush.atom.systems.reinforce.ReinforcementSystem

object EventListeners {
    fun register(atom: Atom) {
        MoldListener.register(atom)
        PlayerDataTrackingListener.register(atom)
        PlayerMiningListener.register(atom)
        RecipeUnlockHandler.register(atom)
        CombatListener.register(atom)
//        PlayerChatListener.register(this)
        ReinforcementSystem.register()
        BlockBreakSystem.register()
        PlayerExhaustionListener.register()
    }
}