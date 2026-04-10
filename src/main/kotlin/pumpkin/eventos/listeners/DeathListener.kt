package pumpkin.eventos.listeners

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import pumpkin.eventos.PumpkinEventos

class DeathListener(private val plugin: PumpkinEventos) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDeath(e: PlayerDeathEvent) {
        val player = e.player

        // 1. Silenciar mensaje Vanilla
        e.deathMessage(null)

        val game = plugin.eventManager.currentGame
        if (game != null && game.isRunning) {

            // 2. Mensaje Custom desde messages.yml
            val rawMsg = plugin.languageManager.get("deaths.player_died")

            // Usamos Placeholder.parsed para reemplazar <player>
            val deathMsg = plugin.messageManager.parse(rawMsg, Placeholder.parsed("player", player.name))

            plugin.server.broadcast(deathMsg)
            player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 0.5f, 2f)
        }
    }
}