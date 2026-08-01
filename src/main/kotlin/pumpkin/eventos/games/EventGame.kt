package pumpkin.eventos.games

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.arena.Arena

abstract class EventGame(val plugin: PumpkinEventos, val id: String, val displayName: String) {
    var isRunning = false
    var currentArena: Arena? = null
    var gameWorld: World? = null
    val players = mutableListOf<Player>()
    val spectators = mutableListOf<Player>()
    /** Resultado de la votación de PvP al inicio del juego */
    var pvpEnabled = true

    /**
     * Tareas recurrentes creadas por [start] que deben morir con la partida.
     * Sin esto se acumulaba una tarea de votación de pociones por cada partida
     * jugada, y todas se reactivaban a la vez en la siguiente.
     */
    private val gameTasks = mutableListOf<ScheduledTask>()

    abstract fun onStart()
    abstract fun onStop()

    open fun getExtraPlaceholders(): Map<String, String> {
        return emptyMap()
    }

    open fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return emptyMap()
    }

    fun start(arena: Arena, plugin: PumpkinEventos, world: World) {
        currentArena = arena
        gameWorld = world
        isRunning = true

        val lines = plugin.languageManager.getList("tutorials.$id")
        if (lines.isNotEmpty()) {
            players.forEach { p ->
                lines.forEach { line -> p.sendMessage(plugin.messageManager.parse(line)) }
                p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.5f)
            }
        }

        // Puntos por sobrevivir 2 minutos
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            if (isRunning) players.forEach { p -> plugin.puntajeManager.addPoints(p, 5, "Sobrevivir 2 minutos") }
        }, 2400L)

        // Votación de PvP al inicio si el modo lo requiere
        if (plugin.pvpVoteManager.shouldLaunchVote(id)) {
            plugin.pvpVoteManager.startVoting(this) { pvpEnabled ->
                this.pvpEnabled = pvpEnabled
                onStart()
            }
        } else {
            onStart()
        }

        // Timer de votación de pociones cada 2 minutos
        if (plugin.config.getBoolean("potion_vote.enabled", true)) {
            val intervalSec = plugin.config.getInt("potion_vote.interval_seconds", 120).toLong()
            gameTasks += plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
                if (!isRunning) { task.cancel(); return@runAtFixedRate }
                plugin.potionVoteManager.startVoting()
            }, intervalSec, intervalSec, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false

        gameTasks.forEach { runCatching { it.cancel() } }
        gameTasks.clear()

        onStop()
        currentArena = null
        gameWorld = null
        players.clear()
        spectators.clear()
    }

    open fun eliminate(player: Player) {
        if (!players.contains(player)) return
        players.remove(player)
        spectators.add(player)

        player.gameMode = org.bukkit.GameMode.SPECTATOR
        player.inventory.clear()
        player.playSound(player.location, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 2f)

        val instruction = plugin.languageManager.get("commands.espectear.instruction")
        if (instruction.isNotEmpty()) {
            player.sendMessage(plugin.messageManager.parse(instruction))
        }

        if (players.size <= 1 && isRunning) {
            checkWinner()
        }
    }

    abstract fun checkWinner()
}
