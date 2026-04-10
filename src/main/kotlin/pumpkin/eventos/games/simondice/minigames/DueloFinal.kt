package pumpkin.eventos.games.simondice.minigames

import net.kyori.adventure.title.Title
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.simondice.SimonDice

class DueloFinal(private val plugin: PumpkinEventos) : Listener {

    var torneoActivo = false
    private var luchador1: Player? = null
    private var luchador2: Player? = null
    private var gameInstance: SimonDice? = null

    fun iniciarTorneo(game: SimonDice) {
        val mm = plugin.messageManager
        if (game.players.size < 2) {
            plugin.server.broadcast(mm.parse("<red>❌ <b>ERROR:</b> <white>No hay suficientes guerreros vivos para el duelo."))
            return
        }
        this.gameInstance = game
        torneoActivo = true
        siguienteRonda(game)
    }

    private fun siguienteRonda(game: SimonDice) {
        val mm = plugin.messageManager

        if (game.players.size <= 1) {
            torneoActivo = false
            game.actionText = "🏁 Evento Finalizado"
            game.checkWinner()
            return
        }

        val vivos = game.players.shuffled()
        luchador1 = vivos[0]
        luchador2 = vivos[1]

        val l1 = luchador1 ?: return
        val l2 = luchador2 ?: return

        val arena = game.currentArena

        // --- AQUÍ USAMOS LAS LOCALIZACIONES DEL COMANDO SETUP ---
        val gradaLoc = arena?.gradas ?: arena?.centerLocation?.clone()?.add(0.0, 10.0, 0.0)
        val spawnA = arena?.dueloA ?: arena?.spawnPoints?.getOrNull(0) ?: l1.location
        val spawnB = arena?.dueloB ?: arena?.spawnPoints?.getOrNull(1) ?: l2.location

        // --- MANDAR AL RESTO A LAS GRADAS ---
        for (i in 2 until vivos.size) {
            val espectador = vivos[i]
            if (gradaLoc != null) espectador.teleportAsync(gradaLoc)
            espectador.sendMessage(mm.parse("<#00FFFF>🍿 <b>GRADAS:</b> <white>Espera tu turno para luchar..."))
        }

        // --- PREPARAR LUCHADORES ---
        l1.teleportAsync(spawnA)
        l2.teleportAsync(spawnB)

        darKitDuelo(l1)
        darKitDuelo(l2)

        val title = Title.title(mm.parse("<#FF3131>⚔️ <b>¡A LUCHAR!</b> ⚔️"), mm.parse("<white>${l1.name} <red>vs <white>${l2.name}"))
        game.players.forEach { it.showTitle(title) }

        game.actionText = "⚔️ ${l1.name} vs ${l2.name}"
        plugin.server.onlinePlayers.forEach { it.playSound(it.location, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f) }
    }

    private fun darKitDuelo(p: Player) {
        p.inventory.clear()
        p.inventory.helmet = ItemStack(Material.DIAMOND_HELMET)
        p.inventory.chestplate = ItemStack(Material.DIAMOND_CHESTPLATE)
        p.inventory.leggings = ItemStack(Material.DIAMOND_LEGGINGS)
        p.inventory.boots = ItemStack(Material.DIAMOND_BOOTS)
        p.inventory.addItem(ItemStack(Material.DIAMOND_SWORD))
        p.inventory.addItem(ItemStack(Material.GOLDEN_APPLE, 16))
        p.sendMessage(plugin.messageManager.parse("<#39FF14>🛡️ <b>EQUIPAMIENTO:</b> <white>Recibiste el kit de gladiador."))
    }

    @EventHandler
    fun alMorirEnDuelo(e: PlayerDeathEvent) {
        if (!torneoActivo) return
        val muerto = e.player
        val game = gameInstance ?: return

        if (muerto == luchador1 || muerto == luchador2) {
            e.isCancelled = true
            muerto.health = 20.0
            muerto.fireTicks = 0

            val ganadorRonda = if (muerto == luchador1) luchador2 else luchador1
            if (ganadorRonda != null) {
                plugin.server.broadcast(plugin.messageManager.parse("<#CCFF00>⭐ <b>${ganadorRonda.name}</b> <white>ha ganado el combate."))
                ganadorRonda.inventory.clear()
            }

            game.aplicarCuello(muerto)

            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                if (torneoActivo) siguienteRonda(game)
            }, 70L)
        }
    }
}
