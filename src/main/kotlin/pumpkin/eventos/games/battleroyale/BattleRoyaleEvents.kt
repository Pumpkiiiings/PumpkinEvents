package pumpkin.eventos.games.battleroyale

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// ── Fases del borde ───────────────────────────────────────────────────────────
data class BRBorderPhase(
    val targetSize: Double,      // radio en bloques al que se cierra
    val shrinkSeconds: Long,     // cuántos segundos tarda en cerrar
    val damagePerSecond: Double, // daño/s fuera del borde
    val label: String
)

val BR_BORDER_PHASES = listOf(
    BRBorderPhase(200.0, 120L, 1.0, "Zona Segura"),
    BRBorderPhase(100.0,  90L, 2.0, "Zona Peligrosa"),
    BRBorderPhase( 50.0,  60L, 4.0, "Zona Crítica"),
    BRBorderPhase( 15.0,  30L, 8.0, "Zona Final")
)

/** Segundos restantes en los que se activa cada fase del borde. */
val BR_PHASE_TIMESTAMPS = listOf(300, 180, 90, 30)

// ── Generador de loot ─────────────────────────────────────────────────────────
object BRLootManager {

    private fun chance(prob: Double) = Math.random() < prob

    private fun ench(mat: Material, e: Enchantment, lvl: Int): ItemStack =
        ItemStack(mat).also { it.addUnsafeEnchantment(e, lvl) }

    private fun potion(type: PotionEffectType, duration: Int, amplifier: Int): ItemStack {
        val item = ItemStack(Material.POTION)
        val meta = item.itemMeta as? PotionMeta ?: return item
        meta.addCustomEffect(PotionEffect(type, duration, amplifier), true)
        item.itemMeta = meta
        return item
    }

    private fun splashPotion(type: PotionEffectType, duration: Int, amplifier: Int): ItemStack {
        val item = ItemStack(Material.SPLASH_POTION)
        val meta = item.itemMeta as? PotionMeta ?: return item
        meta.addCustomEffect(PotionEffect(type, duration, amplifier), true)
        item.itemMeta = meta
        return item
    }

    /**
     * Tabla de loot única del Battle Royale.
     * Sin votación — siempre se genera este pool.
     *
     * ─ Común:      madera, piedra, cuero/hierro, comida, escudo
     * ─ Ocasional:  arco, flechas, cubos de agua, tijeras, telarañas, pociones básicas
     * ─ Raro:       encantamientos menores, diamantes (mineral/lingote), lava, esponjas
     * ─ Muy raro:   espada/armadura diamante sin encanto, manzana dorada encantada
     * ─ Extremo:    netherite, diamante+encantamiento
     */
    fun generarLoot(): List<ItemStack> {
        val items = mutableListOf<ItemStack>()

        // ── ARMA PRINCIPAL ─────────────────────────────────────────────────────
        val arma = when {
            chance(0.03) -> ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 1)  // muy raro
            chance(0.08) -> ItemStack(Material.DIAMOND_SWORD)                        // raro
            chance(0.25) -> if (chance(0.3)) ench(Material.IRON_SWORD, Enchantment.SHARPNESS, 1)
                            else ItemStack(Material.IRON_SWORD)
            chance(0.45) -> ItemStack(Material.STONE_SWORD)
            else         -> listOf(Material.WOODEN_SWORD, Material.STONE_AXE, Material.WOODEN_AXE).random().let { ItemStack(it) }
        }
        items.add(arma)

        // ── ARMADURA (1-3 piezas) ──────────────────────────────────────────────
        val armorPools = mapOf(
            Material.LEATHER_HELMET       to 0.50,
            Material.LEATHER_CHESTPLATE   to 0.45,
            Material.LEATHER_LEGGINGS     to 0.45,
            Material.LEATHER_BOOTS        to 0.50,
            Material.CHAINMAIL_HELMET     to 0.25,
            Material.CHAINMAIL_CHESTPLATE to 0.20,
            Material.IRON_HELMET          to 0.15,
            Material.IRON_CHESTPLATE      to 0.10,
            Material.IRON_LEGGINGS        to 0.10,
            Material.IRON_BOOTS           to 0.12,
            Material.GOLDEN_HELMET        to 0.20,
            Material.DIAMOND_HELMET       to 0.03,
            Material.DIAMOND_CHESTPLATE   to 0.02,
            Material.DIAMOND_LEGGINGS     to 0.02,
            Material.DIAMOND_BOOTS        to 0.03
        )
        val piecesAdded = (1..3).random()
        var added = 0
        for ((mat, prob) in armorPools.entries.shuffled()) {
            if (added >= piecesAdded) break
            if (chance(prob)) {
                val piece = if (chance(0.08)) ench(mat, Enchantment.PROTECTION, 1) else ItemStack(mat)
                items.add(piece)
                added++
            }
        }

