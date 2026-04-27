package pumpkin.eventos.games.lava

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.BorderShrinkManager
import pumpkin.eventos.games.EventGame
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

enum class SlPhase { PREPARING, PLAYING, DEATHMATCH, DRAW }

class SueloLava(plugin: PumpkinEventos) : EventGame(plugin, "suelolava", "<#FF4500>El Suelo es Lava</#FF4500>") {

    var phase = SlPhase.PREPARING
    private var currentLavaY = 1
    private var mainTimer = 15
    private var arrowTimer = 0

    // --- MODO CAOS ---
    private var lastEliminationTime = 0L
    private var chaosActivated = false
    private var chaosBossBar: BossBar? = null

    private var minX = 0
    private var maxX = 0
    private var minZ = 0
    private var maxZ = 0
    private var maxY = 0

    private val lavaSpeedSeconds = 3L
    private val borderManager = BorderShrinkManager(plugin, this)

    // Cofres detectados para loot
    val openedChests = mutableSetOf<org.bukkit.Location>()

    override fun getExtraPlaceholders(): Map<String, String> {
        val min = mainTimer / 60
        val sec = mainTimer % 60
        return mapOf(
            "%lava_y%" to currentLavaY.toString(),
            "%time_left%" to String.format("%02d:%02d", min, sec)
        )
    }

    override fun onStart() {
        phase = SlPhase.PREPARING
        mainTimer = 15
        openedChests.clear()

        val arena = currentArena ?: return

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            val targetWorld = players.firstOrNull()?.world ?: arena.centerLocation?.world ?: return@runDelayed

            val p1 = arena.dueloA ?: return@runDelayed
            val p2 = arena.dueloB ?: return@runDelayed

            minX = min(p1.blockX, p2.blockX)
            maxX = max(p1.blockX, p2.blockX)
            minZ = min(p1.blockZ, p2.blockZ)
            maxZ = max(p1.blockZ, p2.blockZ)

            currentLavaY = min(p1.blockY, p2.blockY)
            maxY = targetWorld.maxHeight

            val mm = plugin.messageManager
            val lang = plugin.languageManager

            val rawTitle = lang.get("suelolava.title.preparation_main")
            val rawSub = lang.get("suelolava.title.preparation_sub")
            val title = Title.title(mm.parse(rawTitle), mm.parse(rawSub))

            players.forEach { p ->
                p.gameMode = GameMode.SURVIVAL
                p.inventory.clear()

                // Armadura
                p.inventory.helmet = ItemStack(Material.LEATHER_HELMET)
                p.inventory.chestplate = ItemStack(Material.LEATHER_CHESTPLATE)
                p.inventory.leggings = ItemStack(Material.LEATHER_LEGGINGS)
                p.inventory.boots = ItemStack(Material.LEATHER_BOOTS)

                // Armas y Herramientas
                p.inventory.addItem(ItemStack(Material.STONE_SWORD))
                p.inventory.addItem(ItemStack(Material.STONE_PICKAXE))
                p.inventory.addItem(ItemStack(Material.STONE_AXE))
                p.inventory.addItem(ItemStack(Material.STONE_SHOVEL))

                p.inventory.addItem(ItemStack(Material.BOW))
                p.inventory.addItem(ItemStack(Material.ARROW, 3))

                // Bloques
                p.inventory.addItem(ItemStack(Material.TERRACOTTA, 64))
                p.inventory.addItem(ItemStack(Material.TERRACOTTA, 64))

                p.showTitle(title)
                p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
            }

            plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
                if (!isRunning) { task.cancel(); return@runAtFixedRate }

                if (phase == SlPhase.PLAYING || phase == SlPhase.DEATHMATCH) {
                    arrowTimer++
                    if (arrowTimer >= 60) {
                        arrowTimer = 0
                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            players.forEach { p ->
                                p.inventory.addItem(ItemStack(Material.ARROW, 3))
                                p.sendMessage(plugin.messageManager.parse("<green>+3 Flechas</green>"))
                            }
                        }
                    }
                }

                if (phase == SlPhase.PREPARING) {
                    mainTimer--
                    val rawPrep = lang.get("suelolava.actionbar.preparation")
                    val bar = mm.parse(rawPrep, Placeholder.parsed("time", mainTimer.toString()))
                    players.forEach { it.sendActionBar(bar); it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f) }

