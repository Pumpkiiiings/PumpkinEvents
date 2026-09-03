package pumpkin.eventos.manager

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.World
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.arena.Arena
import pumpkin.eventos.games.EventGame
import pumpkin.eventos.games.battleroyale.BattleRoyale
import pumpkin.eventos.games.iceboat.IceBoatRacing
import pumpkin.eventos.games.jalarcuerda.JalarCuerda
import pumpkin.eventos.games.parkour.Parkour
import pumpkin.eventos.games.pillars.PillarsOfFortune
import java.util.UUID
import java.util.concurrent.CompletableFuture

enum class EventPhase { IDLE, VOTING, SELECTING, COUNTDOWN, LOADING, RUNNING, STOPPING }

class EventManager(private val plugin: PumpkinEventos) {

    val games = mutableMapOf<String, EventGame>()
    @Volatile var currentGame: EventGame? = null
    val narrators = mutableSetOf<UUID>()

    private val stateLock = Any()
    @Volatile var phase: EventPhase = EventPhase.IDLE
        private set
    val isVoting: Boolean get() = phase == EventPhase.VOTING
    val isBusy: Boolean get() = phase != EventPhase.IDLE

    private var generation = 0L
    private var voteTask: ScheduledTask? = null
    private var countdownTask: ScheduledTask? = null

    fun registerGame(game: EventGame) { games[game.id] = game }

