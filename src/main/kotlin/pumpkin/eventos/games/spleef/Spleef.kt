package pumpkin.eventos.games.spleef

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

class Spleef(private val plugin: PumpkinEventos) : EventGame("spleef", "<#E0FFFF>Spleef Clásico</#E0FFFF>") {

    var isPreparation = true
    private var timer = 10
    private val boosterManager = BoosterManager(plugin, this)

    override fun onStart() {
        isPreparation = true
        timer = 10

        // Tipado explícito para evitar errores del IDE
        players.forEach { p: Player ->
            p.gameMode = GameMode.SURVIVAL
            p.inventory.clear()
        }

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            if (isPreparation) {
                timer--
                val bar = plugin.messageManager.parse("<white>El Spleef comienza en: <aqua><bold>$timer</bold>s")
                players.forEach { p: Player ->
                    p.sendActionBar(bar)
                    p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f)
                }

                if (timer <= 0) {
                    isPreparation = false
                    val runMsg = plugin.messageManager.parse("<#E0FFFF><b>¡A ROMPER NIEVE!</b></#E0FFFF> <white>Haz caer a tus rivales</white>")

                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        players.forEach { p: Player ->
                            p.sendActionBar(runMsg)
                            darPala(p)
                        }
                        boosterManager.start(listOf(BoosterType.SPEED_1, BoosterType.JUMP_1, BoosterType.MIX))
                    }
                }
                return@runAtFixedRate
            }

            // Loop de caída
            val toEliminate = players.filter { p: Player ->
                p.location.y <= p.world.minHeight + 2 || p.location.block.type == Material.WATER || p.location.block.type == Material.LAVA
            }
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                toEliminate.forEach { p: Player -> eliminate(p) }
            }

        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun darPala(p: Player) {
        val shovel = ItemStack(Material.DIAMOND_SHOVEL)
        val meta = shovel.itemMeta
        meta.displayName(plugin.messageManager.parse("<#E0FFFF><b>Pala de Spleef</b></#E0FFFF>"))
        meta.addEnchant(Enchantment.EFFICIENCY, 5, true)
        meta.isUnbreakable = true
        shovel.itemMeta = meta

        p.inventory.setItem(0, shovel)
        p.playSound(p.location, Sound.ENTITY_ITEM_PICKUP, 1f, 1f)
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return
        plugin.server.broadcast(plugin.messageManager.parse("<newline><#E0FFFF><b>SPLEEF CLÁSICO</b></#E0FFFF> <white>» <purple>${winner.name}</purple> ha ganado el evento!<newline>"))
        winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        stop()
    }

    override fun onStop() {
        boosterManager.stop()
        plugin.eventManager.currentGame = null

        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation

        val todos = players + spectators
        todos.forEach { p: Player ->
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.teleportAsync(lobby)
        }
    }
}
