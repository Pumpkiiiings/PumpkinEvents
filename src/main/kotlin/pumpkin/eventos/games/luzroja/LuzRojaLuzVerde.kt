package pumpkin.eventos.games.luzroja

import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.*
import java.util.concurrent.TimeUnit

enum class LuzEstado { ROJA, VERDE, PREPARACION }

class LuzRojaLuzVerde(private val plugin: PumpkinEventos) : EventGame("luzroja", "<#FF3131>Luz Roja, Luz Verde</#FF3131>") {

    var estado = LuzEstado.PREPARACION
    private var timerGlobal = 300 // 5 minutos máximo
    private var metaZ = 0.0 // Coordenada Z donde termina la carrera
    private var estadoTimer = 10

    private val ganadores = mutableSetOf<UUID>()
    private var displayEstado = "<gray>Preparando...</gray>"

    // Variables para guardar las coordenadas del muro y borrarlo después
    private val muroBlocks = mutableListOf<Location>()

    override fun getExtraPlaceholders(): Map<String, String> {
        return mapOf(
            "%estado%" to displayEstado,
            "%meta%" to ganadores.size.toString()
        )
    }

    override fun onStart() {
        estado = LuzEstado.PREPARACION
        ganadores.clear()
        muroBlocks.clear()
        timerGlobal = 300
        estadoTimer = 10
        displayEstado = "<gray>Esperando...</gray>"

        val arena = currentArena ?: return

        // Usamos "dueloA" o centerLocation como la línea de META
        val metaLoc = arena.dueloA ?: arena.centerLocation ?: return
        metaZ = metaLoc.z

        players.forEach { p ->
            p.gameMode = GameMode.ADVENTURE
            p.inventory.clear()
        }

        // --- CREAR EL MURO DE CRISTAL (Bloquea a los jugadores en la salida) ---
        crearMuro(arena)

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            // --- FASE DE PREPARACIÓN ---
            if (estado == LuzEstado.PREPARACION) {
                estadoTimer--
                displayEstado = "<yellow>Preparación ($estadoTimer)</yellow>"

                players.forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f) }

