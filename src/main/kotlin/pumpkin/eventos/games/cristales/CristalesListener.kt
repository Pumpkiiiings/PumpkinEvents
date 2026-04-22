package pumpkin.eventos.games.cristales

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import pumpkin.eventos.PumpkinEventos

class CristalesListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): Cristales? = plugin.eventManager.currentGame as? Cristales

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPvp(e: EntityDamageByEntityEvent) {
        val victim = e.entity as? Player ?: return
        val attacker = e.damager as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning) return
        // En Cristales el PvP es de empuje (0.001 de daño)
        if (game.players.contains(victim) && game.players.contains(attacker)) {
            e.damage = 0.001
        } else {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        if (e is EntityDamageByEntityEvent) return // Manejado arriba
        if (e.cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
            e.cause == EntityDamageEvent.DamageCause.FALLING_BLOCK) {
            e.isCancelled = true; return
        }
        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.damage = 0.001
        }
    }
}
