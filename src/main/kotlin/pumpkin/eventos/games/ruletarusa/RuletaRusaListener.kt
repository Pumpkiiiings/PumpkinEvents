package pumpkin.eventos.games.ruletarusa

import dev.geco.gsit.api.GSitAPI
import dev.geco.gsit.api.event.EntityStopSitEvent
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.vehicle.VehicleExitEvent
import org.bukkit.inventory.EquipmentSlot
import pumpkin.eventos.PumpkinEventos

class RuletaRusaListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): RuletaRusa? = plugin.eventManager.currentGame as? RuletaRusa

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPvp(e: EntityDamageByEntityEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        e.isCancelled = true // Sin PvP en Ruleta Rusa
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        if (e is EntityDamageByEntityEvent) return
        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val player = e.player
        val game = game() ?: return
        if (!game.isRunning || e.hand != EquipmentSlot.HAND) return
        if (player != game.ultimaVictima) return

        // Clic izquierdo — recarga manual
        if ((e.action == org.bukkit.event.block.Action.LEFT_CLICK_AIR ||
             e.action == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) &&
            e.item?.type == Material.GUNPOWDER) {
            game.recargarManual(player)
            return
        }

        // Clic derecho con revólver — dispara
        if ((e.action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
             e.action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) &&
            e.item?.type == Material.IRON_HORSE_ARMOR) {
            e.isCancelled = true
            game.procesarEleccion(player, true)
        }
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        val player = e.player
        val game = game() ?: return
        if (!game.isRunning) return
        e.isCancelled = true
        if (e.itemDrop.itemStack.type == Material.IRON_HORSE_ARMOR) {
            game.procesarEleccion(player, false)
        }
    }

    @EventHandler
    fun onSneak(e: PlayerToggleSneakEvent) {
        val player = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        if (game.camarasTemporales.containsKey(player) ||
            (game.forzarAsientos && game.jugadoresSentados.containsKey(player))) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGSitStop(e: EntityStopSitEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.forzarAsientos) return
        if (game.jugadoresSentados.containsKey(player)) {
            val loc = game.jugadoresSentados[player] ?: return
            plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                GSitAPI.createSeat(loc.block, player)
            }, 1L)
        }
    }

    @EventHandler
    fun onDismount(e: VehicleExitEvent) {
        val player = e.exited as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning) return
        if (game.camarasTemporales.containsKey(player) ||
            (game.forzarAsientos && game.jugadoresSentados.containsKey(player))) {
            e.isCancelled = true
        }
    }
}
