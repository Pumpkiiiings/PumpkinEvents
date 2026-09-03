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
    private var sessionGeneration = 0L

    abstract fun onStart()
    abstract fun onStop()

    open fun getExtraPlaceholders(): Map<String, String> {
        return emptyMap()
    }

    open fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return emptyMap()
    }

    /**
     * Registra una tarea recurrente ligada a la sesión actual. Al terminar la
     * partida se cancela inmediatamente y nunca puede revivir en otra sesión.
     */
    protected fun runAtFixedRate(
        action: (ScheduledTask) -> Unit,
        initialDelayTicks: Long,
        periodTicks: Long
    ): ScheduledTask {
        val session = sessionGeneration
        val scheduled = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || sessionGeneration != session) {
                task.cancel()
                return@runAtFixedRate
            }
            action(task)
        }, initialDelayTicks, periodTicks)
        gameTasks += scheduled
        return scheduled
    }

    fun start(arena: Arena, plugin: PumpkinEventos, world: World) {
        val session = ++sessionGeneration
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
        gameTasks += plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            if (isRunning && sessionGeneration == session) {
                players.toList().forEach { p ->
                    plugin.puntajeManager.addPoints(p, 5, plugin.languageManager.get("common.points.reasons.survival_two_minutes"))
                }
            }
        }, 2400L)

        // Votación de PvP al inicio si el modo lo requiere
        if (plugin.pvpVoteManager.shouldLaunchVote(id)) {
            val launched = plugin.pvpVoteManager.startVoting(this) { pvpEnabled ->
                if (!isRunning || sessionGeneration != session) return@startVoting
                this.pvpEnabled = pvpEnabled
                onStart()
            }
            if (!launched) onStart()
        } else {
            onStart()
        }

        // Timer de votación de pociones cada 2 minutos
        if (plugin.config.getBoolean("potion_vote.enabled", true)) {
            val intervalSec = plugin.config.getInt("potion_vote.interval_seconds", 120).toLong()
            runAtFixedRate({ task ->
                if (!isRunning || sessionGeneration != session) { task.cancel(); return@runAtFixedRate }
                plugin.potionVoteManager.startVoting()
            }, intervalSec * 20L, intervalSec * 20L)
        }
    }

    fun stop() {
        if (!isRunning) return
        val worldToUnload = gameWorld
        isRunning = false
        sessionGeneration++
        plugin.pvpVoteManager.cancelFor(this)
        plugin.potionVoteManager.cancelFor(this)
        plugin.extrasVoteManager.cancelFor(this)

        gameTasks.forEach { runCatching { it.cancel() } }
        gameTasks.clear()

        try {
            onStop()
        } finally {
            currentArena = null
            gameWorld = null
            players.clear()
            spectators.clear()
            plugin.eventManager.finishGame(this, worldToUnload)
        }
    }

    open fun eliminate(player: Player) {
        if (!players.contains(player)) return
        players.remove(player)
        spectators.add(player)

        player.gameMode = org.bukkit.GameMode.SPECTATOR
        player.inventory.clear()
        player.playSound(player.location, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 2f)

        plugin.languageManager.getList("commands.espectear.instruction")
            .forEach { player.sendMessage(plugin.messageManager.parse(player, it)) }

        if (players.size <= 1 && isRunning) {
            checkWinner()
        }
    }

    protected fun allParticipants(vararg additional: Iterable<Player>): List<Player> =
        (players.asSequence() + spectators.asSequence() + additional.asSequence().flatMap { it.asSequence() })
            .distinctBy { it.uniqueId }
            .toList()

    /** Restablecimiento común al abandonar cualquier minijuego. */
    protected fun returnToLobby(
        playersToReturn: Iterable<Player> = allParticipants(),
        beforeReset: (Player) -> Unit = {}
    ) {
        val lobby = plugin.arenaManager.mainLobby
            ?: plugin.server.worlds.firstOrNull()?.spawnLocation
            ?: return

        playersToReturn.distinctBy { it.uniqueId }.forEach { player ->
            beforeReset(player)
            player.inventory.clear()
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            player.isFlying = false
            player.allowFlight = false
            player.gameMode = GameMode.ADVENTURE
            player.teleportAsync(lobby)
        }
    }

    protected fun awardVictory(player: Player, points: Int) {
        plugin.puntajeManager.addPoints(player, points, plugin.languageManager.get("common.points.reasons.victory"))
    }

    abstract fun checkWinner()
}
