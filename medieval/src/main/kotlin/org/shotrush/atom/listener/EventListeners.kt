package org.shotrush.atom.listener

import org.civlabs.atom.core.listener.register
import org.civlabs.atom.core.system.room.RoomSystem
import org.shotrush.atom.Atom
import org.shotrush.atom.systems.blockbreak.BlockBreakSystem
import org.shotrush.atom.systems.reinforce.ReinforcementSystem

object EventListeners {
    fun register(atom: Atom) {
        MoldListener.register(atom)
        PlayerDataTrackingListener.register(atom)
        PlayerMiningListener.register(atom)
        RecipeUnlockHandler.register(atom)
//        PlayerChatListener.register(this)

        DeathPenaltyListener(atom).register()
        RoomSystem.register()
        ReinforcementSystem.register()
        BlockBreakSystem.register()
    }
}