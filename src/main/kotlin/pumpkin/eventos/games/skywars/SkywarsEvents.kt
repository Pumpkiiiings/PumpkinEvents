package pumpkin.eventos.games.skywars

import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.Wither
import org.bukkit.inventory.ItemStack

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

object SkywarsManager {

    fun generarLoot(tipo: SkywarsChest): List<ItemStack> {
        val items = mutableListOf<ItemStack>()

        fun ench(mat: Material, e: org.bukkit.enchantments.Enchantment, lvl: Int, amount: Int = 1): ItemStack {
            val i = ItemStack(mat, amount)
            i.addUnsafeEnchantment(e, lvl)
            return i
        }

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
                if (Math.random() < 0.2) items.add(ench(Material.WOODEN_SWORD, org.bukkit.enchantments.Enchantment.SHARPNESS, 1))
            }
            SkywarsChest.NORMAL -> {
                val armas = listOf(ItemStack(Material.STONE_SWORD), ItemStack(Material.IRON_SWORD), ench(Material.BOW, org.bukkit.enchantments.Enchantment.POWER, 1))
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
                if (Math.random() < 0.2) items.add(ench(Material.IRON_SWORD, org.bukkit.enchantments.Enchantment.SHARPNESS, 1))
            }
            SkywarsChest.OP -> {
                val armas = listOf(
                    ench(Material.DIAMOND_SWORD, org.bukkit.enchantments.Enchantment.SHARPNESS, 2),
                    ench(Material.NETHERITE_SWORD, org.bukkit.enchantments.Enchantment.SHARPNESS, 1),
                    ench(Material.BOW, org.bukkit.enchantments.Enchantment.POWER, 3)
                )
                val armaduras = listOf(
                    ench(Material.DIAMOND_CHESTPLATE, org.bukkit.enchantments.Enchantment.PROTECTION, 2),
                    ench(Material.NETHERITE_CHESTPLATE, org.bukkit.enchantments.Enchantment.PROTECTION, 1),
                    ench(Material.DIAMOND_LEGGINGS, org.bukkit.enchantments.Enchantment.PROTECTION, 2),
                    ench(Material.NETHERITE_LEGGINGS, org.bukkit.enchantments.Enchantment.PROTECTION, 1),
                    ench(Material.DIAMOND_BOOTS, org.bukkit.enchantments.Enchantment.FEATHER_FALLING, 4),
                    ench(Material.DIAMOND_HELMET, org.bukkit.enchantments.Enchantment.PROTECTION, 2)
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
