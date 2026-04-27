package pumpkin.eventos.games.skywars

import org.bukkit.Material
import org.bukkit.World
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.Wither
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

enum class SkywarsChest(val nombre: String, val icono: Material) {
    POBRE("<#A9A9A9>Cofres Pobres</#A9A9A9>", Material.WOODEN_SWORD),
    NORMAL("<#39FF14>Cofres Normales</#39FF14>", Material.IRON_SWORD),
    OP("<#00FFFF>Cofres OP</#00FFFF>", Material.DIAMOND_SWORD)
}

enum class SkywarsEvent(val nombre: String, val icono: Material) {
    BORDE("<#FF3131>Cierre de Borde</#FF3131>", Material.BARRIER),
    WITHERS("<#BF00FF>Ataque Wither</#BF00FF>", Material.WITHER_SKELETON_SKULL),
    DRAGONES("<#FF5500>Dragones</#FF5500>", Material.DRAGON_HEAD)
}

// Tipos de TNT disponibles en Skywars
object SkywarsTntType {
    // Clave NBT para identificar el tipo de TNT custom
    const val KEY = "sw_tnt_type"
    const val INSTANT   = "instant"    // Explota al colocarse
    const val THROWABLE = "throwable"  // Se lanza con clic derecho
}

object SkywarsManager {

    // ---- Helpers para items especiales ----------------------------------------

    fun ench(mat: Material, e: Enchantment, lvl: Int, amount: Int = 1): ItemStack {
        val i = ItemStack(mat, amount)
        i.addUnsafeEnchantment(e, lvl)
        return i
    }

    /** Crea una TNT normal (vanilla) */
    fun tntNormal(amount: Int = 1) = ItemStack(Material.TNT, amount)

