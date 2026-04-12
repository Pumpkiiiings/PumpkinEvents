package pumpkin.eventos.games.blockparty

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

class BlockParty(plugin: PumpkinEventos) : EventGame(plugin, "blockparty", "<#FF1493>Block Party</#FF1493>") {

    private val colors = listOf(
        Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.LIME_CONCRETE,
        Material.YELLOW_CONCRETE, Material.MAGENTA_CONCRETE, Material.ORANGE_CONCRETE
    )

    private var roundTime = 6
    private var colorSelected: Material? = null
    private var floorY = 0
    private var isDancePhase = false

    // Variables expuestas para el Scoreboard / BossBar
    private var countdown = 0
    private var colorNameFormatted = "<gray>Generando...</gray>"

    private val radius = 25

    // --- MAGIA PARA EL SCOREBOARD ---
    override fun getExtraPlaceholders(): Map<String, String> {
        return mapOf(
            "%time%" to countdown.toString(),
            "%color%" to colorNameFormatted
        )
    }

    override fun onStart() {
        roundTime = 6
        countdown = 6
        colorNameFormatted = "<gray>Generando...</gray>"

        val arena = currentArena ?: return
        val center = arena.centerLocation ?: arena.spawnPoints.firstOrNull() ?: return
        floorY = center.blockY - 1

        players.forEach { p ->
            p.gameMode = GameMode.ADVENTURE
            p.inventory.clear()
        }

        siguienteRonda()

        // Loop para chequear quién se cae
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            // Eliminamos al que caiga por debajo de la plataforma
            val toEliminate = players.filter { it.location.y <= floorY - 2.0 }
            toEliminate.forEach { eliminate(it) }

            // --- FIX: CHEQUEAR GANADOR AQUÍ MISMO ---
            if (players.size <= 1) {
                checkWinner() // Detiene la partida de inmediato si queda 1 o 0
                task.cancel()
            }

        }, 10L, 5L) // Checamos muy rápido (cada 5 ticks)
    }

    private fun siguienteRonda() {
        if (!isRunning) return
        if (players.size <= 1) { checkWinner(); return }

        isDancePhase = false
        colorNameFormatted = "<gray>Generando...</gray>"

        val arena = currentArena ?: return
        val center = arena.centerLocation ?: return
        val world = center.world ?: return

        // 1. Generar Piso Aleatorio
        generarPisoOptimizado(world, center.blockX, center.blockZ)

        // 2. Elegir color para sobrevivir
        colorSelected = colors.random()
        colorNameFormatted = formatColorName(colorSelected)

        val itemColor = ItemStack(colorSelected!!)
        val meta = itemColor.itemMeta
        meta.displayName(plugin.messageManager.parse("<white>¡Párate en este color!</white>"))
        itemColor.itemMeta = meta

        players.forEach { p ->
            p.inventory.clear()
            for (i in 0..8) p.inventory.setItem(i, itemColor)
            p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f)
        }

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            iniciarRelojRonda()
        }, 20L)
    }

    private fun iniciarRelojRonda() {
        countdown = roundTime
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            if (countdown <= 0) {
                task.cancel()
                borrarColoresIncorrectos()
                return@runAtFixedRate
            }

            val title = Title.title(plugin.messageManager.parse("<red><bold>$countdown"), plugin.messageManager.parse("<white>¡Encuentra el color!"))
            players.forEach {
                it.showTitle(title)
                it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f)
            }
            countdown--
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun borrarColoresIncorrectos() {
        val arena = currentArena ?: return
        val center = arena.centerLocation ?: return
        val world = center.world ?: return

        colorNameFormatted = "<red>¡CORRE!</red>"
        borrarPisoOptimizado(world, center.blockX, center.blockZ)

        players.forEach { it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.5f) }

        if (roundTime > 2) roundTime--

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            siguienteRonda()
        }, 60L)
    }

    private fun generarPisoOptimizado(world: World, centerX: Int, centerZ: Int) {
        val minX = centerX - radius; val maxX = centerX + radius
        val minZ = centerZ - radius; val maxZ = centerZ + radius

        val minChunkX = minX shr 4; val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4; val maxChunkZ = maxZ shr 4

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                plugin.server.regionScheduler.run(plugin, world, chunkX, chunkZ) { _ ->
                    if (!isRunning) return@run
                    val startX = maxOf(minX, chunkX shl 4); val endX = minOf(maxX, (chunkX shl 4) + 15)
                    val startZ = maxOf(minZ, chunkZ shl 4); val endZ = minOf(maxZ, (chunkZ shl 4) + 15)

                    for (x in startX..endX) {
                        for (z in startZ..endZ) {
                            val block = world.getBlockAt(x, floorY, z)
                            block.setType(colors.random(), false)
                        }
                    }
                }
            }
        }
    }

    private fun borrarPisoOptimizado(world: World, centerX: Int, centerZ: Int) {
        val minX = centerX - radius; val maxX = centerX + radius
        val minZ = centerZ - radius; val maxZ = centerZ + radius

        val minChunkX = minX shr 4; val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4; val maxChunkZ = maxZ shr 4

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                plugin.server.regionScheduler.run(plugin, world, chunkX, chunkZ) { _ ->
                    if (!isRunning) return@run
                    val startX = maxOf(minX, chunkX shl 4); val endX = minOf(maxX, (chunkX shl 4) + 15)
                    val startZ = maxOf(minZ, chunkZ shl 4); val endZ = minOf(maxZ, (chunkZ shl 4) + 15)

                    for (x in startX..endX) {
                        for (z in startZ..endZ) {
                            val block = world.getBlockAt(x, floorY, z)
                            if (block.type != colorSelected) {
                                block.setType(Material.AIR, false)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatColorName(mat: Material?): String {
        return when (mat) {
            Material.RED_CONCRETE -> "<#FF3131>Rojo</#FF3131>"
            Material.BLUE_CONCRETE -> "<#00FFFF>Azul</#00FFFF>"
            Material.LIME_CONCRETE -> "<#39FF14>Verde</#39FF14>"
            Material.YELLOW_CONCRETE -> "<#CCFF00>Amarillo</#CCFF00>"
            Material.MAGENTA_CONCRETE -> "<#BF00FF>Magenta</#BF00FF>"
            Material.ORANGE_CONCRETE -> "<#FF5F1F>Naranja</#FF5F1F>"
            else -> "<gray>Desconocido</gray>"
        }
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return
        val rawWin = plugin.languageManager.get("blockparty.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
        winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        stop()
    }

    override fun onStop() {
        plugin.eventManager.currentGame = null
        val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation
        (players + spectators).forEach { p ->
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}
