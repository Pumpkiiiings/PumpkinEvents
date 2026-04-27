package pumpkin.eventos.games.battleroyale

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

object BattleRoyaleLoot {

    fun generarLootNormal(): List<ItemStack> {
        val items = mutableListOf<ItemStack>()
        val armas = listOf(
            ItemStack(Material.STONE_SWORD),
            ItemStack(Material.IRON_SWORD),
            ench(Material.BOW, Enchantment.POWER, 1)
        )
        val armaduras = listOf(
            Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS,
            Material.IRON_BOOTS, Material.IRON_HELMET,
            Material.LEATHER_CHESTPLATE, Material.GOLDEN_HELMET
        )
        items.add(armas.random())
        items.add(ItemStack(Material.ARROW, (8..20).random()))
        items.add(ItemStack(armaduras.random()))
        if (Math.random() < 0.6) items.add(ItemStack(armaduras.random()))
        items.add(ItemStack(listOf(Material.OAK_PLANKS, Material.COBBLESTONE, Material.STONE).random(), (16..32).random()))
        items.add(ItemStack(Material.COOKED_BEEF, (4..8).random()))
        if (Math.random() < 0.25) items.add(ItemStack(Material.GOLDEN_APPLE, 1))
        if (Math.random() < 0.1) items.add(ItemStack(Material.ENDER_PEARL, 1))
        items.add(listOf(ItemStack(Material.STONE_PICKAXE), ItemStack(Material.IRON_PICKAXE)).random())
        items.add(listOf(ItemStack(Material.STONE_AXE), ItemStack(Material.STONE_SHOVEL)).random())
        if (Math.random() < 0.3) items.add(ItemStack(Material.SHIELD))
        if (Math.random() < 0.3) items.add(ItemStack(Material.TNT, (1..2).random()))
        return items
    }

    fun generarLootOP(): List<ItemStack> {
        val items = mutableListOf<ItemStack>()
        val armas = listOf(
            ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3),
            ench(Material.IRON_SWORD, Enchantment.SHARPNESS, 2),
            ench(Material.BOW, Enchantment.POWER, 3)
        )
        val armaduras = listOf(
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 2),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 2),
            ench(Material.DIAMOND_BOOTS, Enchantment.FEATHER_FALLING, 3),
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 2)
        )
        items.add(armas.random())
        items.add(ItemStack(Material.ARROW, 32))
        items.addAll(armaduras.shuffled().take(3))
        items.add(ItemStack(Material.GOLDEN_APPLE, (3..6).random()))
        items.add(ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1))
        items.add(ItemStack(Material.ENDER_PEARL, (2..4).random()))
        items.add(ench(Material.IRON_PICKAXE, Enchantment.EFFICIENCY, 3))
        items.add(ench(Material.IRON_AXE, Enchantment.SHARPNESS, 2))
        items.add(ItemStack(Material.SHIELD))
        items.add(ItemStack(Material.TNT, (2..4).random()))
        items.add(ItemStack(Material.WATER_BUCKET))
        if (Math.random() < 0.4) items.add(ItemStack(Material.LAVA_BUCKET))
        return items
    }

    private fun ench(mat: Material, e: Enchantment, lvl: Int, amount: Int = 1): ItemStack {
        val i = ItemStack(mat, amount)
        i.addUnsafeEnchantment(e, lvl)
        return i
    }

    fun kitInicial(): Array<ItemStack?> {
        return arrayOf(
            ItemStack(Material.WOODEN_SWORD),
            ItemStack(Material.WOODEN_PICKAXE),
            ItemStack(Material.WOODEN_AXE),
            ItemStack(Material.WOODEN_SHOVEL)
        )
    }

    fun kitArmaduraInicial(): Map<String, ItemStack> = mapOf(
        "helmet" to ItemStack(Material.LEATHER_HELMET),
        "boots" to ItemStack(Material.LEATHER_BOOTS)
    )
}
