package pumpkin.eventos.games.tntgames

import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.util.Vector
import org.bukkit.Material
import pumpkin.eventos.PumpkinEventos

class TntSpleefListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): TntSpleef? = plugin.eventManager.currentGame as? TntSpleef

    @EventHandler
    fun onProjectileHit(e: ProjectileHitEvent) {
        val proj = e.entity
        val game = game() ?: return
        if (!game.isRunning || proj !is Arrow) return

        val block = e.hitBlock ?: return
        if (block.type != Material.TNT) return

        proj.remove()
        plugin.server.regionScheduler.run(plugin, block.location) { _ ->
            val world = block.world
            val centerLoc = block.location
            val totalTntsToFall = (2..5).random()
            var convertedTnts = 0

            fun convertToFallingTnt(targetBlock: org.bukkit.block.Block) {
                if (targetBlock.type == Material.TNT) {
                    targetBlock.type = Material.AIR
                    val tnt = world.spawn(targetBlock.location.add(0.5, 0.0, 0.5), TNTPrimed::class.java)
                    tnt.fuseTicks = 100
                    tnt.velocity = Vector(0.0, -0.1, 0.0)
                    convertedTnts++
                }
            }

            convertToFallingTnt(block)
            val neighbors = mutableListOf<org.bukkit.block.Block>()
            for (x in -1..1) for (z in -1..1) {
                if (x == 0 && z == 0) continue
                val near = world.getBlockAt(centerLoc.blockX + x, centerLoc.blockY, centerLoc.blockZ + z)
                if (near.type == Material.TNT) neighbors.add(near)
            }
            neighbors.shuffle()
            for (nb in neighbors) {
                if (convertedTnts >= totalTntsToFall) break
                convertToFallingTnt(nb)
            }
            world.playSound(centerLoc, Sound.ENTITY_TNT_PRIMED, 1f, 1f)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onExplosion(e: EntityExplodeEvent) {
        val game = game() ?: return
        e.blockList().clear()
        e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        if (e.cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
            e.cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            e.damage = 0.001
            e.isCancelled = true
        }
    }
}
