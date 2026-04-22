package pumpkin.eventos.games.luzroja

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import pumpkin.eventos.PumpkinEventos

class LuzRojaListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): LuzRojaLuzVerde? = plugin.eventManager.currentGame as? LuzRojaLuzVerde

    @EventHandler
    fun onMove(e: PlayerMoveEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(p)) return

        if (game.isPlayerSaved(p)) return
        if (game.hasReachedMeta(e.to)) { game.markWinner(p); return }

        if (game.estado == LuzEstado.ROJA) {
            val diffX = Math.abs(e.to.x - e.from.x)
            val diffZ = Math.abs(e.to.z - e.from.z)
            if (diffX > 0.05 || diffZ > 0.05) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> game.killPlayer(p) }
            }
        }
    }
}
