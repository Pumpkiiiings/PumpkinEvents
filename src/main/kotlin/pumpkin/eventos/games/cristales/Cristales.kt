package pumpkin.eventos.games.cristales

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GlassPanel {
    val blocks = mutableSetOf<Location>()
    var isFake = false
    var shattered = false
}

class Cristales(plugin: PumpkinEventos) : EventGame(plugin, "cristales", "<#00FFFF>Puente de Cristal</#00FFFF>"), Listener {

    var isPreparation = true
    private var prepTimer = 10
    private var gameTimer = 180

    private val ganadores = mutableSetOf<UUID>()
    private val fakeBlocks = mutableSetOf<Location>() // Guarda cada bloque falso exacto
    private val panelsByLocation = mutableMapOf<Location, GlassPanel>() // Para el sobrepeso

    private var isZAxis = true
    private var movingPositive = true
    private var minLimit = 0
    private var maxLimit = 0

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun getExtraPlaceholders(): Map<String, String> {
        val min = gameTimer / 60
        val sec = gameTimer % 60
        return mapOf(
            "%time_left%" to String.format("%02d:%02d", min, sec),
            "%vivos%" to players.size.toString(),
            "%salvados%" to ganadores.size.toString()
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(GameRule.FALL_DAMAGE to true)
    }

    override fun onStart() {
        isPreparation = true
        prepTimer = 10
        gameTimer = 180
        ganadores.clear()
        fakeBlocks.clear()
        panelsByLocation.clear()

        val arena = currentArena ?: return
        val posA = arena.dueloA ?: return
        val posB = arena.dueloB ?: return
        val targetWorld = players.firstOrNull()?.world ?: posA.world ?: return

        players.forEach { p ->
            p.gameMode = GameMode.ADVENTURE
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
        }

        // --- ESCANEO GARANTIZADO DE CRISTALES ---
        plugin.server.regionScheduler.run(plugin, targetWorld, posA.blockX shr 4, posA.blockZ shr 4) { _ ->
            val minX = min(posA.blockX, posB.blockX); val maxX = max(posA.blockX, posB.blockX)
            val minY = min(posA.blockY, posB.blockY); val maxY = max(posA.blockY, posB.blockY)
            val minZ = min(posA.blockZ, posB.blockZ); val maxZ = max(posA.blockZ, posB.blockZ)

            // 1. Detectar dirección del puente
            isZAxis = abs(maxZ - minZ) > abs(maxX - minX)
            val startLoc = arena.spawnPoints.firstOrNull() ?: posA

            if (isZAxis) {
                movingPositive = startLoc.z < (minZ + maxZ) / 2
                minLimit = minZ
                maxLimit = maxZ
            } else {
                movingPositive = startLoc.x < (minX + maxX) / 2
                minLimit = minX
                maxLimit = maxX
            }

            // 2. Extraer todos los cristales del área
            val allGlass = mutableListOf<Location>()
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        val b = targetWorld.getBlockAt(x, y, z)
                        if (b.type.name.contains("GLASS")) {
                            allGlass.add(b.location)
                        }
                    }
                }
            }

            // 3. Agrupar por nivel de avance (Filas o "Pasos")
            // Si avanzan en Z, todos los cristales con el mismo Z son de la misma fila
            val groupedByStep = allGlass.groupBy { if (isZAxis) it.blockZ else it.blockX }

