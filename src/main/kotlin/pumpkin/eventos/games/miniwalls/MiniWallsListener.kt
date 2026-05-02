package pumpkin.eventos.games.miniwalls

import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import pumpkin.eventos.PumpkinEventos

class MiniWallsListener(private val plugin: PumpkinEventos, private val game: MiniWalls) : Listener {

    // --- PROTECCIÓN DE CONSTRUCCIÓN ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(e: BlockPlaceEvent) {
        if (!game.isRunning || !game.players.contains(e.player)) return

        if (e.block.y > game.maxHeightLimit) {
            e.isCancelled = true
            e.player.sendMessage(plugin.messageManager.parse("<red>¡Has alcanzado el límite de altura!</red>"))
            return
        }

        game.placedBlocks.add(e.block.location)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onBlockBreak(e: BlockBreakEvent) {
        if (!game.isRunning || !game.players.contains(e.player)) return

        if (game.phase == MwPhase.PREPARING || game.phase == MwPhase.LOBBY) {
            e.isCancelled = true
            return
        }

        val block = e.block

        // DETECCIÓN DE GOLPE AL NEXO (Beacon)
        if (block.type == Material.BEACON) {
            e.isCancelled = true

            val nexoEntry = game.teamNexusLoc.entries.find { it.value.blockX == block.x && it.value.blockZ == block.z }
            if (nexoEntry != null) {
                val teamDefensor = nexoEntry.key
                val hpActual = game.teamNexusHP[teamDefensor] ?: 0

                if (hpActual <= 0) return

                val attackerTeam = game.playerTeam[e.player]
                if (teamDefensor == attackerTeam) {
                    e.player.sendMessage(plugin.messageManager.parse("<red>¡No puedes dañar a tu propio Nexo!</red>"))
                    return
                }

                // SISTEMA DE COOLDOWN DE DAÑO (5 Segundos)
                val now = System.currentTimeMillis()
                val lastHit = game.teamNexusCooldown[teamDefensor] ?: 0L

                if (now - lastHit < 5000) {
                    val restante = ((5000 - (now - lastHit)) / 1000).toInt() + 1
                    e.player.sendActionBar(plugin.messageManager.parse("<red>🛡 Nexo Inmune por ${restante}s</red>"))
                    e.player.playSound(e.player.location, Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f)
                    return
                }

                // APLICAR DAÑO (-2 de Vida)
                val nuevoHp = hpActual - 2
                game.teamNexusHP[teamDefensor] = nuevoHp
                game.teamNexusCooldown[teamDefensor] = now

                block.world.spawnParticle(Particle.CRIT, block.location.add(0.5, 0.5, 0.5), 20, 0.5, 0.5, 0.5)
                block.world.playSound(block.location, Sound.BLOCK_GLASS_HIT, 1f, 0.5f)

                if (nuevoHp <= 0) {
                    block.setType(Material.AIR)
                    block.getRelative(0, 1, 0).setType(Material.AIR)
                    block.world.playSound(block.location, Sound.ENTITY_WITHER_DEATH, 1f, 1f)

                    val teamColor = teamDefensor.chatColor
                    val closeTag  = teamColor.replace("<", "</")
                    plugin.server.broadcast(
                        plugin.messageManager.parse(
                            "<newline>☠ $teamColor<b>¡EL NEXO ${teamDefensor.displayName.uppercase()} HA SIDO DESTRUIDO!</b>$closeTag <white>¡Ya no reaparecerán!</white><newline>"
                        )
                    )
                } else {
                    game.players.filter { game.playerTeam[it] == teamDefensor }.forEach {
                        it.sendTitle("§c§l¡NEXO ATACADO!", "§fQuedan $nuevoHp HP", 5, 40, 10)
                        it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 0.5f)
                    }
                    e.player.sendMessage(plugin.messageManager.parse("<green>🗡 ¡Le has bajado 2 de vida al nexo! <gray>(Espera 5s para volver a pegarle)</gray></green>"))
                }
            }
            return
        }

        if (game.placedBlocks.contains(block.location)) {
            game.placedBlocks.remove(block.location)
            e.isCancelled = false
            e.isDropItems = true
            return
        }

        val breakableBlocks = plugin.config.getStringList("miniwalls.breakable_blocks")
        if (breakableBlocks.contains(block.type.name)) {
            e.isCancelled = false
            e.isDropItems = true
            return
        }

        e.isCancelled = true
        e.player.sendMessage(plugin.messageManager.parse("<red>Solo puedes romper bloques de lana o bloques puestos por jugadores.</red>"))
    }

    // --- MANEJO DE MENÚS Y CLICS ---
    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        if (!game.isRunning || !game.players.contains(p)) return

        if (game.phase == MwPhase.LOBBY) {
            val item = e.item ?: return
            if (item.type.name.endsWith("_WOOL") || item.type == Material.WHITE_WOOL) {
                e.isCancelled = true
                // ¡AQUÍ SE ABRE LA NUEVA CLASE GUI!
                MiniWallsGUI(plugin, game).open(p)
            }
            return
        }

        if (e.action == Action.LEFT_CLICK_BLOCK) {
            val block = e.clickedBlock ?: return
            if (block.type == Material.BEACON) {
                val fakeEvent = BlockBreakEvent(block, p)
                plugin.server.pluginManager.callEvent(fakeEvent)
            }
        }
    }

    // --- BLOQUEO DE DAÑO EN LOBBY Y FRIENDLY FIRE ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(e: EntityDamageEvent) {
        if (!game.isRunning) return
        val p = e.entity as? Player ?: return
        if (!game.players.contains(p)) return

        if (game.phase == MwPhase.LOBBY) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPunch(e: EntityDamageByEntityEvent) {
        if (!game.isRunning) return
        val victim = e.entity as? Player ?: return

        var attacker = e.damager as? Player
        if (attacker == null && e.damager is Arrow) {
            attacker = (e.damager as Arrow).shooter as? Player
        }
        if (attacker == null) return

        if (game.players.contains(victim) && game.players.contains(attacker)) {
            if (game.phase == MwPhase.LOBBY || game.phase == MwPhase.PREPARING) {
                e.isCancelled = true
                return
            }

            if (game.playerTeam[victim] == game.playerTeam[attacker]) {
                e.isCancelled = true
            }
        }
    }

    // --- CURACIÓN POR KILL (Kits) ---
    @EventHandler
    fun onPlayerKill(e: PlayerDeathEvent) {
        if (!game.isRunning) return
        val killer = e.entity.killer ?: return
        if (!game.players.contains(killer)) return

        val kit = game.playerKit[killer] ?: return
        if (kit.healOnKill > 0.0) {
            killer.health = (killer.health + kit.healOnKill).coerceAtMost(
                killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
            )
            killer.playSound(killer.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
            val hearts = (kit.healOnKill / 2).toInt()
            killer.sendMessage(plugin.messageManager.parse("<color:#FF1493>❤ <b>+$hearts Corazones</b></color>"))
        }
    }
}
