package pumpkin.eventos.games.tntgames

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.BoosterManager
import pumpkin.eventos.games.BoosterType
import pumpkin.eventos.games.EventGame
import java.util.concurrent.TimeUnit

class TntSpleef(private val plugin: PumpkinEventos) : EventGame("tntspleef", "<#FF3131>TNT Spleef</#FF3131>") {

    var isPreparation = true
    private var timer = 10
    private val boosterManager = BoosterManager(plugin, this)

    override fun onStart() {
        isPreparation = true
        timer = 10

        players.forEach { p ->
            p.gameMode = GameMode.ADVENTURE
            p.inventory.clear()
        }

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            if (isPreparation) {
                timer--
                val bar = plugin.messageManager.parse("<white>El Spleef comienza en: <aqua><bold>$timer</bold>s")
                players.forEach { p ->
                    p.sendActionBar(bar)
                    p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f)
                }

                if (timer <= 0) {
                    isPreparation = false
                    val runMsg = plugin.messageManager.parse("<#FF3131><b>¡A DISPARAR!</b></#FF3131> <white>Haz caer a tus rivales</white>")

                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        players.forEach { p ->
                            p.sendActionBar(runMsg)
                            darArco(p) // Arreglado el problema de "it"
                        }
                        boosterManager.start(listOf(BoosterType.SPEED_1, BoosterType.SPEED_2, BoosterType.JUMP_1, BoosterType.MIX))
                    }
                }
                return@runAtFixedRate
            }

            // Loop de caída
            val toEliminate = players.filter { it.location.y <= it.world.minHeight + 2 }
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                toEliminate.forEach { eliminate(it) }
            }

        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun darArco(p: Player) {
        val bow = ItemStack(Material.BOW)
        val meta = bow.itemMeta
        meta.displayName(plugin.messageManager.parse("<#FF3131><b>Arco Ignitor</b></#FF3131>"))
        meta.addEnchant(Enchantment.INFINITY, 1, true)
        meta.isUnbreakable = true
        bow.itemMeta = meta

        p.inventory.setItem(0, bow)
        p.inventory.setItem(9, ItemStack(Material.ARROW)) // Flecha para el Infinity
        p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f)
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return
        plugin.server.broadcast(plugin.messageManager.parse("<newline><#FF3131><b>TNT SPLEEF</b></#FF3131> <white>» <purple>${winner.name}</purple> ha ganado el evento!<newline>"))
        winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        stop()
    }

    override fun onStop() {
        boosterManager.stop()
        plugin.eventManager.currentGame = null

        val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation
        (players + spectators).forEach { p ->
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.teleportAsync(lobby)
        }
    }
}