            for ((_, locsEnMismoPaso) in groupedByStep) {
                // Separamos los paneles si están en lados distintos (Izquierda vs Derecha)
                // Lo hacemos agrupando por el EJE CONTRARIO. Ej: Si avanzan en Z, se separan por X
                val groupedPanels = locsEnMismoPaso.groupBy { if (isZAxis) it.blockX else it.blockZ }

                // Elegimos UN panel seguro al azar de las opciones disponibles en este nivel
                val keys = groupedPanels.keys.toList()
                if (keys.isEmpty()) continue

                val safeKey = keys.random()

                for ((key, locsEnPanel) in groupedPanels) {
                    val panel = GlassPanel()
                    panel.blocks.addAll(locsEnPanel)
                    panel.isFake = (key != safeKey) // Si no es la llave segura, es un cristal falso

                    locsEnPanel.forEach { loc ->
                        panelsByLocation[loc] = panel
                        if (panel.isFake) fakeBlocks.add(loc)
                    }
                }
            }
        }

        // --- BUCLE DE JUEGO ---
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            if (isPreparation) {
                if (prepTimer > 0) {
                    val rawMsg = plugin.languageManager.get("cristales.actionbar.preparation")
                    val bar = plugin.messageManager.parse(rawMsg, Placeholder.parsed("time", prepTimer.toString()))
                    players.forEach {
                        it.sendActionBar(bar)
                        it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f)
                    }
                    prepTimer--
                } else {
                    isPreparation = false
                    val startMsg = plugin.messageManager.parse("<#39FF14><b>¡PUEDEN CRUZAR!</b></#39FF14>")
                    players.forEach {
                        it.sendActionBar(startMsg)
                        it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                    }
                }
                return@runAtFixedRate
            }

            val toEliminate = players.filter { it.location.y <= it.world.minHeight + 2 && !ganadores.contains(it.uniqueId) }
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                toEliminate.forEach {
                    it.world.strikeLightningEffect(it.location)
                    eliminate(it)
                }
            }

            if (players.size <= ganadores.size) {
                task.cancel()
                plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                return@runAtFixedRate
            }

            gameTimer--
            if (gameTimer > 0) {
                val actionMsg = plugin.messageManager.parse("<white>Tiempo restante: <red><bold>${gameTimer}</bold>s</red></white>")
                players.forEach { it.sendActionBar(actionMsg) }
            } else {
                task.cancel()
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    val perdedores = players.filter { !ganadores.contains(it.uniqueId) }
                    perdedores.forEach {
                        it.world.strikeLightningEffect(it.location)
                        eliminate(it)
                    }
                    plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> checkWinner() }, 40L)
                }
            }

        }, 1, 1, TimeUnit.SECONDS)
    }

    @EventHandler
    fun onPlayerMove(e: PlayerMoveEvent) {
        if (!isRunning || isPreparation) return
        val p = e.player
        if (!players.contains(p) || ganadores.contains(p.uniqueId)) return

        val to = e.to

        // 1. COMPROBAR VICTORIA
        if (haCruzado(to)) {
            val blockBelow = to.clone().subtract(0.0, 0.1, 0.0).block
            if (blockBelow.type.isSolid && !blockBelow.type.name.contains("GLASS")) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> markWinner(p) }
                return
            }
        }

        // 2. COMPROBAR QUÉ ESTÁN PISANDO
        val blockBelowLoc = to.clone().subtract(0.0, 0.1, 0.0).block.location
        val panel = panelsByLocation[blockBelowLoc]

        if (panel != null && !panel.shattered) {
            if (panel.isFake) {
                panel.shattered = true
                romperCristal(panel, false)
            } else {
                // SISTEMA DE SOBRECARGA
                var jugadoresEnElCristal = 0
                players.filter { !ganadores.contains(it.uniqueId) }.forEach { vivo ->
                    val footLoc = vivo.location.clone().subtract(0.0, 0.1, 0.0).block.location
                    if (panel.blocks.contains(footLoc)) jugadoresEnElCristal++
                }

                // Más de 4 jugadores = Rompe cristal verdadero
                if (jugadoresEnElCristal >= 4) {
                    panel.shattered = true
                    romperCristal(panel, true)
                    val msg = plugin.messageManager.parse("<red>⚠️ <b>¡CRISTAL ROTO POR SOBREPESO!</b></red>")
                    players.forEach { it.sendMessage(msg) }
                }
            }
        }
    }

    private fun romperCristal(panel: GlassPanel, porSobrepeso: Boolean) {
        val firstBlock = panel.blocks.firstOrNull() ?: return
        val world = firstBlock.world

        plugin.server.regionScheduler.run(plugin, firstBlock) { _ ->
            if (porSobrepeso) world.playSound(firstBlock, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
            world.playSound(firstBlock, Sound.BLOCK_GLASS_BREAK, 2f, 1f)

            panel.blocks.forEach { loc ->
                val block = loc.block
                val blockData = block.blockData
                block.setType(Material.AIR, false)
                world.spawnParticle(org.bukkit.Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, blockData)

                // Limpiamos los bloques falsos si es que este era falso
                fakeBlocks.remove(loc)
            }
        }
    }

    private fun haCruzado(loc: Location): Boolean {
        return if (isZAxis) {
            if (movingPositive) loc.z > maxLimit else loc.z < minLimit
        } else {
            if (movingPositive) loc.x > maxLimit else loc.x < minLimit
        }
    }

    private fun markWinner(p: Player) {
        ganadores.add(p.uniqueId)
        p.sendMessage(plugin.messageManager.parse("<#39FF14>✔ <b>¡CRUZASTE EL PUENTE!</b></#39FF14> <white>Estás a salvo.</white>"))
        p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)

        val arena = currentArena
        val gradaLoc = arena?.gradas?.clone() ?: arena?.centerLocation?.clone()
        if (gradaLoc != null) {
            gradaLoc.world = p.world
            p.teleportAsync(gradaLoc)
        }
    }

    override fun checkWinner() {
        val winnersStr = if (ganadores.isEmpty()) "Nadie" else "${ganadores.size} jugadores"
        val rawWin = plugin.languageManager.get("cristales.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winnersStr)))
        stop()
    }

    override fun onStop() {
        org.bukkit.event.HandlerList.unregisterAll(this) // Nos desregistramos del listener interno
        plugin.eventManager.currentGame = null
        val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation

        (players + spectators).forEach { p ->
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}
