package pumpkin.eventos.games.lava

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class SueloLava(plugin: PumpkinEventos) : EventGame(plugin, "suelolava", "<#FF4500>El Suelo es Lava</#FF4500>") {

    var isPreparation = true
    private var currentLavaY = 1
    private var timer = 15
    var gameTimer = 300

    // Coordenadas de los límites de la arena (Pos1 y Pos2)
    private var minX = 0
    private var maxX = 0
    private var minZ = 0
    private var maxZ = 0

    private val lavaSpeedSeconds = 3L

    override fun getExtraPlaceholders(): Map<String, String> {
        val minutes = gameTimer / 60
        val seconds = gameTimer % 60
        val formattedTime = String.format("%02d:%02d", minutes, seconds)
        return mapOf(
            "%lava_y%" to currentLavaY.toString(),
            "%time_left%" to formattedTime
        )
    }

    override fun onStart() {
        isPreparation = true
        timer = 15
        gameTimer = 300

        val arena = currentArena ?: return
        val pos1 = arena.dueloA ?: return // Obligatorio configurar DueloA (Pos1)
        val pos2 = arena.dueloB ?: return // Obligatorio configurar DueloB (Pos2)

        val targetWorld = players.firstOrNull()?.world ?: pos1.world ?: return

        // Calculamos los límites absolutos del área (Pos1 y Pos2)
        minX = min(pos1.blockX, pos2.blockX)
        maxX = max(pos1.blockX, pos2.blockX)
        minZ = min(pos1.blockZ, pos2.blockZ)
        maxZ = max(pos1.blockZ, pos2.blockZ)

        // Definimos la altura inicial de la lava basándonos en la coordenada Y más baja de los dos puntos
        currentLavaY = min(pos1.blockY, pos2.blockY)

        val mm = plugin.messageManager
        val lang = plugin.languageManager

        val rawTitle = lang.get("suelolava.title.preparation_main")
        val rawSub = lang.get("suelolava.title.preparation_sub")
        val title = Title.title(mm.parse(rawTitle), mm.parse(rawSub))

        players.forEach { p ->
            p.gameMode = GameMode.SURVIVAL
            p.inventory.clear()
            p.inventory.addItem(ItemStack(Material.TERRACOTTA, 64))
            p.inventory.addItem(ItemStack(Material.TERRACOTTA, 64))
            p.showTitle(title)
            p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
        }

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            if (isPreparation) {
                timer--
                val rawPrep = lang.get("suelolava.actionbar.preparation")
                val bar = mm.parse(rawPrep, Placeholder.parsed("time", timer.toString()))
                players.forEach { it.sendActionBar(bar); it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f) }

                if (timer <= 0) {
                    isPreparation = false
                    plugin.server.broadcast(mm.parse(lang.get("suelolava.broadcast.started")))
                    startRisingLava(targetWorld)
                }
                return@runAtFixedRate
            }

            gameTimer--
            if (gameTimer <= 0) {
                plugin.server.broadcast(mm.parse("<newline><red><b>¡TIEMPO AGOTADO!</b></red> <white>El juego ha terminado en empate.</white><newline>"))
                stop()
                task.cancel()
                return@runAtFixedRate
            }

            val toEliminate = players.filter { it.location.block.type == Material.LAVA || it.location.y <= it.world.minHeight + 2 }
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                toEliminate.forEach { eliminate(it) }
            }

            // Validar si solo queda un jugador
            if (players.size <= 1) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                task.cancel()
            }

        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun startRisingLava(world: World) {
        val mm = plugin.messageManager
        val lang = plugin.languageManager

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) {
                task.cancel()
                return@runAtFixedRate
            }

            val rawRising = lang.get("suelolava.actionbar.rising")
            val bar = mm.parse(rawRising, Placeholder.parsed("y", currentLavaY.toString()))

            players.forEach {
                it.sendActionBar(bar)
                it.playSound(it.location, Sound.BLOCK_LAVA_EXTINGUISH, 0.5f, 1.5f)
            }

            // Subir la lava en las coordenadas seleccionadas
            fillLavaSafe(world, currentLavaY, currentLavaY + 1)

            currentLavaY += 2

        }, lavaSpeedSeconds, lavaSpeedSeconds, TimeUnit.SECONDS)
    }

    /**
     * Rellena con lava el área exacta entre DueloA y DueloB.
     * Delega el trabajo a los hilos de los Chunks para compatibilidad total con Folia/Paper.
     */
    private fun fillLavaSafe(world: World, startY: Int, endY: Int) {
        // Convertir las coordenadas de bloques a coordenadas de Chunks
        val minChunkX = minX shr 4
        val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4
        val maxChunkZ = maxZ shr 4

        // Iterar chunk por chunk para asegurar que Folia no bloquee la ejecución cruzada
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {

                plugin.server.regionScheduler.run(plugin, world, chunkX, chunkZ) { _ ->
                    if (!isRunning) return@run

                    // Definir qué bloques pertenecen a ESTE chunk y al mismo tiempo están dentro de los límites del jugador
                    val startX = max(minX, chunkX shl 4)
                    val endX = min(maxX, (chunkX shl 4) + 15)
                    val startBlockZ = max(minZ, chunkZ shl 4)
                    val endBlockZ = min(maxZ, (chunkZ shl 4) + 15)

                    // Cambiar los bloques a lava
                    for (x in startX..endX) {
                        for (z in startBlockZ..endBlockZ) {
                            for (y in startY..endY) {
                                val block = world.getBlockAt(x, y, z)
                                val type = block.type

                                // Lista de bloques "blandos" que la lava destruirá y reemplazará
                                if (type.isAir || type == Material.WATER || type == Material.SHORT_GRASS ||
                                    type == Material.TALL_GRASS || type == Material.SNOW || type == Material.FERN) {

                                    block.setType(Material.LAVA, false) // False = No avisa a los bloques vecinos (Evita lag de físicas)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return
        val rawWin = plugin.languageManager.get("suelolava.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
        winner.world.spawnParticle(org.bukkit.Particle.FIREWORK, winner.location, 100)
        winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        plugin.puntajeManager.addPoints(winner, 10, "¡Victoria conseguida!")
        plugin.puntajeManager.addPoints(winner, 10, "¡Victoria conseguida!")
        stop()
    }

    override fun onStop() {
        plugin.eventManager.currentGame = null
        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation

        val todos = players + spectators
        todos.forEach { p ->
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}
