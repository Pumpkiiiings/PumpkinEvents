package pumpkin.eventos.games.spleef

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageEvent
import pumpkin.eventos.PumpkinEventos

class SpleefListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): Spleef? = plugin.eventManager.currentGame as? Spleef

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(e: BlockBreakEvent) {
        val player = e.player
        val game = game() ?: return
        if (!game.isRunning) return

        if (game.isPreparation) { e.isCancelled = true; return }

        if (!game.players.contains(player)) { e.isCancelled = true; return }

        val block = e.block
        if (block.type == Material.SNOW_BLOCK || block.type == Material.SNOW) {
            e.isDropItems = false
            block.world.spawnParticle(org.bukkit.Particle.ITEM_SNOWBALL, block.location.add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, 0.05)
            block.world.playSound(block.location, Sound.BLOCK_SNOW_BREAK, 1f, 1f)
            return // Permitir la rotura
        }
        // Cualquier otro bloque no se puede romper
        e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.isCancelled = true
        }
    }
}
