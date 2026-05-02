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
            // Solo actuar si el jugador estaba registrado en un equipo (era participante)
            val team = game.playerTeam[p] ?: return
            val hpNexo = game.teamNexusHP[team] ?: 0

            if (hpNexo > 0 && game.players.contains(p)) {
                // Nexo vivo → lanzar cuenta regresiva real de respawn
                p.gameMode = GameMode.SPECTATOR
                p.sendMessage(plugin.messageManager.parse("<#FFFF00>¡Reconectado! Tu nexo está vivo, reaparecerás en 5 segundos.</#FFFF00>"))

                val spawn = game.teamSpawns[team]
                var secondsLeft = 5
                plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
                    if (!game.isRunning || !game.players.contains(p)) {
                        task.cancel()
                        return@runAtFixedRate
                    }
                    if (secondsLeft <= 0) {
                        task.cancel()
                        if (spawn != null) {
                            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                                p.teleportAsync(spawn).thenRun {
                                    plugin.server.regionScheduler.runDelayed(plugin, spawn, { _ ->
                                        if (game.isRunning && game.players.contains(p)) game.equipPlayer(p)
                                    }, 5L)
                                }
                            }
                        }
                        return@runAtFixedRate
                    }
                    val title = net.kyori.adventure.title.Title.title(
                        plugin.messageManager.parse("<yellow><b>¡RECONECTADO!</b></yellow>"),
                        plugin.messageManager.parse("<white>Reaparecerás en <yellow><b>$secondsLeft</b></yellow> segundo${if (secondsLeft == 1) "" else "s"}</white>"),
                        net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ZERO,
                            java.time.Duration.ofMillis(1100),
                            java.time.Duration.ZERO
                        )
                    )
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> p.showTitle(title) }
                    secondsLeft--
                }, 0L, 1L, java.util.concurrent.TimeUnit.SECONDS)

            } else if (!game.spectators.contains(p)) {
                // Nexo destruido o ya eliminado → poner como espectador definitivo
                game.players.remove(p)
                game.spectators.add(p)
                p.gameMode = GameMode.SPECTATOR
                p.sendMessage(plugin.messageManager.parse("<red>Tu nexo fue destruido mientras no estabas. Ahora eres espectador.</red>"))
            } else {
                // Ya era espectador, solo asegurarse del modo de juego
                p.gameMode = GameMode.SPECTATOR
            }
            return
        }

        // --- OTROS JUEGOS: solo manejar a quienes YA estaban descalificados o eran espectadores ---
        // Si el jugador nunca estuvo en este evento, no hacer nada
        val eraParticipante = game.players.contains(p) || game.spectators.contains(p)
        if (!eraParticipante) return

        if (!game.players.contains(p)) {
            // Estaba descalificado (ya está en spectators o fue removido en onQuit)
            if (!game.spectators.contains(p)) game.spectators.add(p)
            p.gameMode = GameMode.SPECTATOR
            p.inventory.clear()
            val loc = game.currentArena?.centerLocation?.clone()?.apply { world = game.gameWorld } ?: p.world.spawnLocation
            p.teleportAsync(loc)
            p.sendMessage(plugin.messageManager.parse("<red>Te desconectaste y fuiste descalificado. Ahora eres espectador.</red>"))
        } else {
            // Sigue siendo jugador activo (ej. se desconectó brevemente), solo asegurar modo
            p.gameMode = GameMode.SURVIVAL
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
                // En MiniWalls no sacamos al jugador para que pueda reconectarse
                // Solo avisamos al broadcast
                plugin.server.broadcast(plugin.messageManager.parse("<yellow><b>${p.name}</b> se ha desconectado. Reconéctate pronto o serás eliminado.</yellow>"))
                return
            }

            // DESCALIFICAR EN TODOS LOS DEMÁS
            game.players.remove(p)
            if (!game.spectators.contains(p)) game.spectators.add(p)
            plugin.server.broadcast(plugin.messageManager.parse("<red><b>${p.name}</b> se ha desconectado y ha sido descalificado.</red>"))

            // Solo terminar el juego si realmente queda ≤1 jugador activo
            if (game.players.size <= 1 && game.isRunning) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> game.checkWinner() }
            }
        }
    }
}
