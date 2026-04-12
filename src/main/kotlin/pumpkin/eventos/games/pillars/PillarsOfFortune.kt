package pumpkin.eventos.games.pillars

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Egg
import org.bukkit.entity.Fireball
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.concurrent.ConcurrentLinkedQueue

class PillarsOfFortune(plugin: PumpkinEventos) : EventGame(plugin, "pillars", "<aqua>Pillars of Fortune</aqua>"), Listener {

    var isPreparation = true
    private var startTimer = 10
    var itemTimer = 10

    private val cageLocations = ConcurrentLinkedQueue<Location>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun getExtraPlaceholders(): Map<String, String> {
        return mapOf(
            "%item_timer%" to itemTimer.toString(),
            "%mapa%" to (currentArena?.id ?: "Desconocido")
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(
            GameRule.FALL_DAMAGE to true,
            GameRule.NATURAL_REGENERATION to false,
            GameRule.DO_MOB_SPAWNING to false
        )
    }

    override fun onStart() {
        isPreparation = true
        startTimer = 10
        itemTimer = 10
        cageLocations.clear()

        val arena = currentArena ?: return

        // Esperamos medio segundo (10 ticks) para garantizar que el EventManager ya teletransportó a todos
        // Así podemos agarrar de forma segura el mundo de Slime cargado.
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            val targetWorld = players.firstOrNull()?.world ?: arena.centerLocation?.world ?: return@runDelayed
            val spawns = arena.spawnPoints
            var spawnIndex = 0

            players.forEach { player ->
                player.gameMode = GameMode.SURVIVAL
                player.inventory.clear()
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                player.health = 20.0
                player.foodLevel = 20

                if (spawns.isNotEmpty()) {
                    val pilarLoc = spawns[spawnIndex % spawns.size].clone()
                    pilarLoc.world = targetWorld // Seteamos el mundo clonado a la coordenada del spawn
                    spawnIndex++

                    centerAndCage(player, pilarLoc)
                }
            }
        }, 10L)

        // LOOP PRINCIPAL
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            // -- FASE DE PREPARACIÓN --
            if (isPreparation) {
                if (startTimer > 0) {
                    val rawMsg = plugin.languageManager.get("pillars.actionbar.starting")
                    val bar = plugin.messageManager.parse(rawMsg, Placeholder.parsed("time", startTimer.toString()))
                    players.forEach {
                        it.sendActionBar(bar)
                        it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
                    }
                    startTimer--
                } else {
                    isPreparation = false
                    removeCages()
                    val startMsg = plugin.messageManager.parse(plugin.languageManager.get("pillars.actionbar.started"))
                    players.forEach {
                        it.sendActionBar(startMsg)
                        it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                    }
                }
                return@runAtFixedRate
            }

