package pumpkin.eventos.games.tntgames

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.BorderShrinkManager
import pumpkin.eventos.games.BoosterManager
import pumpkin.eventos.games.BoosterType
import pumpkin.eventos.games.EventGame
import java.util.concurrent.TimeUnit

class TntRun(plugin: PumpkinEventos) : EventGame(plugin, "tntrun", "<yellow>TNT Run</yellow>"), Listener {

    var isPreparation = true
    var timer = 10
    private var gameSecondsElapsed = 0

    // Booster system
    private val boosterManager = BoosterManager(plugin, this)
    private val borderManager = BorderShrinkManager(plugin, this)

    override fun onStart() {
        isPreparation = true
        timer = 10
        gameSecondsElapsed = 0

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            if (isPreparation) {
                timer--
                val rawMsg = plugin.languageManager.get("tntrun.actionbar.preparation")
                val bar = plugin.messageManager.parse(rawMsg, Placeholder.parsed("time", timer.toString()))

                players.forEach { it.sendActionBar(bar); it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f) }

                if (timer <= 0) {
                    isPreparation = false
                    val runMsg = plugin.messageManager.parse(plugin.languageManager.get("tntrun.actionbar.run"))

                    players.forEach { p ->
                        p.sendActionBar(runMsg)
                        giveFeather(p)
                    }

                    boosterManager.start(BoosterType.entries.toList())
                }
                return@runAtFixedRate
            }

            val toEliminate = players.filter { it.location.y <= it.world.minHeight + 2 }
            toEliminate.forEach { eliminate(it) }

            // Border shrink: arrancar a los 5 minutos (300s) de juego real
            gameSecondsElapsed++
            if (gameSecondsElapsed == 300) {
                val world = gameWorld ?: return@runAtFixedRate
                val center = currentArena?.centerLocation
                val cx = center?.x ?: 0.0
                val cz = center?.z ?: 0.0
                plugin.server.asyncScheduler.runNow(plugin) { _ ->
                    borderManager.start(world, cx, cz, delaySeconds = 0L)
                }
            }

        }, 20L, 20L)

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || isPreparation) { if (!isRunning) task.cancel(); return@runAtFixedRate }
            for (player in players) scheduleBlockBreak(player)
        }, 10L, 2L)
    }

    private fun giveFeather(player: Player) {
        val feather = ItemStack(Material.FEATHER)
        val meta = feather.itemMeta
        meta.displayName(plugin.messageManager.parse("<aqua><b>PLUMA DE SALTO</b></aqua>"))
        meta.lore(listOf(
            plugin.messageManager.parse("<white>¡Impúlsate hacia el cielo!</white>"),
            plugin.messageManager.parse("<gray>Cooldown: 3s</gray>")
        ))

        // Etiqueta interna para que el GameListener sepa qué es este ítem
        val key = NamespacedKey(plugin, "tnt_item")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, "feather")

        feather.itemMeta = meta
        player.inventory.setItem(4, feather) // Lo pone en el medio de la hotbar
    }

    @EventHandler
    fun onFeatherUse(e: PlayerInteractEvent) {
        val p = e.player
        if (!isRunning || isPreparation) return
        if (!players.contains(p)) return
        if (e.hand != EquipmentSlot.HAND) return

        val item = e.item ?: return
        val key = NamespacedKey(plugin, "tnt_item")
        val tag = item.itemMeta?.persistentDataContainer?.get(key, PersistentDataType.STRING) ?: return
        if (tag != "feather") return

        e.isCancelled = true
        if (p.hasCooldown(Material.FEATHER)) return
        p.setCooldown(Material.FEATHER, 60) // 3 segundos (60 ticks)

        p.scheduler.run(plugin, { _ ->
            p.velocity = p.location.direction.normalize().multiply(0.3).setY(1.2)
            p.playSound(p.location, Sound.ENTITY_BAT_TAKEOFF, 1f, 1.2f)
            p.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false))
        }, null)
    }

    private fun scheduleBlockBreak(player: Player) {
        val box = player.boundingBox
        val y = box.minY - 0.05
        val world = player.world

        val corners = listOf(
            Location(world, box.minX, y, box.minZ), Location(world, box.maxX, y, box.minZ),
            Location(world, box.minX, y, box.maxZ), Location(world, box.maxX, y, box.maxZ)
        )

        val blocksToBreak = corners.map { it.block }.toSet().filter {
            it.type == Material.SAND || it.type == Material.RED_SAND || it.type == Material.GRAVEL || it.type == Material.TNT
        }

        for (block in blocksToBreak) {
            plugin.server.regionScheduler.runDelayed(plugin, block.location, { _ ->
                if (!isRunning) return@runDelayed
                if (block.type != Material.AIR) {
                    block.setType(Material.AIR, false)
                    // Eliminar también el bloque de soporte debajo (segunda capa)
                    val blockBelow = block.getRelative(BlockFace.DOWN)
                    if (blockBelow.type != Material.AIR) {
                        blockBelow.setType(Material.AIR, false)
                    }
                }
            }, 6L)
        }
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return

        val rawWin = plugin.languageManager.get("tntrun.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
        plugin.puntajeManager.addPoints(winner, 10, "¡Victoria conseguida!")
        stop()
    }

    override fun onStop() {
        boosterManager.stop()
        gameWorld?.let { borderManager.stop(it) }
        plugin.eventManager.currentGame = null

        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) {
            lobby = plugin.server.worlds[0].spawnLocation
        }

        val todos = players + spectators

        todos.forEach { p ->
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = org.bukkit.GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}
