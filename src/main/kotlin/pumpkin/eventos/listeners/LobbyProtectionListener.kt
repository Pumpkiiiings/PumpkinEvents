package pumpkin.eventos.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import pumpkin.eventos.PumpkinEventos

class LobbyProtectionListener(private val plugin: PumpkinEventos) : Listener {

    /**
     * Verifica si un jugador está en el mundo del Lobby Principal.
     */
    private fun isInLobby(player: Player): Boolean {
        val lobbyLoc = plugin.arenaManager.mainLobby ?: return false
        return player.world == lobbyLoc.world
    }

    // --- CANCELAR EL PVP POR COMPLETO ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLobbyPvP(e: EntityDamageByEntityEvent) {
        val damager = e.damager as? Player ?: return

        if (isInLobby(damager)) {
            // Si no es admin, no puede pegar
            if (!damager.hasPermission("pumpkin.admin")) {
                e.isCancelled = true
            }
        }
    }

    // --- REGENERACIÓN INFINITA (Cura instantánea de cualquier daño) ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLobbyDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return

        if (isInLobby(player)) {
            // Cancelamos el daño (caída, fuego, etc)
            e.isCancelled = true

            // Forzamos salud al máximo por si acaso
            player.health = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
            player.fireTicks = 0 // Apagar si se quema
        }
    }

    // --- HAMBRE INFINITA (Saturación) ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLobbyHunger(e: FoodLevelChangeEvent) {
        val player = e.entity as? Player ?: return

        if (isInLobby(player)) {
            e.isCancelled = true
            player.foodLevel = 20
        }
    }
}
