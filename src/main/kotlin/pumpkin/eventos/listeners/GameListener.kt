package pumpkin.eventos.listeners

import dev.geco.gsit.api.GSitAPI
import dev.geco.gsit.api.event.EntityStopSitEvent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.vehicle.VehicleExitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.corona.RobaLaCorona
import pumpkin.eventos.games.cristales.Cristales
import pumpkin.eventos.games.iceboat.IceBoatRacing
import pumpkin.eventos.games.jalarcuerda.JalarCuerda
import pumpkin.eventos.games.lava.SueloLava
import pumpkin.eventos.games.luzroja.LuzEstado
import pumpkin.eventos.games.luzroja.LuzRojaLuzVerde
import pumpkin.eventos.games.pillars.PillarsOfFortune
import pumpkin.eventos.games.ruletarusa.RuletaRusa
import pumpkin.eventos.games.sillas.SillasMusicales
import pumpkin.eventos.games.simondice.SimonDice
import pumpkin.eventos.games.skywars.Skywars
import pumpkin.eventos.games.skywars.SwPhase
import pumpkin.eventos.games.spleef.Spleef
import pumpkin.eventos.games.sumo.Sumo
import pumpkin.eventos.games.tntgames.TntSpleef
import pumpkin.eventos.games.tntgames.TntTag

class GameListener(private val plugin: PumpkinEventos) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(e: BlockPlaceEvent) {
        val game = plugin.eventManager.currentGame
        if (e.player.hasPermission("pumpkin.admin")) return
        if (game == null || !game.isRunning) { e.isCancelled = true; return }

        if (game is PillarsOfFortune && game.isPreparation) { e.isCancelled = true; return }

        // CORRECCIÓN SKYWARS: Eliminado .DEATHMATCH que no existe en SwPhase
        if (game is Skywars && game.phase != SwPhase.PLAYING) { e.isCancelled = true; return }

        if (game is PillarsOfFortune || game is SueloLava || game is Skywars) return
        e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(e: BlockBreakEvent) {
        val game = plugin.eventManager.currentGame
        if (e.player.hasPermission("pumpkin.admin")) return
        if (game == null || !game.isRunning) { e.isCancelled = true; return }

        if (game is Spleef && !game.isPreparation) {
            if (e.block.type == Material.SNOW_BLOCK || e.block.type == Material.SNOW) {
                e.isDropItems = false
                return
            }
        }

        if (game is PillarsOfFortune && game.isPreparation) { e.isCancelled = true; return }

        // CORRECCIÓN SKYWARS: Eliminado .DEATHMATCH
        if (game is Skywars && game.phase != SwPhase.PLAYING) { e.isCancelled = true; return }

        if (game is PillarsOfFortune || game is Skywars) return
        e.isCancelled = true
    }

    @EventHandler
    fun onMove(e: PlayerMoveEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning || !game.players.contains(p)) return

