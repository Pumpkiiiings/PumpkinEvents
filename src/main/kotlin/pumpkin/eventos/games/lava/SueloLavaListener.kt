package pumpkin.eventos.games.lava

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerDropItemEvent
import pumpkin.eventos.PumpkinEventos

class SueloLavaListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): SueloLava? = plugin.eventManager.currentGame as? SueloLava

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPvp(e: EntityDamageByEntityEvent) {
        val attacker = e.damager as? Player ?: return
        val victim = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning) return
        if (!game.players.contains(attacker) || !game.players.contains(victim)) {
            e.isCancelled = true; return
        }
        // El PvP depende de si fue habilitado por la votación
        if (!game.pvpEnabled) {
            e.isCancelled = true
        } else {
            e.damage = 0.001 // Solo empuje
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        if (e is EntityDamageByEntityEvent) return
        if (e.cause == EntityDamageEvent.DamageCause.LAVA ||
            e.cause == EntityDamageEvent.DamageCause.FIRE ||
            e.cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
            // Daño de lava: eliminar directamente
            e.isCancelled = true
            plugin.server.globalRegionScheduler.run(plugin) { _ -> game.eliminate(player) }
            return
        }
        if (e.cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
            e.cause == EntityDamageEvent.DamageCause.FALLING_BLOCK) {
            e.isCancelled = true; return
        }
        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        e.isCancelled = true
    }
}
