package pumpkin.eventos.games.parkour

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import pumpkin.eventos.PumpkinEventos

class ParkourListener(private val plugin: PumpkinEventos) : Listener {

    // ─────────────────────────────────────────────────────────────────────────
    //  Item interactions
    // ─────────────────────────────────────────────────────────────────────────
    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        val game = getGame() ?: return
        if (!game.isRunning) return

        if (e.action != Action.RIGHT_CLICK_AIR && e.action != Action.RIGHT_CLICK_BLOCK) return
        val item = e.item ?: return
        e.isCancelled = true

        when (item.type) {
            // Team select menu (SELECTING phase, duos only)
            Material.BLAZE_POWDER -> {
                if (game.phase == ParkourPhase.SELECTING && game.isDuos) {
                    abrirMenuEquipos(p, game)
                }
            }

            // Toggle hide players (PLAYING)
            Material.ENDER_EYE -> {
                if (game.phase == ParkourPhase.PLAYING) {
                    game.toggleHidePlayers(p)
                }
            }

            // Checkpoint return request — solo leader, duos PLAYING
            Material.FEATHER -> {
                if (game.phase == ParkourPhase.PLAYING && game.isDuos) {
                    game.requestCheckpointReturn(p)
                }
            }

            else -> { e.isCancelled = false }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  No friendly fire in duos
    // ─────────────────────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(e: EntityDamageByEntityEvent) {
        val victim  = e.entity as? Player ?: return
        val damager = e.damager as? Player ?: return
        val game = getGame() ?: return
        if (!game.isRunning || !game.isDuos) return
        if (!game.players.contains(victim)) return
        if (game.playerTeam[victim] == game.playerTeam[damager]) e.isCancelled = true
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Team selection menu
    // ─────────────────────────────────────────────────────────────────────────
    private fun abrirMenuEquipos(p: Player, game: Parkour) {
        val title = plugin.languageManager.get("parkour.menu.team_select_title")
            .ifBlank { "§8Seleccionar Equipo" }
        val gui = Gui.gui().title(Component.text(title)).rows(3).disableAllInteractions().create()
        val maxEquipos = (game.players.size / 2) + 1

        for (i in 1..maxEquipos) {
            val teamPlayers = game.players.filter { game.playerTeam[it] == i }
            val isFull = teamPlayers.size >= 2
            val icon = if (isFull) Material.RED_STAINED_GLASS_PANE else Material.GREEN_STAINED_GLASS_PANE
            val status = if (isFull)
                plugin.languageManager.get("parkour.menu.team_full").ifBlank { "<red>Lleno</red>" }
            else
                plugin.languageManager.get("parkour.menu.team_join").ifBlank { "<green>Clic para unirte</green>" }

            val lore = mutableListOf(status, "")
            teamPlayers.forEach { lore.add("<gray>- ${it.name}") }

            val item = ItemBuilder.from(icon)
                .name(plugin.messageManager.parse("<yellow>Equipo $i</yellow>"))
                .lore(lore.map { plugin.messageManager.parse(it) })
                .asGuiItem { _ ->
                    if (!isFull) {
                        game.playerTeam[p] = i
                        game.teamMembers.getOrPut(i) { mutableListOf() }.let {
                            if (!it.contains(p)) it.add(p)
                        }
                        // First player to join becomes leader
                        if (!game.teamLeader.containsKey(i)) game.teamLeader[i] = p

                        p.sendMessage(plugin.messageManager.parse(
                            plugin.languageManager.get("parkour.team_joined")
                                .ifBlank { "<green>Te uniste al Equipo $i.</green>" }
                                .replace("%team%", i.toString())
                        ))
                        p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                        gui.close(p)
                    }
                }
            gui.addItem(item)
        }
        gui.open(p)
    }

    private fun getGame(): Parkour? = plugin.eventManager.currentGame as? Parkour
}
