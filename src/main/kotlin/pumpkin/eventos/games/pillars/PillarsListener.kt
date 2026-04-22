package pumpkin.eventos.games.pillars

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import pumpkin.eventos.PumpkinEventos

class PillarsListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): PillarsOfFortune? = plugin.eventManager.currentGame as? PillarsOfFortune

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(e: BlockPlaceEvent) {
        val game = game() ?: return
        if (e.player.hasPermission("pumpkin.admin")) return
        if (game.isPreparation) { e.isCancelled = true; return }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(e: BlockBreakEvent) {
        val game = game() ?: return
        if (e.player.hasPermission("pumpkin.admin")) return
        if (game.isPreparation) { e.isCancelled = true; return }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPvp(e: EntityDamageByEntityEvent) {
        val attacker = e.damager as? Player ?: return
        val victim = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning) return
        if (game.isPreparation) { e.isCancelled = true; return }
        if (!game.players.contains(attacker) || !game.players.contains(victim)) {
            e.isCancelled = true
        }
        // En pillars el PvP es REAL — no se modifica el daño
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        if (game.isPreparation) { e.isCancelled = true; return }
        // En pillars el daño es real — no se cancela ni se modifica
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        if (game.isPreparation) { e.isCancelled = true; return }
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        // En pillars se puede hacer drop
    }
}
