package pumpkin.eventos.games.skywars

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import pumpkin.eventos.PumpkinEventos

class SkywarsListener(private val plugin: PumpkinEventos) : Listener {

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame as? Skywars ?: return
        if (!game.isRunning) return

        // 1. LLENAR COFRES AL ABRIRLOS Y CREAR HOLOGRAMA
        if (e.action == Action.RIGHT_CLICK_BLOCK) {
            val block = e.clickedBlock ?: return
            if (block.type == Material.CHEST || block.type == Material.TRAPPED_CHEST) {
                if (game.phase == SwPhase.PLAYING && !game.openedChests.contains(block.location)) {
                    game.openedChests.add(block.location)

                    // Llenar inventario
                    val state = block.state as org.bukkit.block.Chest
                    state.inventory.clear()
                    val loot = SkywarsManager.generarLoot(game.chosenChest)
                    loot.forEach { item ->
                        val slot = (0 until 27).random()
                        state.inventory.setItem(slot, item)
                    }

                    // Poner holograma encima
                    game.spawnHologramAtChest(block.location)
                }
            }
        }

        // 2. MENÚS DE PREPARACIÓN
        if (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK) {
            val item = e.item ?: return
            when (item.type) {
                Material.BLAZE_POWDER -> {
                    if (game.phase == SwPhase.TEAM_SELECT) {
                        e.isCancelled = true
                        abrirMenuEquipos(p, game)
                    }
                }
                Material.CHEST -> {
                    if (game.phase == SwPhase.VOTING) {
                        e.isCancelled = true
                        abrirMenuCofres(p, game)
                    }
                }
                Material.DRAGON_HEAD -> {
                    if (game.phase == SwPhase.VOTING) {
                        e.isCancelled = true
                        abrirMenuEventos(p, game)
                    }
                }
                else -> {}
            }
        }
    }

    // --- REGISTRO DE KILLS ---
    @EventHandler
    fun onDeath(e: PlayerDeathEvent) {
        val victim = e.entity
        val killer = victim.killer
        val game = plugin.eventManager.currentGame as? Skywars ?: return

        if (game.isRunning && game.players.contains(victim) && killer != null && game.players.contains(killer)) {
            val currentKills = game.kills[killer] ?: 0
            game.kills[killer] = currentKills + 1
        }
    }

    private fun abrirMenuEquipos(p: Player, game: Skywars) {
        val gui = Gui.gui().title(Component.text("§8Selecciona Equipo")).rows(3).disableAllInteractions().create()
        val maxEquipos = (game.players.size / 2) + 1

        for (i in 1..maxEquipos) {
            val teamPlayers = game.players.filter { game.playerTeam[it] == i }
            val icon = if (teamPlayers.size >= 2) Material.RED_STAINED_GLASS_PANE else Material.GREEN_STAINED_GLASS_PANE
            val status = if (teamPlayers.size >= 2) "<red>Lleno</red>" else "<green>Click para unirte</green>"

            val lore = mutableListOf(status, "")
            teamPlayers.forEach { lore.add("<gray>- ${it.name}") }

            val item = dev.triumphteam.gui.builder.item.ItemBuilder.from(icon).name(plugin.messageManager.parse("<yellow>Equipo $i</yellow>"))
                .lore(lore.map { plugin.messageManager.parse(it) }).asGuiItem { _ ->
                    if (teamPlayers.size < 2) {
                        game.playerTeam[p] = i
                        p.sendMessage(plugin.messageManager.parse("<green>Te has unido al Equipo $i.</green>"))
                        p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                        gui.close(p)
                    }
                }
            gui.addItem(item)
        }
        gui.open(p)
    }

    private fun abrirMenuCofres(p: Player, game: Skywars) {
        val gui = Gui.gui().title(Component.text("§8Votar Cofres")).rows(1).disableAllInteractions().create()
        SkywarsChest.values().forEachIndexed { index, tipo ->
            val item = dev.triumphteam.gui.builder.item.ItemBuilder.from(tipo.icono).name(plugin.messageManager.parse(tipo.nombre))
                .lore(plugin.messageManager.parse("<gray>Click para votar por este tipo.</gray>")).asGuiItem { _ ->
                    game.chestVotes[p.uniqueId] = tipo
                    p.sendMessage(plugin.messageManager.parse("<green>Has votado por: ${tipo.nombre}</green>"))
                    p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
                    gui.close(p)
                }
            gui.setItem(index + 3, item)
        }
        gui.open(p)
    }

    private fun abrirMenuEventos(p: Player, game: Skywars) {
        val gui = Gui.gui().title(Component.text("§8Votar Evento Final")).rows(1).disableAllInteractions().create()
        SkywarsEvent.values().forEachIndexed { index, tipo ->
            val item = dev.triumphteam.gui.builder.item.ItemBuilder.from(tipo.icono).name(plugin.messageManager.parse(tipo.nombre))
                .lore(plugin.messageManager.parse("<gray>Click para votar por este evento.</gray>")).asGuiItem { _ ->
                    game.eventVotes[p.uniqueId] = tipo
                    p.sendMessage(plugin.messageManager.parse("<green>Has votado por: ${tipo.nombre}</green>"))
                    p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
                    gui.close(p)
                }
            gui.setItem(index + 3, item)
        }
        gui.open(p)
    }
}
