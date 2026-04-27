package pumpkin.eventos.games.findbutton

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.concurrent.TimeUnit

class FindTheButton(plugin: PumpkinEventos) : EventGame(plugin, "findbutton", "<#FFA500>Buscar el Botón</#FFA500>") {

    var mainTimer = 300
    val winners = mutableListOf<Player>()

    override fun getExtraPlaceholders(): Map<String, String> {
        val min = mainTimer / 60
        val sec = mainTimer % 60
        return mapOf(
            "%time_left%" to String.format("%02d:%02d", min, sec),
            "%winners_found%" to winners.size.toString()
        )
    }

    override fun onStart() {
        mainTimer = 300
        winners.clear()

        val mm = plugin.messageManager
        val lang = plugin.languageManager

        val title = Title.title(
            mm.parse("<#FFA500><b>BUSCAR EL BOTÓN</b></#FFA500>"),
            mm.parse("<white>Encuentra el botón oculto para ganar</white>")
        )

        players.forEach { p ->
            p.gameMode = GameMode.ADVENTURE
            p.inventory.clear()
            p.activePotionEffects.forEach { effect -> p.removePotionEffect(effect.type) }
            p.showTitle(title)
            p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
        }

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) {
                task.cancel()
                return@runAtFixedRate
            }

            mainTimer--
            
            // Action bar
            val min = mainTimer / 60
            val sec = mainTimer % 60
            val timeStr = String.format("%02d:%02d", min, sec)
            val bar = mm.parse("<yellow>Tiempo restante: <gold>$timeStr</gold></yellow> <gray>|</gray> <green>Encontrados: ${winners.size}/3</green>")
            players.forEach { it.sendActionBar(bar) }

            if (mainTimer <= 0) {
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    plugin.server.broadcast(mm.parse("<red>¡El tiempo se ha agotado en Buscar el Botón!</red>"))
                    checkWinner()
                }
                task.cancel()
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    override fun checkWinner() {
        val mm = plugin.messageManager

        if (winners.isEmpty()) {
            plugin.server.broadcast(mm.parse("<gray>Nadie encontró el botón a tiempo.</gray>"))
        } else {
            plugin.server.broadcast(mm.parse("<#FFA500><b>¡GANADORES DE BUSCAR EL BOTÓN!</b></#FFA500>"))
            
            for (i in winners.indices) {
                val winner = winners[i]
                val position = i + 1
                val points = when (position) {
                    1 -> 15
                    2 -> 10
                    3 -> 5
                    else -> 2
                }
                
                plugin.server.broadcast(mm.parse("<gold>#$position</gold> - <yellow>${winner.name}</yellow> <gray>(+$points puntos)</gray>"))
                plugin.puntajeManager.addPoints(winner, points, "Ganar Buscar el Botón (#$position)")
                
                winner.world.spawnParticle(org.bukkit.Particle.FIREWORK, winner.location, 50)
                winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            }
        }
        
        stop()
    }

    override fun onStop() {
        plugin.eventManager.currentGame = null
        
        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation
        
        val todos = players + spectators + winners
        todos.distinct().forEach { p ->
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
        
        winners.clear()
    }
}
