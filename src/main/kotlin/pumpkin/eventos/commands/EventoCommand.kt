package pumpkin.eventos.commands

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.title.Title
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.arena.ArenaSetupSession
import pumpkin.eventos.games.simondice.SimonDice
import pumpkin.eventos.games.simondice.SimonMode
import java.time.Duration

object EventoCommand {

    private fun sendMsg(sender: CommandSender, plugin: PumpkinEventos, path: String, vararg resolvers: TagResolver) {
        val prefix = plugin.languageManager.get("prefix")
        val rawMsg = plugin.languageManager.get(path).replace("<prefix>", prefix)
        sender.sendMessage(plugin.messageManager.parse(rawMsg, *resolvers))
    }

    private fun enviarAyuda(sender: CommandSender, plugin: PumpkinEventos) {
        val list = plugin.languageManager.getList("commands.help")
        if (list.isEmpty()) {
            sender.sendMessage(plugin.messageManager.parse("<red>Falta la sección 'commands.help' en messages.yml</red>"))
        } else {
            list.forEach { line -> sender.sendMessage(plugin.messageManager.parse(line)) }
        }
    }

    fun register(commands: io.papermc.paper.command.brigadier.Commands, plugin: PumpkinEventos) {

        val root = Commands.literal("evento")
            .requires { it.sender.hasPermission("pumpkin.admin") }
            .executes { ctx ->
                enviarAyuda(ctx.source.sender, plugin)
                1
            }

        val ayuda = Commands.literal("ayuda").executes { ctx ->
            enviarAyuda(ctx.source.sender, plugin)
            1
        }

        val reload = Commands.literal("reload").executes { ctx ->
            val sender = ctx.source.sender
            plugin.reloadConfig()
            plugin.languageManager.reload()

            val prefix = plugin.languageManager.get("prefix")
            sender.sendMessage(plugin.messageManager.parse("$prefix<#39FF14>Archivos de configuración recargados correctamente.</#39FF14>"))
            1
        }

        val votacion = Commands.literal("lanzarvotacion").executes { ctx ->
            plugin.eventManager.startVoting()
            1
        }

        val setup = Commands.literal("setup")
            .then(Commands.literal("lobby").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                plugin.arenaManager.setMainLobbyLoc(sender.location)
                sendMsg(sender, plugin, "commands.setup.lobby_set")
                1
            })
            .then(Commands.literal("waiting").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                plugin.arenaManager.setWaitingSpawnLoc(sender.location)
                sendMsg(sender, plugin, "commands.setup.waiting_set")
                1
            })

        val arena = Commands.literal("arena")
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests { _, builder ->
                            listOf("tnttag", "tntrun", "simondice", "suelolava", "pillars", "sumo", "blockparty", "spleef", "tntspleef", "luzroja", "sillas", "ruletarusa").forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val sender = ctx.source.sender as? Player ?: return@executes 0
                            val name = StringArgumentType.getString(ctx, "name")
                            val type = StringArgumentType.getString(ctx, "type")

                            if (plugin.arenaManager.getArena(name) != null) {
                                sendMsg(sender, plugin, "commands.arena.already_exists", Placeholder.parsed("map", name))
                                return@executes 0
                            }

                            plugin.arenaManager.activeSetups[sender.uniqueId] = ArenaSetupSession(name, type)
                            val lines = plugin.languageManager.getList("commands.arena.creation_started")
                            val prefix = plugin.languageManager.get("prefix")
                            lines.forEach { line ->
                                sender.sendMessage(plugin.messageManager.parse(line.replace("<prefix>", prefix), Placeholder.parsed("map", name), Placeholder.parsed("type", type)))
                            }
                            1
                        }
                    )
                )
            )
            .then(Commands.literal("setcenter").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { sendMsg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }
                session.center = sender.location
                sendMsg(sender, plugin, "commands.arena.center_set", Placeholder.parsed("map", session.name))
                1
            })
            .then(Commands.literal("setgradas").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { sendMsg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }
                session.gradas = sender.location
                sendMsg(sender, plugin, "commands.arena.gradas_set", Placeholder.parsed("map", session.name))
                1
            })
            .then(Commands.literal("setdueloa").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { sendMsg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }
                session.dueloA = sender.location
                sendMsg(sender, plugin, "commands.arena.dueloa_set", Placeholder.parsed("map", session.name))
                1
            })
            .then(Commands.literal("setduelob").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { sendMsg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }
                session.dueloB = sender.location
                sendMsg(sender, plugin, "commands.arena.duelob_set", Placeholder.parsed("map", session.name))
                1
            })
            .then(Commands.literal("addspawn").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { sendMsg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }
                session.spawns.add(sender.location)
                sendMsg(sender, plugin, "commands.arena.spawn_added", Placeholder.parsed("map", session.name), Placeholder.parsed("count", session.spawns.size.toString()))
                1
            })
            // --- NUEVO: AÑADIR SILLAS ---
            .then(Commands.literal("addchair").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { sendMsg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }

                val block = sender.getTargetBlockExact(5)
                if (block == null || block.type.isAir) {
                    sender.sendMessage(plugin.messageManager.parse("<red>Debes mirar directamente a un bloque (Slab/Escalera) para añadirlo como silla.</red>"))
                    return@executes 0
                }

                session.chairs.add(block.location)
                val prefix = plugin.languageManager.get("prefix")
                sender.sendMessage(plugin.messageManager.parse("$prefix<green>🪑 <white>Silla #${session.chairs.size} añadida a <yellow>${session.name}</yellow>"))
                1
            })
            .then(Commands.literal("save").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { sendMsg(sender, plugin, "commands.arena.no_creation_pending"); return@executes 0 }
                if (session.spawns.isEmpty()) { sendMsg(sender, plugin, "commands.arena.no_spawns"); return@executes 0 }
                plugin.arenaManager.saveSetupSession(session)
                plugin.arenaManager.activeSetups.remove(sender.uniqueId)
                sendMsg(sender, plugin, "commands.arena.saved", Placeholder.parsed("map", session.name))
                1
            })
            .then(Commands.literal("cancel").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                if (plugin.arenaManager.activeSetups.remove(sender.uniqueId) != null) {
                    sendMsg(sender, plugin, "commands.arena.cancelled")
                } else {
                    sendMsg(sender, plugin, "commands.arena.not_creating")
                }
                1
            })

        val iniciar = Commands.literal("iniciar")
            .then(Commands.argument("game", StringArgumentType.word())
                .suggests { _, builder -> plugin.eventManager.games.keys.forEach { builder.suggest(it) }; builder.buildFuture() }
                .then(Commands.argument("modo", StringArgumentType.word())
                    .suggests { _, builder -> listOf("automatico", "manual").forEach { builder.suggest(it) }; builder.buildFuture() }
                    .then(Commands.argument("streamer", ArgumentTypes.player())
                        .executes { ctx ->
                            procesarInicio(ctx.source.sender, plugin, StringArgumentType.getString(ctx, "game"), StringArgumentType.getString(ctx, "modo"), ctx.getArgument("streamer", PlayerSelectorArgumentResolver::class.java).resolve(ctx.source).firstOrNull())
                            1
                        })
                    .executes { ctx -> procesarInicio(ctx.source.sender, plugin, StringArgumentType.getString(ctx, "game"), StringArgumentType.getString(ctx, "modo"), null); 1 }
                )
                .executes { ctx -> procesarInicio(ctx.source.sender, plugin, StringArgumentType.getString(ctx, "game"), "automatico", null); 1 }
            )

        val detener = Commands.literal("detener").executes { ctx ->
            val game = plugin.eventManager.currentGame
            if (game != null && game.isRunning) {
                game.stop()
                plugin.eventManager.currentGame = null
                sendMsg(ctx.source.sender, plugin, "commands.stop.stopped")
            } else {
                sendMsg(ctx.source.sender, plugin, "commands.stop.no_event")
            }
            1
        }

        val revivir = Commands.literal("revivir")
            .then(Commands.argument("player", ArgumentTypes.player()).executes { ctx ->
                val resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                val target = resolver.resolve(ctx.source).firstOrNull() ?: return@executes 0
                val game = plugin.eventManager.currentGame
                if (game != null && game.spectators.contains(target)) {
                    game.spectators.remove(target)
                    game.players.add(target)
                    target.gameMode = org.bukkit.GameMode.ADVENTURE
                    target.teleportAsync(game.currentArena?.spawnPoints?.randomOrNull() ?: target.location)
                    sendMsg(ctx.source.sender, plugin, "commands.revive.success", Placeholder.parsed("player", target.name))
                }
                1
            })

        val ruleta = Commands.literal("ruleta").executes { ctx ->
            val sender = ctx.source.sender as? Player ?: return@executes 0

            if (plugin.eventManager.currentGame != null || plugin.eventManager.isVoting) {
                sender.sendMessage(plugin.messageManager.parse("<red>¡Ya hay un evento en curso o una votación activa!</red>"))
                return@executes 0
            }

            iniciarRuleta(plugin, sender)
            1
        }

        val commandNode = root.then(ayuda).then(reload).then(votacion).then(setup).then(arena).then(iniciar).then(detener).then(revivir).then(ruleta).build()
        commands.register(commandNode, "Core de Eventos Pumpkin", listOf("eventos", "ev"))
    }

    private fun procesarInicio(sender: CommandSender, plugin: PumpkinEventos, gameId: String, modo: String, streamerTarget: Player?) {
        val game = plugin.eventManager.games[gameId]
        if (game == null) {
            sendMsg(sender, plugin, "commands.start.not_found")
            return
        }
        if (game is SimonDice) {
            game.mode = if (modo.lowercase() == "manual") SimonMode.MANUAL else SimonMode.AUTOMATICO
            if (game.mode == SimonMode.MANUAL) game.streamer = streamerTarget ?: (sender as? Player)
        }
        plugin.eventManager.startCountdown(game)
        sendMsg(sender, plugin, "commands.start.starting", Placeholder.parsed("game", game.displayName), Placeholder.parsed("mode", modo))
    }

    private fun iniciarRuleta(plugin: PumpkinEventos, sender: CommandSender) {
        val games = plugin.eventManager.games.values.toList()
        if (games.isEmpty()) {
            sender.sendMessage(plugin.messageManager.parse("<red>No hay juegos registrados en el core.</red>"))
            return
        }

        var spins = 0
        val maxSpins = 30

        fun spin(delay: Long) {
            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                val game = games.random()

                val titleStr = "<yellow>▶</yellow> <aqua><bold>${game.displayName}</bold></aqua> <yellow>◀</yellow>"
                val title = Title.title(
                    plugin.messageManager.parse(titleStr),
                    net.kyori.adventure.text.Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO)
                )

                plugin.server.onlinePlayers.forEach { p ->
                    p.showTitle(title)
                    p.playSound(p.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f)
                }

                spins++
                if (spins < maxSpins) {
                    val nextDelay = when {
                        spins < 15 -> 2L
                        spins < 22 -> 4L
                        spins < 26 -> 8L
                        else -> 15L
                    }
                    spin(nextDelay)
                } else {
                    plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                        val winnerTitle = Title.title(
                            plugin.messageManager.parse("<green>▶</green> <gold><bold>${game.displayName}</bold></gold> <green>◀</green>"),
                            plugin.messageManager.parse("<white>¡EL SIGUIENTE JUEGO HA SIDO ELEGIDO!</white>"),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofSeconds(1))
                        )

                        plugin.server.onlinePlayers.forEach { p ->
                            p.showTitle(winnerTitle)
                            p.playSound(p.location, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
                        }

                        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                            if (game is SimonDice) {
                                game.mode = SimonMode.AUTOMATICO
                            }
                            plugin.eventManager.startCountdown(game)
                        }, 60L)

                    }, 10L)
                }
            }, delay)
        }

        spin(2L)
    }
}
