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

class SueloLava(private val plugin: PumpkinEventos) : EventGame("suelolava", "<#FF4500>El Suelo es Lava</#FF4500>") {

    var isPreparation = true
    private var currentLavaY = 1
    private var timer = 15
    var gameTimer = 300

    // RADIO EXPANDIDO: 75 de radio = Área de 150x150 bloques
    private val radius = 75
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
        val center = arena.centerLocation ?: arena.spawnPoints.firstOrNull() ?: return
        val targetWorld = center.world ?: players.firstOrNull()?.world ?: return

        plugin.server.regionScheduler.run(plugin, center) { _ ->
            val heights = listOf(
                targetWorld.getHighestBlockYAt(center.blockX, center.blockZ),
                targetWorld.getHighestBlockYAt(center.blockX + 15, center.blockZ),
                targetWorld.getHighestBlockYAt(center.blockX - 15, center.blockZ),
                targetWorld.getHighestBlockYAt(center.blockX, center.blockZ + 15),
                targetWorld.getHighestBlockYAt(center.blockX, center.blockZ - 15)
            ).filter { it > targetWorld.minHeight }

            val avgY = if (heights.isNotEmpty()) heights.minOrNull()!! else center.blockY
            currentLavaY = avgY - 2
        }

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
                    startRisingLava(targetWorld, center.blockX, center.blockZ)
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

        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun startRisingLava(world: World, centerX: Int, centerZ: Int) {
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

            // OPTIMIZACIÓN: Enviamos las 2 capas en un solo llamado (currentLavaY y currentLavaY + 1)
            fillLavaSafe(world, centerX, centerZ, currentLavaY, currentLavaY + 1)

            currentLavaY += 2

        }, lavaSpeedSeconds, lavaSpeedSeconds, TimeUnit.SECONDS)
    }

    /**
     * Algoritmo ÓPTIMO para llenar áreas gigantes en Folia/Paper.
     * Divide el área en Chunks y delega el trabajo al procesador (hilo) de cada Chunk.
     */
    private fun fillLavaSafe(world: World, cx: Int, cz: Int, startY: Int, endY: Int) {
        val minX = cx - radius
        val maxX = cx + radius
        val minZ = cz - radius
        val maxZ = cz + radius

        // Calculamos qué Chunks abarcan nuestro radio (dividiendo por 16)
        val minChunkX = minX shr 4
        val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4
        val maxChunkZ = maxZ shr 4

        // Iteramos sobre los Chunks en lugar de bloque por bloque a nivel general
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {

                // Ejecutamos en el Schedule SEGURO de este Chunk específico
                plugin.server.regionScheduler.run(plugin, world, chunkX, chunkZ) { _ ->
                    if (!isRunning) return@run

                    // Limites locales para no salirnos de este Chunk ni del radio máximo
                    val startX = maxOf(minX, chunkX shl 4)
                    val endX = minOf(maxX, (chunkX shl 4) + 15)
                    val startBlockZ = maxOf(minZ, chunkZ shl 4)
                    val endBlockZ = minOf(maxZ, (chunkZ shl 4) + 15)

                    // Cambiamos los bloques locales
                    for (x in startX..endX) {
                        for (z in startBlockZ..endBlockZ) {
                            for (y in startY..endY) { // Rellenamos las 2 capas (Y) de un golpe
                                val block = world.getBlockAt(x, y, z)
                                val type = block.type

                                // Si es aire o vegetación, lo reemplazamos
                                if (type.isAir || type == Material.WATER || type == Material.SHORT_GRASS || type == Material.TALL_GRASS) {
                                    block.setType(Material.LAVA, false) // false = sin actualización de físicas (0 lag)
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
