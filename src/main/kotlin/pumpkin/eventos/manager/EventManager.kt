package pumpkin.eventos.manager

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import pumpkin.eventos.games.pillars.PillarsOfFortune
import java.util.concurrent.TimeUnit

class EventManager(private val plugin: PumpkinEventos) {

    val games = mutableMapOf<String, EventGame>()
    var isVoting = false
    var currentGame: EventGame? = null

    fun registerGame(game: EventGame) { games[game.id] = game }

    // --- SISTEMA DE VOTACIÓN ---
    fun startVoting() {
        if (isVoting || currentGame != null) return

        isVoting = true
        plugin.voteManager.resetVotes()

        val lang = plugin.languageManager
        val mm = plugin.messageManager

        // Construir mensaje de votación desde config
        val header = lang.get("event_manager.vote_start.header")
        val itemTemplate = lang.get("event_manager.vote_start.item")
        val footer = lang.get("event_manager.vote_start.footer")

        val builder = StringBuilder(header)
        games.values.forEach { game ->
            val formattedItem = itemTemplate
                .replace("<id>", game.id)
                .replace("<name>", game.displayName)
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

        val rawMsg = plugin.languageManager.get("event_manager.vote_end")
        Bukkit.broadcast(plugin.messageManager.parse(rawMsg, Placeholder.parsed("game", winnerGame.displayName)))

        startCountdown(winnerGame)
    }

    // --- SISTEMA DE CUENTA REGRESIVA ---
    fun startCountdown(game: EventGame) {
        var timeLeft = 10
        val lang = plugin.languageManager

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (timeLeft <= 0) {
                task.cancel()
                prepareMapAndStart(game)
                return@runAtFixedRate
            }

            val rawTitle = lang.get("event_manager.countdown_title")
            val rawSub = lang.get("event_manager.countdown_subtitle")

            val title = Title.title(
                plugin.messageManager.parse(rawTitle, Placeholder.parsed("time", timeLeft.toString())),
                plugin.messageManager.parse(rawSub, Placeholder.parsed("game", game.displayName))
            )

            Bukkit.getOnlinePlayers().forEach { p ->
                p.showTitle(title)
                p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f)
            }

            val rawChat = lang.get("event_manager.countdown_chat")
            Bukkit.broadcast(plugin.messageManager.parse(rawChat, Placeholder.parsed("time", timeLeft.toString()), Placeholder.parsed("game", game.displayName)))

            timeLeft--
        }, 0, 1, TimeUnit.SECONDS)
    }

    private fun prepareMapAndStart(game: EventGame) {
        val lang = plugin.languageManager
        val mm = plugin.messageManager

        Bukkit.broadcast(mm.parse(lang.get("event_manager.map_loading")))

        val arenasCompatibles = plugin.arenaManager.getAvailableArenas().filter { it.type.lowercase() == game.id.lowercase() }
        val arenaTemplate = arenasCompatibles.randomOrNull()

        if (arenaTemplate == null || arenaTemplate.slimeWorldName == null) {
            val errorMsg = lang.get("event_manager.map_error_no_arena")
            Bukkit.broadcast(mm.parse("$errorMsg <gray>(Falta crear un mapa de tipo '${game.id}')</gray>"))
            return
        }

        val customRules = game.getCustomGameRules()

        plugin.mapManager.loadArenaWorld(arenaTemplate.slimeWorldName!!, customRules).thenAccept { world ->
            if (world == null) {
                Bukkit.broadcast(mm.parse(lang.get("event_manager.map_error_slime")))
                return@thenAccept
            }

            plugin.server.globalRegionScheduler.execute(plugin) {
                val actualSpawn = arenaTemplate.centerLocation?.clone() ?: world.spawnLocation
                actualSpawn.world = world

                Bukkit.getOnlinePlayers().forEach { p ->
                    // --- CORREGIDO: AHORA SÍ LOS MANDAMOS A TODOS AL MUNDO NUEVO ---
                    p.teleportAsync(actualSpawn)
                    p.inventory.clear()
                    game.players.add(p)
                }

                currentGame = game

                // Le damos un pequeño "respiro" al servidor (1 segundo) para que termine
                // de teletransportar a todos al mundo antes de arrancar el onStart del juego
                plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                    game.start(arenaTemplate)
                    Bukkit.broadcast(mm.parse(lang.get("event_manager.event_started")))
                }, 20L)
            }
        }
    }
}
