package pumpkin.eventos.games.sillas

import dev.geco.gsit.api.GSitAPI
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import pumpkin.eventos.PumpkinEventos

class SillasListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): SillasMusicales? = plugin.eventManager.currentGame as? SillasMusicales

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(p)) return

        if (e.action == Action.RIGHT_CLICK_BLOCK) {
            val block = e.clickedBlock ?: return
            if (game.estado == SillasEstado.SENTARSE) {
                e.isCancelled = true
                game.intentarSentarse(p, block.location)
            }
        }
    }
}
