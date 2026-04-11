package pumpkin.eventos.listeners

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
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
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.lava.SueloLava
import pumpkin.eventos.games.luzroja.LuzEstado
import pumpkin.eventos.games.luzroja.LuzRojaLuzVerde
import pumpkin.eventos.games.pillars.PillarsOfFortune
import pumpkin.eventos.games.simondice.SimonDice
import pumpkin.eventos.games.spleef.Spleef
import pumpkin.eventos.games.sumo.Sumo
import pumpkin.eventos.games.tntgames.TntSpleef
import pumpkin.eventos.games.tntgames.TntTag

class GameListener(private val plugin: PumpkinEventos) : Listener {

    // --- PROTECCIÓN DE BLOQUES ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(e: BlockPlaceEvent) {
        val game = plugin.eventManager.currentGame
        if (e.player.hasPermission("pumpkin.admin")) return

        val allowedGames = game is SueloLava || game is PillarsOfFortune
        if (game == null || !game.isRunning || !allowedGames || (game is PillarsOfFortune && game.isPreparation)) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(e: BlockBreakEvent) {
        val game = plugin.eventManager.currentGame
        if (e.player.hasPermission("pumpkin.admin")) return

        if (game is Spleef && game.isRunning && !game.isPreparation) {
            if (e.block.type == Material.SNOW_BLOCK || e.block.type == Material.SNOW) {
                e.isDropItems = false
                return
            }
        }

        val allowedGames = game is PillarsOfFortune
        if (game == null || !game.isRunning || !allowedGames || (game is PillarsOfFortune && game.isPreparation)) {
            e.isCancelled = true
        }
    }

    // --- DETECCIÓN DE MOVIMIENTO ---
    @EventHandler
    fun onMove(e: PlayerMoveEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame ?: return
        if (!game.isRunning || !game.players.contains(p)) return

        if (game is LuzRojaLuzVerde) {
            if (game.isPlayerSaved(p)) return

            // Verificación Dinámica de cruce de meta
            if (game.hasReachedMeta(e.to)) {
                game.markWinner(p)
                return
            }

            if (game.estado == LuzEstado.ROJA) {
                // Cálculo de movimiento real (Ignorar giros de cámara)
                val diffX = Math.abs(e.to.x - e.from.x)
                val diffZ = Math.abs(e.to.z - e.from.z)

                // Si el jugador realmente caminó (tolerancia de 0.03 bloques por tick)
                if (diffX > 0.03 || diffZ > 0.03) {
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> game.killPlayer(p) }
                }
            }
            return
        }

