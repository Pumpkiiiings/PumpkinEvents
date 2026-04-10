package pumpkin.eventos.hud

import org.bukkit.configuration.file.YamlConfiguration
import pumpkin.eventos.PumpkinEventos
import java.io.File

class ChatFormatManager(private val plugin: PumpkinEventos) {
    private val file = File(plugin.dataFolder, "chat-format.yml")
    private lateinit var config: YamlConfiguration

    init { reload() }

    fun reload() {
        if (!file.exists()) plugin.saveResource("chat-format.yml", false)
        config = YamlConfiguration.loadConfiguration(file)
    }

    private fun getRankData(group: String, path: String, def: String): String {
        return config.getString("ranks.$group.$path") ?: config.getString("ranks.default.$path") ?: def
    }

    fun getFormat(group: String) = getRankData(group, "format", "<prefix><player><suffix> <white>» <message>")
    fun getClickAction(group: String) = getRankData(group, "click", "/msg <player> ")
    fun getHover(group: String): List<String> {
        val hover = config.getStringList("ranks.$group.hover")
        return if (hover.isEmpty()) config.getStringList("ranks.default.hover") else hover
    }
}
