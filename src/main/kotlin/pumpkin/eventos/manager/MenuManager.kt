package pumpkin.eventos.manager

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import java.io.File

/**
 * Sistema de menús dinámicos leídos desde archivos YAML en la carpeta menus/.
 * Cada menú es totalmente editable: título, filas, items, lore, acciones, enchants.
 */
class MenuManager(private val plugin: PumpkinEventos) : Listener {

    data class MenuItem(
        val slot: Int,
        val material: Material,
        val name: String,
        val lore: List<String>,
        val action: String,
        val enchanted: Boolean
    )

    data class MenuConfig(
        val id: String,
        val title: String,
        val rows: Int,
        val fillEnabled: Boolean,
        val fillMaterial: Material,
        val fillName: String,
        val items: List<MenuItem>
    )

    private val menus = mutableMapOf<String, MenuConfig>()
    private val menuFiles = mutableMapOf<String, YamlConfiguration>()
    // Track qué inventario abierto corresponde a qué menú
    private val openInventories = mutableMapOf<String, Inventory>() // inventoryTitle -> MenuConfig id

    init {
        reload()
    }

    fun reload() {
        menus.clear()
        val menusDir = File(plugin.dataFolder, "menus")
        if (!menusDir.exists() && !menusDir.mkdirs()) {
            plugin.componentLogger.warn("[MenuManager] No se pudo crear la carpeta de menús.")
            return
        }
        listOf("miniwalls.yml", "voteskywars.yml", "team-selection.yml", "iceboat.yml", "simondice.yml", "parkour.yml").forEach { resourceName ->
            val target = File(menusDir, resourceName)
            if (!target.exists()) {
                plugin.saveResource("menus/$resourceName", false)
            }
        }
        menusDir.listFiles { f -> f.extension == "yml" }?.forEach { file ->
            try {
                val cfg = YamlConfiguration.loadConfiguration(file)
                val id = file.nameWithoutExtension
                menuFiles[id] = cfg

                val title = cfg.getString("title", "<white>Menú</white>")!!
                val rows = cfg.getInt("rows", 3).coerceIn(1, 6)
                val fillEnabled = cfg.getBoolean("fill.enabled", false)
                val fillMat = Material.getMaterial(cfg.getString("fill.material", "GRAY_STAINED_GLASS_PANE")!!) ?: Material.GRAY_STAINED_GLASS_PANE
                val fillName = cfg.getString("fill.name", " ")!!

                val itemsSection = cfg.getConfigurationSection("items")
                val items = mutableListOf<MenuItem>()

                itemsSection?.getKeys(false)?.forEach { key ->
                    val base = "items.$key"
                    val slot = cfg.getInt("$base.slot", 0)
                    val matName = cfg.getString("$base.material", "STONE")!!
                    val mat = Material.getMaterial(matName) ?: Material.STONE
                    val name = cfg.getString("$base.name", key)!!
                    val lore = cfg.getStringList("$base.lore")
                    val action = cfg.getString("$base.action", "none")!!
                    val enchanted = cfg.getBoolean("$base.enchanted", false)
                    items.add(MenuItem(slot, mat, name, lore, action, enchanted))
                }

                menus[id] = MenuConfig(id, title, rows, fillEnabled, fillMat, fillName, items)
                plugin.componentLogger.info(plugin.messageManager.parse("<gray>[MenuManager] Menú cargado: $id (${items.size} items)</gray>"))
            } catch (e: Exception) {
                plugin.componentLogger.warn(plugin.messageManager.parse("<yellow>[MenuManager] Error al cargar ${file.name}: ${e.message}</yellow>"))
            }
        }
    }

    /** Lectura segura para menús con acciones dinámicas (equipos, votos y retos). */
    fun text(menuId: String, path: String, fallback: String): String =
        menuFiles[menuId]?.getString(path)?.takeIf { it.isNotBlank() } ?: fallback

    fun lines(menuId: String, path: String, fallback: List<String> = emptyList()): List<String> =
        menuFiles[menuId]?.getStringList(path)?.takeIf { it.isNotEmpty() } ?: fallback

    fun number(menuId: String, path: String, fallback: Int): Int =
        menuFiles[menuId]?.takeIf { it.contains(path) }?.getInt(path) ?: fallback

    fun material(menuId: String, path: String, fallback: Material): Material =
        menuFiles[menuId]?.getString(path)?.let(Material::matchMaterial) ?: fallback

    fun openMenu(player: Player, menuId: String) {
        val config = menus[menuId] ?: run {
            plugin.languageManager.send(player, "menu.not_found", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("menu", menuId))
            return
        }

        val titleComp = plugin.messageManager.parse(config.title)
        val inv: Inventory = Bukkit.createInventory(null, config.rows * 9, titleComp)

        // Fill decorativo
        if (config.fillEnabled) {
            val fillItem = ItemStack(config.fillMaterial).apply {
                itemMeta = itemMeta?.apply {
                    displayName(plugin.messageManager.parse(config.fillName))
                    addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
                }
            }
            for (i in 0 until inv.size) inv.setItem(i, fillItem)
        }

        // Colocar items
        config.items.forEach { menuItem ->
            if (menuItem.slot < inv.size) {
                val stack = ItemStack(menuItem.material).apply {
                    itemMeta = itemMeta?.apply {
                        displayName(plugin.messageManager.parse(menuItem.name))
                        lore(menuItem.lore.map { plugin.messageManager.parse(it) })
                        addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
                        if (menuItem.enchanted) {
                            addEnchant(Enchantment.UNBREAKING, 1, true)
                            addItemFlags(ItemFlag.HIDE_ENCHANTS)
                        }
                    }
                }
                inv.setItem(menuItem.slot, stack)
            }
        }

        player.openInventory(inv)
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return
        val clickedInv = e.inventory

        // Buscar si este inventario corresponde a algún menú activo
        val titleStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(clickedInv.type.defaultTitle().let { clickedInv.viewers.firstOrNull()?.openInventory?.title()
                ?: net.kyori.adventure.text.Component.empty() })

        // Buscar el menú correspondiente al inventario abierto
        val matchedConfig = menus.values.find { menuCfg ->
            plugin.messageManager.parse(menuCfg.title).let { comp ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(comp) ==
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                    player.openInventory.title() ?: net.kyori.adventure.text.Component.empty()
                )
            }
        } ?: return

        e.isCancelled = true

        val slot = e.rawSlot
        if (slot < 0 || slot >= e.inventory.size) return

        val menuItem = matchedConfig.items.find { it.slot == slot } ?: return
        handleAction(player, menuItem.action)
    }

    private fun handleAction(player: Player, action: String) {
        when {
            action == "none" -> { /* nada */ }
            action == "close" -> player.closeInventory()
            action.startsWith("vote:") -> {
                val option = action.removePrefix("vote:")
                player.closeInventory()
                plugin.languageManager.send(player, "menu.voted", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("option", option))
                // Aquí se puede conectar con un sistema de votos específico del juego
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    plugin.server.dispatchCommand(player, "pvote $option")
                }
            }
            action.startsWith("command:") -> {
                val cmd = action.removePrefix("command:")
                player.closeInventory()
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    plugin.server.dispatchCommand(player, cmd)
                }
            }
        }
    }
}
