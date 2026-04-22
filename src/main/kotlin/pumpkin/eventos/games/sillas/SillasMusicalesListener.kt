package pumpkin.eventos.games.sillas

import dev.geco.gsit.api.GSitAPI
import dev.geco.gsit.api.event.EntityStopSitEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.vehicle.VehicleExitEvent
import pumpkin.eventos.PumpkinEventos

class SillasMusicalesListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): SillasMusicales? = plugin.eventManager.currentGame as? SillasMusicales

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPvp(e: EntityDamageByEntityEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        // En sillas se permiten golpes de empuje (0.001)
        e.damage = 0.001
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        if (e is EntityDamageByEntityEvent) return
        if (e.cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
            e.cause == EntityDamageEvent.DamageCause.FALLING_BLOCK) {
            e.isCancelled = true; return
        }
        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.damage = 0.001
        }
    }

    @EventHandler
    fun onSneak(e: PlayerToggleSneakEvent) {
        val player = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        if (game.jugadoresSentados.contains(player)) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGSitStop(e: EntityStopSitEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.jugadoresSentados.contains(player)) return
        val block = e.seat.block
        plugin.server.regionScheduler.runDelayed(plugin, block.location, { _ ->
            GSitAPI.createSeat(block, player)
            player.sendMessage(plugin.messageManager.parse(
                plugin.languageManager.get("sillas.cannot_stand")
            ))
        }, 1L)
    }

    @EventHandler
    fun onDismount(e: VehicleExitEvent) {
        val player = e.exited as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning) return
        if (game.jugadoresSentados.contains(player)) {
            e.isCancelled = true
        }
    }
}
