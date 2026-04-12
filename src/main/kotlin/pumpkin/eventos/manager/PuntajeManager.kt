package pumpkin.eventos.manager

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import java.io.File
import java.util.UUID

class PuntajeManager(private val plugin: PumpkinEventos) {
    private val file = File(plugin.dataFolder, "puntajes.yml")
    private lateinit var config: YamlConfiguration

    // Caché en memoria para acceso ultrarrápido
    private val scores = mutableMapOf<UUID, PlayerScore>()

    data class PlayerScore(var name: String, var points: Int)

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
        config = YamlConfiguration.loadConfiguration(file)
        scores.clear()

        val section = config.getConfigurationSection("scores") ?: return
        for (key in section.getKeys(false)) {
            val uuid = UUID.fromString(key)
            val name = section.getString("$key.name") ?: "Unknown"
            val pts = section.getInt("$key.points", 0)
            scores[uuid] = PlayerScore(name, pts)
        }
    }

    private fun save() {
        // Guardamos de forma asíncrona para que Folia no tenga tirones al escribir en el disco
        val currentScores = scores.toMap()
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            config.set("scores", null)
            for ((uuid, data) in currentScores) {
                config.set("scores.$uuid.name", data.name)
                config.set("scores.$uuid.points", data.points)
            }
            config.save(file)
        }
    }

    fun getPoints(player: Player): Int {
        return scores[player.uniqueId]?.points ?: 0
    }

    fun getPointsByName(name: String): Int? {
        return scores.values.find { it.name.equals(name, ignoreCase = true) }?.points
    }

    fun addPoints(player: Player, amount: Int, reason: String? = null) {
        val data = scores.getOrPut(player.uniqueId) { PlayerScore(player.name, 0) }
        data.name = player.name // Actualizamos el nombre por si se lo cambió
        data.points += amount
        save()

        if (reason != null) {
            player.sendMessage(plugin.messageManager.parse("<#39FF14>+${amount} Puntos</#39FF14> <gray>($reason)</gray>"))
        }
    }

    fun addPointsAdmin(name: String, amount: Int): Boolean {
        val entry = scores.entries.find { it.value.name.equals(name, ignoreCase = true) } ?: return false
        entry.value.points += amount
        save()
        return true
    }

    fun removePointsAdmin(name: String, amount: Int): Boolean {
        val entry = scores.entries.find { it.value.name.equals(name, ignoreCase = true) } ?: return false
        entry.value.points = (entry.value.points - amount).coerceAtLeast(0)
        save()
        return true
    }

    fun clearPoints(name: String): Boolean {
        val entry = scores.entries.find { it.value.name.equals(name, ignoreCase = true) } ?: return false
        entry.value.points = 0
        save()
        return true
    }

    fun clearAll() {
        scores.clear()
        save()
    }

    fun getTop10(): List<PlayerScore> {
        return scores.values.sortedByDescending { it.points }.take(10)
    }
}
