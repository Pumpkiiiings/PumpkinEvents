package pumpkin.eventos.games.corona

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.corona.RobaLaCorona

class RobaLaCoronaListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): RobaLaCorona? = plugin.eventManager.currentGame as? RobaLaCorona

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPunch(e: EntityDamageByEntityEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        // RobaLaCorona maneja su lógica de golpes internamente vía alGolpear()
        game.alGolpear(e)
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        game.alMoverInventario(e)
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        game.alTirarCorona(e)
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
}
