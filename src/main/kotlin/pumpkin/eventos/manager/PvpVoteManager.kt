package pumpkin.eventos.manager

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Gestiona la votación de PvP al inicio de ciertos modos de juego.
 * Configurable por modo en config.yml bajo pvp_vote.<gameId>: true/false
 */
class PvpVoteManager(private val plugin: PumpkinEventos) {

    private var yesVotes = 0
    private var noVotes = 0
    private val voted = mutableSetOf<UUID>()
    var isVoting = false
        private set

    var pendingGame: EventGame? = null
    private var pvpEnabled = true

    fun shouldLaunchVote(gameId: String): Boolean {
        return plugin.config.getBoolean("pvp_vote.$gameId", false)
    }

    fun startVoting(game: EventGame, onResult: (Boolean) -> Unit) {
        if (isVoting) return
        isVoting = true
        yesVotes = 0
        noVotes = 0
        voted.clear()
        pendingGame = game

        val lang = plugin.languageManager
        val mm = plugin.messageManager

        val header = lang.get("pvp_vote.header")
        val yes = lang.get("pvp_vote.yes_option")
        val no = lang.get("pvp_vote.no_option")
        val footer = lang.get("pvp_vote.footer")

        Bukkit.broadcast(mm.parse("$header\n$yes\n$no\n$footer"))
        Bukkit.getOnlinePlayers().forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f) }

        plugin.server.asyncScheduler.runDelayed(plugin, { _ ->
            val result = yesVotes >= noVotes
            isVoting = false
            pendingGame = null
            val msg = if (result) lang.get("pvp_vote.result_yes") else lang.get("pvp_vote.result_no")
            Bukkit.broadcast(mm.parse(msg))
            Bukkit.getOnlinePlayers().forEach {
                it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, if (result) 2f else 0.5f)
            }
            plugin.server.globalRegionScheduler.run(plugin) { _ -> onResult(result) }
        }, 10L, TimeUnit.SECONDS)
    }

    fun registerVote(player: Player, voteSi: Boolean) {
        if (!isVoting) {
            player.sendMessage(plugin.messageManager.parse(plugin.languageManager.get("pvp_vote.no_game")))
            return
        }
        if (voted.contains(player.uniqueId)) {
            player.sendMessage(plugin.messageManager.parse(plugin.languageManager.get("pvp_vote.already_voted")))
            return
        }
        voted.add(player.uniqueId)
        if (voteSi) yesVotes++ else noVotes++
        val msg = if (voteSi) "<green>✔ Votaste SÍ</green>" else "<red>✘ Votaste NO</red>"
        player.sendMessage(plugin.messageManager.parse(msg))
    }
}