    /**
     * Crea un item de TNT con un tipo especial (instant / throwable).
     * Se identifica por un tag NBT personalizado en el ItemMeta.
     */
    fun tntCustom(plugin: pumpkin.eventos.PumpkinEventos, tipo: String, displayName: String): ItemStack {
        val item = ItemStack(Material.TNT, 1)
        val meta = item.itemMeta ?: return item
        meta.displayName(plugin.messageManager.parse(displayName))
        val key = org.bukkit.NamespacedKey(plugin, SkywarsTntType.KEY)
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, tipo)
        item.itemMeta = meta
        return item
    }

    /** Crea una Fireball de knockback (FIRE_CHARGE + tag NBT "sw_fireball") */
    fun fireballItem(plugin: pumpkin.eventos.PumpkinEventos, amount: Int = 1): ItemStack {
        val item = ItemStack(Material.FIRE_CHARGE, amount)
        val meta = item.itemMeta ?: return item
        meta.displayName(plugin.messageManager.parse("<#FF6A00><b>🔥 Bola de Fuego</b></#FF6A00>"))
        meta.lore(listOf(
            plugin.messageManager.parse("<gray>Clic derecho para lanzar</gray>"),
            plugin.messageManager.parse("<red>Genera knockback al impactar</red>")
        ))
        val key = org.bukkit.NamespacedKey(plugin, "sw_fireball")
        meta.persistentDataContainer.set(key, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    // ---- Generador de loot ----------------------------------------------------

    fun generarLoot(tipo: SkywarsChest, plugin: pumpkin.eventos.PumpkinEventos? = null): List<ItemStack> {
        val items = mutableListOf<ItemStack>()

        when (tipo) {
            SkywarsChest.POBRE -> {
                val armas = listOf(ItemStack(Material.WOODEN_SWORD), ItemStack(Material.STONE_SWORD), ItemStack(Material.WOODEN_AXE))
                val armaduras = listOf(Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, Material.GOLDEN_HELMET)
                val bloques = listOf(Material.DIRT, Material.OAK_PLANKS)

                items.add(armas.random())
                items.add(ItemStack(armaduras.random()))
                if (Math.random() < 0.5) items.add(ItemStack(armaduras.random()))
                items.add(ItemStack(bloques.random(), (16..32).random()))
                items.add(ItemStack(Material.SNOWBALL, 8))
                items.add(ItemStack(Material.APPLE, 4))
                if (Math.random() < 0.2) items.add(ench(Material.WOODEN_SWORD, Enchantment.SHARPNESS, 1))
                // Herramientas básicas
                items.add(listOf(ItemStack(Material.WOODEN_PICKAXE), ItemStack(Material.STONE_PICKAXE)).random())
                items.add(listOf(ItemStack(Material.WOODEN_AXE), ItemStack(Material.WOODEN_SHOVEL)).random())
                // TNT (baja prob)
                if (Math.random() < 0.25) items.add(tntNormal(1))
                if (plugin != null && Math.random() < 0.10) items.add(tntCustom(plugin, SkywarsTntType.THROWABLE, "<#FF5500><b>TNT Lanzable</b></#FF5500>"))
            }
            SkywarsChest.NORMAL -> {
                val armas = listOf(ItemStack(Material.STONE_SWORD), ItemStack(Material.IRON_SWORD), ench(Material.BOW, Enchantment.POWER, 1))
                val armaduras = listOf(Material.CHAINMAIL_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.IRON_HELMET)
                val bloques = listOf(Material.STONE, Material.OAK_PLANKS, Material.GLASS)

                items.add(armas.random())
                items.add(ItemStack(Material.ARROW, (8..16).random()))
                items.add(ItemStack(armaduras.random()))
                items.add(ItemStack(armaduras.random()))
                items.add(ItemStack(bloques.random(), 64))
                items.add(ItemStack(Material.EGG, 16))
                items.add(ItemStack(Material.COOKED_BEEF, 8))
                if (Math.random() < 0.3) items.add(ItemStack(Material.GOLDEN_APPLE, 1))
                if (Math.random() < 0.1) items.add(ItemStack(Material.ENDER_PEARL, 1))
                if (Math.random() < 0.2) items.add(ench(Material.IRON_SWORD, Enchantment.SHARPNESS, 1))
                // Herramientas
                items.add(listOf(ItemStack(Material.STONE_PICKAXE), ItemStack(Material.IRON_PICKAXE)).random())
                items.add(listOf(ItemStack(Material.STONE_AXE), ItemStack(Material.STONE_SHOVEL)).random())
                items.add(ItemStack(Material.SHIELD, 1))
                // TNT
                if (Math.random() < 0.35) items.add(tntNormal((1..2).random()))
                if (plugin != null) {
                    if (Math.random() < 0.20) items.add(tntCustom(plugin, SkywarsTntType.INSTANT, "<#FF3131><b>TNT Instantánea</b></#FF3131>"))
                    if (Math.random() < 0.20) items.add(tntCustom(plugin, SkywarsTntType.THROWABLE, "<#FF5500><b>TNT Lanzable</b></#FF5500>"))
                    if (Math.random() < 0.15) items.add(fireballItem(plugin))
                }
                items.add(ItemStack(Material.FLINT_AND_STEEL, 1))
            }
            SkywarsChest.OP -> {
                val armas = listOf(
                    ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 2),
                    ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 1),
                    ench(Material.BOW, Enchantment.POWER, 3)
                )
                val armaduras = listOf(
                    ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 2),
                    ench(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, 1),
                    ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 2),
                    ench(Material.NETHERITE_LEGGINGS, Enchantment.PROTECTION, 1),
                    ench(Material.DIAMOND_BOOTS, Enchantment.FEATHER_FALLING, 4),
                    ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 2)
                )
                val bloques = listOf(Material.COBBLESTONE, Material.OAK_WOOD, Material.OBSIDIAN)

                items.add(armas.random())
                items.add(ItemStack(Material.ARROW, 32))
                items.add(armaduras.random())
                items.add(armaduras.random())
                items.add(armaduras.random())
                items.add(ItemStack(bloques.random(), 64))
                items.add(ItemStack(Material.ENDER_PEARL, (2..4).random()))
                items.add(ItemStack(Material.GOLDEN_APPLE, (3..6).random()))
                items.add(ItemStack(Material.ENCHANTED_GOLDEN_APPLE, (1..2).random()))
                items.add(ItemStack(Material.WATER_BUCKET, 1))
                if (Math.random() < 0.5) items.add(ItemStack(Material.LAVA_BUCKET, 1))
                // Herramientas OP
                items.add(ench(Material.IRON_PICKAXE, Enchantment.EFFICIENCY, 3))
                items.add(ench(Material.IRON_AXE, Enchantment.SHARPNESS, 2))
                items.add(ItemStack(Material.IRON_SHOVEL, 1))
                items.add(ItemStack(Material.SHIELD, 1))
                // TNT OP
                items.add(tntNormal((2..4).random()))
                if (plugin != null) {
                    items.add(tntCustom(plugin, SkywarsTntType.INSTANT, "<#FF3131><b>TNT Instantánea</b></#FF3131>"))
                    items.add(tntCustom(plugin, SkywarsTntType.THROWABLE, "<#FF5500><b>TNT Lanzable</b></#FF5500>"))
                    items.add(fireballItem(plugin, (1..2).random()))
                }
                items.add(ItemStack(Material.FLINT_AND_STEEL, 1))
            }
        }
        return items
    }

    fun ejecutarEventoFinal(evento: SkywarsEvent, world: World, center: org.bukkit.Location, plugin: pumpkin.eventos.PumpkinEventos) {
        when (evento) {
            SkywarsEvent.BORDE -> {
                val border = world.worldBorder
                border.center = center
                border.size = 150.0 // Tamaño inicial
                border.setSize(10.0, 60L) // Se reduce a 10 bloques en 60 segundos
                plugin.server.broadcast(plugin.messageManager.parse("<red>⚠️ <b>¡EL BORDE SE ESTÁ CERRANDO!</b></red>"))
            }
            SkywarsEvent.WITHERS -> {
                plugin.server.regionScheduler.run(plugin, center) { _ ->
                    val w1 = world.spawnEntity(center.clone().add(5.0, 15.0, 5.0), EntityType.WITHER) as Wither
                    val w2 = world.spawnEntity(center.clone().add(-5.0, 15.0, -5.0), EntityType.WITHER) as Wither
                    w1.target = null; w2.target = null
                }
                plugin.server.broadcast(plugin.messageManager.parse("<#BF00FF>☠ <b>¡LOS WITHERS HAN SIDO LIBERADOS!</b></#BF00FF>"))
            }
            SkywarsEvent.DRAGONES -> {
                plugin.server.regionScheduler.run(plugin, center) { _ ->
                    world.spawnEntity(center.clone().add(0.0, 20.0, 0.0), EntityType.ENDER_DRAGON)
                }
                plugin.server.broadcast(plugin.messageManager.parse("<#FF5500>🐉 <b>¡UN DRAGÓN DE ENDER HA APARECIDO!</b></#FF5500>"))
            }
        }
    }
}