        if (game is LuzRojaLuzVerde) {
            if (game.isPlayerSaved(p)) return
            if (game.hasReachedMeta(e.to)) { game.markWinner(p); return }

            if (game.estado == LuzEstado.ROJA) {
                val diffX = Math.abs(e.to.x - e.from.x)
                val diffZ = Math.abs(e.to.z - e.from.z)

                if (diffX > 0.05 || diffZ > 0.05) {
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> game.killPlayer(p) }
                }
            }
            return
        }
    }

    @EventHandler
    fun onSneak(e: PlayerToggleSneakEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning || !game.players.contains(p)) return

        if (game is SillasMusicales && game.jugadoresSentados.contains(p)) {
            e.isCancelled = true
            return
        }

        if (game is RuletaRusa && game.isRunning) {
            if (game.camarasTemporales.containsKey(p) || (game.forzarAsientos && game.jugadoresSentados.containsKey(p))) {
                e.isCancelled = true
                return
            }
        }
    }

    @EventHandler
    fun onProjectileHit(e: ProjectileHitEvent) {
        val proj = e.entity
        val game = plugin.eventManager.currentGame

        if (game is TntSpleef && game.isRunning && proj is Arrow) {
            val block = e.hitBlock ?: return

            if (block.type == Material.TNT) {
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
                    for (x in -1..1) {
                        for (z in -1..1) {
                            if (x == 0 && z == 0) continue
                            val nearBlock = world.getBlockAt(centerLoc.blockX + x, centerLoc.blockY, centerLoc.blockZ + z)
                            if (nearBlock.type == Material.TNT) {
                                neighbors.add(nearBlock)
                            }
                        }
                    }

                    neighbors.shuffle()
                    for (nearBlock in neighbors) {
                        if (convertedTnts >= totalTntsToFall) break
                        convertToFallingTnt(nearBlock)
                    }

                    world.playSound(centerLoc, Sound.ENTITY_TNT_PRIMED, 1f, 1f)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onExplosion(e: EntityExplodeEvent) {
        val game = plugin.eventManager.currentGame
        if (game is TntSpleef) {
            e.blockList().clear()
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPunch(e: EntityDamageByEntityEvent) {
        val attacker = e.damager as? Player ?: return
        val victim = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame ?: return

        // --- EXCEPCIÓN: DAÑO REAL EN COMBATE ---
        if (game is PillarsOfFortune) {
            if (game.isPreparation) e.isCancelled = true
            return
        }
        if (game is Skywars) {
            if (game.phase == SwPhase.TEAM_SELECT || game.phase == SwPhase.VOTING) e.isCancelled = true
            // Bloqueo de Friendly Fire en Duos
            if (game.isDuos && game.playerTeam[attacker] == game.playerTeam[victim]) e.isCancelled = true
            return
        }

        // --- BLOQUEOS TOTALES ---
        if (game is RuletaRusa || game is JalarCuerda) {
            e.isCancelled = true
            return
        }
        if (game is RobaLaCorona) return // RobaLaCorona maneja su propio PVP interno en su clase

        // --- GOLPES DE EMPUJE (Daño 0.001) ---
        if (game is SillasMusicales || game is Cristales) {
            e.damage = 0.001
            return
        }

        if (game is TntTag && game.isRunning) {
            e.damage = 0.001
            game.handlePunch(attacker, victim)
            return
        }

        if (game is Sumo && game.isRunning) {
            if (game.isLuchador(attacker) && game.isLuchador(victim) && game.rondaActiva) {
                e.damage = 0.001
            } else {
                e.isCancelled = true
            }
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame ?: return

        // Excepciones para Daño Real Completo
        if (game is PillarsOfFortune) {
            if (game.isPreparation) e.isCancelled = true
            return
        }
        if (game is Skywars) {
            if (game.phase == SwPhase.TEAM_SELECT || game.phase == SwPhase.VOTING) e.isCancelled = true
            return
        }

        if (game is JalarCuerda || game is IceBoatRacing) {
            e.isCancelled = true
            return
        }

        if (e.cause == EntityDamageEvent.DamageCause.SUFFOCATION || e.cause == EntityDamageEvent.DamageCause.FALLING_BLOCK) {
            e.isCancelled = true
            return
        }

        if (game is Sumo) {
            if (e.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && e.cause != EntityDamageEvent.DamageCause.VOID) {
                e.isCancelled = true
            }
            return
        }

        if (game is TntSpleef) {
            if (e.cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || e.cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                e.damage = 0.001
                e.isCancelled = true
                return
            }
        }

        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.damage = 0.001
        }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val player = e.player
        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning) return

        // --- EXCEPCIÓN PRIORITARIA PARA COMER Y COFRES ---
        if (game is PillarsOfFortune) {
            if (game.isPreparation) e.isCancelled = true
            return
        }
        if (game is Skywars) {
            if (game.phase == SwPhase.TEAM_SELECT || game.phase == SwPhase.VOTING) e.isCancelled = true
            return
        }

        if (e.hand != EquipmentSlot.HAND) return

        if (game is RuletaRusa) {
            if (e.action.isRightClick && e.item?.type == Material.IRON_HORSE_ARMOR) {
                e.isCancelled = true
                game.procesarEleccion(player, true)
                return
            }
        }

        if (game is SillasMusicales) {
            val clickedBlock = e.clickedBlock
            if (clickedBlock != null && e.action == Action.RIGHT_CLICK_BLOCK) {
                e.isCancelled = true
                game.intentarSentarse(player, clickedBlock.location)
                return
            }
        }

        val item = e.item ?: return
        if (game is SimonDice && game.mode == pumpkin.eventos.games.simondice.SimonMode.MANUAL && item.type == Material.NETHER_STAR) return

        val key = NamespacedKey(plugin, "tnt_item")
        val type = item.itemMeta?.persistentDataContainer?.get(key, PersistentDataType.STRING) ?: return

        e.isCancelled = true

        when (type) {
            "dash" -> {
                if (player.hasCooldown(Material.SUGAR)) return
                player.setCooldown(Material.SUGAR, 600)
                player.velocity = player.location.direction.normalize().multiply(1.8).setY(0.3)
                player.playSound(player.location, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.5f)
            }
            "doublejump" -> {
                if (player.hasCooldown(Material.RABBIT_FOOT)) return
                player.setCooldown(Material.RABBIT_FOOT, 600)
                player.velocity = player.location.direction.multiply(0.5).setY(1.3)
                player.playSound(player.location, Sound.ENTITY_BAT_TAKEOFF, 1f, 1f)
            }
            "feather" -> {
                if (player.hasCooldown(Material.FEATHER)) return
                player.setCooldown(Material.FEATHER, 60)
                player.velocity = player.location.direction.multiply(0.5).setY(1.3)
                player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.2f)
                player.world.spawnParticle(org.bukkit.Particle.CLOUD, player.location, 15, 0.2, 0.1, 0.2, 0.05)
            }
            "pearl" -> player.playSound(player.location, Sound.ENTITY_ENDER_PEARL_THROW, 1f, 1f)
            "gapple" -> e.isCancelled = false
        }
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning) return
        val player = e.player

        if (game is RuletaRusa) {
            e.isCancelled = true
            if (e.itemDrop.itemStack.type == Material.IRON_HORSE_ARMOR) {
                game.procesarEleccion(player, false)
            }
            return
        }

        // Permitimos drop en juegos de supervivencia
        if (game !is PillarsOfFortune && game !is Skywars) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGSitStop(e: EntityStopSitEvent) {
        val player = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame ?: return

        if (game is SillasMusicales && game.isRunning && game.jugadoresSentados.contains(player)) {
            val block = e.seat.block
            plugin.server.regionScheduler.runDelayed(plugin, block.location, { _ ->
                GSitAPI.createSeat(block, player)
                player.sendMessage(plugin.messageManager.parse("<red>¡No te puedes levantar hasta que inicie la siguiente ronda!</red>"))
            }, 1L)
        }

        if (game is RuletaRusa && game.isRunning && game.forzarAsientos) {
            if (game.jugadoresSentados.containsKey(player)) {
                val loc = game.jugadoresSentados[player] ?: return
                plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                    GSitAPI.createSeat(loc.block, player)
                }, 1L)
            }
        }
    }

    @EventHandler
    fun onDismount(e: VehicleExitEvent) {
        val player = e.exited as? Player ?: return
        val game = plugin.eventManager.currentGame ?: return

        if (game is SillasMusicales && game.isRunning) {
            if (game.jugadoresSentados.contains(player)) {
                e.isCancelled = true
            }
        }

        if (game is RuletaRusa && game.isRunning) {
            if (game.camarasTemporales.containsKey(player) || (game.forzarAsientos && game.jugadoresSentados.containsKey(player))) {
                e.isCancelled = true
            }
        }

        if (game is IceBoatRacing && game.isRunning) {
            e.isCancelled = true // Imposible bajarse del bote en carreras
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLethalDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame ?: return

        if (game.isRunning) {
            if (player.health - e.finalDamage <= 0) {

                val mainHand = player.inventory.itemInMainHand.type
                val offHand = player.inventory.itemInOffHand.type

                if (mainHand == Material.TOTEM_OF_UNDYING || offHand == Material.TOTEM_OF_UNDYING) {
                    return
                }

                e.isCancelled = true
                player.health = 20.0
                player.fireTicks = 0
                player.world.spawnParticle(org.bukkit.Particle.SOUL, player.location, 20, 0.5, 1.0, 0.5, 0.1)

                var killer: Player? = null
                if (e is EntityDamageByEntityEvent) {
                    val damager = e.damager
                    if (damager is Player) {
                        killer = damager
                    } else if (damager is Projectile) {
                        killer = damager.shooter as? Player
                    }
                }

                if (killer != null && killer != player && game.players.contains(killer)) {
                    killer.playSound(killer.location, Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1.5f)
                }

                // Soltar todo el inventario si es juego de supervivencia (Pillars o Skywars)
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
    }
}
