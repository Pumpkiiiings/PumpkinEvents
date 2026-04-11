package pumpkin.eventos.games.simondice

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import pumpkin.eventos.PumpkinEventos

class SimonListener(private val plugin: PumpkinEventos) : Listener {

    // --- NUEVO: DETECTAR GOLPES PARA EL RETO "GOLPEAR" ---
    @EventHandler
    fun onHit(e: EntityDamageByEntityEvent) {
        // Obtenemos al atacante
        val p = e.damager as? Player ?: return
        val game = plugin.eventManager.currentGame as? SimonDice ?: return

        // Validaciones: El juego corre, el jugador sigue vivo participando y NO se ha salvado aún.
        if (!game.isRunning || !game.players.contains(p) || game.savedPlayers.contains(p.uniqueId)) return

        // Si el reto activo es golpear, lo salvamos
        if (game.activeChallenge == "GOLPEAR") {
            e.isCancelled = true // Evitamos que maten a alguien accidentalmente

            // Folia/Paper: Aseguramos que los cambios se hagan sincronizados globalmente
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                game.markSaved(p)
            }
        }
    }

    // --- DETECTAR CLIC EN LA ESTRELLA DEL STREAMER ---
    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame as? SimonDice ?: return

        // Verifica si es el streamer asignado
        if (game.mode != SimonMode.MANUAL || game.streamer?.uniqueId != p.uniqueId) return

        if (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK) {
            if (e.item?.type == Material.NETHER_STAR) {
                e.isCancelled = true
                SimonMenu.abrirPanelPrincipal(plugin, p, game)
            }
        }
    }

    // --- DETECTAR CHAT Y RESPUESTAS ---
    @EventHandler
    fun onChat(e: AsyncChatEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame as? SimonDice ?: return
        val mensaje = PlainTextComponentSerializer.plainText().serialize(e.message())

        // 1. INTERCEPTAR INPUT DEL MENÚ DEL STREAMER
        if (SimonMenu.esperandoInput.containsKey(p.uniqueId)) {
            e.isCancelled = true
            val tipo = SimonMenu.esperandoInput.remove(p.uniqueId) ?: return

            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                if (!game.isRunning) return@run

                when (tipo) {
                    "FRASE" -> {
                        game.targetPhrase = mensaje
                        p.sendMessage(plugin.messageManager.parse("<#00FFFF>✎ <b>FRASE LISTA:</b> <white>$mensaje"))
                        game.startChallenge("FRASE", "✎ DIGAN: <white>$mensaje", 15)
                    }
                    "MATES" -> {
                        // Nota: Asumo que tienes MotorMatematico importado / en tu código
                        val resultado = MotorMatematico.evaluar(mensaje)
                        if (resultado == -999999.0) {
                            p.sendMessage(plugin.messageManager.parse("<red>❌ <b>ERROR:</b> ¡Cuenta inválida!</red>"))
                        } else {
                            game.targetPhrase = mensaje
                            game.targetMathResult = resultado
                            p.sendMessage(plugin.messageManager.parse("<#CCFF00>⌗ <b>MATES LISTAS:</b> <white>$mensaje = $resultado"))
                            game.startChallenge("MATES", "⌗ CUÁNTO ES: <white>$mensaje", 15)
                        }
                    }
                }
            }
            return
        }

        // 2. JUGADORES CUMPLIENDO EL RETO
        if (!game.isRunning || !game.players.contains(p) || game.savedPlayers.contains(p.uniqueId)) return

        when (game.activeChallenge) {
            "FRASE" -> {
                if (mensaje.trim().equals(game.targetPhrase.trim(), ignoreCase = true)) {
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> game.markSaved(p) }
                }
            }
            "MATES" -> {
                try {
                    val numeroLimpio = mensaje.replace(Regex("[^0-9.-]"), "")
                    if (numeroLimpio.isNotEmpty()) {
                        val numJugador = numeroLimpio.toDouble()
                        if (Math.abs(numJugador - game.targetMathResult) <= 0.01) {
                            plugin.server.globalRegionScheduler.run(plugin) { _ -> game.markSaved(p) }
                        }
                    }
                } catch (ignored: NumberFormatException) {}
            }
        }
    }
}
