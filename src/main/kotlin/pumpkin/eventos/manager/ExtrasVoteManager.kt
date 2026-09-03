package pumpkin.eventos.manager

import org.bukkit.Sound
import org.bukkit.entity.Player
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.* // Importa todo de extras
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.UUID

class ExtrasVoteManager(private val plugin: PumpkinEventos) {

    private val votes = mutableMapOf<String, Int>()
    private val voted = mutableSetOf<UUID>()
    var isVoting = false
        private set
    private var votingGame: EventGame? = null
    private var voteTask: ScheduledTask? = null

    // LISTA ACTUALIZADA
    private val availableExtras = listOf("lava", "storm", "anvils", "geoffrey", "acidrain", "zerogravity", "antlife")

    fun startVoting() {
        if (isVoting || plugin.eventManager.currentGame == null) return
        if (!plugin.config.getBoolean("extras_vote.enabled", true)) return

        val game = plugin.eventManager.currentGame ?: return
        val allowedGames = plugin.config.getStringList("extras_vote.allowed_games")
        if (!allowedGames.contains(game.id)) return

        isVoting = true
        votingGame = game
        votes.clear()
        voted.clear()

        val options = plugin.config.getStringList("extras_vote.options")
        options.filter { availableExtras.contains(it) }.forEach { votes[it] = 0 }

        if (votes.isEmpty()) {
            isVoting = false
            return
        }

        val duration = plugin.config.getInt("extras_vote.duration_seconds", 15)

        val lang = plugin.languageManager
        val sb = StringBuilder(lang.get("extras_vote.header"))
        votes.keys.forEach { id ->
            val emoji = when(id) {
                "lava" -> "🌋"
                "storm" -> "⚡"
                "anvils" -> "⚓"
                "geoffrey" -> "👽"
                "acidrain" -> "🌧"
                "zerogravity" -> "🚀"
                "antlife" -> "🐜"
                else -> "❓"
            }
            sb.append(lang.get("extras_vote.item")
                .replace("<id>", id)
                .replace("<emoji>", emoji)
                .replace("<name>", id.uppercase()))
                .append('\n')
        }
        sb.append(lang.get("extras_vote.footer").replace("<time>", duration.toString()))

        val parsedMsg = plugin.messageManager.parse(sb.toString())
        plugin.server.broadcast(parsedMsg)

        plugin.server.onlinePlayers.forEach { it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1f) }

        voteTask = plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> endVoting() }, duration.toLong() * 20L)
    }

    fun registerVote(player: Player, extraId: String) {
        if (!isVoting) { plugin.languageManager.send(player, "extras_vote.no_active"); return }
        if (voted.contains(player.uniqueId)) {
            plugin.languageManager.send(player, "extras_vote.already_voted")
            return
        }
        if (!votes.containsKey(extraId)) return

        votes[extraId] = (votes[extraId] ?: 0) + 1
        voted.add(player.uniqueId)
        plugin.languageManager.send(player, "extras_vote.voted", Placeholder.unparsed("extra", extraId.uppercase()))
    }

    private fun endVoting() {
        isVoting = false
        val game = votingGame ?: return
        votingGame = null
        voteTask = null
        if (plugin.eventManager.currentGame !== game || !game.isRunning) return

        val winner = votes.maxByOrNull { it.value }?.key ?: votes.keys.randomOrNull() ?: return

        plugin.languageManager.broadcast("extras_vote.result", Placeholder.unparsed("extra", winner.uppercase()))

        when (winner) {
            "lava" -> game.triggerLava(plugin)
            "storm" -> game.triggerStorm(plugin)
            "anvils" -> game.triggerAnvils(plugin)
            "geoffrey" -> game.triggerGeoffrey(plugin)
            "acidrain" -> game.triggerAcidRain(plugin)
            "zerogravity" -> game.triggerZeroGravity(plugin)
            "antlife" -> game.triggerAntLife(plugin)
        }
    }

    fun cancelFor(game: EventGame) {
        if (votingGame !== game) return
        voteTask?.cancel()
        voteTask = null
        votingGame = null
        isVoting = false
        votes.clear()
        voted.clear()
    }
}
