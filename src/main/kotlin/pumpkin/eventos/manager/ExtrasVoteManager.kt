package pumpkin.eventos.manager

import org.bukkit.Sound
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.* // Importa todo de extras
import java.util.UUID
import java.util.concurrent.TimeUnit

class ExtrasVoteManager(private val plugin: PumpkinEventos) {

    private val votes = mutableMapOf<String, Int>()
    private val voted = mutableSetOf<UUID>()
    var isVoting = false
        private set

    // LISTA ACTUALIZADA
    private val availableExtras = listOf("lava", "storm", "anvils", "geoffrey", "acidrain", "zerogravity", "antlife")

    fun startVoting() {
        if (isVoting || plugin.eventManager.currentGame == null) return
        if (!plugin.config.getBoolean("extras_vote.enabled", true)) return

        val game = plugin.eventManager.currentGame ?: return
        val allowedGames = plugin.config.getStringList("extras_vote.allowed_games")
        if (!allowedGames.contains(game.id)) return

        isVoting = true
        votes.clear()
        voted.clear()

        val options = plugin.config.getStringList("extras_vote.options")
        options.filter { availableExtras.contains(it) }.forEach { votes[it] = 0 }

        if (votes.isEmpty()) {
            isVoting = false
            return
        }

        val duration = plugin.config.getInt("extras_vote.duration_seconds", 15)

        val sb = StringBuilder("<newline><#FF0055><b>⚠ ¡DESASTRE INMINENTE!</b></#FF0055>\n<white>Vota el próximo desastre dando clic:</white>\n")
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
            sb.append("<click:run_command:'/evote $id'><hover:show_text:'<green>Clic para votar por $id'><gray>  » $emoji <yellow><b>${id.uppercase()}</b></yellow></hover></click>\n")
        }
        sb.append("<gray><i>Tienen $duration segundos para votar...</i></gray><newline>")

        val parsedMsg = plugin.messageManager.parse(sb.toString())
        plugin.server.broadcast(parsedMsg)

        plugin.server.onlinePlayers.forEach { it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1f) }

        plugin.server.asyncScheduler.runDelayed(plugin, { _ -> endVoting() }, duration.toLong(), TimeUnit.SECONDS)
    }

    fun registerVote(player: Player, extraId: String) {
        if (!isVoting) { player.sendMessage(plugin.messageManager.parse("<red>No hay ninguna votación de desastre activa.</red>")); return }
        if (voted.contains(player.uniqueId)) {
            player.sendMessage(plugin.messageManager.parse("<red>Ya has votado por un desastre.</red>"))
            return
        }
        if (!votes.containsKey(extraId)) return

        votes[extraId] = (votes[extraId] ?: 0) + 1
        voted.add(player.uniqueId)
        player.sendMessage(plugin.messageManager.parse("<green>✔ Votaste por que inicie el desastre: <b>${extraId.uppercase()}</b></green>"))
    }

    private fun endVoting() {
        isVoting = false
        val game = plugin.eventManager.currentGame ?: return

        val winner = votes.maxByOrNull { it.value }?.key ?: votes.keys.randomOrNull() ?: return

        plugin.server.broadcast(plugin.messageManager.parse("<newline><#FF0055><b>💥 EL DESASTRE ELEGIDO ES: ${winner.uppercase()}</b></#FF0055><newline>"))

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
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
    }
}