        if (game is SimonDice) {
            if (game.activeChallenge == "SALTAR" && e.to.y > e.from.y && !p.location.block.getRelative(BlockFace.DOWN).type.isAir) game.markSaved(p)

            val diffX = Math.abs(e.to.x - e.from.x)
            val diffZ = Math.abs(e.to.z - e.from.z)

            if (diffX > 0.03 || diffZ > 0.03) {
                if (game.activeChallenge == "CAMINAR") game.markSaved(p)
                if (game.activeChallenge == "QUIETO") {
                    if (game.savedPlayers.contains(p.uniqueId)) {
                        game.savedPlayers.remove(p.uniqueId)
                        p.isGlowing = false
                        p.sendMessage(plugin.messageManager.parse("<red>⚠️ <b>¡TE MOVISTE!</b></red> <white>Has perdido tu salvación."))
                    }
                }
            }
        }
    }

    @EventHandler
    fun onSneak(e: PlayerToggleSneakEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame as? SimonDice ?: return
        if (!game.isRunning || !game.players.contains(p)) return
        if (e.isSneaking && game.activeChallenge == "AGACHARSE") game.markSaved(p)
    }

    // --- NUEVO SISTEMA DE FLECHAS PARA TNT SPLEEF ---
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

                    // Queremos que caigan entre 2 y 5 TNTs en total (la principal + vecinas)
                    val totalTntsToFall = (2..5).random()
                    var convertedTnts = 0

                    // Función interna para crear la animación de TNT falsa que cae
                    fun convertToFallingTnt(targetBlock: org.bukkit.block.Block) {
                        if (targetBlock.type == Material.TNT) {
                            targetBlock.type = Material.AIR // Borramos el bloque

                            // Spawneamos la entidad de TNT encendida
                            val tnt = world.spawn(targetBlock.location.add(0.5, 0.0, 0.5), TNTPrimed::class.java)

                            // 100 ticks = 5 segundos (Tiempo suficiente para que caiga al vacío y muera sola)
                            tnt.fuseTicks = 100

                            // Evitamos que "salte" como la TNT normal, forzamos a que caiga recta
                            tnt.velocity = Vector(0.0, -0.1, 0.0)

                            convertedTnts++
                        }
                    }

                    // 1. Convertimos la TNT a la que le dio la flecha directamente
                    convertToFallingTnt(block)

                    // 2. Buscamos TNTs alrededor (solo en el mismo piso X/Z)
                    val neighbors = mutableListOf<org.bukkit.block.Block>()
                    for (x in -1..1) {
                        for (z in -1..1) {
                            if (x == 0 && z == 0) continue // Ignorar el centro que ya procesamos
                            val nearBlock = world.getBlockAt(centerLoc.blockX + x, centerLoc.blockY, centerLoc.blockZ + z)
                            if (nearBlock.type == Material.TNT) {
                                neighbors.add(nearBlock)
                            }
                        }
                    }

                    // 3. Desordenamos los vecinos para que sea un patrón aleatorio y los dejamos caer
                    neighbors.shuffle()
                    for (nearBlock in neighbors) {
                        if (convertedTnts >= totalTntsToFall) break // Paramos si ya llegamos al límite
                        convertToFallingTnt(nearBlock)
                    }

                    // Un solo sonido de encendido para no aturdir
                    world.playSound(centerLoc, Sound.ENTITY_TNT_PRIMED, 1f, 1f)
                }
            }
        }
    }

    // --- BLOQUEADOR DE EXPLOSIONES (ANTI-CRASH / ANTI-LAG) ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onExplosion(e: EntityExplodeEvent) {
        val game = plugin.eventManager.currentGame

        // Si estamos en TNT Spleef, CANCELAMOS la explosión. Las TNT solo deben ser visuales.
        if (game is TntSpleef) {
            e.blockList().clear() // Evita que se rompan bloques
            e.isCancelled = true  // Cancela daño y físicas
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPunch(e: EntityDamageByEntityEvent) {
        val attacker = e.damager as? Player ?: return
        val victim = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame ?: return

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

        if (game is PillarsOfFortune && game.isRunning && !game.isPreparation) {
            e.isCancelled = false
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame ?: return

        if (game is PillarsOfFortune) {
            if (game.isPreparation) {
                e.isCancelled = true
            } else {
                e.isCancelled = false
            }
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
                e.isCancelled = true // Aseguramos cancelar el daño por si acaso
                return
            }
        }

        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.damage = 0.001
        }
    }

    // --- INTERACT Y DROPS ---
    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        if (e.hand != EquipmentSlot.HAND) return
        if (!e.action.isRightClick) return

        val player = e.player
        val item = e.item ?: return
        val currentGame = plugin.eventManager.currentGame ?: return

        if (currentGame is SimonDice && currentGame.mode == pumpkin.eventos.games.simondice.SimonMode.MANUAL && item.type == Material.NETHER_STAR) return

        if (!currentGame.isRunning) return

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
        val game = plugin.eventManager.currentGame
        if (game != null && game.isRunning && game !is PillarsOfFortune) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLethalDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame ?: return

        if (game.isRunning) {
            if (player.health - e.finalDamage <= 0) {
                e.isCancelled = true
                player.health = 20.0
                player.fireTicks = 0
                player.world.spawnParticle(org.bukkit.Particle.SOUL, player.location, 20, 0.5, 1.0, 0.5, 0.1)

                game.eliminate(player)
            }
        }
    }
}
