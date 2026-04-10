package pumpkin.eventos.utils

import org.bukkit.configuration.file.YamlConfiguration
import pumpkin.eventos.PumpkinEventos
import java.io.File

class LanguageManager(private val plugin: PumpkinEventos) {
    private val file = File(plugin.dataFolder, "messages.yml")
    private lateinit var config: YamlConfiguration

    init { reload() }

    fun reload() {
        if (!file.exists()) plugin.saveResource("messages.yml", false)
        config = YamlConfiguration.loadConfiguration(file)
    }

    fun get(path: String, def: String = "<red>Error: Mensaje no encontrado ($path)</red>"): String {
        return config.getString(path) ?: def
    }

    // --- NUEVA FUNCIÓN PARA LEER LISTAS ---
    fun getList(path: String): List<String> {
        return config.getStringList(path)
    }
}
