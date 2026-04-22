package pumpkin.eventos.games.sumo

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos

class SumoListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): Sumo? = plugin.eventManager.currentGame as? Sumo

    @EventHandler
    fun onDamage(e: EntityDamageEvent) {
        val p = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(p)) return
        // Bloquear todo daño excepto el que viene de otros luchadores
        if (e !is EntityDamageByEntityEvent) { e.isCancelled = true; return }
    }

    @EventHandler
    fun onPvpDamage(e: EntityDamageByEntityEvent) {
        val victim = e.entity as? Player ?: return
        val attacker = e.damager as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning) return
        if (!game.isLuchador(victim) || !game.isLuchador(attacker)) {
            e.isCancelled = true; return
        }
        // Solo luchadores activos pueden hacerse daño
    }
}
