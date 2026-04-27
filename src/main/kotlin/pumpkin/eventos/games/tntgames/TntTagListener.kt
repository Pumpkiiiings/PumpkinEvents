package pumpkin.eventos.games.tntgames

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import pumpkin.eventos.PumpkinEventos

class TntTagListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): TntTag? = plugin.eventManager.currentGame as? TntTag

    /** Da los ítems especiales del TntTag leyendo cooldowns y estados desde config.yml */
    fun giveSpecialItems(player: Player) {
        val cfg = plugin.config
        val key = NamespacedKey(plugin, "tnt_item")

        if (cfg.getBoolean("tnttag.items.dash.enabled", true)) {
            val item = ItemStack(Material.SUGAR).apply {
                editMeta { meta: ItemMeta ->
                    meta.displayName(plugin.messageManager.parse("<#39FF14><b>⚡ Dash</b></#39FF14>"))
                    meta.lore(listOf(
                        plugin.messageManager.parse("<gray>Clic derecho para lanzarte hacia adelante.</gray>"),
                        plugin.messageManager.parse("<dark_gray>Cooldown: ${cfg.getInt("tnttag.items.dash.cooldown_ticks", 600) / 20}s</dark_gray>")
                    ))
                    meta.persistentDataContainer.set(key, PersistentDataType.STRING, "dash")
                }
            }
            player.inventory.addItem(item)
        }

        if (cfg.getBoolean("tnttag.items.doublejump.enabled", true)) {
            val item = ItemStack(Material.RABBIT_FOOT).apply {
                editMeta { meta: ItemMeta ->
                    meta.displayName(plugin.messageManager.parse("<#00FFFF><b>🐇 Doble Salto</b></#00FFFF>"))
                    meta.lore(listOf(
                        plugin.messageManager.parse("<gray>Clic derecho para saltar hacia arriba.</gray>"),
                        plugin.messageManager.parse("<dark_gray>Cooldown: ${cfg.getInt("tnttag.items.doublejump.cooldown_ticks", 600) / 20}s</dark_gray>")
                    ))
                    meta.persistentDataContainer.set(key, PersistentDataType.STRING, "doublejump")
                }
            }
            player.inventory.addItem(item)
        }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val player = e.player
        val game = game() ?: return
        if (!game.isRunning || e.hand != EquipmentSlot.HAND) return

        val item = e.item ?: return
        val key = NamespacedKey(plugin, "tnt_item")
        val type = item.itemMeta?.persistentDataContainer?.get(key, PersistentDataType.STRING) ?: return
        e.isCancelled = true

        val cfg = plugin.config
        when (type) {
            "dash" -> {
                val cd = cfg.getInt("tnttag.items.dash.cooldown_ticks", 600)
                if (player.hasCooldown(Material.SUGAR)) return
                player.setCooldown(Material.SUGAR, cd)
                val vH = cfg.getDouble("tnttag.items.dash.velocity_horizontal", 1.8)
                val vV = cfg.getDouble("tnttag.items.dash.velocity_vertical", 0.3)
                player.velocity = player.location.direction.normalize().multiply(vH).setY(vV)
                player.playSound(player.location, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.5f)
            }
            "doublejump" -> {
                val cd = cfg.getInt("tnttag.items.doublejump.cooldown_ticks", 600)
                if (player.hasCooldown(Material.RABBIT_FOOT)) return
                player.setCooldown(Material.RABBIT_FOOT, cd)
                val vH = cfg.getDouble("tnttag.items.doublejump.velocity_horizontal", 0.5)
                val vV = cfg.getDouble("tnttag.items.doublejump.velocity_vertical", 1.3)
                player.velocity = player.location.direction.multiply(vH).setY(vV)
                player.playSound(player.location, Sound.ENTITY_BAT_TAKEOFF, 1f, 1f)
            }
        }
    }

    @EventHandler
    fun onPunch(e: EntityDamageByEntityEvent) {
        val attacker = e.damager as? Player ?: return
        val victim = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning) return
        e.damage = 0.001
        game.handlePunch(attacker, victim)
    }

    @EventHandler
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return
        if (e.cause != EntityDamageEvent.DamageCause.VOID) {
            e.damage = 0.001
        }
    }
}
