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

        when (tipo) {
            SkywarsChest.POBRE -> {
                val armas = listOf(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.WOODEN_AXE)
                val armaduras = listOf(Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, Material.GOLDEN_HELMET)
                val bloques = listOf(Material.DIRT, Material.OAK_PLANKS)

                items.add(ItemStack(armas.random()))
                items.add(ItemStack(armaduras.random()))
                if (Math.random() < 0.5) items.add(ItemStack(armaduras.random()))
                items.add(ItemStack(bloques.random(), (16..32).random()))
                items.add(ItemStack(Material.SNOWBALL, 8))
                items.add(ItemStack(Material.APPLE, 4))
            }
            SkywarsChest.NORMAL -> {
                val armas = listOf(Material.STONE_SWORD, Material.IRON_SWORD, Material.IRON_AXE, Material.BOW)
                val armaduras = listOf(Material.CHAINMAIL_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.IRON_HELMET)
                val bloques = listOf(Material.STONE, Material.OAK_PLANKS, Material.GLASS)

                items.add(ItemStack(armas.random()))
                if (Math.random() < 0.3) items.add(ItemStack(Material.ARROW, 12))
                items.add(ItemStack(armaduras.random()))
                items.add(ItemStack(armaduras.random()))
                items.add(ItemStack(bloques.random(), 64))
                items.add(ItemStack(Material.EGG, 16))
                items.add(ItemStack(Material.COOKED_BEEF, 8))
                if (Math.random() < 0.2) items.add(ItemStack(Material.GOLDEN_APPLE, 1))
                if (Math.random() < 0.1) items.add(ItemStack(Material.ENDER_PEARL, 1))
            }
            SkywarsChest.OP -> {
                val armas = listOf(Material.DIAMOND_SWORD, Material.DIAMOND_AXE, Material.BOW)
                val armaduras = listOf(Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, Material.DIAMOND_HELMET)
                val bloques = listOf(Material.COBBLESTONE, Material.OAK_WOOD)

                items.add(ItemStack(armas.random()))
                items.add(ItemStack(Material.ARROW, 32))
                items.add(ItemStack(armaduras.random()))
                items.add(ItemStack(armaduras.random()))
                items.add(ItemStack(bloques.random(), 64))
                items.add(ItemStack(bloques.random(), 64))
                items.add(ItemStack(Material.ENDER_PEARL, (1..3).random()))
                items.add(ItemStack(Material.GOLDEN_APPLE, (2..4).random()))
                if (Math.random() < 0.3) items.add(ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1)) // 30% Notch Apple
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
