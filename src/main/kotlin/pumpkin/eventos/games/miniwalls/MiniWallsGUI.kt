package pumpkin.eventos.games.miniwalls

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import java.io.File

class MiniWallsGUI(private val plugin: PumpkinEventos, private val game: MiniWalls) {

    private var config: YamlConfiguration

    init {
        // Cargar el archivo menus/miniwalls.yml
        val file = File(plugin.dataFolder, "menus/miniwalls.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            // Si no existe, creamos un archivo en blanco (lo ideal es poner saveResource si lo tienes en tu jar)
            file.createNewFile()
        }
        config = YamlConfiguration.loadConfiguration(file)
    }

    fun open(p: Player) {
        val titleStr = config.getString("title") ?: "<dark_gray>⚑ <white><b>Selecciona tu Equipo</b></white> <dark_gray>⚑"
        val rows = config.getInt("rows", 4)

        val gui = Gui.gui()
            .title(plugin.messageManager.parse(titleStr))
            .rows(rows)
            .disableAllInteractions()
            .create()

        val currentTeam = game.playerTeam[p]

        // --- RELLENO ---
        val fillMatName = config.getString("fill_item.material", "BLACK_STAINED_GLASS_PANE")
        val fillMat = Material.matchMaterial(fillMatName!!) ?: Material.BLACK_STAINED_GLASS_PANE
        val fillItem = ItemBuilder.from(fillMat).name(Component.empty()).build()
        for (slot in 0 until (rows * 9)) {
            gui.setItem(slot, ItemBuilder.from(fillItem).asGuiItem())
        }

        // --- EQUIPOS Y BALANCEO ---
        val teamSizes = MwTeam.values().associateWith { team -> game.playerTeam.values.count { it == team } }
        val minPlayersInAnyTeam = teamSizes.values.minOrNull() ?: 0

        MwTeam.values().forEach { team ->
            val isSelected = currentTeam == team
            val membersOfTeam = game.playerTeam.entries.filter { it.value == team }.map { it.key.name }
            val teamCount = teamSizes[team] ?: 0

            // Lógica de Desbalanceo
            var isUnbalanced = false
            if (!isSelected) {
                if ((teamCount + 1) - minPlayersInAnyTeam > 1) {
                    isUnbalanced = true
                }
            }

            val isFull = game.maxTeamSize > 0 && teamCount >= game.maxTeamSize

            // Construir Lore
            val lore = mutableListOf<Component>()
            lore.add(plugin.messageManager.parse("<dark_gray>▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔"))
            lore.add(plugin.messageManager.parse("<gray>Jugadores: <white><b>$teamCount</b></white>"))

            if (membersOfTeam.isEmpty()) {
                lore.add(plugin.messageManager.parse("<dark_gray>  <i>Sin jugadores aún</i>"))
            } else {
                membersOfTeam.forEach { name ->
                    lore.add(plugin.messageManager.parse("  <dark_gray>• <white>$name</white>"))
                }
            }

            lore.add(plugin.messageManager.parse("<dark_gray>▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔"))

            when {
                isSelected -> lore.add(plugin.messageManager.parse("<green><b>✔ ¡Ya estás en este equipo!</b></green>"))
                isFull -> lore.add(plugin.messageManager.parse("<red><b>✘ ¡Este equipo ya está lleno!</b></red>"))
                isUnbalanced -> lore.add(plugin.messageManager.parse("<red><b>⚖ ¡Desbalanceado! Únete a otro equipo.</b></red>"))
                else -> lore.add(plugin.messageManager.parse("<yellow>» <b>¡Clic para unirte!</b></yellow>"))
            }

            val teamColor = team.chatColor
            val closeTag = teamColor.replace("<", "</")

            val itemBuilder = ItemBuilder.from(team.wool)
                .name(plugin.messageManager.parse("$teamColor<b>⚑ ${team.displayName}</b>$closeTag"))
                .lore(lore.toList()) // ¡Solucionado el error aquí!

            if (isSelected) itemBuilder.glow(true)

            // Obtener slot del YAML (Fallback predeterminado por si falta)
            val slot = config.getInt("teams.${team.name}.slot", 10 + team.ordinal * 2)

            gui.setItem(slot, itemBuilder.asGuiItem { _ ->
                if (isSelected) return@asGuiItem

                if (isFull) {
                    p.sendMessage(plugin.messageManager.parse("<red>✘ ¡Este equipo ya está lleno!</red>"))
                    p.playSound(p.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    return@asGuiItem
                }

                if (isUnbalanced) {
                    p.sendMessage(plugin.messageManager.parse("<red>⚖ ¡Los equipos están desbalanceados! Únete a uno con menos jugadores.</red>"))
                    p.playSound(p.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    return@asGuiItem
                }

                game.playerTeam[p] = team

                val newItem = ItemStack(team.wool).apply {
                    itemMeta = itemMeta?.apply { displayName(plugin.messageManager.parse("<aqua><b>🚩 Elegir Equipo</b></aqua>")) }
                }
                p.inventory.setItem(0, newItem)

                val closeTagInner = team.chatColor.replace("<", "</")
                p.sendMessage(plugin.messageManager.parse("<white>✔ Ahora eres del equipo ${team.chatColor}<b>${team.displayName}</b>$closeTagInner<white>!</white>"))
                p.playSound(p.location, Sound.UI_BUTTON_CLICK, 1f, 1.2f)

                gui.close(p)
                // Refrescar menú
                plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> open(p) }, 1L)
            })
        }

        // --- INFO ITEM ---
        val infoSlot = config.getInt("info_item.slot", 31)
        val infoMatName = config.getString("info_item.material", "PAPER")
        val infoMat = Material.matchMaterial(infoMatName!!) ?: Material.PAPER
        val infoName = config.getString("info_item.name", "<aqua><b>ℹ Información</b></aqua>")
        val infoLoreStr = config.getStringList("info_item.lore")

        val infoLoreComps = infoLoreStr.map { plugin.messageManager.parse(it) }

        val infoItem = ItemBuilder.from(infoMat)
            .name(plugin.messageManager.parse(infoName))
            .lore(infoLoreComps)
            .build()

        gui.setItem(infoSlot, ItemBuilder.from(infoItem).asGuiItem())

        gui.open(p)
    }
}
