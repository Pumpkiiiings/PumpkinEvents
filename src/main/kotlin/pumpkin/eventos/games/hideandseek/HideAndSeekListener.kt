package pumpkin.eventos.games.hideandseek

import me.libraryaddict.disguise.disguisetypes.DisguiseType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import pumpkin.eventos.PumpkinEventos

class HideAndSeekListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): HideAndSeek? = plugin.eventManager.currentGame as? HideAndSeek

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(e: EntityDamageByEntityEvent) {
        val game = game() ?: return
        if (!game.isRunning || game.isPreparation) {
            e.isCancelled = true
            return
        }

        val attacker = e.damager as? Player ?: return

        // 1. GOLPES ENTRE JUGADORES
        val victimPlayer = e.entity as? Player
        if (victimPlayer != null) {

            // --- PVP LIBERADO ---
            // Si el atacante es cazador y la víctima es escondite -> SÍ DAÑO
            // Si el atacante es escondite y la víctima es cazador -> SÍ DAÑO
            if ((game.cazadores.contains(attacker) && game.escondites.contains(victimPlayer)) ||
                (game.escondites.contains(attacker) && game.cazadores.contains(victimPlayer))) {

                // Si el escondido muere, lo transformamos
                if (game.escondites.contains(victimPlayer)) {
                    if (victimPlayer.health - e.finalDamage <= 0) {
                        e.isCancelled = true // Evitamos la muerte real de Bukkit
                        victimPlayer.health = victimPlayer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            game.convertToHunter(victimPlayer)
                        }
                    }
                }

                // Si el cazador muere a manos del escondido, lo reaparecemos
                if (game.cazadores.contains(victimPlayer)) {
                    if (victimPlayer.health - e.finalDamage <= 0) {
                        e.isCancelled = true
                        victimPlayer.health = victimPlayer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            // Teleport seguro (Lo mandamos lejos de los escondidos para que no se vengue de inmediato)
                            victimPlayer.teleportAsync(game.currentArena?.spawnPoints?.firstOrNull()?.clone()?.apply { world = victimPlayer.world } ?: victimPlayer.location)
                            victimPlayer.sendMessage(plugin.messageManager.parse("<red>¡Fuiste asesinado por un animal furioso! Has reaparecido en el spawn.</red>"))
                        }
                    }
                }

                return // Dejamos que el daño aplique
            }

            // Bloquear Fuego Amigo (Cazador a Cazador o Escondite a Escondite)
            e.isCancelled = true
            return
        }

        // 2. CAZADOR GOLPEA A UN DECOY (Animal Falso)
        if (game.cazadores.contains(attacker) && game.spawnedDecoys.contains(e.entity)) {
            e.isCancelled = true // Evitamos que suelte carne/ítems
            val decoy = e.entity
            game.spawnedDecoys.remove(decoy)

            plugin.server.regionScheduler.run(plugin, decoy.location) { _ ->
                decoy.remove()
                decoy.world.spawnParticle(Particle.ANGRY_VILLAGER, decoy.location, 15)

                // Penalización: Lentitud 3 por 3 segundos
                attacker.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 20 * 3, 2, false, false))
                attacker.playSound(attacker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                attacker.sendMessage(plugin.messageManager.parse(plugin.languageManager.get("hideandseek.decoy_penalty").ifEmpty { "<red>¡Ese era un mob real! Penalización de 3 segundos de lentitud.</red>" }))
            }
            return
        }

        // 3. CAZADOR GOLPEA MOB REAL (no decoy, no jugador) → cancelar sin mensaje
        if (e.entity !is Player) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val game = game() ?: return
        if (!game.isRunning) return

        val player = e.entity as? Player ?: return
        if (!game.players.contains(player)) return

        if (game.isPreparation) {
            e.isCancelled = true
            return
        }

        if (e is EntityDamageByEntityEvent) return // Manejado en la función superior

        if (game.escondites.contains(player)) {
            if (player.health - e.finalDamage <= 0) {
                e.isCancelled = true
                player.health = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    game.convertToHunter(player)
                }
            }
        }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning) return
        if (e.hand != EquipmentSlot.HAND) return

        val item = e.item

        if (game.cazadores.contains(p) && item?.type == Material.ENDER_EYE && (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK)) {
            e.isCancelled = true
            if (game.isPreparation) return

            val now = System.currentTimeMillis()
            val cooldown = plugin.config.getLong("hideandseek.eye_cooldown_seconds", 30) * 1000 // Cooldown de 30s
            val lastUse = game.hunterEyeCooldown[p] ?: 0L

            if (now - lastUse < cooldown) {
                val timeLeft = ((cooldown - (now - lastUse)) / 1000).toInt()
                p.sendMessage(plugin.messageManager.parse("<red>El ojo aún está cargando... faltan ${timeLeft}s.</red>"))
                return
            }

            game.hunterEyeCooldown[p] = now
            val duration = plugin.config.getLong("hideandseek.eye_duration_seconds", 5) * 1000

            p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.5f)
            p.sendMessage(plugin.messageManager.parse("<#00FFFF>¡Has activado la visión reveladora!</#00FFFF>"))

            plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
                if (!game.isRunning || System.currentTimeMillis() - now > duration) {
                    task.cancel()
                    return@runAtFixedRate
                }

                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    game.escondites.forEach { escondite ->
                        val loc = escondite.location.clone().add(0.0, 1.0, 0.0)
                        p.spawnParticle(Particle.FLAME, loc, 15, 0.4, 0.4, 0.4, 0.0)
                    }
                }
            }, 0, 500, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    @EventHandler
    fun onMove(e: PlayerMoveEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.escondites.contains(p)) return

        val type = game.disguisedPlayers[p.uniqueId] ?: return

        // Cuando un animal se mueve, hay una pequeña probabilidad de hacer ruido natural
        if (Math.random() < 0.005) {
            plugin.server.regionScheduler.run(plugin, p.location) { _ ->
                playMobAmbientSound(p, type)
            }
        }
    }

    @EventHandler
    fun onSneak(e: PlayerToggleSneakEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.escondites.contains(p)) return

        if (e.isSneaking) {
            val type = game.disguisedPlayers[p.uniqueId] ?: return
            playMobAmbientSound(p, type)
            spawnMobParticles(p, type)
        }
    }

    private fun playMobAmbientSound(player: Player, type: DisguiseType) {
        val sound = when (type) {
            DisguiseType.COW -> Sound.ENTITY_COW_AMBIENT
            DisguiseType.PIG -> Sound.ENTITY_PIG_AMBIENT
            DisguiseType.SHEEP -> Sound.ENTITY_SHEEP_AMBIENT
            DisguiseType.CHICKEN -> Sound.ENTITY_CHICKEN_AMBIENT
            else -> Sound.ENTITY_PIG_AMBIENT
        }
        player.world.playSound(player.location, sound, 1f, 1f)
    }

    private fun spawnMobParticles(player: Player, type: DisguiseType) {
        player.world.spawnParticle(Particle.HEART, player.location.add(0.0, 1.0, 0.0), 3)
    }
}
