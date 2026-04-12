package pumpkin.eventos.utils

import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import pumpkin.eventos.PumpkinEventos

class VoidUtil(private val plugin: PumpkinEventos) : Listener {

    @EventHandler
    fun onFallToVoid(e: PlayerMoveEvent) {
        val p = e.player

        // Verificamos si pasó el umbral de Y = -67 (evitando procesar si ya está muerto o es espectador)
        if (e.to.y <= -67.0 && p.gameMode != GameMode.SPECTATOR && !p.isDead) {

            val currentGame = plugin.eventManager.currentGame

            // Si el jugador está jugando un minijuego, lo eliminamos con el sistema oficial
            if (currentGame != null && currentGame.players.contains(p)) {
                currentGame.eliminate(p)
            } else {
                // Si está en el Lobby u otro mundo sin minijuego, lo matamos al instante
                p.health = 0.0
            }
        }
    }
}
