package pumpkin.eventos.manager

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PuntajeManager(private val plugin: PumpkinEventos) {
    private val file = File(plugin.dataFolder, "puntajes.yml")
    private lateinit var config: YamlConfiguration

    // Caché en memoria para acceso ultrarrápido
    private val scores = mutableMapOf<UUID, PlayerScore>()
    private val scoreLock = Any()
    private val saveExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PumpkinEventos-score-save").apply { isDaemon = true }
    }

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
        val snapshot = snapshot()
        saveExecutor.execute { writeSnapshot(snapshot) }
    }

    private fun snapshot(): Map<UUID, PlayerScore> = synchronized(scoreLock) {
        scores.mapValues { (_, data) -> PlayerScore(data.name, data.points) }
    }

    private fun writeSnapshot(snapshot: Map<UUID, PlayerScore>) {
        try {
            config.set("scores", null)
            for ((uuid, data) in snapshot) {
                config.set("scores.$uuid.name", data.name)
                config.set("scores.$uuid.points", data.points)
            }
            config.save(file)
        } catch (error: Exception) {
            plugin.componentLogger.error("No se pudieron guardar los puntajes.", error)
        }
    }

    fun getPoints(player: Player): Int {
        return synchronized(scoreLock) { scores[player.uniqueId]?.points ?: 0 }
    }

    fun getPointsByName(name: String): Int? {
        return synchronized(scoreLock) { scores.values.find { it.name.equals(name, ignoreCase = true) }?.points }
    }

    fun addPoints(player: Player, amount: Int, reason: String? = null) {
        synchronized(scoreLock) {
            val data = scores.getOrPut(player.uniqueId) { PlayerScore(player.name, 0) }
            data.name = player.name
            data.points += amount
        }
        save()

        if (reason != null) {
            plugin.languageManager.send(
                player,
                "common.points.earned",
                Placeholder.unparsed("amount", amount.toString()),
                Placeholder.unparsed("reason", reason)
            )
        }
    }

    fun addPointsAdmin(name: String, amount: Int): Boolean {
        synchronized(scoreLock) {
            val entry = scores.entries.find { it.value.name.equals(name, ignoreCase = true) } ?: return false
            entry.value.points += amount
        }
        save()
        return true
    }

    fun removePointsAdmin(name: String, amount: Int): Boolean {
        synchronized(scoreLock) {
            val entry = scores.entries.find { it.value.name.equals(name, ignoreCase = true) } ?: return false
            entry.value.points = (entry.value.points - amount).coerceAtLeast(0)
        }
        save()
        return true
    }

    fun clearPoints(name: String): Boolean {
        synchronized(scoreLock) {
            val entry = scores.entries.find { it.value.name.equals(name, ignoreCase = true) } ?: return false
            entry.value.points = 0
        }
        save()
        return true
    }

    fun clearAll() {
        synchronized(scoreLock) { scores.clear() }
        save()
    }

    fun getAllSorted(): List<PlayerScore> {
        return snapshot().values.sortedByDescending { it.points }
    }

    fun getTop10(): List<PlayerScore> {
        return snapshot().values.sortedByDescending { it.points }.take(10)
    }

    fun shutdown() {
        val finalSave = saveExecutor.submit { writeSnapshot(snapshot()) }
        runCatching { finalSave.get(10, TimeUnit.SECONDS) }
            .onFailure { plugin.componentLogger.error("No se pudo completar el guardado final de puntajes.", it) }
        saveExecutor.shutdown()
    }
}
