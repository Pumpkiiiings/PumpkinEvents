package pumpkin.eventos.arena

import kotlinx.coroutines.*
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import pumpkin.eventos.PumpkinEventos
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class ArenaSetupSession(
    val name: String,
    val type: String,
    var center: Location? = null,
    val spawns: MutableList<Location> = mutableListOf(),
    var gradas: Location? = null,
    var dueloA: Location? = null,
    var dueloB: Location? = null,
    // --- NUEVO: SILLAS ---
    val chairs: MutableList<Location> = mutableListOf()
)

class ArenaManager(private val plugin: PumpkinEventos) {

    private val arenas = ConcurrentHashMap<String, Arena>()
    val activeSetups = ConcurrentHashMap<UUID, ArenaSetupSession>()

    var mainLobby: Location? = null
    var waitingSpawn: Location? = null

    private val file = File(plugin.dataFolder, "arenas.yml")
    private var config = YamlConfiguration()
    private val mm = MiniMessage.miniMessage()
    private val fileLock = Any()
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        if (!file.exists()) plugin.saveResource("arenas.yml", false)
        loadArenasAsync()
    }

    private fun loadArenasAsync() {
        ioScope.launch {
            synchronized(fileLock) { config = YamlConfiguration.loadConfiguration(file) }

            mainLobby = loadSafeLocation("lobbies.main")
            waitingSpawn = loadSafeLocation("lobbies.waiting")

            val section = config.getConfigurationSection("arenas") ?: return@launch
            val tempArenas = mutableMapOf<String, Arena>()

            for (key in section.getKeys(false)) {
                val path = "arenas.$key."
                val type = config.getString("${path}type", "desconocido")!!
                val arena = Arena(key, type)

                arena.slimeWorldName = config.getString("${path}slimeWorld", key)
                arena.centerLocation = loadSafeLocation("${path}centerLocation")

                arena.gradas = loadSafeLocation("${path}gradas")
                arena.dueloA = loadSafeLocation("${path}dueloA")
                arena.dueloB = loadSafeLocation("${path}dueloB")

                loadLocationList("${path}spawnPoints").forEach { arena.addSpawnPoint(it) }

                // --- NUEVO: CARGAR SILLAS ---
                loadLocationList("${path}chairs").forEach { arena.addChair(it) }

                tempArenas[key] = arena
            }

            arenas.clear()
            arenas.putAll(tempArenas)
            plugin.componentLogger.info(mm.deserialize("<green>[Arenas] ${arenas.size} mapas cargados. Lobbies listos.</green>"))
        }
    }

    fun setMainLobbyLoc(loc: Location) {
        val cleanLoc = Location(null, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
        mainLobby = cleanLoc
        saveSafeLocation("lobbies.main", loc)
    }

    fun setWaitingSpawnLoc(loc: Location) {
        val cleanLoc = Location(null, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
        waitingSpawn = cleanLoc
        saveSafeLocation("lobbies.waiting", loc)
    }

    fun saveSetupSession(session: ArenaSetupSession) {
        val arena = Arena(session.name, session.type)
        arena.centerLocation = session.center
        session.spawns.forEach { arena.addSpawnPoint(it) }

        arena.gradas = session.gradas
        arena.dueloA = session.dueloA
        arena.dueloB = session.dueloB

        // --- NUEVO: TRANSFERIR SILLAS ---
        session.chairs.forEach { arena.addChair(it) }

        arenas[session.name] = arena

        synchronized(fileLock) {
            config.set("arenas.${session.name}.type", session.type)
            config.set("arenas.${session.name}.slimeWorld", session.name)
        }

        if (session.center != null) saveSafeLocation("arenas.${session.name}.centerLocation", session.center!!)
        if (session.gradas != null) saveSafeLocation("arenas.${session.name}.gradas", session.gradas!!)
        if (session.dueloA != null) saveSafeLocation("arenas.${session.name}.dueloA", session.dueloA!!)
        if (session.dueloB != null) saveSafeLocation("arenas.${session.name}.dueloB", session.dueloB!!)

        session.spawns.forEach { loc ->
            saveSafeLocation("arenas.${session.name}.spawnPoints.${UUID.randomUUID()}", loc)
        }

        // --- NUEVO: GUARDAR SILLAS EN YAML ---
        session.chairs.forEach { loc ->
            saveSafeLocation("arenas.${session.name}.chairs.${UUID.randomUUID()}", loc)
        }

        saveAsync()
    }

    private fun loadLocationList(path: String): List<Location> {
        val list = mutableListOf<Location>()
        val section = config.getConfigurationSection(path) ?: return list
        section.getKeys(false).forEach { key ->
            loadSafeLocation("$path.$key")?.let { list.add(it) }
        }
        return list
    }

    private fun loadSafeLocation(path: String): Location? {
        if (!config.contains("$path.x")) return null

        val worldName = config.getString("$path.world", "world")
        val realWorld = org.bukkit.Bukkit.getWorld(worldName!!)

        return Location(
            realWorld,
            config.getDouble("$path.x"),
            config.getDouble("$path.y"),
            config.getDouble("$path.z"),
            config.getDouble("$path.yaw").toFloat(),
            config.getDouble("$path.pitch").toFloat()
        )
    }

    private fun saveSafeLocation(path: String, loc: Location) {
        val worldName = loc.world?.name ?: "world"
        synchronized(fileLock) {
            config.set("$path.world", worldName)
            config.set("$path.x", loc.x)
            config.set("$path.y", loc.y)
            config.set("$path.z", loc.z)
            config.set("$path.yaw", loc.yaw)
            config.set("$path.pitch", loc.pitch)
        }
        saveAsync()
    }

    private fun saveAsync() {
        ioScope.launch {
            try {
                synchronized(fileLock) { config.save(file) }
            } catch (e: IOException) {
                plugin.componentLogger.error(mm.deserialize("<red>Error al guardar arenas: ${e.message}</red>"))
            }
        }
    }

    fun getArena(name: String): Arena? = arenas[name]
    fun getAvailableArenas(): List<Arena> = arenas.values.toList()
    fun shutdown() = ioScope.cancel()
}
