package pumpkin.eventos.games

import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Sound
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos // <-- Necesitamos acceso al plugin principal
import pumpkin.eventos.arena.Arena

abstract class EventGame(val plugin: PumpkinEventos, val id: String, val displayName: String) {
    var isRunning = false
    var currentArena: Arena? = null
    val players = mutableListOf<Player>()
    val spectators = mutableListOf<Player>()

    abstract fun onStart()
    abstract fun onStop()

    open fun getExtraPlaceholders(): Map<String, String> {
        return emptyMap()
    }

    open fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return emptyMap()
    }

    // PASAMOS EL PLUGIN AL START PARA PODER ENVIAR EL MENSAJE
    fun start(arena: Arena, plugin: PumpkinEventos) {
        currentArena = arena
        isRunning = true

        // --- 1. ENVIAR TUTORIAL DEL CHAT (0-Hardcode) ---
        val lines = plugin.languageManager.getList("tutorials.$id")
        if (lines.isNotEmpty()) {
            players.forEach { p ->
                lines.forEach { line ->
                    p.sendMessage(plugin.messageManager.parse(line))
                }
                p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.5f)
            }
        }
        // -------------------------------------------------

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