    fun startVoting(): Boolean {
        val token = synchronized(stateLock) {
            if (phase != EventPhase.IDLE || currentGame != null) return false
            phase = EventPhase.VOTING
            ++generation
        }

        plugin.voteManager.resetVotes()
        val lang = plugin.languageManager
        val builder = StringBuilder(lang.get("event_manager.vote_start.header"))
        games.values.forEach { game ->
            builder.append(lang.get("event_manager.vote_start.item")
                .replace("<id>", game.id)
                .replace("<name>", game.displayName))
                .append('\n')
        }
        builder.append(lang.get("event_manager.vote_start.footer"))

        Bukkit.broadcast(plugin.messageManager.parse(builder.toString()))
        Bukkit.getOnlinePlayers().forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f) }
        voteTask = plugin.server.globalRegionScheduler.runDelayed(plugin, { endVoting(token) }, 600L)
        return true
    }

    private fun endVoting(token: Long) {
        val winnerGame = synchronized(stateLock) {
            if (phase != EventPhase.VOTING || generation != token) return
            val winnerId = plugin.voteManager.getWinningGame(games)
            val game = games[winnerId]
            if (game == null) {
                phase = EventPhase.IDLE
                return
            }
            phase = EventPhase.COUNTDOWN
            game
        }

        Bukkit.broadcast(plugin.messageManager.parse(
            plugin.languageManager.get("event_manager.vote_end"),
            Placeholder.parsed("game", winnerGame.displayName)
        ))
        scheduleCountdown(winnerGame, token)
    }

    fun startCountdown(game: EventGame): Boolean {
        val token = synchronized(stateLock) {
            if (phase != EventPhase.IDLE || currentGame != null) return false
            phase = EventPhase.COUNTDOWN
            ++generation
        }
        scheduleCountdown(game, token)
        return true
    }

    fun beginSelection(): Boolean = synchronized(stateLock) {
        if (phase != EventPhase.IDLE || currentGame != null) return false
        phase = EventPhase.SELECTING
        generation++
        true
    }

    fun startCountdownAfterSelection(game: EventGame): Boolean {
        val token = synchronized(stateLock) {
            if (phase != EventPhase.SELECTING) return false
            phase = EventPhase.COUNTDOWN
            generation
        }
        scheduleCountdown(game, token)
        return true
    }

    private fun scheduleCountdown(game: EventGame, token: Long) {
        var timeLeft = 10
        countdownTask = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isCurrent(token, EventPhase.COUNTDOWN)) {
                task.cancel()
                return@runAtFixedRate
            }
            if (timeLeft <= 0) {
                task.cancel()
                synchronized(stateLock) {
                    if (generation != token || phase != EventPhase.COUNTDOWN) return@runAtFixedRate
                    phase = EventPhase.LOADING
                }
                prepareMapAndStart(game, token)
                return@runAtFixedRate
            }

            val title = Title.title(
                plugin.messageManager.parse(plugin.languageManager.get("event_manager.countdown_title"), Placeholder.parsed("time", timeLeft.toString())),
                plugin.messageManager.parse(plugin.languageManager.get("event_manager.countdown_subtitle"), Placeholder.parsed("game", game.displayName))
            )
            Bukkit.getOnlinePlayers().forEach { player ->
                player.showTitle(title)
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f)
            }
            Bukkit.broadcast(plugin.messageManager.parse(
                plugin.languageManager.get("event_manager.countdown_chat"),
                Placeholder.parsed("time", timeLeft.toString()),
                Placeholder.parsed("game", game.displayName)
            ))
            timeLeft--
        }, 1L, 20L)
    }

    private fun prepareMapAndStart(game: EventGame, token: Long) {
        val lang = plugin.languageManager
        Bukkit.broadcast(plugin.messageManager.parse(lang.get("event_manager.map_loading")))
        val arenaTemplate = plugin.arenaManager.getAvailableArenas()
            .filter { it.type.equals(game.id, ignoreCase = true) }
            .randomOrNull()

        if (arenaTemplate?.slimeWorldName == null) {
            Bukkit.broadcast(plugin.messageManager.parse(
                "${lang.get("event_manager.map_error_no_arena")} <gray>(Falta crear un mapa de tipo '${game.id}')</gray>"
            ))
            resetIfCurrent(token)
            return
        }

        plugin.mapManager.loadArenaWorld(arenaTemplate.slimeWorldName!!, game.getCustomGameRules())
            .whenComplete { world, error ->
                if (error != null || world == null) {
                    plugin.server.globalRegionScheduler.execute(plugin) {
                        if (isCurrent(token, EventPhase.LOADING)) {
                            Bukkit.broadcast(plugin.messageManager.parse(lang.get("event_manager.map_error_slime")))
                            resetIfCurrent(token)
                        }
                    }
                    return@whenComplete
                }
                plugin.server.globalRegionScheduler.runDelayed(plugin, { startInLoadedWorld(game, arenaTemplate, world, token) }, 20L)
            }
    }

    private fun startInLoadedWorld(game: EventGame, arena: Arena, world: World, token: Long) {
        if (!isCurrent(token, EventPhase.LOADING)) {
            plugin.mapManager.unloadWorld(world)
            return
        }

        val actualSpawn = (arena.centerLocation?.clone() ?: world.spawnLocation).also { it.world = world }
        val spawns = arena.spawnPoints
        var spawnIndex = 0
        val teleports = mutableListOf<CompletableFuture<Boolean>>()

        game.players.clear()
        game.spectators.clear()
        Bukkit.getOnlinePlayers().forEach { player ->
            player.inventory.clear()
            if (narrators.contains(player.uniqueId)) {
                player.gameMode = GameMode.SPECTATOR
                game.spectators.add(player)
                teleports += player.teleportAsync(actualSpawn)
            } else {
                game.players.add(player)
                when {
                    game is pumpkin.eventos.games.skywars.Skywars ||
                        game is pumpkin.eventos.games.miniwalls.MiniWalls ||
                        game is pumpkin.eventos.games.sumo.Sumo ||
                        game is BattleRoyale || game is Parkour -> teleports += player.teleportAsync(actualSpawn)
                    game !is PillarsOfFortune && game !is JalarCuerda && game !is IceBoatRacing -> {
                        val location = if (spawns.isNotEmpty()) {
                            spawns[spawnIndex++ % spawns.size].clone()
                        } else {
                            actualSpawn.clone()
                        }
                        location.world = world
                        teleports += player.teleportAsync(location)
                    }
                }
            }
        }

        CompletableFuture.allOf(*teleports.toTypedArray()).whenComplete { _, _ ->
            plugin.server.globalRegionScheduler.execute(plugin) {
                if (!isCurrent(token, EventPhase.LOADING)) {
                    plugin.mapManager.unloadWorld(world)
                    return@execute
                }
                try {
                    currentGame = game
                    phase = EventPhase.RUNNING
                    game.start(arena, plugin, world)
                    Bukkit.broadcast(plugin.messageManager.parse(plugin.languageManager.get("event_manager.event_started")))
                } catch (error: Throwable) {
                    plugin.componentLogger.error("No se pudo iniciar el juego ${game.id}", error)
                    runCatching { game.stop() }
                    resetIfCurrent(token)
                    plugin.mapManager.unloadWorld(world)
                }
            }
        }
    }

    fun cancelPending(): Boolean = synchronized(stateLock) {
        if (phase == EventPhase.IDLE || phase == EventPhase.RUNNING) return false
        generation++
        voteTask?.cancel()
        countdownTask?.cancel()
        voteTask = null
        countdownTask = null
        phase = EventPhase.IDLE
        true
    }

    fun finishGame(game: EventGame, world: World?) {
        synchronized(stateLock) {
            if (currentGame != null && currentGame !== game) return
            phase = EventPhase.STOPPING
            currentGame = null
            generation++
            phase = EventPhase.IDLE
        }
        plugin.mapManager.unloadWorld(world)
    }

    private fun isCurrent(token: Long, expected: EventPhase): Boolean =
        generation == token && phase == expected

    private fun resetIfCurrent(token: Long) {
        synchronized(stateLock) {
            if (generation == token) {
                currentGame = null
                phase = EventPhase.IDLE
            }
        }
    }
}
