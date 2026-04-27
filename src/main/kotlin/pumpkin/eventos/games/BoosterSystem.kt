package pumpkin.eventos.games

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Vector3f
import pumpkin.eventos.PumpkinEventos
import java.util.concurrent.ConcurrentLinkedQueue

enum class BoosterType(val material: Material, val displayName: String, val color: String) {
    // --- BOOSTERS DE MOVIMIENTO ---
    SPEED_1(Material.FEATHER, "Velocidad I", "<#00FF00>") {
        override fun applyEffect(player: Player, plugin: PumpkinEventos) {
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 200, 0))
        }
    },
    SPEED_2(Material.FEATHER, "Velocidad II", "<#00FF00>") {
        override fun applyEffect(player: Player, plugin: PumpkinEventos) {
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 1))
        }
    },
    JUMP_1(Material.RABBIT_FOOT, "Súper Salto I", "<#FFFF00>") {
        override fun applyEffect(player: Player, plugin: PumpkinEventos) {
            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 200, 0))
        }
    },
    MIX(Material.BLAZE_POWDER, "Mix Poder", "<#BF00FF>") {
        override fun applyEffect(player: Player, plugin: PumpkinEventos) {
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 1))
            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 100, 1))
        }
    },
    PEARL(Material.ENDER_PEARL, "Perla Salvación", "<#00FFFF>") {
        override fun applyEffect(player: Player, plugin: PumpkinEventos) {
            val item = ItemStack(Material.ENDER_PEARL)
            val meta = item.itemMeta
            meta.displayName(plugin.messageManager.parse("<#00FFFF><b>Perla de Salvación</b>"))
            val key = NamespacedKey(plugin, "tnt_item") // Importante: usar "tnt_item" para que el GameListener lo detecte
            meta.persistentDataContainer.set(key, PersistentDataType.STRING, "pearl")
            item.itemMeta = meta
            player.inventory.addItem(item)
        }
    },

    // --- NUEVO: BOOSTERS DE COMBATE (Para WoolWars, etc) ---
    STRENGTH_1(Material.REDSTONE, "Fuerza Bruta", "<#FF3131>") {
        override fun applyEffect(player: Player, plugin: PumpkinEventos) {
            player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 200, 0))
        }
    },
    REGEN_1(Material.GHAST_TEAR, "Regeneración", "<#FF1493>") {
        override fun applyEffect(player: Player, plugin: PumpkinEventos) {
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 160, 1))
        }
    },
    INSTA_HEAL(Material.GLISTERING_MELON_SLICE, "Cura Instantánea", "<#FF5500>") {
        override fun applyEffect(player: Player, plugin: PumpkinEventos) {
            player.health = (player.health + 8.0).coerceAtMost(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0)
            player.world.spawnParticle(org.bukkit.Particle.HEART, player.location.add(0.0, 1.0, 0.0), 5, 0.5, 0.5, 0.5, 0.0)
        }
    },
    ABSORPTION(Material.GOLD_INGOT, "Escudo Extra", "<#FFD700>") {
        override fun applyEffect(player: Player, plugin: PumpkinEventos) {
            player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 600, 1))
        }
    },
    GAPPLE(Material.GOLDEN_APPLE, "Manzana Dorada", "<#CCFF00>") {
        override fun applyEffect(player: Player, plugin: PumpkinEventos) {
            val item = ItemStack(Material.GOLDEN_APPLE)
            val meta = item.itemMeta
            meta.displayName(plugin.messageManager.parse("<#CCFF00><b>Manzana Dorada</b>"))
            val key = NamespacedKey(plugin, "tnt_item")
            meta.persistentDataContainer.set(key, PersistentDataType.STRING, "gapple")
            item.itemMeta = meta
            player.inventory.addItem(item)
        }
    };

    abstract fun applyEffect(player: Player, plugin: PumpkinEventos)
}

data class ActiveBooster(val item: ItemDisplay, val text: TextDisplay, var task: ScheduledTask? = null)

class BoosterManager(private val plugin: PumpkinEventos, private val game: EventGame) {
    private val activeBoosters = ConcurrentLinkedQueue<ActiveBooster>()
    private var boosterTask: ScheduledTask? = null
    private val mm = MiniMessage.miniMessage()

    fun start(availableBoosters: List<BoosterType>) {
        boosterTask = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!game.isRunning) { task.cancel(); return@runAtFixedRate }

