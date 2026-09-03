package pumpkin.eventos.games.simondice.minigames

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.simondice.SimonDice
import java.util.UUID
import java.util.concurrent.TimeUnit

class Congelados(private val plugin: PumpkinEventos) : Listener {

    var activo = false
    val azules = mutableListOf<UUID>() // Corredores ❄️
    val rojos = mutableListOf<UUID>()  // Congeladores 🔥
    val congelados = mutableListOf<UUID>()
    private var gameInstance: SimonDice? = null

    /** Restaura efectos/equipo si Simón se detiene antes de concluir la ronda. */
    fun stop() {
        activo = false
        (azules + rojos).distinct().mapNotNull(plugin.server::getPlayer).forEach { player ->
            player.inventory.helmet = null
            player.removePotionEffect(PotionEffectType.SLOWNESS)
            player.removePotionEffect(PotionEffectType.JUMP_BOOST)
        }
        azules.clear()
        rojos.clear()
        congelados.clear()
        gameInstance = null
    }

    fun iniciar(game: SimonDice, tiempoSegundos: Int) {
        val mm = plugin.messageManager
        activo = true
        gameInstance = game
        azules.clear()
        rojos.clear()
        congelados.clear()

        val shuffleVivos = game.players.shuffled()
        val cantRojos = (shuffleVivos.size / 3).coerceAtLeast(1)

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            for (i in shuffleVivos.indices) {
                val p = shuffleVivos[i]
                if (i < cantRojos) {
                    rojos.add(p.uniqueId)
                    p.inventory.helmet = ItemStack(Material.MAGMA_BLOCK)
                    plugin.languageManager.send(p, "simon_game.frozen.red_team")
                } else {
                    azules.add(p.uniqueId)
                    p.inventory.helmet = ItemStack(Material.ICE)
                    plugin.languageManager.send(p, "simon_game.frozen.blue_team")
                }
            }
        }

        val title = Title.title(plugin.languageManager.component("simon_game.frozen.title_main"), plugin.languageManager.component("simon_game.frozen.title_subtitle"))
        game.players.forEach { it.showTitle(title) }

        var restante = tiempoSegundos
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!activo || !game.isRunning) {
                task.cancel()
                return@runAtFixedRate
            }

            if (restante <= 5) {
                val color = if (restante <= 3) "<red>" else "<yellow>"
                val aviso = Title.title(
                    plugin.languageManager.component("simon_game.frozen.countdown_main", Placeholder.parsed("countdown", "$color<bold>⏳ $restante")),
                    plugin.languageManager.component("simon_game.frozen.countdown_subtitle"))
                game.players.forEach { it.showTitle(aviso); it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f) }
            }

            if (restante <= 0 || congelados.size >= azules.size) {
                task.cancel()
                finalizarJuego(game)
                return@runAtFixedRate
            }

            game.actionText = "❄️ <#00FFFF>Congelados: ${congelados.size}/${azules.size} <gray>|</gray> <white>⏳ ${restante}s"
            restante--
        }, 20L, 20L)
    }

    @EventHandler
    fun alGolpear(e: EntityDamageByEntityEvent) {
        if (!activo) return
        val atacante = e.damager as? Player ?: return
        val victima = e.entity as? Player ?: return
        val game = gameInstance ?: return
        val mm = plugin.messageManager

        if (!game.players.contains(atacante) || !game.players.contains(victima)) return
        e.damage = 0.001

        val idA = atacante.uniqueId
        val idV = victima.uniqueId

        // ROJO CONGELA AZUL
        if (rojos.contains(idA) && azules.contains(idV) && !congelados.contains(idV)) {
            congelados.add(idV)
            victima.inventory.helmet = ItemStack(Material.PACKED_ICE)
            victima.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 999999, 255, false, false))
            victima.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 999999, 200, false, false))

            plugin.languageManager.send(victima, "simon_game.frozen.frozen")
            atacante.playSound(atacante.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)
            victima.playSound(victima.location, Sound.BLOCK_SNOW_BREAK, 1f, 1f)
        }

        // AZUL RESCATA AZUL
        if (azules.contains(idA) && !congelados.contains(idA) && congelados.contains(idV)) {
            congelados.remove(idV)
            victima.inventory.helmet = ItemStack(Material.ICE)
            victima.removePotionEffect(PotionEffectType.SLOWNESS)
            victima.removePotionEffect(PotionEffectType.JUMP_BOOST)

            plugin.languageManager.send(victima, "simon_game.frozen.rescued")
            plugin.languageManager.send(atacante, "simon_game.frozen.rescuer", Placeholder.unparsed("player", victima.name))
            victima.playSound(victima.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f)
        }
    }

    private fun finalizarJuego(game: SimonDice) {
        activo = false
        val mm = plugin.messageManager

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            game.players.forEach {
                it.inventory.helmet = null
                it.removePotionEffect(PotionEffectType.SLOWNESS)
                it.removePotionEffect(PotionEffectType.JUMP_BOOST)
            }

            val gananRojos = congelados.size >= azules.size
            val perdedores = if (gananRojos) azules else rojos
            val colorGanador = if (gananRojos) "<#FF3131>" else "<#00FFFF>"
            val nombreGanador = if (gananRojos) "LOS ROJOS 🔥" else "LOS AZULES ❄️"

            val titleFin = Title.title(
                plugin.languageManager.component("simon_game.frozen.winner_main", Placeholder.parsed("winner", "$colorGanador<bold>¡$nombreGanador GANAN!")),
                plugin.languageManager.component("simon_game.frozen.winner_subtitle")
            )
            game.players.forEach { it.showTitle(titleFin) }

            game.actionText = "<red>¡TIEMPO AGOTADO!</red>"

            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                if (!game.isRunning) return@runDelayed
                perdedores.forEach { id ->
                    val p = plugin.server.getPlayer(id)
                    if (p != null) game.aplicarCuello(p)
                }

                plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                    game.actionText = "Esperando orden..."
                }, 60L)

            }, 40L)
        }
    }
}
