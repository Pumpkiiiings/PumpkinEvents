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
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Representa un grupo entero de cristales (ej: tu panel de 2x2)
class GlassPanel {
    val blocks = mutableSetOf<Location>()
    var isFake = false
    var shattered = false
}

class Cristales(plugin: PumpkinEventos) : EventGame(plugin, "cristales", "<#00FFFF>Puente de Cristal</#00FFFF>"), Listener {

    var isPreparation = true
    private var prepTimer = 5
    private var gameTimer = 180

    private val ganadores = mutableSetOf<UUID>()
    private val panels = mutableListOf<GlassPanel>()

    private var isZAxis = true
    private var movingPositive = true
    private var minLimit = 0
    private var maxLimit = 0
    private var alturaPuente = 0 // NUEVO: Altura Y del puente para muertes por caída instantáneas

    init {
        // Registramos el Listener una sola vez al cargar el plugin
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
        prepTimer = 5
        gameTimer = 180
        ganadores.clear()
        panels.clear()

        val arena = currentArena ?: return
        val posA = arena.dueloA ?: return
        val posB = arena.dueloB ?: return

        // Guardamos la altura del puente (el Y más bajo de tu selección)
        alturaPuente = min(posA.blockY, posB.blockY)

        // Asegurarnos de usar el mundo donde fueron teletransportados
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            val targetWorld = players.firstOrNull()?.world ?: return@runDelayed

            players.forEach { p ->
                p.gameMode = GameMode.ADVENTURE
                p.inventory.clear()
                p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            }

            // --- ESCANEO INTELIGENTE DE CRISTALES (Flood Fill) ---
            plugin.server.regionScheduler.run(plugin, targetWorld, posA.blockX shr 4, posA.blockZ shr 4) { _ ->
                val minX = min(posA.blockX, posB.blockX); val maxX = max(posA.blockX, posB.blockX)
                val minY = min(posA.blockY, posB.blockY); val maxY = max(posA.blockY, posB.blockY)
                val minZ = min(posA.blockZ, posB.blockZ); val maxZ = max(posA.blockZ, posB.blockZ)

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

                val visited = mutableSetOf<String>()

                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        for (z in minZ..maxZ) {
                            val coordKey = "$x,$y,$z"
                            val b = targetWorld.getBlockAt(x, y, z)

                            if (!visited.contains(coordKey) && b.type.name.contains("GLASS")) {
                                val panel = GlassPanel()
                                val queue = java.util.LinkedList<org.bukkit.block.Block>()
                                queue.add(b)
                                visited.add(coordKey)

                                while (queue.isNotEmpty()) {
                                    val current = queue.poll()
                                    panel.blocks.add(current.location)

                                    val neighbors = listOf(
                                        current.getRelative(1, 0, 0), current.getRelative(-1, 0, 0),
                                        current.getRelative(0, 0, 1), current.getRelative(0, 0, -1)
                                    )

                                    for (n in neighbors) {
                                        val nKey = "${n.x},${n.y},${n.z}"
                                        if (n.x in minX..maxX && n.y in minY..maxY && n.z in minZ..maxZ) {
                                            if (!visited.contains(nKey) && n.type.name.contains("GLASS")) {
                                                visited.add(nKey)
                                                queue.add(n)
                                            }
                                        }
                                    }
                                }
                                panels.add(panel)
                            }
                        }
                    }
                }

                val groupedByStep = panels.groupBy { panel ->
                    if (isZAxis) kotlin.math.round(panel.blocks.map { it.blockZ }.average()).toInt()
                    else kotlin.math.round(panel.blocks.map { it.blockX }.average()).toInt()
                }

                for ((_, panelList) in groupedByStep) {
                    if (panelList.size > 1) {
                        val safePanel = panelList.random()
                        for (p in panelList) {
                            p.isFake = (p != safePanel)
                        }
                    } else {
                        panelList.firstOrNull()?.isFake = false
                    }
                }
            }

            iniciarReloj()

        }, 10L)
    }

    private fun iniciarReloj() {
        runAtFixedRate( { task ->

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

                    // FIX: Enviar las pociones a través del GlobalRegionScheduler para evitar el Crash de Folia
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        players.forEach { p ->
                            p.sendActionBar(startMsg)
                            p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)

                            p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 99999, 0, false, false, false))
                        }
                    }
                }
                return@runAtFixedRate
            }

            val targetWorld = players.firstOrNull()?.world ?: return@runAtFixedRate
            val toEliminate = players.filter { it.location.y <= (alturaPuente - 2) && !ganadores.contains(it.uniqueId) }
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

        }, 20L, 20L)
    }

    @EventHandler
    fun onPlayerMove(e: PlayerMoveEvent) {
        if (!isRunning) return
        val p = e.player
        if (!players.contains(p) || ganadores.contains(p.uniqueId)) return

        val from = e.from
        val to = e.to

        // --- BLOQUEO DE MOVIMIENTO MEJORADO ---
        if (isPreparation) {
            // Permitimos mover la cabeza, pero si las coordenadas X o Z cambian, cancelamos y lo obligamos a quedarse en el sitio
            if (from.x != to.x || from.z != to.z) {
                e.setTo(Location(from.world, from.x, from.y, from.z, to.yaw, to.pitch))
            }
            return
        }

        // 1. COMPROBAR VICTORIA
        if (haCruzado(to)) {
            val blockBelow = to.clone().subtract(0.0, 0.1, 0.0).block
            if (blockBelow.type.isSolid && !blockBelow.type.name.contains("GLASS")) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> markWinner(p) }
                return
            }
        }

        // 2. COMPROBAR SI ESTÁ PISANDO UN CRISTAL
        val footBlock = to.clone().subtract(0.0, 0.1, 0.0).block
        if (footBlock.type.name.contains("GLASS")) {

            val panel = getPanelAt(footBlock)

            if (panel != null && !panel.shattered) {
                if (panel.isFake) {
                    panel.shattered = true
                    romperCristal(panel, false)
                } else {
                    var jugadoresEnElCristal = 0
                    players.filter { !ganadores.contains(it.uniqueId) }.forEach { vivo ->
                        val posVivo = vivo.location.clone().subtract(0.0, 0.1, 0.0).block
                        if (panel.blocks.any { it.blockX == posVivo.x && it.blockZ == posVivo.z }) {
                            jugadoresEnElCristal++
                        }
                    }

                    if (jugadoresEnElCristal >= 4) { // Si hay 4, se rompe.
                        panel.shattered = true
                        romperCristal(panel, true)
                        val msg = plugin.messageManager.parse("<red>⚠️ <b>¡CRISTAL ROTO POR SOBREPESO!</b></red>")
                        players.forEach { it.sendMessage(msg) }
                    }
                }
            }
        }
    }

    private fun getPanelAt(block: org.bukkit.block.Block): GlassPanel? {
        return panels.firstOrNull { panel ->
            panel.blocks.any { it.blockX == block.x && it.blockY == block.y && it.blockZ == block.z }
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

        p.activePotionEffects.forEach { p.removePotionEffect(it.type) }

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
        returnToLobby()
    }
}
