package pumpkin.eventos.listeners

import org.bukkit.Material
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.util.Vector
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.pillars.PillarsOfFortune
import pumpkin.eventos.games.skywars.Skywars
import pumpkin.eventos.games.skywars.SwPhase
import pumpkin.eventos.games.spleef.Spleef

/**
 * Listener global que maneja eventos comunes a todos los juegos:
 * - Cancelar bloques en juegos sin construcción
 * - Prevenir muerte real (convertir en eliminación)
 * - Cancelar drop global
 */
class GlobalDamageListener(private val plugin: PumpkinEventos) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(e: BlockPlaceEvent) {
        val game = plugin.eventManager.currentGame
        if (e.player.hasPermission("pumpkin.admin")) return
        if (game == null || !game.isRunning) { e.isCancelled = true; return }
        if (game is PillarsOfFortune && game.isPreparation) { e.isCancelled = true; return }
        if (game is Skywars && game.phase != SwPhase.PLAYING) { e.isCancelled = true; return }
        if (game is PillarsOfFortune || game is Spleef || game is Skywars) return
        e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(e: BlockBreakEvent) {
        val game = plugin.eventManager.currentGame
        if (e.player.hasPermission("pumpkin.admin")) return
        if (game == null || !game.isRunning) { e.isCancelled = true; return }
        if (game is Spleef && !game.isPreparation) {
            if (e.block.type == Material.SNOW_BLOCK || e.block.type == Material.SNOW) {
                e.isDropItems = false; return
            }
        }
        if (game is PillarsOfFortune && game.isPreparation) { e.isCancelled = true; return }
        if (game is Skywars && game.phase != SwPhase.PLAYING) { e.isCancelled = true; return }
        if (game is PillarsOfFortune || game is Skywars) return
        e.isCancelled = true
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning) return
        if (game !is PillarsOfFortune && game !is Skywars) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onLethalDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning) return
        if (!game.players.contains(player)) return

        if (player.health - e.finalDamage <= 0) {
            val mainHand = player.inventory.itemInMainHand.type
            val offHand = player.inventory.itemInOffHand.type
            if (mainHand == Material.TOTEM_OF_UNDYING || offHand == Material.TOTEM_OF_UNDYING) return

            e.isCancelled = true
            player.health = 20.0
            player.fireTicks = 0
            player.world.spawnParticle(org.bukkit.Particle.SOUL, player.location, 20, 0.5, 1.0, 0.5, 0.1)

            var killer: Player? = null
            if (e is EntityDamageByEntityEvent) {
                val damager = e.damager
                killer = when (damager) {
                    is Player -> damager
                    is Projectile -> damager.shooter as? Player
                    else -> null
                }
            }

            if (killer != null && killer != player && game.players.contains(killer)) {
                killer.playSound(killer.location, org.bukkit.Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1.5f)
            }

            // Drop inventario en juegos de supervivencia
            if (game is PillarsOfFortune || game is Skywars) {
                val loc = player.location
                player.inventory.contents.forEach { item ->
                    if (item != null && item.type != Material.AIR) {
                        val droppedItem = loc.world.dropItemNaturally(loc, item)
                        droppedItem.pickupDelay = 40
                    }
                }
            }

            game.eliminate(player)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame ?: return
        if (!game.players.contains(player)) return

        if (game is Skywars) {
            if (game.phase == SwPhase.TEAM_SELECT || game.phase == SwPhase.VOTING) e.isCancelled = true
            return
        }

        if (e.cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
            e.cause == EntityDamageEvent.DamageCause.FALLING_BLOCK) {
            e.isCancelled = true; return
        }

        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.damage = 0.001
        }
    }
}
