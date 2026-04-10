package pumpkin.eventos.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent
import pumpkin.eventos.utils.WorldUtils

class WorldListener : Listener {

    @EventHandler
    fun onWorldLoad(e: WorldLoadEvent) {
        // En cuanto cualquier mundo se cargue, le inyectamos el Insta Respawn
        WorldUtils.setupWorldRules(e.world)
    }
}
