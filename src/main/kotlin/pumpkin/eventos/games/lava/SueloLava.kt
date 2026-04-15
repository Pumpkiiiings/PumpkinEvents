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

    private var minX = 0
    private var maxX = 0
    private var minZ = 0
    private var maxZ = 0
    private var maxY = 0

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

        // --- FIX CRÍTICO 1: RETRASO PARA EL MUNDO CLONADO ---
        // Esperamos medio segundo (10 ticks) para que el EventManager termine de teletransportar a todos.
        // Así aseguramos que el "targetWorld" sea el mundo de juego y no el Lobby.
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            val targetWorld = players.firstOrNull()?.world ?: arena.centerLocation?.world ?: return@runDelayed

            // Obtenemos los puntos de la arena
            val p1 = arena.dueloA ?: return@runDelayed
            val p2 = arena.dueloB ?: return@runDelayed

            minX = min(p1.blockX, p2.blockX)
            maxX = max(p1.blockX, p2.blockX)
            minZ = min(p1.blockZ, p2.blockZ)
            maxZ = max(p1.blockZ, p2.blockZ)

            // La lava empieza en el punto más bajo de la selección
            currentLavaY = min(p1.blockY, p2.blockY)

            // --- FIX CRÍTICO 2: LAVA INFINITA ---
            // Ignoramos la altura de tu selección. La lava subirá hasta el techo del mundo.
            maxY = targetWorld.maxHeight

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

                // Comprobar eliminación (si toca lava o cae al vacío)
                val toEliminate = players.filter { it.location.block.type == Material.LAVA || it.location.y <= targetWorld.minHeight + 2 }
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    toEliminate.forEach { eliminate(it) }
                }

                if (players.size <= 1) {
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                    task.cancel()
                }

            }, 1, 1, TimeUnit.SECONDS)

        }, 10L)
    }

    private fun startRisingLava(world: World) {
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) {
                task.cancel()
                return@runAtFixedRate
            }

            // Si llegamos al techo del mundo, dejamos de subir
            if (currentLavaY > maxY) {
                task.cancel()
                return@runAtFixedRate
            }

            val rawRising = plugin.languageManager.get("suelolava.actionbar.rising")
            val bar = plugin.messageManager.parse(rawRising, Placeholder.parsed("y", currentLavaY.toString()))

            players.forEach {
                it.sendActionBar(bar)
                it.playSound(it.location, Sound.BLOCK_LAVA_EXTINGUISH, 0.5f, 1.5f)
            }

            // Rellenamos 1 capa por vez
            fillLavaLayer(world, currentLavaY)

            currentLavaY++

        }, lavaSpeedSeconds, lavaSpeedSeconds, TimeUnit.SECONDS)
    }

    private fun fillLavaLayer(world: World, y: Int) {
        val minChunkX = minX shr 4
        val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4
        val maxChunkZ = maxZ shr 4

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                plugin.server.regionScheduler.run(plugin, world, chunkX, chunkZ) { _ ->
                    if (!isRunning) return@run

                    val startBlockX = max(minX, chunkX shl 4)
                    val endBlockX = min(maxX, (chunkX shl 4) + 15)
                    val startBlockZ = max(minZ, chunkZ shl 4)
                    val endBlockZ = min(maxZ, (chunkZ shl 4) + 15)

                    for (x in startBlockX..endBlockX) {
                        for (z in startBlockZ..endBlockZ) {
                            val block = world.getBlockAt(x, y, z)
                            val type = block.type

                            if (type.isAir || type == Material.WATER || type.name.contains("GRASS") ||
                                type == Material.SNOW || type == Material.FERN || type == Material.TALL_GRASS) {

                                block.setType(Material.LAVA, false)
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
