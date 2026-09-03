package pumpkin.eventos.manager

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.UUID

/**
 * Gestiona la votación de efectos de poción que ocurre cada 2 minutos durante los juegos.
 */
class PotionVoteManager(private val plugin: PumpkinEventos) {

    private val votes = mutableMapOf<String, Int>() // efecto -> votos
    private val voted = mutableSetOf<UUID>()
    var isVoting = false
        private set
    private var votingGame: EventGame? = null
    private var voteTask: ScheduledTask? = null

    private val effectTypes = mapOf(
        "speed" to PotionEffectType.SPEED,
        "jump" to PotionEffectType.JUMP_BOOST,
        "regen" to PotionEffectType.REGENERATION,
        "strength" to PotionEffectType.STRENGTH,
        "haste" to PotionEffectType.HASTE
    )

    private val effectAmplifiers = mapOf(
        "speed" to 1,
        "jump" to 2,
        "regen" to 0,
        "strength" to 0,
        "haste" to 1
    )

    fun startVoting() {
        if (isVoting || plugin.eventManager.currentGame == null) return
        if (!plugin.config.getBoolean("potion_vote.enabled", true)) return

        isVoting = true
        votingGame = plugin.eventManager.currentGame
        votes.clear()
        voted.clear()
        effectTypes.keys.forEach { votes[it] = 0 }

        val lang = plugin.languageManager
        val mm = plugin.messageManager

        val duration = plugin.config.getInt("potion_vote.duration_seconds", 15)
        val header = lang.get("potion_vote.header")
        val itemTemplate = lang.get("potion_vote.item")
        val footer = lang.get("potion_vote.footer").replace("<time>", duration.toString())

        val sb = StringBuilder(header).append("\n")
        effectTypes.keys.forEach { id ->
            val name = lang.get("potion_vote.options.$id")
            sb.append(itemTemplate.replace("<id>", id).replace("<name>", name)).append("\n")
        }
        sb.append(footer)

        Bukkit.broadcast(mm.parse(sb.toString()))
        Bukkit.getOnlinePlayers().forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f) }

        voteTask = plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> endVoting() }, duration.toLong() * 20L)
    }

    fun registerVote(player: Player, efectoId: String) {
        if (!isVoting) { plugin.languageManager.send(player, "potion_vote.no_game"); return }
        if (voted.contains(player.uniqueId)) {
            plugin.languageManager.send(player, "potion_vote.already_voted")
            return
        }
        if (!votes.containsKey(efectoId)) return
        votes[efectoId] = (votes[efectoId] ?: 0) + 1
        voted.add(player.uniqueId)
        val name = plugin.languageManager.get("potion_vote.options.$efectoId")
        plugin.languageManager.send(player, "potion_vote.voted", Placeholder.component("effect", plugin.languageManager.component("potion_vote.options.$efectoId")))
    }

    private fun endVoting() {
        isVoting = false
        val winner = votes.maxByOrNull { it.value }?.key ?: "speed"
        val effectType = effectTypes[winner] ?: PotionEffectType.SPEED
        val amplifier = effectAmplifiers[winner] ?: 0
        val durationTicks = plugin.config.getInt("potion_vote.effect_duration_seconds", 60) * 20

        val game = votingGame ?: return
        votingGame = null
        voteTask = null
        if (plugin.eventManager.currentGame !== game || !game.isRunning) return
        game.players.forEach { p ->
            p.addPotionEffect(PotionEffect(effectType, durationTicks, amplifier, false, true, true))
            p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f)
        }
        val effectName = plugin.languageManager.get("potion_vote.options.$winner")
        Bukkit.broadcast(plugin.messageManager.parse(
            plugin.languageManager.get("potion_vote.result").replace("<effect>", effectName)
        ))
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
