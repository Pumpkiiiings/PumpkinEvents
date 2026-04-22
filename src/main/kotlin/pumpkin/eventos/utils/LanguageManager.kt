package pumpkin.eventos.utils

import org.bukkit.configuration.file.YamlConfiguration
import pumpkin.eventos.PumpkinEventos
import java.io.File

class LanguageManager(private val plugin: PumpkinEventos) {
    private val messagesFile = File(plugin.dataFolder, "messages.yml")
    private val scoreboardsFile = File(plugin.dataFolder, "scoreboards.yml")

    private lateinit var messagesConfig: YamlConfiguration
    private lateinit var scoreboardsConfig: YamlConfiguration

    init { reload() }

    fun reload() {
        if (!messagesFile.exists()) plugin.saveResource("messages.yml", false)
        if (!scoreboardsFile.exists()) plugin.saveResource("scoreboards.yml", false)
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile)
        scoreboardsConfig = YamlConfiguration.loadConfiguration(scoreboardsFile)
    }

    /** Leer desde messages.yml y reemplazar prefix automáticamente */
    fun get(path: String, def: String = "<red>Error: Mensaje no encontrado ($path)</red>"): String {
        val raw = messagesConfig.getString(path) ?: def
        val prefix = messagesConfig.getString("prefix") ?: "<bold><gradient:#FF8C00:#FFCC00>PumpkinEvents</gradient></bold> <dark_gray>»</dark_gray>"
        return raw.replace("<prefix>", prefix)
    }

    /** Leer lista desde messages.yml */
    fun getList(path: String): List<String> {
        return messagesConfig.getStringList(path)
    }

    /** Leer desde scoreboards.yml */
    fun getScoreboard(path: String, def: String = ""): String {
        return scoreboardsConfig.getString(path) ?: def
    }

    /** Leer lista desde scoreboards.yml */
    fun getScoreboardList(path: String): List<String> {
        return scoreboardsConfig.getStringList(path)
    }
}
