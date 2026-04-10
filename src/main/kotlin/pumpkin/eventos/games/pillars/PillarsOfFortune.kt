package pumpkin.eventos.games.pillars

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.concurrent.ConcurrentLinkedQueue

class PillarsOfFortune(private val plugin: PumpkinEventos) : EventGame("pillars", "<aqua>Pillars of Fortune</aqua>") {

    var isPreparation = true
    private var startTimer = 5
    var itemTimer = 10

    private val cageLocations = ConcurrentLinkedQueue<Location>()

    override fun getExtraPlaceholders(): Map<String, String> {
        return mapOf(
            "%item_timer%" to itemTimer.toString(),
            "%mapa%" to (currentArena?.id ?: "Desconocido")
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(
            GameRule.FALL_DAMAGE to true,
            GameRule.NATURAL_REGENERATION to false
        )
    }

    override fun onStart() {
        isPreparation = true
        startTimer = 5
        itemTimer = 10
        cageLocations.clear()

        val spawns = currentArena?.spawnPoints ?: emptyList()

        // Obtenemos el mundo en el que los jugadores ya están (el clonado)
        val targetWorld = players.firstOrNull()?.world ?: currentArena?.centerLocation?.world ?: return

        var spawnIndex = 0

        // Repartimos a los jugadores en los pilares
        players.forEach { player ->
            player.gameMode = GameMode.SURVIVAL
            player.inventory.clear()
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }

            if (spawns.isNotEmpty()) {
                val pilarLoc = spawns[spawnIndex % spawns.size].clone()
                pilarLoc.world = targetWorld // Seteamos el mundo clonado a la coordenada del spawn
                spawnIndex++
                centerAndCage(player, pilarLoc)
            }
        }

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            // -- FASE DE PREPARACIÓN --
            if (isPreparation) {
                if (startTimer > 0) {
                    val rawMsg = plugin.languageManager.get("pillars.actionbar.starting")
                    val bar = plugin.messageManager.parse(rawMsg, Placeholder.parsed("time", startTimer.toString()))
                    players.forEach {
                        it.sendActionBar(bar)
                        it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
                    }
                    startTimer--
                } else {
                    isPreparation = false
                    removeCages()
                    val startMsg = plugin.messageManager.parse(plugin.languageManager.get("pillars.actionbar.started"))
                    players.forEach {
                        it.sendActionBar(startMsg)
                        it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                    }
                }
                return@runAtFixedRate
            }

            // -- FASE DE JUEGO --
            val toEliminate = players.filter { it.location.y <= it.world.minHeight + 5 }
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                toEliminate.forEach { eliminate(it) }
            }

            itemTimer--
            if (itemTimer > 0) {
                val actionMsg = plugin.messageManager.parse("<white>Próximo ítem en: <aqua><bold>${itemTimer}</bold>s</aqua></white>")
                players.forEach { it.sendActionBar(actionMsg) }
            } else {
                itemTimer = 10
                val prefix = plugin.languageManager.get("prefix")
                val receiveMsg = plugin.messageManager.parse("$prefix<#39FF14>¡Se te ha dado un ítem sorpresa!</#39FF14>")

                players.forEach { p ->
                    val randomItem = getRandomItem()
                    p.inventory.addItem(randomItem)
                    p.playSound(p.location, Sound.ENTITY_ITEM_PICKUP, 1f, 1.5f)
                    p.sendMessage(receiveMsg)
                }
            }

        }, 20L, 20L)
    }

    private fun centerAndCage(player: Player, targetLoc: Location) {
        val targetWorld = targetLoc.world ?: return
        val centerLoc = Location(targetWorld, targetLoc.blockX + 0.5, targetLoc.blockY.toDouble(), targetLoc.blockZ + 0.5, targetLoc.yaw, targetLoc.pitch)

        player.teleportAsync(centerLoc).thenAccept {
            plugin.server.regionScheduler.run(plugin, centerLoc) { _ ->
                val cx = centerLoc.blockX
                val cy = centerLoc.blockY
                val cz = centerLoc.blockZ

                for (x in -1..1) {
                    for (y in -1..3) {
                        for (z in -1..1) {
                            if (x == -1 || x == 1 || z == -1 || z == 1 || y == -1 || y == 3) {
                                val block = targetWorld.getBlockAt(cx + x, cy + y, cz + z)
                                if (block.isEmpty || !block.type.isSolid) {
                                    block.setType(Material.GLASS, false)
                                    cageLocations.add(block.location)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun removeCages() {
        cageLocations.forEach { loc ->
            plugin.server.regionScheduler.run(plugin, loc) { _ ->
                val block = loc.block
                if (block.type == Material.GLASS) block.setType(Material.AIR, false)
            }
        }
        cageLocations.clear()
    }

    private fun getRandomItem(): ItemStack {
        val options = listOf(
            Pair(Material.COBBLESTONE, 16..32), Pair(Material.OBSIDIAN, 4..8),
            Pair(Material.GLASS, 8..16), Pair(Material.DIAMOND_CHESTPLATE, 1..1),
            Pair(Material.IRON_SWORD, 1..1), Pair(Material.BOW, 1..1), Pair(Material.ARROW, 5..15),
            Pair(Material.TNT, 1..4), Pair(Material.WATER_BUCKET, 1..1), Pair(Material.LAVA_BUCKET, 1..1),
            Pair(Material.COBWEB, 2..5), Pair(Material.SNOWBALL, 8..16), Pair(Material.GOLDEN_APPLE, 1..2),
            Pair(Material.ENDER_PEARL, 1..1)
        )
        val selected = options.random()
        return ItemStack(selected.first, selected.second.random())
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return
        val rawWin = plugin.languageManager.get("pillars.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
        stop()
    }

    override fun onStop() {
        removeCages()
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
