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
import pumpkin.eventos.games.miniwalls.MiniWalls
import pumpkin.eventos.games.hideandseek.HideAndSeek
import pumpkin.eventos.games.lava.SueloLava
import pumpkin.eventos.games.battleroyale.BattleRoyale
import pumpkin.eventos.games.battleroyale.BRPhase
import pumpkin.eventos.games.buildbattle.BuildBattle

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
        if (game is BattleRoyale && game.phase != BRPhase.PLAYING) { e.isCancelled = true; return }
        if (game is PillarsOfFortune || game is Spleef || game is Skywars || game is MiniWalls || game is SueloLava || game is BattleRoyale || game is BuildBattle) return
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
        if (game is BattleRoyale && game.phase != BRPhase.PLAYING) { e.isCancelled = true; return }
        // Modos con drops vanilla — se permite romper y se mantiene isDropItems = true
        if (game is PillarsOfFortune || game is Skywars || game is MiniWalls || game is SueloLava || game is BattleRoyale || game is BuildBattle) return
        e.isCancelled = true
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning) return
        if (game !is PillarsOfFortune && game !is Skywars && game !is BattleRoyale) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onLethalDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning) return
        if (!game.players.contains(player)) return

        // Si el PvP está desactivado por votación, cancelamos cualquier daño entre jugadores
        if (!game.pvpEnabled && e is EntityDamageByEntityEvent) {
            val damager = e.damager
            val isPlayerHit = damager is Player || (damager is Projectile && damager.shooter is Player)
            if (isPlayerHit) {
                e.isCancelled = true
                return
            }
        }

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
            if (game is PillarsOfFortune || game is Skywars || game is BattleRoyale) {
                val loc = player.location
                player.inventory.contents.forEach { item ->
                    if (item != null && item.type != Material.AIR) {
                        val droppedItem = loc.world.dropItemNaturally(loc, item)
                        droppedItem.pickupDelay = 40
                    }
                }
            }

            // =====================================
            // SISTEMA UNIVERSAL DE MUERTES
            // =====================================
            val mm = plugin.messageManager

            if (killer != null && killer != player && game.players.contains(killer)) {
                plugin.puntajeManager.addPoints(killer, 5, "Asesinato")
                
                val cause = player.lastDamageCause?.cause ?: e.cause
                var msgs = listOf(
                    "<#FF3131>⚔ <yellow>${killer.name}</yellow> <gray>asesinó brutalmente a</gray> <red>${player.name}</red></#FF3131>",
                    "<#FF3131>⚔ <red>${player.name}</red> <gray>fue destrozado por</gray> <yellow>${killer.name}</yellow></#FF3131>",
                    "<#FF3131>⚔ <yellow>${killer.name}</yellow> <gray>mandó al lobby a</gray> <red>${player.name}</red></#FF3131>"
                )
                var msg = msgs.random()

                if (cause == EntityDamageEvent.DamageCause.VOID) {
                    msg = "<#FF3131>☠ <yellow>${killer.name}</yellow> <gray>empujó al vacío a</gray> <red>${player.name}</red></#FF3131>"
                } else if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
                    msg = "<#FF3131>🏹 <yellow>${killer.name}</yellow> <gray>acertó un flechazo mortal a</gray> <red>${player.name}</red></#FF3131>"
                }
                plugin.server.broadcast(mm.parse(msg))

                // Fix para curación de MiniWalls
                if (game is MiniWalls) {
                    val kit = game.playerKit[killer]
                    if (kit != null && kit.healOnKill > 0.0) {
                        killer.health = (killer.health + kit.healOnKill).coerceAtMost(killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0)
                        killer.playSound(killer.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                        val hearts = (kit.healOnKill / 2).toInt()
                        killer.sendMessage(mm.parse("<#FF1493>❤ <b>+$hearts Corazones</b></#FF1493>"))
                    }
                }

            } else {
                val isVoid = player.location.y <= (game.gameWorld?.minHeight ?: -64) + 10
                val cause = if (isVoid) EntityDamageEvent.DamageCause.VOID else (player.lastDamageCause?.cause ?: e.cause)
                
                val msg = when {
                    cause == EntityDamageEvent.DamageCause.LAVA -> "<#FF3131>🔥 <red>${player.name}</red> <gray>se fundió en la lava.</gray></#FF3131>"
                    cause == EntityDamageEvent.DamageCause.VOID || isVoid -> "<#FF3131>☠ <red>${player.name}</red> <gray>cayó al vacío infinito.</gray></#FF3131>"
                    cause == EntityDamageEvent.DamageCause.FALL -> "<#FF3131>☠ <red>${player.name}</red> <gray>se estrelló contra el suelo.</gray></#FF3131>"
                    cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK -> "<#FF3131>🔥 <red>${player.name}</red> <gray>murió calcinado.</gray></#FF3131>"
                    else -> "<#FF3131>☠ <red>${player.name}</red> <gray>murió de forma misteriosa.</gray></#FF3131>"
                }
                plugin.server.broadcast(mm.parse(msg))
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

        if (game is BattleRoyale) {
            if (game.phase == BRPhase.TEAM_SELECT) e.isCancelled = true
            return
        }

        if (e.cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
            e.cause == EntityDamageEvent.DamageCause.FALLING_BLOCK) {
            e.isCancelled = true; return
        }

        // MiniWalls, HideAndSeek, PillarsOfFortune y SueloLava manejan su propio daño
        if (game is MiniWalls || game is HideAndSeek || game is PillarsOfFortune || game is SueloLava) return

        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.damage = 0.001
        }
    }
}


