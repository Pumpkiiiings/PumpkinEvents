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

    private fun isInLobby(player: Player): Boolean {
        // Si el juego está activo, el Lobby no cuenta
        if (plugin.eventManager.currentGame != null && plugin.eventManager.currentGame!!.isRunning) {
            return false
        }
        val lobbyLoc = plugin.arenaManager.mainLobby ?: return false
        return player.world == lobbyLoc.world
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLobbyPvP(e: EntityDamageByEntityEvent) {
        val damager = e.damager as? Player ?: return
        if (isInLobby(damager) && !damager.hasPermission("pumpkin.admin")) {
            e.isCancelled = true
            val msg = plugin.languageManager.get("lobby.pvp_disabled").replace("<prefix>", plugin.languageManager.get("prefix"))
            damager.sendMessage(plugin.messageManager.parse(msg))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLobbyDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        if (isInLobby(player)) {
            e.isCancelled = true
            player.health = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
            player.fireTicks = 0
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLobbyHunger(e: FoodLevelChangeEvent) {
        val player = e.entity as? Player ?: return

        // Si el jugador está en un minijuego, DEJAMOS que le baje el hambre.
        if (plugin.eventManager.currentGame != null && plugin.eventManager.currentGame!!.isRunning) {
            return
        }

        // Si está en el lobby, no le baja
        if (isInLobby(player)) {
            e.isCancelled = true
            player.foodLevel = 20
        }
    }
}
