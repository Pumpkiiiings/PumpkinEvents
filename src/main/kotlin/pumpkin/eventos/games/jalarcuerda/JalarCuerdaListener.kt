package pumpkin.eventos.games.jalarcuerda

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import pumpkin.eventos.PumpkinEventos

class JalarCuerdaListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): JalarCuerda? = plugin.eventManager.currentGame as? JalarCuerda

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPvp(e: EntityDamageByEntityEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        // En Jalar Cuerda no existe PvP directo
        e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        // Cancelar TODO el daño — las eliminaciones son por posición en la cuerda
        e.isCancelled = true
    }
}