                if (estadoTimer <= 0) {
                    // Al cambiar a Luz Verde, quitamos el muro
                    quitarMuro()
                    cambiarALuzVerde()
                }
                return@runAtFixedRate
            }

            // --- CHECK DE TIEMPO GLOBAL Y LLEGADAS A LA META ---
            timerGlobal--
            if (timerGlobal <= 0) {
                val perdedores = players.filter { !ganadores.contains(it.uniqueId) }
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    perdedores.forEach { killPlayer(it) }

                    plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                        plugin.server.broadcast(plugin.messageManager.parse("<newline><#FF3131><b>¡TIEMPO AGOTADO!</b></#FF3131> <white>El juego ha terminado.</white><newline>"))
                        stop()
                    }, 40L)
                }
                task.cancel()
                return@runAtFixedRate
            }

            // --- CICLO DE LUZ (Cambios de estado automáticos) ---
            estadoTimer--
            if (estadoTimer <= 0) {
                if (estado == LuzEstado.VERDE) {
                    cambiarALuzRoja()
                } else {
                    cambiarALuzVerde()
                }
            } else if (estado == LuzEstado.VERDE && estadoTimer <= 3) {
                val warnTitle = Title.title(plugin.messageManager.parse("<yellow>¡Frena!</yellow>"), plugin.messageManager.parse("<white>Luz roja en $estadoTimer"))
                players.filter { !ganadores.contains(it.uniqueId) }.forEach { it.showTitle(warnTitle); it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 2f) }
            }

        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun crearMuro(arena: pumpkin.eventos.arena.Arena) {
        // Usamos dueloB como la línea del muro. Si no existe, usamos los spawns como antes.
        val wallCenter = arena.dueloB
        val targetWorld = arena.spawnPoints.firstOrNull()?.world ?: wallCenter?.world ?: return

        val minX: Int
        val maxX: Int
        val startZ: Int
        val startY: Int

        if (wallCenter != null) {
            minX = wallCenter.blockX - 30 // Muro de 60 bloques de ancho
            maxX = wallCenter.blockX + 30
            startZ = wallCenter.blockZ
            startY = wallCenter.blockY
        } else {
            val spawns = arena.spawnPoints
            if (spawns.isEmpty()) return
            minX = spawns.minOf { it.blockX } - 5
            maxX = spawns.maxOf { it.blockX } + 5
            startZ = spawns.first().blockZ + if (metaZ > spawns.first().blockZ) 1 else -1
            startY = spawns.minOf { it.blockY }
        }

        plugin.server.regionScheduler.run(plugin, targetWorld, minX shr 4, startZ shr 4) { _ ->
            for (x in minX..maxX) {
                for (y in startY..(startY + 4)) { // Muro de 5 bloques de alto
                    val block = targetWorld.getBlockAt(x, y, startZ)
                    if (block.type.isAir) {
                        block.setType(Material.GLASS, false)
                        muroBlocks.add(block.location)
                    }
                }
            }
        }
    }

    private fun quitarMuro() {
        if (muroBlocks.isEmpty()) return

        val world = muroBlocks.first().world ?: return
        plugin.server.regionScheduler.run(plugin, world, muroBlocks.first().blockX shr 4, muroBlocks.first().blockZ shr 4) { _ ->
            muroBlocks.forEach { loc ->
                val block = loc.block
                if (block.type == Material.GLASS) {
                    block.setType(Material.AIR, false)
                }
            }
            muroBlocks.clear()
        }
    }

    private fun cambiarALuzRoja() {
        estado = LuzEstado.ROJA
        estadoTimer = (3..7).random()
        displayEstado = "<#FF3131>LUZ ROJA</#FF3131>"

        val title = Title.title(plugin.messageManager.parse("<#FF3131><bold>¡LUZ ROJA!</bold></#FF3131>"), plugin.messageManager.parse("<white>¡El que se mueva muere!</white>"))
        players.filter { !ganadores.contains(it.uniqueId) }.forEach {
            it.showTitle(title)
            it.playSound(it.location, Sound.BLOCK_ANVIL_PLACE, 1f, 1f)
        }
    }

    private fun cambiarALuzVerde() {
        estado = LuzEstado.VERDE
        estadoTimer = (5..10).random()
        displayEstado = "<#39FF14>LUZ VERDE</#39FF14>"

        val title = Title.title(plugin.messageManager.parse("<#39FF14><bold>¡LUZ VERDE!</bold></#39FF14>"), plugin.messageManager.parse("<white>¡Corre hacia la meta!</white>"))
        players.filter { !ganadores.contains(it.uniqueId) }.forEach {
            it.showTitle(title)
            it.playSound(it.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        }
    }

    fun markWinner(p: Player) {
        if (!ganadores.contains(p.uniqueId)) {
            ganadores.add(p.uniqueId)
            p.sendMessage(plugin.messageManager.parse("<#39FF14>✔ <b>¡LLEGASTE A LA META!</b></#39FF14> <white>Estás a salvo.</white>"))
            p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)

            val gradaLoc = currentArena?.gradas ?: currentArena?.centerLocation
            if (gradaLoc != null) p.teleportAsync(gradaLoc)

            if (ganadores.size >= players.size) {
                plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> checkWinner() }, 20L)
            }
        }
    }

    fun killPlayer(p: Player) {
        if (!players.contains(p)) return // <-- ESTO EVITA EL SPAM MASIVO DE MUERTES
        if (ganadores.contains(p.uniqueId)) return

        p.world.strikeLightningEffect(p.location)

        eliminate(p)
        p.sendMessage(plugin.messageManager.parse("<red>☠ <b>¡TE MOVISTE EN LUZ ROJA!</b></red>"))
        p.playSound(p.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f)

        plugin.server.broadcast(plugin.messageManager.parse("<red>☠</red> <yellow>${p.name}</yellow> <white>ha sido eliminado.</white>"))
    }

    override fun checkWinner() {
        val winnersStr = if (ganadores.isEmpty()) "Nadie" else "${ganadores.size} jugadores"
        plugin.server.broadcast(plugin.messageManager.parse("<newline><#FF3131><b>LUZ ROJA, LUZ VERDE</b></#FF3131> <white>» ¡$winnersStr lograron cruzar la meta!<newline>"))
        stop()
    }

    override fun onStop() {
        quitarMuro()
        plugin.eventManager.currentGame = null
        val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation
        (players + spectators).forEach { p ->
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }

    fun hasReachedMeta(loc: Location): Boolean {
        // Necesitamos saber dónde empezaron para entender hacia qué lado corren
        val startZ = currentArena?.dueloB?.z ?: currentArena?.spawnPoints?.firstOrNull()?.z ?: return false

        return if (startZ < metaZ) {
            loc.z >= metaZ // Corriendo hacia números positivos
        } else {
            loc.z <= metaZ // Corriendo hacia números negativos
        }
    }

    fun isPlayerSaved(p: Player): Boolean = ganadores.contains(p.uniqueId)
}
