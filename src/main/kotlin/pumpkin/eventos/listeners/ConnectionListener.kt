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
        e.joinMessage(plugin.languageManager.component("connection.join", Placeholder.unparsed("player", p.name)))

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
                plugin.languageManager.send(p, "connection.miniwalls_reconnected")

                val spawn = game.teamSpawns[team]
                var secondsLeft = 5
                plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
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
                        plugin.languageManager.component("connection.reconnect_title"),
                        plugin.languageManager.component("connection.reconnect_subtitle",
                            Placeholder.unparsed("time", secondsLeft.toString()),
                            Placeholder.unparsed("unit", if (secondsLeft == 1) "segundo" else "segundos")),
                        net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ZERO,
                            java.time.Duration.ofMillis(1100),
                            java.time.Duration.ZERO
                        )
                    )
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> p.showTitle(title) }
                    secondsLeft--
                }, 1L, 20L)

            } else if (!game.spectators.contains(p)) {
                // Nexo destruido o ya eliminado → poner como espectador definitivo
                game.players.remove(p)
                game.spectators.add(p)
                p.gameMode = GameMode.SPECTATOR
                plugin.languageManager.send(p, "connection.nexus_destroyed")
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
            plugin.languageManager.send(p, "connection.disqualified_self")
        } else {
            // Sigue siendo jugador activo (ej. se desconectó brevemente), solo asegurar modo
            p.gameMode = GameMode.SURVIVAL
        }
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        val p = e.player
        e.quitMessage(plugin.languageManager.component("connection.quit", Placeholder.unparsed("player", p.name)))

        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning) return

        if (game.players.contains(p)) {
            if (game is MiniWalls) {
                // En MiniWalls no sacamos al jugador para que pueda reconectarse
                // Solo avisamos al broadcast
                plugin.languageManager.broadcast("connection.miniwalls_disconnected", Placeholder.unparsed("player", p.name))
                return
            }

            // DESCALIFICAR EN TODOS LOS DEMÁS
            game.players.remove(p)
            if (!game.spectators.contains(p)) game.spectators.add(p)
            plugin.languageManager.broadcast("connection.disqualified_broadcast", Placeholder.unparsed("player", p.name))

            // Solo terminar el juego si realmente queda ≤1 jugador activo
            if (game.players.size <= 1 && game.isRunning) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> game.checkWinner() }
            }
        }
    }
}
