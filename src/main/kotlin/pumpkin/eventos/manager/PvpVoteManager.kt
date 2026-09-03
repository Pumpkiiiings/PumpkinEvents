package pumpkin.eventos.manager

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.UUID

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
    private var voteTask: ScheduledTask? = null

    fun shouldLaunchVote(gameId: String): Boolean {
        return plugin.config.getBoolean("pvp_vote.$gameId", false)
    }

    fun startVoting(game: EventGame, onResult: (Boolean) -> Unit): Boolean {
        if (isVoting) return false
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

        voteTask = plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            if (pendingGame !== game) return@runDelayed
            val result = yesVotes >= noVotes
            isVoting = false
            pendingGame = null
            val msg = if (result) lang.get("pvp_vote.result_yes") else lang.get("pvp_vote.result_no")
            Bukkit.broadcast(mm.parse(msg))
            Bukkit.getOnlinePlayers().forEach {
                it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, if (result) 2f else 0.5f)
            }
            onResult(result)
        }, 200L)
        return true
    }

    fun cancelFor(game: EventGame) {
        if (pendingGame !== game) return
        voteTask?.cancel()
        voteTask = null
        pendingGame = null
        isVoting = false
        voted.clear()
    }

    fun registerVote(player: Player, voteSi: Boolean) {
        if (!isVoting) {
            plugin.languageManager.send(player, "pvp_vote.no_game")
            return
        }
        if (voted.contains(player.uniqueId)) {
            plugin.languageManager.send(player, "pvp_vote.already_voted")
            return
        }
        voted.add(player.uniqueId)
        if (voteSi) yesVotes++ else noVotes++
        val msg = if (voteSi) "<green>✔ Votaste SÍ</green>" else "<red>✘ Votaste NO</red>"
        player.sendMessage(plugin.messageManager.parse(msg))
    }
}