            // -- FASE DE JUEGO --
            // Chequeo de caída manual al vacío por si el daño de Void falla
            val toEliminate = players.filter { it.location.y <= it.world.minHeight + 5 }
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                toEliminate.forEach { eliminate(it) }
            }

            if (players.size <= 1) {
                task.cancel()
                checkWinner()
                return@runAtFixedRate
            }

            itemTimer--
            if (itemTimer > 0) {
                val actionMsg = plugin.messageManager.parse("<white>Próximo ítem en: <aqua><bold>${itemTimer}</bold>s</aqua></white>")
                players.forEach { it.sendActionBar(actionMsg) }
            } else {
                itemTimer = 10
                darItemsATodos()
            }

        }, 20L, 20L)
    }

    private fun centerAndCage(player: Player, targetLoc: Location) {
        val targetWorld = targetLoc.world ?: return
        val centerLoc = Location(targetWorld, targetLoc.blockX + 0.5, targetLoc.blockY.toDouble(), targetLoc.blockZ + 0.5, targetLoc.yaw, targetLoc.pitch)

        player.teleportAsync(centerLoc).thenAccept {
            plugin.server.regionScheduler.runDelayed(plugin, centerLoc, { _ ->
                val cx = centerLoc.blockX
                val cy = centerLoc.blockY
                val cz = centerLoc.blockZ

                for (x in -1..1) {
                    for (y in -1..3) {
                        for (z in -1..1) {
                            if (x == -1 || x == 1 || z == -1 || z == 1 || y == -1 || y == 3) {
                                val block = targetWorld.getBlockAt(cx + x, cy + y, cz + z)
                                if (block.isEmpty || !block.type.isSolid) {
                                    block.setType(Material.GLASS, false)
                                    cageLocations.add(block.location)
                                }
                            }
                        }
                    }
                }
            }, 5L) // Retraso de 5 ticks para asegurar la carga del chunk (Igual a tu código original)
        }
    }

    private fun removeCages() {
        cageLocations.forEach { loc ->
            plugin.server.regionScheduler.run(plugin, loc) { _ ->
                val block = loc.block
                if (block.type == Material.GLASS) block.setType(Material.AIR, false)
            }
        }
        cageLocations.clear()
    }

    private fun darItemsATodos() {
        val prefix = plugin.languageManager.get("prefix")
        val receiveMsg = plugin.messageManager.parse("$prefix<#39FF14>¡Has recibido suministros!</#39FF14>")

        players.forEach { p ->
            val randomItems = getRandomItems()
            randomItems.forEach { p.inventory.addItem(it) }
            p.playSound(p.location, Sound.ENTITY_ITEM_PICKUP, 1f, 1.5f)
            p.sendMessage(receiveMsg)
        }
    }

    // --- SISTEMA DE LOOT EXPANDIDO ---
    private fun getRandomItems(): List<ItemStack> {
        val chance = (1..100).random()
        val items = mutableListOf<ItemStack>()

        when {
            // 45% Probabilidad: OBJETOS COMUNES
            chance <= 45 -> {
                val material = listOf(Material.OAK_PLANKS, Material.COBBLESTONE, Material.WHITE_WOOL, Material.DIRT, Material.GLASS, Material.ANDESITE).random()
                items.add(ItemStack(material, (16..64).random()))

                val util = listOf(
                    ItemStack(Material.STONE_SWORD), ItemStack(Material.STONE_AXE),
                    ItemStack(Material.COOKED_CHICKEN, 12), ItemStack(Material.APPLE, 8),
                    ItemStack(Material.SNOWBALL, 16), ItemStack(Material.EGG, 16),
                    ItemStack(Material.LEATHER_CHESTPLATE), ItemStack(Material.CHAINMAIL_BOOTS),
                    ItemStack(Material.SHIELD)
                ).random()
                items.add(util)
            }

            // 35% Probabilidad: OBJETOS RAROS
            chance <= 80 -> {
                val list = listOf(
                    listOf(ItemStack(Material.IRON_SWORD)),
                    listOf(ItemStack(Material.IRON_CHESTPLATE)),
                    listOf(ItemStack(Material.BOW), ItemStack(Material.ARROW, 24)),
                    listOf(ItemStack(Material.IRON_PICKAXE), ItemStack(Material.IRON_AXE)),
                    listOf(ItemStack(Material.WATER_BUCKET)),
                    listOf(ItemStack(Material.LAVA_BUCKET)),
                    listOf(ItemStack(Material.GOLDEN_APPLE, 2)),
                    listOf(ItemStack(Material.ENDER_PEARL, 1)),
                    listOf(ItemStack(Material.FISHING_ROD)),
                    listOf(ItemStack(Material.FIRE_CHARGE, 4)),
                    listOf(ItemStack(Material.COBWEB, 8)),
                    listOf(crearPocion(PotionType.HEALING, true)),
                    listOf(crearPocion(PotionType.SWIFTNESS, false))
                ).random()
                items.addAll(list)
            }

            // 15% Probabilidad: OBJETOS ÉPICOS
            chance <= 95 -> {
                val list = listOf(
                    listOf(ItemStack(Material.DIAMOND_SWORD)),
                    listOf(ItemStack(Material.DIAMOND_CHESTPLATE)),
                    listOf(ItemStack(Material.DIAMOND_LEGGINGS)),
                    listOf(ItemStack(Material.CROSSBOW).apply { addEnchantment(Enchantment.QUICK_CHARGE, 2) }, ItemStack(Material.ARROW, 32)),
                    listOf(ItemStack(Material.TNT, 12), ItemStack(Material.FLINT_AND_STEEL)),
                    listOf(ItemStack(Material.SLIME_BLOCK, 10)),
                    listOf(ItemStack(Material.GOLDEN_APPLE, 5)),
                    listOf(ItemStack(Material.ENDER_PEARL, 3)),
                    listOf(crearPocion(PotionType.STRENGTH, false)),
                    listOf(ItemStack(Material.OBSIDIAN, 12)),
                    listOf(ItemStack(Material.TRIDENT).apply { addEnchantment(Enchantment.LOYALTY, 1) })
                ).random()
                items.addAll(list)
            }

            // 5% Probabilidad: OBJETOS LEGENDARIOS
            else -> {
                val bat = ItemStack(Material.STICK).apply {
                    itemMeta = itemMeta?.apply {
                        displayName(plugin.messageManager.parse("<#FF3131><b>Mega Bate Knockback</b></#FF3131>"))
                        addEnchant(Enchantment.KNOCKBACK, 3, true)
                    }
                }
                val setGod = listOf(
                    listOf(bat),
                    listOf(ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1)),
                    listOf(ItemStack(Material.NETHERITE_CHESTPLATE)),
                    listOf(ItemStack(Material.ENDER_PEARL, 8)),
                    listOf(ItemStack(Material.TOTEM_OF_UNDYING)),
                    listOf(ItemStack(Material.WIND_CHARGE, 16)),
                    listOf(ItemStack(Material.MACE))
                ).random()
                items.addAll(setGod)
                plugin.server.broadcast(plugin.messageManager.parse("<gold>¡Alguien ha recibido un objeto <b>LEGENDARIO</b>!</gold>"))
            }
        }
        return items
    }

    private fun crearPocion(tipo: PotionType, splash: Boolean): ItemStack {
        val item = ItemStack(if (splash) Material.SPLASH_POTION else Material.POTION)
        val meta = item.itemMeta as PotionMeta
        meta.basePotionType = tipo
        item.itemMeta = meta
        return item
    }

    // --- HABILIDADES DE ITEMS ESPECIALES ---
    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        if (plugin.eventManager.currentGame != this || !isRunning) return

        if (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK) {
            // Habilidad Carga de Fuego
            if (e.item?.type == Material.FIRE_CHARGE) {
                e.isCancelled = true
                e.item!!.amount -= 1

                val fireball = p.launchProjectile(Fireball::class.java)
                fireball.yield = 2.0f
                fireball.setIsIncendiary(true)
                fireball.velocity = p.location.direction.multiply(1.5)
                p.playSound(p.location, Sound.ENTITY_GHAST_SHOOT, 1f, 1f)
            }
        }
    }

    @EventHandler
    fun onProjectileDamage(e: EntityDamageByEntityEvent) {
        val victim = e.entity as? Player ?: return
        val damager = e.damager

        if (plugin.eventManager.currentGame != this || !isRunning) return

        // Extra Knockback a Bolas de nieve y Huevos
        if (damager is Snowball || damager is Egg) {
            e.damage = 0.01
            val knockback = damager.velocity.normalize().multiply(0.6).setY(0.3)
            victim.velocity = victim.velocity.add(knockback)
            victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1f, 1f)
        }
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return
        val rawWin = plugin.languageManager.get("pillars.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
        stop()
    }

    override fun onStop() {
        removeCages()
        plugin.eventManager.currentGame = null

        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation

        val todos = players + spectators
        todos.forEach { p ->
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}
