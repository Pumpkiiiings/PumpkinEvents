package pumpkin.eventos.games.hideandseek

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import pumpkin.eventos.PumpkinEventos

class HideAndSeekListener(private val plugin: PumpkinEventos) : PacketListenerAbstract(), Listener {

    init {
        PacketEvents.getAPI().eventManager.registerListener(this)
    }

    private fun game(): HideAndSeek? = plugin.eventManager.currentGame as? HideAndSeek

    override fun onPacketReceive(e: PacketReceiveEvent) {
        if (e.packetType == PacketType.Play.Client.INTERACT_ENTITY) {
            val wrapper = WrapperPlayClientInteractEntity(e)
            if (wrapper.action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                val game = game() ?: return
                if (!game.isRunning) return
                val attacker = e.getPlayer<Player>() ?: return
                
                if (attacker != game.cazador) return
                
                val targetId = wrapper.entityId
                val foundEntry = game.disguises.entries.find { it.value.entityId == targetId }
                if (foundEntry != null) {
                    val hider = foundEntry.key
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        hider.damage(100.0, attacker)
                    }
                }
            }
        }
    }

    @EventHandler
    fun onMove(e: PlayerMoveEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.escondites.contains(p)) return

        val diffX = Math.abs(e.to.x - e.from.x)
        val diffY = Math.abs(e.to.y - e.from.y)
        val diffZ = Math.abs(e.to.z - e.from.z)

        if (diffX > 0.05 || diffY > 0.05 || diffZ > 0.05) {
            val data = game.disguises[p] ?: return
            
            if (data.isSolid) {
                game.unsolidifyPlayer(p, data)
            } else {
                data.lastMoveTime = System.currentTimeMillis()
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

        if (p == game.cazador && item?.type == Material.ENDER_EYE && (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK)) {
            e.isCancelled = true
            
            if (game.isPreparation) return

            val now = System.currentTimeMillis()
            val cooldown = plugin.config.getLong("hideandseek.eye_cooldown_seconds", 60) * 1000
            val lastUse = game.hunterEyeCooldown[p] ?: 0L

            if (now - lastUse < cooldown) {
                val timeLeft = ((cooldown - (now - lastUse)) / 1000).toInt()
                p.sendMessage(plugin.messageManager.parse("<red>La habilidad está en cooldown. Faltan ${timeLeft}s.</red>"))
                return
            }

            game.hunterEyeCooldown[p] = now
            val duration = plugin.config.getLong("hideandseek.eye_duration_seconds", 5) * 1000
            
            p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.5f)
            p.sendMessage(plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hunter.eye_activated")))

            plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
                if (!game.isRunning || System.currentTimeMillis() - now > duration) {
                    task.cancel()
                    return@runAtFixedRate
                }
                
                game.escondites.forEach { escondite ->
                    val loc = escondite.location.clone().add(0.0, 1.0, 0.0)
                    p.spawnParticle(Particle.FLAME, loc, 15, 0.4, 0.4, 0.4, 0.0)
                }
            }, 0, 500, java.util.concurrent.TimeUnit.MILLISECONDS)
            return
        }

        if (game.escondites.contains(p) && item?.type == Material.BLAZE_ROD && e.action == Action.RIGHT_CLICK_BLOCK) {
            e.isCancelled = true
            val clickedBlock = e.clickedBlock ?: return
            
            if (!clickedBlock.type.isSolid || clickedBlock.type == Material.BEDROCK || clickedBlock.type == Material.BARRIER) {
                p.sendMessage(plugin.messageManager.parse("<red>No puedes transformarte en este bloque.</red>"))
                return
            }

            val data = game.disguises[p] ?: return
            if (data.type == clickedBlock.type) return

            if (data.isSolid) {
                game.unsolidifyPlayer(p, data)
            }
            
            game.spawnDisguise(p, clickedBlock.type)
            p.playSound(p.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f)
            p.sendMessage(plugin.messageManager.parse(
                plugin.languageManager.get("hideandseek.hider.disguise_changed"), 
                Placeholder.parsed("block", clickedBlock.type.name)
            ))
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockDamage(e: BlockDamageEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning || p != game.cazador || game.isPreparation) return

        val block = e.block
        
        val foundEntry = game.disguises.entries.find { it.value.isSolid && it.value.solidLocation == block.location }
        if (foundEntry != null) {
            val hider = foundEntry.key
            game.unsolidifyPlayer(hider, foundEntry.value)
            
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                hider.damage(100.0, p)
            }
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(e: EntityDamageByEntityEvent) {
        val game = game() ?: return
        if (!game.isRunning) return

        val attacker = e.damager as? Player ?: return
        val victim = e.entity as? Player ?: return

        if (attacker == game.cazador && game.escondites.contains(victim)) {
            e.damage = 100.0 
        } else {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        
        val player = e.entity as? Player ?: return
        if (!game.players.contains(player)) return

        if (e is EntityDamageByEntityEvent) return
        
        if (game.isPreparation) {
            e.isCancelled = true
            return
        }

        if (game.escondites.contains(player)) {
            if (e.cause == EntityDamageEvent.DamageCause.FALL || e.cause == EntityDamageEvent.DamageCause.SUFFOCATION || e.cause == EntityDamageEvent.DamageCause.FALLING_BLOCK) {
                e.isCancelled = true
            }
        }
    }
}

