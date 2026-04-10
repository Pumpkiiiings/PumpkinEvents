package pumpkin.eventos.games

import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Sound
import org.bukkit.entity.Player
import pumpkin.eventos.arena.Arena

abstract class EventGame(val id: String, val displayName: String) {
    var isRunning = false
    var currentArena: Arena? = null
    val players = mutableListOf<Player>()
    val spectators = mutableListOf<Player>()

    abstract fun onStart()
    abstract fun onStop()

    // --- MAGIA PARA SCOREBOARDS CUSTOM ---
    open fun getExtraPlaceholders(): Map<String, String> {
        return emptyMap()
    }

    // --- MAGIA PARA REGLAS DE MUNDO CUSTOM (AÑADIR ESTO) ---
    open fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return emptyMap()
    }

    fun start(arena: Arena) {
        currentArena = arena
        isRunning = true
        onStart()
    }

    fun stop() {
        isRunning = false
        onStop()
        currentArena = null
        players.clear()
        spectators.clear()
    }

    fun eliminate(player: Player) {
        if (!players.contains(player)) return
        players.remove(player)
        spectators.add(player)

        player.gameMode = GameMode.SPECTATOR
        player.inventory.clear()
        player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 2f)

        if (players.size <= 1 && isRunning) {
            checkWinner()
        }
    }

    abstract fun checkWinner()
}