                    if (mainTimer <= 0) {
                        phase = SlPhase.PLAYING
                        mainTimer = 300 // 5 minutos de juego normal
                        plugin.server.broadcast(mm.parse(lang.get("suelolava.broadcast.started")))
                        startRisingLava(targetWorld)
                    }
                    return@runAtFixedRate
                }

                if (phase == SlPhase.PLAYING) {
                    mainTimer--
                    if (mainTimer <= 0) {
                        phase = SlPhase.DEATHMATCH
                        mainTimer = 120 // 2 minutos de deathmatch

                        val center = arena.centerLocation
                        val cx = center?.x ?: ((minX + maxX) / 2.0)
                        val cz = center?.z ?: ((minZ + maxZ) / 2.0)

                        plugin.server.broadcast(mm.parse("<#FF0000><b>¡DEATHMATCH!</b></#FF0000> <white>El borde mágico se reducirá cada 20 segundos. ¡Sobrevive a toda costa!</white>"))

                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            borderManager.start(targetWorld, cx, cz, delaySeconds = 0L, intervalSecs = 20L)
                        }
                    }
                }

                if (phase == SlPhase.DEATHMATCH) {
                    mainTimer--
                    if (mainTimer <= 0) {
                        phase = SlPhase.DRAW
                        plugin.server.broadcast(mm.parse("<#00FF00><b>¡EMPATE!</b></#00FF00> <white>El tiempo se agotó y la lava no pudo consumir a todos.</white>"))
                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            players.forEach { p ->
                                plugin.puntajeManager.addPoints(p, 10, "Sobrevivir al Empate")
                                p.sendTitle("¡EMPATE!", "Sobreviviste a la lava", 10, 70, 20)
                                plugin.server.broadcast(mm.parse("<#00FFFF>🏆 <yellow>${p.name}</yellow> <gray>sobrevivió a El Suelo es Lava!</gray></#00FFFF>"))
                            }
                            stop()
                        }
                        task.cancel()
                        return@runAtFixedRate
                    }
                }

                // Comprobar eliminación por lava o void
                val toEliminate = players.filter {
                    it.location.block.type == org.bukkit.Material.LAVA || it.location.y <= targetWorld.minHeight + 2
                }
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    toEliminate.forEach { p ->
                        eliminate(p)
                        lastEliminationTime = System.currentTimeMillis()
                    }
                }

                // Comprobar modo caos
                val chaosCfg = plugin.config
                if (chaosCfg.getBoolean("suelolava.chaos_mode.enabled", true) && !chaosActivated) {
                    val threshold = chaosCfg.getInt("suelolava.chaos_mode.alive_threshold", 4)
                    val idleSec = chaosCfg.getInt("suelolava.chaos_mode.idle_seconds", 60)
                    val timeSinceElim = (System.currentTimeMillis() - lastEliminationTime) / 1000
                    if (players.size <= threshold && timeSinceElim >= idleSec && lastEliminationTime > 0) {
                        chaosActivated = true
                        plugin.server.globalRegionScheduler.run(plugin) { _ -> activateChaosMode() }
                    }
                }

                if (players.size <= 1) {
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                    task.cancel()
                }

            }, 1, 1, TimeUnit.SECONDS)

        }, 10L)
    }

    fun fillChestWithLoot(chest: Chest) {
        // Desactivado a petición del usuario.
    }

    private fun startRisingLava(world: World) {
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }
            if (currentLavaY > maxY) { task.cancel(); return@runAtFixedRate }

            val rawRising = plugin.languageManager.get("suelolava.actionbar.rising")
            val bar = plugin.messageManager.parse(rawRising, Placeholder.parsed("y", currentLavaY.toString()))
            players.forEach {
                it.sendActionBar(bar)
                it.playSound(it.location, Sound.BLOCK_LAVA_EXTINGUISH, 0.5f, 1.5f)
            }

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

    private fun activateChaosMode() {
        val lang = plugin.languageManager
        val mm = plugin.messageManager
        val arrows = plugin.config.getInt("suelolava.chaos_mode.bow_arrows", 3)

        val title = Title.title(
            mm.parse(lang.get("suelolava.chaos.title_main")),
            mm.parse(lang.get("suelolava.chaos.title_sub"))
        )

        val bcMsg = mm.parse(lang.get("suelolava.chaos.broadcast"))
        plugin.server.broadcast(bcMsg)

        chaosBossBar = BossBar.bossBar(
            bcMsg,
            1.0f,
            BossBar.Color.RED,
            BossBar.Overlay.NOTCHED_6
        )

        players.forEach { p ->
            p.showTitle(title)
            chaosBossBar?.let { bar -> p.showBossBar(bar) }
            p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f)
            p.inventory.addItem(ItemStack(Material.BOW))
            p.inventory.addItem(ItemStack(Material.ARROW, arrows))
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
        gameWorld?.let { borderManager.stop(it) }

        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation

        chaosBossBar?.let { bar ->
            plugin.server.onlinePlayers.forEach { it.hideBossBar(bar) }
        }
        chaosBossBar = null
        openedChests.clear()

        val todos = players + spectators
        todos.forEach { p ->
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}