        // ── HERRAMIENTA DE MINERÍA ────────────────────────────────────────────
        items.add(when {
            chance(0.08) -> if (chance(0.3)) ench(Material.IRON_PICKAXE, Enchantment.EFFICIENCY, 1)
                            else ItemStack(Material.IRON_PICKAXE)
            chance(0.40) -> ItemStack(Material.STONE_PICKAXE)
            else         -> ItemStack(Material.WOODEN_PICKAXE)
        })

        // ── HACHA / HERRAMIENTA SECUNDARIA ────────────────────────────────────
        if (chance(0.35)) {
            items.add(listOf(
                ItemStack(Material.WOODEN_AXE),
                ItemStack(Material.STONE_AXE),
                ItemStack(Material.IRON_AXE)
            ).random())
        }

        // ── ESCUDO ───────────────────────────────────────────────────────────
        if (chance(0.65)) items.add(ItemStack(Material.SHIELD))

        // ── ARCO + FLECHAS ────────────────────────────────────────────────────
        if (chance(0.45)) {
            val bow = if (chance(0.12)) ench(Material.BOW, Enchantment.POWER, 1) else ItemStack(Material.BOW)
            items.add(bow)
            items.add(ItemStack(Material.ARROW, (6..20).random()))
        }

        // ── TIJERAS ───────────────────────────────────────────────────────────
        if (chance(0.40)) items.add(ItemStack(Material.SHEARS))

        // ── BLOQUES ───────────────────────────────────────────────────────────
        val bloques = listOf(Material.OAK_PLANKS, Material.STONE, Material.COBBLESTONE,
                             Material.DIRT, Material.GRAVEL, Material.SAND)
        items.add(ItemStack(bloques.random(), (8..32).random()))
        if (chance(0.3)) items.add(ItemStack(bloques.random(), (8..16).random()))

        // ── TELARAÑAS ────────────────────────────────────────────────────────
        if (chance(0.40)) items.add(ItemStack(Material.COBWEB, (2..8).random()))

        // ── CUBOS DE AGUA ────────────────────────────────────────────────────
        if (chance(0.40)) items.add(ItemStack(Material.WATER_BUCKET))

        // ── LAVA ─────────────────────────────────────────────────────────────
        if (chance(0.15)) items.add(ItemStack(Material.LAVA_BUCKET))

        // ── ESPONJAS ─────────────────────────────────────────────────────────
        if (chance(0.15)) items.add(ItemStack(Material.SPONGE, (1..3).random()))

        // ── COMIDA ───────────────────────────────────────────────────────────
        items.add(ItemStack(Material.BREAD, (3..6).random()))
        if (chance(0.35)) items.add(ItemStack(Material.COOKED_BEEF, (2..4).random()))
        if (chance(0.40)) items.add(ItemStack(Material.APPLE, (1..3).random()))
        if (chance(0.12)) items.add(ItemStack(Material.GOLDEN_APPLE))
        if (chance(0.02)) items.add(ItemStack(Material.ENCHANTED_GOLDEN_APPLE)) // extremadamente raro

        // ── POCIONES ─────────────────────────────────────────────────────────
        if (chance(0.45)) items.add(potion(PotionEffectType.INSTANT_HEALTH, 1, 0))
        if (chance(0.35)) items.add(potion(PotionEffectType.REGENERATION, 400, 0))
        if (chance(0.30)) items.add(potion(PotionEffectType.SPEED, 600, 0))
        if (chance(0.15)) items.add(potion(PotionEffectType.STRENGTH, 400, 0))
        if (chance(0.10)) items.add(potion(PotionEffectType.FIRE_RESISTANCE, 800, 0))
        if (chance(0.12)) items.add(splashPotion(PotionEffectType.INSTANT_HEALTH, 1, 0))
        if (chance(0.08)) items.add(splashPotion(PotionEffectType.SLOWNESS, 200, 1))
        if (chance(0.06)) items.add(splashPotion(PotionEffectType.WEAKNESS, 300, 0))

        // ── MINERALES / RECURSOS RAROS ────────────────────────────────────────
        if (chance(0.20)) items.add(ItemStack(Material.IRON_INGOT, (2..6).random()))
        if (chance(0.10)) items.add(ItemStack(Material.GOLD_INGOT, (1..3).random()))
        if (chance(0.08)) items.add(ItemStack(Material.DIAMOND, (1..3).random()))
        if (chance(0.05)) items.add(ItemStack(Material.DIAMOND_ORE, (1..2).random()))
        if (chance(0.01)) items.add(ItemStack(Material.NETHERITE_INGOT)) // extremadamente raro

        // ── PERLA DE ENDER ────────────────────────────────────────────────────
        if (chance(0.10)) items.add(ItemStack(Material.ENDER_PEARL, (1..2).random()))

        // ── ESLABÓN + PEDERNAL ────────────────────────────────────────────────
        if (chance(0.20)) items.add(ItemStack(Material.FLINT_AND_STEEL))

        return items
    }
}
