package pumpkin.eventos.games.skywars

import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Fireball
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import pumpkin.eventos.PumpkinEventos
import java.util.UUID

/**
 * Maneja los items especiales de Skywars:
 *
 *  - TNT Instantánea  → explota inmediatamente al colocarse (fusible = 0 ticks)
 *  - TNT Lanzable     → clic derecho lanza un TNTPrimed hacia donde miras
 *  - Fireball (FIRE_CHARGE custom) → clic derecho lanza Fireball con KB estilo BedWars (Con Cooldown)
 */
class SkywarsTntFireballListener(private val plugin: PumpkinEventos) : Listener {

    private val tntKey = org.bukkit.NamespacedKey(plugin, SkywarsTntType.KEY)
    private val fireballKey = org.bukkit.NamespacedKey(plugin, "sw_fireball")

    // Mapa para gestionar los cooldowns de la Fireball
    private val fireballCooldowns = mutableMapOf<UUID, Long>()

    // ─── HELPER: ¿es el juego válido? ─────────────────────────────

    private fun isPlayerInValidGame(player: Player): Boolean {
        val game = plugin.eventManager.currentGame ?: return false
        if (!game.isRunning) return false
        if (game is pumpkin.eventos.games.skywars.Skywars && game.phase == pumpkin.eventos.games.skywars.SwPhase.PLAYING && game.players.contains(player)) return true
        if (game is pumpkin.eventos.games.battleroyale.BattleRoyale && game.phase == pumpkin.eventos.games.battleroyale.BRPhase.PLAYING && game.players.contains(player)) return true
        return false
    }

    private fun isEntityInValidGame(player: Player): Boolean {
        val game = plugin.eventManager.currentGame ?: return false
        if (!game.isRunning) return false
        if (game is pumpkin.eventos.games.skywars.Skywars && game.players.contains(player)) return true
        if (game is pumpkin.eventos.games.battleroyale.BattleRoyale && game.players.contains(player)) return true
        return false
    }

    // ─── TNT INSTANTÁNEA — detectar al colocarla ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockPlace(e: BlockPlaceEvent) {
        val player = e.player
        if (!isPlayerInValidGame(player)) return

        val item = e.itemInHand
        if (item.type != Material.TNT) return

        val meta = item.itemMeta ?: return
        val tntType = meta.persistentDataContainer.get(tntKey, PersistentDataType.STRING)

