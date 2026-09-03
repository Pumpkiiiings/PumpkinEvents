package pumpkin.eventos.commands

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos

object VotarCommand {

    fun register(commands: io.papermc.paper.command.brigadier.Commands, plugin: PumpkinEventos) {
        val lang = plugin.languageManager

        val root = Commands.literal("votar")
            // AÑADIDO: Qué hacer si el jugador solo escribe "/votar" sin argumentos
            .executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                lang.send(sender, "vote.usage")
                1
            }
            .then(Commands.argument("juego", StringArgumentType.word())
                .suggests { _, builder ->
                    plugin.eventManager.games.keys.forEach { builder.suggest(it) }
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val sender = ctx.source.sender as? Player ?: return@executes 0

                    if (!plugin.eventManager.isVoting) {
                        lang.send(sender, "vote.no_active")
                        return@executes 0
                    }

                    if (plugin.voteManager.hasVoted(sender.uniqueId)) {
                        lang.send(sender, "vote.already_voted")
                        return@executes 0
                    }

                    val gameId = StringArgumentType.getString(ctx, "juego")
                    val game = plugin.eventManager.games[gameId]

                    if (game == null) {
                        lang.send(sender, "vote.not_found")
                        return@executes 0
                    }

                    plugin.voteManager.addVote(sender.uniqueId, gameId)

                    lang.send(sender, "vote.success", Placeholder.parsed("game", game.displayName))
                    sender.playSound(sender.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                    1
                }
            ).build()

        commands.register(root, "Comando para votar por el siguiente evento")
    }
}
