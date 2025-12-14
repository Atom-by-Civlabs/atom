package org.shotrush.atom.systems.room

interface RoomScan {
    suspend fun scan(): Boolean

    fun toRoom(): Room?
}