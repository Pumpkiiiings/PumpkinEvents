package pumpkin.eventos.manager

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI
import com.infernalsuite.asp.api.loaders.SlimeLoader
import com.infernalsuite.asp.api.world.properties.SlimeProperties
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap
import com.infernalsuite.asp.loaders.file.FileLoader
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.World
import pumpkin.eventos.PumpkinEventos
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * [PUMPKIN EVENTOS CORE]
 * MapManager: Gestión de mundos dinámicos con AdvancedSlimePaper (ASP).
 */
class MapManager(private val plugin: PumpkinEventos) {

    private val asp = AdvancedSlimePaperAPI.instance()
    private val fileLoader: SlimeLoader

    init {
        val slimeFolder = File(plugin.dataFolder, "slime_worlds")
        if (!slimeFolder.exists()) slimeFolder.mkdirs()
        this.fileLoader = FileLoader(slimeFolder)
    }

    /**
     * Carga un mundo de arena desde una plantilla .slime.
     * Permite inyectar GameRules personalizadas en el momento de creación.
     */
    fun loadArenaWorld(
        templateName: String,
        customGameRules: Map<GameRule<*>, Any> = emptyMap()
    ): CompletableFuture<World?> {

        val future = CompletableFuture<World?>()
        val instanceName = "${templateName}_${System.currentTimeMillis()}"

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            try {
                if (!fileLoader.worldExists(templateName)) {
                    plugin.componentLogger.error(plugin.messageManager.parse("<red>[MapManager] El archivo .slime '$templateName' no existe.</red>"))
                    future.complete(null)
                    return@runNow
                }

                val props = SlimePropertyMap().apply {
                    setValue(SlimeProperties.ALLOW_ANIMALS, false)
                    setValue(SlimeProperties.ALLOW_MONSTERS, false)
                    setValue(SlimeProperties.PVP, true)
                }

                val template = asp.readWorld(fileLoader, templateName, true, props)
                val worldInstance = template.clone(instanceName)

                plugin.server.globalRegionScheduler.execute(plugin) {
                    try {
                        val instance = asp.loadWorld(worldInstance, false)
                        val bukkitWorld = instance.bukkitWorld

                        if (bukkitWorld == null) {
                            plugin.componentLogger.error(plugin.messageManager.parse("<red>[MapManager] Bukkit devolvió un mundo nulo al intentar cargar '$templateName'.</red>"))
                            future.complete(null)
                            return@execute
                        }

                        // --- CONFIGURACIÓN DE AMBIENTE BASE ---
                        bukkitWorld.apply {
                            isAutoSave = false
                            time = 6000L
                            setStorm(false)
                            isThundering = false

                            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                            setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                            setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                            setGameRule(GameRule.DO_MOB_SPAWNING, false)
                            setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                            setGameRule(GameRule.DO_FIRE_TICK, false)

                            // El daño por caída es false por defecto para casi todos los juegos
                            setGameRule(GameRule.FALL_DAMAGE, false)

                            // --- APLICAR REGLAS PERSONALIZADAS ---
                            // Fix del tipado usando as! de forma genérica
                            for ((rule, value) in customGameRules) {
                                @Suppress("UNCHECKED_CAST")
                                val typedRule = rule as GameRule<Any>
                                setGameRule(typedRule, value)
                            }
                        }

                        plugin.componentLogger.info(plugin.messageManager.parse("<green>[MapManager] Mundo instanciado correctamente: ${bukkitWorld.name}</green>"))
                        future.complete(bukkitWorld)

                    } catch (e: Exception) {
                        plugin.componentLogger.error(plugin.messageManager.parse("<red>[MapManager] Error al registrar mundo en Bukkit: ${e.message}</red>"))
                        future.complete(null)
                    }
                }
            } catch (e: Exception) {
                plugin.componentLogger.error(plugin.messageManager.parse("<red>[MapManager] Fallo crítico cargando $templateName: ${e.message}</red>"))
                e.printStackTrace()
                future.complete(null)
            }
        }

        return future
    }

    fun unloadWorld(world: World?) {
        if (world == null) return

        plugin.server.globalRegionScheduler.execute(plugin) {
            world.players.forEach { p ->
                val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation
                p.teleportAsync(lobby)
            }

            val success = Bukkit.unloadWorld(world, false)

            if (success) {
                plugin.componentLogger.info(plugin.messageManager.parse("<gray>[MapManager] Mundo temporal '${world.name}' descargado y borrado de la RAM.</gray>"))
            } else {
                plugin.componentLogger.warn(plugin.messageManager.parse("<yellow>[MapManager] No se pudo descargar el mundo '${world.name}'. ¿Hay jugadores dentro?</yellow>"))
            }
        }
    }

    fun shutdown() { }
}
