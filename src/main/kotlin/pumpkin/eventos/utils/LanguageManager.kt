package pumpkin.eventos.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

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

        // Los servidores existentes conservan su archivo personalizado, pero
        // reciben como fallback cualquier clave nueva incluida en el JAR.
        plugin.getResource("messages.yml")?.use { stream ->
            messagesConfig.setDefaults(YamlConfiguration.loadConfiguration(InputStreamReader(stream, StandardCharsets.UTF_8)))
        }
        plugin.getResource("scoreboards.yml")?.use { stream ->
            scoreboardsConfig.setDefaults(YamlConfiguration.loadConfiguration(InputStreamReader(stream, StandardCharsets.UTF_8)))
        }
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

    /** Obtiene y renderiza un mensaje sin repetir el acceso a ambos managers. */
    fun component(path: String, vararg resolvers: TagResolver): Component =
        plugin.messageManager.parse(get(path), *resolvers)

    /** Versión que también resuelve placeholders propios del jugador. */
    fun component(player: Player, path: String, vararg resolvers: TagResolver): Component =
        plugin.messageManager.parse(player, get(path), *resolvers)

    fun send(player: Player, path: String, vararg resolvers: TagResolver) {
        player.sendMessage(component(player, path, *resolvers))
    }

    fun send(audience: Audience, path: String, vararg resolvers: TagResolver) {
        val component = if (audience is Player) component(audience, path, *resolvers) else component(path, *resolvers)
        audience.sendMessage(component)
    }

    fun broadcast(path: String, vararg resolvers: TagResolver) {
        Bukkit.broadcast(component(path, *resolvers))
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
