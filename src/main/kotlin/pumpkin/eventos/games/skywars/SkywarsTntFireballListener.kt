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
import pumpkin.eventos.PumpkinEventos

/**
 * Maneja los items especiales de Skywars:
 *
 *  - TNT Instantánea  → explota inmediatamente al colocarse (fusible = 0 ticks)
 *  - TNT Lanzable     → clic derecho lanza un TNTPrimed hacia donde miras
 *  - Fireball (FIRE_CHARGE custom) → clic derecho lanza SmallFireball con KB estilo BedWars
 */
class SkywarsTntFireballListener(private val plugin: PumpkinEventos) : Listener {

    private val tntKey = org.bukkit.NamespacedKey(plugin, SkywarsTntType.KEY)
    private val fireballKey = org.bukkit.NamespacedKey(plugin, "sw_fireball")

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    // ─── HELPER: ¿es el juego de Skywars activo? ─────────────────────────────

    private fun game(): Skywars? = plugin.eventManager.currentGame as? Skywars

    // ─── TNT INSTANTÁNEA — detectar al colocarla ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockPlace(e: BlockPlaceEvent) {
        val game = game() ?: return
        if (!game.isRunning || game.phase != SwPhase.PLAYING) return

        val player = e.player
        if (!game.players.contains(player)) return

        val item = e.itemInHand
        if (item.type != Material.TNT) return

        val meta = item.itemMeta ?: return
        val tntType = meta.persistentDataContainer.get(tntKey, PersistentDataType.STRING) ?: return

        if (tntType == SkywarsTntType.INSTANT) {
            e.isCancelled = true // No colocar el bloque normal
            val loc = e.blockPlaced.location.add(0.5, 0.5, 0.5)

            plugin.server.regionScheduler.run(plugin, loc) { _ ->
                // Reducir el item del inventario
                val hand = player.inventory.itemInMainHand
                if (hand.amount <= 1) player.inventory.setItemInMainHand(null)
                else hand.amount--

                // Spawnar TNT con fusible 0 (explota al instante)
                val tnt = loc.world.spawnEntity(loc, EntityType.TNT) as TNTPrimed
                tnt.fuseTicks = 0
                tnt.source = player

                player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f)
                loc.world.spawnParticle(Particle.FLASH, loc, 3)
            }
        }
    }

    // ─── TNT LANZABLE — detectar clic derecho ─────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(e: PlayerInteractEvent) {
        val game = game() ?: return
        if (!game.isRunning || game.phase != SwPhase.PLAYING) return
        if (e.hand != EquipmentSlot.HAND) return

        val player = e.player
        if (!game.players.contains(player)) return

        val item = e.item ?: return

        // ─── FIREBALL (FIRE_CHARGE custom) ────────────────────────────────────
        if (item.type == Material.FIRE_CHARGE) {
            val meta = item.itemMeta ?: return
            val isSwFireball = meta.persistentDataContainer.has(fireballKey, PersistentDataType.BYTE)
            if (!isSwFireball) return

            e.isCancelled = true

            val action = e.action
            if (action.name.contains("RIGHT_CLICK")) {
                // Reducir el stack
                if (item.amount <= 1) player.inventory.setItemInMainHand(null)
                else item.amount--

                val eyeLoc = player.eyeLocation
                val dir = eyeLoc.direction.normalize().multiply(1.5)

                plugin.server.regionScheduler.run(plugin, eyeLoc) { _ ->
                    val fireball = player.launchProjectile(Fireball::class.java)
                    fireball.direction = dir
                    fireball.velocity = dir
                    fireball.shooter = player
                    fireball.yield = 2.0f
                    fireball.setIsIncendiary(true)

                    player.playSound(player.location, Sound.ENTITY_GHAST_SHOOT, 1f, 1.2f)
                    player.world.spawnParticle(Particle.FLAME, eyeLoc, 5, 0.1, 0.1, 0.1, 0.05)
                }
            }
            return
        }

        // ─── TNT LANZABLE ────────────────────────────────────────────────────
        if (item.type != Material.TNT) return
        val meta = item.itemMeta ?: return
        val tntType = meta.persistentDataContainer.get(tntKey, PersistentDataType.STRING) ?: return
        if (tntType != SkywarsTntType.THROWABLE) return

        val action = e.action
        if (!action.name.contains("RIGHT_CLICK")) return

        e.isCancelled = true

        // Reducir el stack
        if (item.amount <= 1) player.inventory.setItemInMainHand(null)
        else item.amount--

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

    // ─── FIREBALL KNOCKBACK al impactar ──────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onFireballHitPlayer(e: EntityDamageByEntityEvent) {
        val game = game() ?: return
        if (!game.isRunning) return

        if (e.damager !is Fireball) return
        val fireball = e.damager as Fireball
        val shooter = fireball.shooter as? Player ?: return
        if (!game.players.contains(shooter)) return

        val victim = e.entity as? Player ?: return
        if (victim == shooter) return

        // Cancelar el daño de la propia explosión, solo aplicar KB
        e.isCancelled = true

        val dir = victim.location.subtract(fireball.location).toVector().normalize()
        val kb = dir.multiply(1.8).setY(0.5)
        victim.velocity = victim.velocity.add(kb)

        // Efecto visual
        victim.world.spawnParticle(Particle.FLAME, victim.location.add(0.0, 1.0, 0.0), 20, 0.3, 0.3, 0.3, 0.05)
        victim.playSound(victim.location, Sound.ENTITY_BLAZE_HURT, 1f, 1f)
        victim.sendMessage(plugin.messageManager.parse("<#FF6A00>¡Una bola de fuego te ha lanzado!</#FF6A00>"))
    }
}
