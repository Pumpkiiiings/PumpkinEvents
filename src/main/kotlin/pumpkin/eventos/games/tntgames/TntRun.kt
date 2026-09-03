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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TntRun(plugin: PumpkinEventos) : EventGame(plugin, "tntrun", "<yellow>TNT Run</yellow>"), Listener {

    var isPreparation = true
    var timer = 10
    private var gameSecondsElapsed = 0

    // Evitar programar el mismo bloque múltiples veces
    private val blocksToRemove = ConcurrentHashMap.newKeySet<Location>()

    // Booster system
    private val boosterManager = BoosterManager(plugin, this)
    private val borderManager = BorderShrinkManager(plugin, this)

    override fun onStart() {
        isPreparation = true
        timer = 10
        gameSecondsElapsed = 0
        blocksToRemove.clear()

        runAtFixedRate( { task ->

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
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    borderManager.start(world, cx, cz, delaySeconds = 0L)
                }
            }

        }, 20L, 20L)

        // Escáner de bloques bajo los pies (Se ejecuta cada 1 TICK para no fallar saltos)
        runAtFixedRate( { task ->
            if (isPreparation) return@runAtFixedRate // ¡Protección para que no se caiga en preparación!

            for (player in players) {
                scheduleBlockBreak(player)
            }
        }, 1L, 1L)
    }

    private fun giveFeather(player: Player) {
        val feather = ItemStack(Material.FEATHER)
        val meta = feather.itemMeta
        meta.displayName(plugin.messageManager.parse("<aqua><b>PLUMA DE SALTO</b></aqua>"))
        meta.lore(listOf(
            plugin.messageManager.parse("<white>¡Impúlsate hacia el cielo!</white>"),
            plugin.messageManager.parse("<gray>Cooldown: 3s</gray>")
        ))

        val key = NamespacedKey(plugin, "tnt_item")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, "feather")

        feather.itemMeta = meta
        player.inventory.setItem(4, feather)
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
        // 0.1 asegura que detecte el bloque incluso justo al momento de saltar
        val y = box.minY - 0.1
        val world = player.world

        // 4 esquinas de la hitbox para ser súper preciso cuando camina en los bordes
        val corners = listOf(
            Location(world, box.minX, y, box.minZ), Location(world, box.maxX, y, box.minZ),
            Location(world, box.minX, y, box.maxZ), Location(world, box.maxX, y, box.maxZ)
        )

        val blocksToBreak = corners.map { it.block }.toSet().filter {
            it.type == Material.SAND || it.type == Material.RED_SAND || it.type == Material.GRAVEL || it.type == Material.TNT
        }

        for (block in blocksToBreak) {
            val loc = block.location
            if (blocksToRemove.contains(loc)) continue // Ya está programado para borrarse

            blocksToRemove.add(loc)

            // Retraso de 8 Ticks (0.4 segundos) para borrar el bloque
            plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                blocksToRemove.remove(loc)
                if (!isRunning) return@runDelayed

                // 1. Borrar bloque de arriba
                if (block.type != Material.AIR) {
                    block.setType(Material.AIR, false)
                }

                // 2. Borrar bloque de soporte de abajo (La TNT)
                val blockBelow = block.getRelative(BlockFace.DOWN)
                if (blockBelow.type == Material.TNT || blockBelow.type.name.contains("SAND") || blockBelow.type == Material.GRAVEL) {
                    blockBelow.setType(Material.AIR, false)
                }

            }, 8L)
        }
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return

        val rawWin = plugin.languageManager.get("tntrun.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
        awardVictory(winner, 10)
        stop()
    }

    override fun onStop() {
        boosterManager.stop()
        gameWorld?.let { borderManager.stop(it) }
        blocksToRemove.clear()

        returnToLobby()
    }
}
