package pumpkin.eventos.games.tntgames.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.tntgames.TntRun

class TntRunListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): TntRun? = plugin.eventManager.currentGame as? TntRun

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return

        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.isCancelled = true
        }
    }
}