        // Si tiene tag INSTANT explota al instante, sino, se comporta como TNT de Bedwars (fuse = 60)
        if (tntType == SkywarsTntType.INSTANT || tntType == null) {
            e.isCancelled = true // No colocar el bloque normal
            val loc = e.blockPlaced.location.add(0.5, 0.5, 0.5)

            // Reducir el item de forma segura
            val hand = player.inventory.itemInMainHand
            hand.subtract(1)

            plugin.server.regionScheduler.run(plugin, loc) { _ ->
                val tnt = loc.world.spawnEntity(loc, EntityType.TNT) as TNTPrimed
                tnt.fuseTicks = if (tntType == SkywarsTntType.INSTANT) 0 else 60
                tnt.source = player

                if (tnt.fuseTicks == 0) {
                    player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f)
                    loc.world.spawnParticle(Particle.EXPLOSION, loc, 1)
                } else {
                    player.playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f)
                }
            }
        }
    }

    // ─── LANZAR FIREBALL O TNT — detectar clic derecho ───────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(e: PlayerInteractEvent) {
        val player = e.player
        if (!isPlayerInValidGame(player)) return
        if (e.hand != EquipmentSlot.HAND) return

        val item = e.item ?: return
        val action = e.action
        if (!action.name.contains("RIGHT_CLICK")) return

        // ─── FIREBALL (FIRE_CHARGE custom o normal) ───────────────────────────
        if (item.type == Material.FIRE_CHARGE) {
            e.isCancelled = true

            // --- SISTEMA DE COOLDOWN (0.5 SEGUNDOS) ---
            val uuid = player.uniqueId
            val currentTime = System.currentTimeMillis()
            val lastUsed = fireballCooldowns[uuid] ?: 0L

            if (currentTime - lastUsed < 500L) { // 500 ms cooldown para evitar doble click
                return
            }
            fireballCooldowns[uuid] = currentTime

            // Reducir el stack de forma segura
            val hand = player.inventory.itemInMainHand
            hand.subtract(1)
            player.updateInventory()

            val eyeLoc = player.eyeLocation
            val dir = eyeLoc.direction.normalize().multiply(1.5)

            plugin.server.regionScheduler.run(plugin, eyeLoc) { _ ->
                val fireball = player.launchProjectile(Fireball::class.java)
                fireball.direction = dir
                fireball.velocity = dir
                fireball.shooter = player
                fireball.yield = 2.0f
                fireball.setIsIncendiary(true)

                // Marcamos la entidad como nuestra Fireball de SkyWars/BR
                fireball.persistentDataContainer.set(fireballKey, PersistentDataType.BYTE, 1.toByte())

                player.playSound(player.location, Sound.ENTITY_GHAST_SHOOT, 1f, 1.2f)
                player.world.spawnParticle(Particle.FLAME, eyeLoc, 5, 0.1, 0.1, 0.1, 0.05)
            }
            return
        }

        // ─── TNT LANZABLE ────────────────────────────────────────────────────
        if (item.type == Material.TNT) {
            val meta = item.itemMeta ?: return
            val tntType = meta.persistentDataContainer.get(tntKey, PersistentDataType.STRING)
            if (tntType != SkywarsTntType.THROWABLE) return

            e.isCancelled = true

            // Reducir el stack de forma segura
            val hand = player.inventory.itemInMainHand
            hand.subtract(1)
            player.updateInventory()

            val eyeLoc = player.eyeLocation
            val dir = eyeLoc.direction.normalize().multiply(1.0)

            plugin.server.regionScheduler.run(plugin, eyeLoc) { _ ->
                val tnt = eyeLoc.world.spawnEntity(eyeLoc, EntityType.TNT) as TNTPrimed
                tnt.fuseTicks = 60  // 3 segundos de fusible
                tnt.source = player
                tnt.velocity = dir

                player.playSound(player.location, Sound.ENTITY_TNT_PRIMED, 1f, 1f)
            }
        }
    }

    // ─── FIREBALL KNOCKBACK (ESTILO BEDWARS) ─────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onExplosionDamage(e: EntityDamageByEntityEvent) {
        val victim = e.entity as? Player ?: return
        if (!isEntityInValidGame(victim)) return

        // 1. Manejo de Fireball (Fireball Jump)
        if (e.damager is Fireball) {
            val fireball = e.damager as Fireball
            if (!fireball.persistentDataContainer.has(fireballKey, PersistentDataType.BYTE)) return

            val shooter = fireball.shooter as? Player
            val fireballLoc = fireball.location
            val victimLoc = victim.location

            var dir = victimLoc.toVector().subtract(fireballLoc.toVector())
            if (dir.lengthSquared() < 0.01) {
                dir = victimLoc.direction.multiply(-1)
            }
            dir.normalize()

            val distance = fireballLoc.distance(victimLoc)
            // Mejora del knockback estilo Bedwars (más fuerte y constante)
            val kbMultiplier = (4.0 - distance).coerceIn(1.2, 3.5)

            dir.multiply(kbMultiplier)
            dir.y = 1.2

            if (victim == shooter) {
                e.damage = 2.0 // Menos daño para self-hit
            } else {
                e.damage = 4.0
                victim.sendMessage(plugin.messageManager.parse("<#FF6A00>¡Una bola de fuego te ha lanzado!</#FF6A00>"))
            }

            victim.velocity = victim.velocity.add(dir)
            victim.world.spawnParticle(Particle.FLAME, victimLoc.add(0.0, 1.0, 0.0), 20, 0.3, 0.3, 0.3, 0.05)
            victim.playSound(victimLoc, Sound.ENTITY_BLAZE_HURT, 1f, 1f)
            return
        }

        // 2. Manejo de TNT (TNT Jump)
        if (e.damager is TNTPrimed) {
            val tnt = e.damager as TNTPrimed
            val tntLoc = tnt.location
            val victimLoc = victim.location

            val distance = tntLoc.distance(victimLoc)
            if (distance > 6.0) return

            var dir = victimLoc.toVector().subtract(tntLoc.toVector())
            if (dir.lengthSquared() < 0.01) {
                dir = victimLoc.direction.multiply(-1)
            }
            dir.normalize()

            // Knockback fuerte para TNT jump
            val kbMultiplier = (5.0 - distance).coerceIn(1.0, 3.0)
            dir.multiply(kbMultiplier)
            dir.y = 1.1

            e.damage = 2.0 // Reducir daño explosivo para que se pueda hacer tnt jump seguro
            victim.velocity = victim.velocity.add(dir)
            return
        }
    }
}
