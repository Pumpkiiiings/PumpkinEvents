package pumpkin.eventos.games.simondice

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import pumpkin.eventos.PumpkinEventos

class SimonDiceGameListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): SimonDice? = plugin.eventManager.currentGame as? SimonDice

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPvp(e: EntityDamageByEntityEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        e.isCancelled = true // Sin PvP en Simon Dice
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
        val game = game() ?: return
        if (!game.isRunning || e.hand != EquipmentSlot.HAND) return
        // Permitir la Nether Star del streamer (Simon Manual)
        val item = e.item ?: return
        if (game.mode == SimonMode.MANUAL && item.type == Material.NETHER_STAR) return
        // Bloquear cualquier otro interact que no sea del flujo del juego
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        e.isCancelled = true
    }
}