            if (Math.random() < 0.30 && activeBoosters.size < 8 && game.players.isNotEmpty()) {
                spawnBooster(availableBoosters)
            }
        }, 60L, 40L)
    }

    fun stop() {
        boosterTask?.cancel()
        activeBoosters.forEach { booster ->
            booster.task?.cancel()
            plugin.server.regionScheduler.run(plugin, booster.item.location) { _ ->
                if (!booster.item.isDead) booster.item.remove()
                if (!booster.text.isDead) booster.text.remove()
            }
        }
        activeBoosters.clear()
    }

    private fun spawnBooster(availableBoosters: List<BoosterType>) {
        if (availableBoosters.isEmpty()) return
        val type = availableBoosters.random()
        val targetPlayer = game.players.randomOrNull() ?: return
        val centerLoc = targetPlayer.location

        val offsetX = (Math.random() * 24 - 12)
        val offsetZ = (Math.random() * 24 - 12)
        val searchLoc = centerLoc.clone().add(offsetX, 0.0, offsetZ)

        plugin.server.regionScheduler.run(plugin, searchLoc) { _ ->
            val world = searchLoc.world
            var highestY = searchLoc.blockY
            var foundSolid = false

            for (y in (searchLoc.blockY + 15) downTo (searchLoc.blockY - 20)) {
                val b = world.getBlockAt(searchLoc.blockX, y, searchLoc.blockZ)
                if (b.type.isSolid) {
                    highestY = y
                    foundSolid = true
                    break
                }
            }

            val finalLoc = if (foundSolid) {
                Location(world, searchLoc.x, highestY + 1.2, searchLoc.z)
            } else {
                centerLoc.clone().add(targetPlayer.location.direction.multiply(3)).add(0.0, 1.2, 0.0)
            }

            val itemDisplay = world.spawn(finalLoc, ItemDisplay::class.java)
            itemDisplay.setItemStack(ItemStack(type.material))
            val transform = itemDisplay.transformation
            itemDisplay.transformation = Transformation(transform.translation, transform.leftRotation, Vector3f(1.5f, 1.5f, 1.5f), transform.rightRotation)

            val textLoc = finalLoc.clone().add(0.0, 0.8, 0.0)
            val textDisplay = world.spawn(textLoc, TextDisplay::class.java)
            textDisplay.text(plugin.messageManager.parse("${type.color}<b>${type.displayName.uppercase()}</b>"))
            textDisplay.billboard = org.bukkit.entity.Display.Billboard.CENTER
            textDisplay.backgroundColor = org.bukkit.Color.fromARGB(0, 0, 0, 0)
            textDisplay.isShadowed = true

            val activeBooster = ActiveBooster(itemDisplay, textDisplay, null)
            activeBoosters.add(activeBooster)

            var tick = 0
            val animTask = plugin.server.regionScheduler.runAtFixedRate(plugin, finalLoc, { task ->
                if (!game.isRunning || itemDisplay.isDead) {
                    itemDisplay.remove()
                    textDisplay.remove()
                    activeBoosters.remove(activeBooster)
                    task.cancel()
                    return@runAtFixedRate
                }

                tick++
                val currentLoc = itemDisplay.location
                val blockBelow = world.getBlockAt(currentLoc.blockX, currentLoc.blockY - 1, currentLoc.blockZ)

                if (!blockBelow.type.isSolid && blockBelow.type != Material.WATER) {
                    itemDisplay.teleport(currentLoc.clone().subtract(0.0, 0.4, 0.0))
                    textDisplay.teleport(textDisplay.location.clone().subtract(0.0, 0.4, 0.0))
                    if (currentLoc.y < world.minHeight) {
                        itemDisplay.remove(); textDisplay.remove(); activeBoosters.remove(activeBooster); task.cancel()
                    }
                } else {
                    itemDisplay.interpolationDuration = 1
                    val currentTransform = itemDisplay.transformation
                    currentTransform.rightRotation.rotateY(0.1f)
                    val floatOffset = (Math.sin(tick / 10.0) * 0.15).toFloat()
                    currentTransform.translation.set(0f, floatOffset, 0f)
                    itemDisplay.transformation = currentTransform
                }

                val picker = currentLoc.getNearbyPlayers(1.5).firstOrNull { game.players.contains(it) }
                if (picker != null) {
                    picker.scheduler.run(plugin, { _ ->
                        type.applyEffect(picker, plugin)
                        picker.playSound(picker.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f)
                        picker.sendMessage(plugin.messageManager.parse("<light_purple><b>!</b></light_purple> <white>Has recogido: ${type.color}<b>${type.displayName}</b></white>"))
                    }, null)

                    world.spawnParticle(org.bukkit.Particle.FIREWORK, currentLoc, 15, 0.2, 0.2, 0.2, 0.1)
                    itemDisplay.remove(); textDisplay.remove(); activeBoosters.remove(activeBooster); task.cancel()
                }
            }, 1L, 1L)
            activeBooster.task = animTask
        }
    }
}
