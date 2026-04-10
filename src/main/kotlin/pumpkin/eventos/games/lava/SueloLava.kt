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
    var gameTimer = 300 // Tiempo máximo: 5 minutos

    // Reducimos un poco el radio (35 = 70x70) para no sobrecargar si no es necesario,
    // y sube 2 bloques cada 3 segundos.
    private val radius = 35
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

        // 1. Calcular de dónde empieza a subir la lava de forma más precisa.
        plugin.server.regionScheduler.run(plugin, center) { _ ->
            // Escaneamos solo algunos puntos cardinales para encontrar una altura razonable
            val heights = listOf(
                targetWorld.getHighestBlockYAt(center.blockX, center.blockZ),
                targetWorld.getHighestBlockYAt(center.blockX + 15, center.blockZ),
                targetWorld.getHighestBlockYAt(center.blockX - 15, center.blockZ),
                targetWorld.getHighestBlockYAt(center.blockX, center.blockZ + 15),
                targetWorld.getHighestBlockYAt(center.blockX, center.blockZ - 15)
            ).filter { it > targetWorld.minHeight }

            // Sacamos el promedio o el mínimo de las plataformas
            val avgY = if (heights.isNotEmpty()) heights.minOrNull()!! else center.blockY

            // Empieza 2 bloques debajo de la parte más baja encontrada
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

            // REDUCIR TIEMPO GLOBAL DEL JUEGO
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

            // Llamamos a la función segura para poner lava en las 2 capas
            fillLavaSafe(world, centerX, centerZ, currentLavaY)
            fillLavaSafe(world, centerX, centerZ, currentLavaY + 1)

            currentLavaY += 2

        }, lavaSpeedSeconds, lavaSpeedSeconds, TimeUnit.SECONDS)
    }

    /**
     * Pone lava en un radio circular/cuadrado delegándolo en el Scheduler correcto.
     */
    private fun fillLavaSafe(world: World, cx: Int, cz: Int, y: Int) {
        // En lugar de dividir por chunks nosotros, hacemos una ejecución sólida
        // en el chunk central que afectará a los vecinos.
        plugin.server.regionScheduler.run(plugin, world, cx shr 4, cz shr 4) { _ ->
            if (!isRunning) return@run

            val minX = cx - radius
            val maxX = cx + radius
            val minZ = cz - radius
            val maxZ = cz + radius

            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    val block = world.getBlockAt(x, y, z)

                    if (block.type.isAir || block.type == Material.WATER || block.type == Material.SHORT_GRASS || block.type == Material.TALL_GRASS) {
                        block.setType(Material.LAVA, false) // False evita actualización de físicas = 0 lag
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
