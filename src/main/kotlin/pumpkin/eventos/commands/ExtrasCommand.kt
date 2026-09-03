package pumpkin.eventos.commands

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.* // Importa todos los triggers

object ExtrasCommand {

    fun register(commands: Commands, plugin: PumpkinEventos) {

        // --- COMANDO INVISIBLE PARA VOTAR (/evote) ---
        commands.register(
            Commands.literal("evote")
                .then(Commands.argument("desastre", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes { ctx ->
                        val source = ctx.source.sender as? Player ?: return@executes 1
                        val desastre = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "desastre")
                        plugin.extrasVoteManager.registerVote(source, desastre.lowercase())
                        1
                    }
                )
                .build()
        )

        // --- COMANDO DE ADMIN (/extras start <tipo> / startall) ---
        commands.register(
            Commands.literal("extras")
                .requires { ctx: CommandSourceStack -> ctx.sender.hasPermission("pumpkin.admin") }

                // /extras start <desastre>
                .then(Commands.literal("start")
                    .then(Commands.argument("desastre", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests { _, builder ->
                            listOf("lava", "storm", "anvils", "geoffrey", "acidrain", "zerogravity", "antlife").forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val game = plugin.eventManager.currentGame
                            val sender = ctx.source.sender

                            if (game == null || !game.isRunning) {
                                plugin.languageManager.send(sender, "extras_command.no_active")
                                return@executes 1
                            }

                            val desastre = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "desastre").lowercase()

                            plugin.languageManager.send(sender, "extras_command.forcing", Placeholder.unparsed("extra", desastre))
                            when (desastre) {
                                "lava" -> game.triggerLava(plugin)
                                "storm" -> game.triggerStorm(plugin)
                                "anvils" -> game.triggerAnvils(plugin)
                                "geoffrey" -> game.triggerGeoffrey(plugin)
                                "acidrain" -> game.triggerAcidRain(plugin)
                                "zerogravity" -> game.triggerZeroGravity(plugin)
                                "antlife" -> game.triggerAntLife(plugin)
                                else -> plugin.languageManager.send(sender, "extras_command.unknown")
                            }
                            1
                        }
                    )
                )

                // /extras startall (Lanza todos a la vez)
                .then(Commands.literal("startall")
                    .executes { ctx ->
                        val game = plugin.eventManager.currentGame
                        val sender = ctx.source.sender

                        if (game == null || !game.isRunning) {
                            plugin.languageManager.send(sender, "extras_command.no_active")
                            return@executes 1
                        }

                        plugin.languageManager.send(sender, "extras_command.apocalypse")

                        game.triggerLava(plugin)
                        game.triggerStorm(plugin)
                        game.triggerAnvils(plugin)
                        game.triggerGeoffrey(plugin)
                        game.triggerAcidRain(plugin)
                        game.triggerZeroGravity(plugin)
                        game.triggerAntLife(plugin)

                        1
                    }
                )

                // /extras vote
                .then(Commands.literal("vote")
                    .executes { ctx ->
                        plugin.extrasVoteManager.startVoting()
                        1
                    }
                )
                .build()
        )
    }
}
