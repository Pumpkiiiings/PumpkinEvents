package pumpkin.eventos.listeners

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.miniwalls.MiniWalls

class ConnectionListener(private val plugin: PumpkinEventos) : Listener {

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val p = e.player
        val prefix = plugin.languageManager.get("prefix")
        val rawMsg = plugin.languageManager.get("connection.join").replace("<prefix>", prefix)
        e.joinMessage(plugin.messageManager.parse(rawMsg, Placeholder.parsed("player", p.name)))

        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning) return

        // --- CASO MINI-WALLS ---
        if (game is MiniWalls) {
            val team = game.playerTeam[p]
            val hpNexo = if (team != null) game.teamNexusHP[team] ?: 0 else 0

            if (team != null && hpNexo > 0) {
                // Si el nexo sigue vivo, activamos el respawn de 5 segundos
                p.gameMode = GameMode.SPECTATOR
                game.respawnTimers[p.uniqueId] = 5
                p.sendMessage(plugin.messageManager.parse("<#FFFF00>¡Reconectado! Tu nexo está vivo, reaparecerás en 5 segundos.</#FFFF00>"))
            } else {
                // Si no tiene nexo o no estaba en equipo, muere definitivamente
                game.players.remove(p)
                if (!game.spectators.contains(p)) game.spectators.add(p)
                p.gameMode = GameMode.SPECTATOR
                p.sendMessage(plugin.messageManager.parse("<red>Tu nexo fue destruido mientras no estabas. Ahora eres espectador.</red>"))
            }
            return
        }

        // --- OTROS JUEGOS (DESCALIFICACIÓN) ---
        if (!game.players.contains(p)) {
            game.spectators.add(p)
            p.gameMode = GameMode.SPECTATOR
            p.inventory.clear()
            val loc = game.currentArena?.centerLocation ?: p.world.spawnLocation
            p.teleportAsync(loc)
            p.sendMessage(plugin.messageManager.parse("<red>Te desconectaste y fuiste descalificado.</red>"))
        }
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        val p = e.player
        val prefix = plugin.languageManager.get("prefix")
        val rawMsg = plugin.languageManager.get("connection.quit").replace("<prefix>", prefix)
        e.quitMessage(plugin.messageManager.parse(rawMsg, Placeholder.parsed("player", p.name)))

        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning) return

        if (game.players.contains(p)) {
            if (game is MiniWalls) {
                // En MiniWalls no lo sacamos de la lista para que pueda volver
                return
            }

            // DESCALIFICAR EN TODOS LOS DEMÁS
            game.players.remove(p)
            game.spectators.add(p)
            plugin.server.broadcast(plugin.messageManager.parse("<red><b>${p.name}</b> se ha desconectado y ha sido descalificado.</red>"))

            // Si quedaban 2, el otro gana automáticamente
            game.checkWinner()
        }
    }
}
