package pumpkin.eventos.listeners

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import pumpkin.eventos.PumpkinEventos

class ConnectionListener(private val plugin: PumpkinEventos) : Listener {

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val player = e.player
        val prefix = plugin.languageManager.get("prefix")
        val rawMsg = plugin.languageManager.get("connection.join").replace("<prefix>", prefix)

        // Reemplazamos el mensaje original por el nuestro parseado
        e.joinMessage(plugin.messageManager.parse(rawMsg, Placeholder.parsed("player", player.name)))
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        val player = e.player
        val prefix = plugin.languageManager.get("prefix")
        val rawMsg = plugin.languageManager.get("connection.quit").replace("<prefix>", prefix)

        // Reemplazamos el mensaje original por el nuestro parseado
        e.quitMessage(plugin.messageManager.parse(rawMsg, Placeholder.parsed("player", player.name)))
    }
}
