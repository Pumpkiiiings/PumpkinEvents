package pumpkin.eventos.games.tntgames

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import pumpkin.eventos.PumpkinEventos

class TntRunListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): TntRun? = plugin.eventManager.currentGame as? TntRun

    @EventHandler
    fun onMove(e: PlayerMoveEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(p)) return

        // Si el jugador se mueve horizontalmente, disuelve el bloque bajo sus pies
        val diffX = Math.abs(e.to.x - e.from.x)
        val diffZ = Math.abs(e.to.z - e.from.z)
        if (diffX < 0.01 && diffZ < 0.01) return

        val below = p.location.clone().subtract(0.0, 1.0, 0.0).block
        if (below.type != Material.AIR && below.type.isSolid) {
            plugin.server.regionScheduler.runDelayed(plugin, below.location, { _ ->
                if (!game.isRunning) return@runDelayed
                val belowBlock = below.location.block
                if (belowBlock.type != Material.AIR) {
                    belowBlock.type = Material.AIR
                    p.world.spawnParticle(org.bukkit.Particle.BLOCK, belowBlock.location.add(0.5, 1.0, 0.5),
                        10, 0.3, 0.1, 0.3, belowBlock.blockData)
                }
            }, 10L)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        // En TntRun todo el daño excepto el vacío se cancela
        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.isCancelled = true
        }
    }
}
