package pumpkin.eventos.games.findbutton

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import pumpkin.eventos.PumpkinEventos

class FindTheButtonListener(private val plugin: PumpkinEventos) : Listener {

    @EventHandler
    fun onButtonInteract(event: PlayerInteractEvent) {
        val game = plugin.eventManager.currentGame as? FindTheButton ?: return
        if (!game.isRunning) return

        val player = event.player
        if (!game.players.contains(player)) return

        if (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.LEFT_CLICK_BLOCK) {
            val clickedBlock = event.clickedBlock ?: return
            
            // Verificamos que sea exactamente WARPED_BUTTON
            if (clickedBlock.type == Material.WARPED_BUTTON) {
                event.isCancelled = true

                // Si ya ganaron los 3 primeros, no hacemos nada más
                if (game.winners.size >= 3) return
                if (game.winners.contains(player)) return

                game.winners.add(player)
                val position = game.winners.size
                
                plugin.server.broadcast(
                    plugin.messageManager.parse(
                        "<#00FFCC>✔ <yellow>${player.name}</yellow> <gray>ha encontrado el botón! (#$position/3)</gray>"
                    )
                )
                
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                
                // Lo eliminamos (lo pasa a espectador) para que no siga jugando
                game.eliminate(player)

                // Si ya encontramos a los 3 ganadores, terminamos el juego
                if (game.winners.size >= 3) {
                    game.checkWinner()
                }
            }
        }
    }
}
