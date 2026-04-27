package pumpkin.eventos.manager

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import pumpkin.eventos.games.pillars.PillarsOfFortune
import pumpkin.eventos.games.jalarcuerda.JalarCuerda
import pumpkin.eventos.games.iceboat.IceBoatRacing // NUEVO
import java.util.concurrent.TimeUnit

class EventManager(private val plugin: PumpkinEventos) {

    val games = mutableMapOf<String, EventGame>()
    var isVoting = false
    var currentGame: EventGame? = null
    val narrators = mutableSetOf<java.util.UUID>()

    fun registerGame(game: EventGame) { games[game.id] = game }

    fun startVoting() {
        if (isVoting || currentGame != null) return
        isVoting = true
        plugin.voteManager.resetVotes()

        val lang = plugin.languageManager
        val mm = plugin.messageManager

        val header = lang.get("event_manager.vote_start.header")
        val itemTemplate = lang.get("event_manager.vote_start.item")
        val footer = lang.get("event_manager.vote_start.footer")

        val builder = StringBuilder(header)
        games.values.forEach { game ->
            val formattedItem = itemTemplate.replace("<id>", game.id).replace("<name>", game.displayName)
            builder.append(formattedItem).append("\n")
        }
        builder.append(footer)

        Bukkit.broadcast(mm.parse(builder.toString()))
        Bukkit.getOnlinePlayers().forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f) }
        plugin.server.asyncScheduler.runDelayed(plugin, { _ -> endVoting() }, 30, TimeUnit.SECONDS)
    }

    private fun endVoting() {
        isVoting = false
        val winnerId = plugin.voteManager.getWinningGame(games)
        val winnerGame = games[winnerId] ?: return
        Bukkit.broadcast(plugin.messageManager.parse(plugin.languageManager.get("event_manager.vote_end"), Placeholder.parsed("game", winnerGame.displayName)))
        startCountdown(winnerGame)
    }

    fun startCountdown(game: EventGame) {
        var timeLeft = 10
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (timeLeft <= 0) {
                task.cancel()
                prepareMapAndStart(game)
                return@runAtFixedRate
            }
            val title = Title.title(
                plugin.messageManager.parse(plugin.languageManager.get("event_manager.countdown_title"), Placeholder.parsed("time", timeLeft.toString())),
                plugin.messageManager.parse(plugin.languageManager.get("event_manager.countdown_subtitle"), Placeholder.parsed("game", game.displayName))
            )
            Bukkit.getOnlinePlayers().forEach { p -> p.showTitle(title); p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f) }
            Bukkit.broadcast(plugin.messageManager.parse(plugin.languageManager.get("event_manager.countdown_chat"), Placeholder.parsed("time", timeLeft.toString()), Placeholder.parsed("game", game.displayName)))
            timeLeft--
        }, 0, 1, TimeUnit.SECONDS)
    }

    private fun prepareMapAndStart(game: EventGame) {
        val lang = plugin.languageManager
        val mm = plugin.messageManager

        Bukkit.broadcast(mm.parse(lang.get("event_manager.map_loading")))
        val arenaTemplate = plugin.arenaManager.getAvailableArenas().filter { it.type.lowercase() == game.id.lowercase() }.randomOrNull()

        if (arenaTemplate == null || arenaTemplate.slimeWorldName == null) {
            Bukkit.broadcast(mm.parse("${lang.get("event_manager.map_error_no_arena")} <gray>(Falta crear un mapa de tipo '${game.id}')</gray>"))
            return
        }

        plugin.mapManager.loadArenaWorld(arenaTemplate.slimeWorldName!!, game.getCustomGameRules()).thenAccept { world ->
            if (world == null) {
                Bukkit.broadcast(mm.parse(lang.get("event_manager.map_error_slime")))
                return@thenAccept
            }

            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                val actualSpawn = arenaTemplate.centerLocation?.clone() ?: world.spawnLocation
                actualSpawn.world = world

                val spawns = arenaTemplate.spawnPoints
                var spawnIndex = 0

                Bukkit.getOnlinePlayers().forEach { p ->
                    if (narrators.contains(p.uniqueId)) {
                        p.teleportAsync(actualSpawn)
                        p.inventory.clear()
                        p.gameMode = org.bukkit.GameMode.SPECTATOR
                        game.spectators.add(p)
                    } else {
                        // Modos que NECESITAN estar en el centro para una fase previa de lobby/votación
                        if (game is pumpkin.eventos.games.skywars.Skywars || 
                            game is pumpkin.eventos.games.miniwalls.MiniWalls || 
                            game is pumpkin.eventos.games.sumo.Sumo) {
                            p.teleportAsync(actualSpawn)
                        } else if (game !is PillarsOfFortune && game !is JalarCuerda && game !is IceBoatRacing) {
                            // Si hay spawns en la arena, se reparten circularmente; si no, usan el actualSpawn
                            val loc = if (spawns.isNotEmpty()) spawns[spawnIndex % spawns.size].clone() else actualSpawn
                            loc.world = world
                            p.teleportAsync(loc)
                            spawnIndex++
                        }
                        p.inventory.clear()
                        game.players.add(p)
                    }
                }

                currentGame = game
                game.start(arenaTemplate, plugin, world)
                Bukkit.broadcast(mm.parse(lang.get("event_manager.event_started")))
            }, 20L)
        }
    }
}
