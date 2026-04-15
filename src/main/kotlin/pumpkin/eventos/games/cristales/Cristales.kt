package pumpkin.eventos.games.cristales

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
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
    private var gameTimer = 180 // 3 minutos para cruzar

    private val ganadores = mutableSetOf<UUID>()
    private val panels = mutableListOf<GlassPanel>()
    private val panelMap = mutableMapOf<Location, GlassPanel>()

    // Variables de orientación y límites para ganar
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

    override fun onStart() {
        isPreparation = true
        prepTimer = 10
        gameTimer = 180
        ganadores.clear()
        panels.clear()
        panelMap.clear()

        val arena = currentArena ?: return
        val posA = arena.dueloA ?: return
        val posB = arena.dueloB ?: return
        val targetWorld = players.firstOrNull()?.world ?: posA.world ?: return

        players.forEach { p ->
            p.gameMode = GameMode.ADVENTURE
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
        }

        // Escanear la selección de DueloA a DueloB
        plugin.server.regionScheduler.run(plugin, targetWorld, posA.blockX shr 4, posA.blockZ shr 4) { _ ->
            escanearPuente(targetWorld, posA, posB)
        }

        // Loop de Tiempo
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

            // Comprobar daño/caída
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

    private fun escanearPuente(world: org.bukkit.World, posA: Location, posB: Location) {
        val minX = min(posA.blockX, posB.blockX); val maxX = max(posA.blockX, posB.blockX)
        val minY = min(posA.blockY, posB.blockY); val maxY = max(posA.blockY, posB.blockY)
        val minZ = min(posA.blockZ, posB.blockZ); val maxZ = max(posA.blockZ, posB.blockZ)

        // Definir si el puente avanza en X o en Z
        isZAxis = abs(maxZ - minZ) > abs(maxX - minX)
        val startLoc = currentArena?.spawnPoints?.firstOrNull() ?: posA

        if (isZAxis) {
            movingPositive = startLoc.z < (minZ + maxZ) / 2
            minLimit = minZ
            maxLimit = maxZ
        } else {
            movingPositive = startLoc.x < (minX + maxX) / 2
            minLimit = minX
            maxLimit = maxX
        }

        val visited = mutableSetOf<Location>()

        // Buscar grupos de cristal usando Flood Fill
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val loc = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                    val block = world.getBlockAt(loc)

                    if (!visited.contains(loc) && block.type.name.contains("GLASS")) {
                        val newPanel = GlassPanel()
                        val queue = java.util.LinkedList<Location>()
                        queue.add(loc)
                        visited.add(loc)

                        while (queue.isNotEmpty()) {
                            val current = queue.poll()
                            newPanel.blocks.add(current)

                            val directions = listOf(
                                current.clone().add(1.0, 0.0, 0.0), current.clone().add(-1.0, 0.0, 0.0),
                                current.clone().add(0.0, 0.0, 1.0), current.clone().add(0.0, 0.0, -1.0)
                            )

                            for (dir in directions) {
                                if (!visited.contains(dir) && dir.block.type.name.contains("GLASS")) {
                                    // Limitamos a que NO agrupe cristales si están separados por aire.
                                    visited.add(dir)
                                    queue.add(dir)
                                }
                            }
                        }
                        panels.add(newPanel)
                    }
                }
            }
        }

        // Agrupar los paneles por "Paso" (para saber cuáles están frente a frente)
        val groupedPanels = panels.groupBy { panel ->
            if (isZAxis) panel.blocks.map { it.blockZ }.average().toInt()
            else panel.blocks.map { it.blockX }.average().toInt()
        }

        // De cada par de cristales (izquierdo y derecho), marcamos uno como Falso al azar
        for ((_, panelList) in groupedPanels) {
            if (panelList.size > 1) {
                val safePanel = panelList.random()
                for (p in panelList) {
                    if (p != safePanel) p.isFake = true
                    p.blocks.forEach { loc -> panelMap[loc] = p }
                }
            } else {
                // Si por error de construcción solo hay 1 panel en esa línea, lo hacemos seguro por defecto
                val p = panelList.first()
                p.blocks.forEach { loc -> panelMap[loc] = p }
            }
        }
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

        // 2. COMPROBAR QUÉ ESTÁN PISANDO (Falso o Verdadero)
        val blockBelow = to.clone().subtract(0.0, 0.1, 0.0).block
        if (blockBelow.type.name.contains("GLASS")) {
            val panel = panelMap[blockBelow.location]
            if (panel != null && !panel.shattered) {

                // Si es falso, se rompe inmediatamente
                if (panel.isFake) {
                    panel.shattered = true
                    romperCristal(panel, false)
                } else {
                    // --- SISTEMA DE SOBRECARGA (MÁXIMO 3 JUGADORES) ---
                    // Contamos cuántos jugadores activos están sobre los bloques de este panel específico
                    var jugadoresEnElCristal = 0
                    players.filter { !ganadores.contains(it.uniqueId) }.forEach { vivo ->
                        val footBlock = vivo.location.clone().subtract(0.0, 0.1, 0.0).block
                        if (panel.blocks.any { it.blockX == footBlock.x && it.blockZ == footBlock.z }) {
                            jugadoresEnElCristal++
                        }
                    }

                    // Si hay 4 o más jugadores, se rompe por sobrepeso (aunque sea el verdadero)
                    if (jugadoresEnElCristal >= 4) {
                        panel.shattered = true
                        romperCristal(panel, true)

                        val msg = plugin.messageManager.parse("<red>⚠️ <b>¡CRISTAL ROTO POR SOBREPESO!</b></red>")
                        players.forEach { it.sendMessage(msg) }
                    }
                }
            }
        }
    }

    private fun romperCristal(panel: GlassPanel, porSobrepeso: Boolean) {
        val firstBlock = panel.blocks.firstOrNull() ?: return
        val world = firstBlock.world

        plugin.server.regionScheduler.run(plugin, firstBlock) { _ ->
            // Sonido más grave si se rompió por sobrepeso
            if (porSobrepeso) world.playSound(firstBlock, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
            world.playSound(firstBlock, Sound.BLOCK_GLASS_BREAK, 2f, 1f)

            panel.blocks.forEach { loc ->
                val block = loc.block
                val blockData = block.blockData
                block.setType(Material.AIR, false) // Quita el cristal sin físicas de agua
                world.spawnParticle(Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, blockData)
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
        plugin.server.broadcast(plugin.messageManager.parse("<newline><#00FFFF><b>PUENTE DE CRISTAL</b></#00FFFF> <white>» ¡$winnersStr lograron cruzar el puente!<newline>"))
        stop()
    }

    override fun onStop() {
        org.bukkit.event.HandlerList.unregisterAll(this)
        plugin.eventManager.currentGame = null
        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation

        (players + spectators).forEach { p ->
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}
