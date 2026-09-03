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
            sendMsg(sender, plugin, "arena_usage.missing_section", Placeholder.parsed("section", "commands.help"))
        } else {
            list.forEach { line -> sender.sendMessage(plugin.messageManager.parse(line)) }
        }
    }

    fun register(commands: io.papermc.paper.command.brigadier.Commands, plugin: PumpkinEventos) {

        // Raíz del comando: Solo se puede ver si tienes admin, o alguno de los 3 permisos nuevos.
        val root = Commands.literal("evento")
            .requires { ctx ->
                val s = ctx.sender
                s.hasPermission("pumpkin.admin") ||
                        s.hasPermission("pumpkin.evento.iniciar") ||
                        s.hasPermission("pumpkin.evento.votacion") ||
                        s.hasPermission("pumpkin.evento.ruleta")
            }
            .executes { ctx ->
                enviarAyuda(ctx.source.sender, plugin)
                1
            }

        val ayuda = Commands.literal("ayuda").executes { ctx ->
            enviarAyuda(ctx.source.sender, plugin)
            1
        }

        // --- RELOAD (Solo Admin) ---
        val reload = Commands.literal("reload")
            .requires { it.sender.hasPermission("pumpkin.admin") }
            .executes { ctx ->
                plugin.reloadConfig()
                plugin.languageManager.reload()
                plugin.menuManager.reload()
                sendMsg(ctx.source.sender, plugin, "commands.reload.success")
                1
            }

        // --- VOTACIÓN (Admin o pumpkin.evento.votacion) ---
        val votacion = Commands.literal("lanzarvotacion")
            .requires { it.sender.hasPermission("pumpkin.admin") || it.sender.hasPermission("pumpkin.evento.votacion") }
            .executes { ctx ->
                plugin.eventManager.startVoting()
                1
            }

        // --- SETUP (Solo Admin) ---
        val setup = Commands.literal("setup")
            .requires { it.sender.hasPermission("pumpkin.admin") }
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

        // --- ARENA → delegado a ArenaCommands (Admin por defecto dentro de ArenaCommands) ---
        val arena = ArenaCommands.build(plugin)

        // --- INICIAR (Admin o pumpkin.evento.iniciar) ---
        val iniciar = Commands.literal("iniciar")
            .requires { it.sender.hasPermission("pumpkin.admin") || it.sender.hasPermission("pumpkin.evento.iniciar") }
            .then(Commands.argument("game", StringArgumentType.word())
                .suggests { _, builder -> plugin.eventManager.games.keys.forEach { builder.suggest(it) }; builder.buildFuture() }
                .then(Commands.argument("modo", StringArgumentType.word())
                    .suggests { _, builder -> listOf("automatico", "manual").forEach { builder.suggest(it) }; builder.buildFuture() }
                    .then(Commands.argument("streamer", ArgumentTypes.player())
                        .executes { ctx ->
                            procesarInicio(ctx.source.sender, plugin,
                                StringArgumentType.getString(ctx, "game"),
                                StringArgumentType.getString(ctx, "modo"),
                                ctx.getArgument("streamer", PlayerSelectorArgumentResolver::class.java).resolve(ctx.source).firstOrNull())
                            1
                        })
                    .executes { ctx -> procesarInicio(ctx.source.sender, plugin, StringArgumentType.getString(ctx, "game"), StringArgumentType.getString(ctx, "modo"), null); 1 }
                )
                .executes { ctx -> procesarInicio(ctx.source.sender, plugin, StringArgumentType.getString(ctx, "game"), "automatico", null); 1 }
            )

        // --- DETENER (Admin o pumpkin.evento.iniciar) ---
        val detener = Commands.literal("detener")
            .requires { it.sender.hasPermission("pumpkin.admin") || it.sender.hasPermission("pumpkin.evento.iniciar") }
            .executes { ctx ->
                val game = plugin.eventManager.currentGame
                if (game != null && game.isRunning) {
                    game.stop()
                    sendMsg(ctx.source.sender, plugin, "commands.stop.stopped")
                } else if (plugin.eventManager.cancelPending()) {
                    sendMsg(ctx.source.sender, plugin, "commands.stop.stopped")
                } else {
                    sendMsg(ctx.source.sender, plugin, "commands.stop.no_event")
                }
                1
            }

        // --- REVIVIR (Solo Admin) ---
        val revivir = Commands.literal("revivir")
            .requires { it.sender.hasPermission("pumpkin.admin") }
            .then(Commands.argument("player", ArgumentTypes.player()).executes { ctx ->
                val resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                val target = resolver.resolve(ctx.source).firstOrNull() ?: return@executes 0
                val game = plugin.eventManager.currentGame
                if (game != null && game.spectators.contains(target)) {
                    game.spectators.remove(target)
                    game.players.add(target)
                    target.gameMode = org.bukkit.GameMode.ADVENTURE

                    // --- CORRECCIÓN: Asignar el mundo de juego a la Location ---
                    val rawSpawn = game.currentArena?.spawnPoints?.randomOrNull()?.clone()
                    if (rawSpawn != null) {
                        rawSpawn.world = game.gameWorld ?: target.world
                    }
                    val finalLoc = rawSpawn ?: target.location

                    target.teleportAsync(finalLoc)
                    sendMsg(ctx.source.sender, plugin, "commands.revive.success", Placeholder.parsed("player", target.name))
                }
                1
            })

        // --- RULETA (Admin o pumpkin.evento.ruleta) ---
        val ruleta = Commands.literal("ruleta")
            .requires { it.sender.hasPermission("pumpkin.admin") || it.sender.hasPermission("pumpkin.evento.ruleta") }
            .executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                if (plugin.eventManager.isBusy) {
                    sendMsg(sender, plugin, "commands.ruleta.already_active")
                    return@executes 0
                }
                iniciarRuleta(plugin, sender)
                1
            }

        // --- NARRADOR (Solo Admin) ---
        val narrador = Commands.literal("narrator")
            .requires { it.sender.hasPermission("pumpkin.admin") }
            .executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val em = plugin.eventManager
                if (em.narrators.contains(p.uniqueId)) {
                    em.narrators.remove(p.uniqueId)
                    plugin.languageManager.send(p, "commands.narrator.disabled")
                } else {
                    em.narrators.add(p.uniqueId)
                    plugin.languageManager.send(p, "commands.narrator.enabled")
                }
                1
            }

        // --- /pvote <efecto> ---
        val pvote = Commands.literal("pvote")
            .then(Commands.argument("efecto", StringArgumentType.word())
                .suggests { _, builder ->
                    listOf("speed", "jump", "regen", "strength", "haste").forEach { builder.suggest(it) }
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val sender = ctx.source.sender as? Player ?: return@executes 0
                    val game = plugin.eventManager.currentGame
                    if (game == null) { sendMsg(sender, plugin, "potion_vote.no_game"); return@executes 0 }
                    val efecto = StringArgumentType.getString(ctx, "efecto")
                    plugin.potionVoteManager.registerVote(sender, efecto)
                    1
                }
            )

        // --- /pvpvote si|no ---
        val pvpvote = Commands.literal("pvpvote")
            .then(Commands.argument("opcion", StringArgumentType.word())
                .suggests { _, builder -> listOf("si", "no").forEach { builder.suggest(it) }; builder.buildFuture() }
                .executes { ctx ->
                    val sender = ctx.source.sender as? Player ?: return@executes 0
                    val game = plugin.eventManager.currentGame
                    if (game == null) { sendMsg(sender, plugin, "pvp_vote.no_game"); return@executes 0 }
                    val opcion = StringArgumentType.getString(ctx, "opcion")
                    plugin.pvpVoteManager.registerVote(sender, opcion.equals("si", ignoreCase = true))
                    1
                }
            )

        // Agregar los nodos al comando principal
        root.then(ayuda)
        root.then(reload)
        root.then(votacion)
        root.then(setup)
        root.then(arena)
        root.then(iniciar)
        root.then(detener)
        root.then(revivir)
        root.then(ruleta)
        root.then(narrador)
        root.then(pvote)
        root.then(pvpvote)

        val commandNode = root.build()
        commands.register(commandNode, "Core de Eventos Pumpkin", listOf("eventos", "ev"))

        // Registrar también como comandos principales (root) para los jugadores normales
        commands.register(pvote.build(), "Votar por efecto de poción", emptyList())
        commands.register(pvpvote.build(), "Votar por PvP", emptyList())
        commands.register(arena, "Comandos de Arena", emptyList())
    }

    private fun procesarInicio(sender: CommandSender, plugin: PumpkinEventos, gameId: String, modo: String, streamerTarget: Player?) {
        val game = plugin.eventManager.games[gameId]
        if (game == null) { sendMsg(sender, plugin, "commands.start.not_found"); return }
        if (game is SimonDice) {
            game.mode = if (modo.lowercase() == "manual") SimonMode.MANUAL else SimonMode.AUTOMATICO
            if (game.mode == SimonMode.MANUAL) game.streamer = streamerTarget ?: (sender as? Player)
        }
        if (!plugin.eventManager.startCountdown(game)) {
            sendMsg(sender, plugin, "commands.start.already_active")
            return
        }
        sendMsg(sender, plugin, "commands.start.starting",
            Placeholder.parsed("game", game.displayName),
            Placeholder.parsed("mode", modo))
    }

    private fun iniciarRuleta(plugin: PumpkinEventos, sender: CommandSender) {
        val games = plugin.eventManager.games.values.toList()
        if (games.isEmpty()) {
            sender.sendMessage(plugin.messageManager.parse(plugin.languageManager.get("commands.ruleta.no_games")))
            return
        }
        if (!plugin.eventManager.beginSelection()) {
            sendMsg(sender, plugin, "commands.ruleta.already_active")
            return
        }

        var spins = 0
        val maxSpins = 30

        fun spin(delay: Long) {
            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                val game = games.random()
                val title = Title.title(
                    plugin.languageManager.component("commands.ruleta.spinning", Placeholder.parsed("game", game.displayName)),
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
                            plugin.languageManager.component("commands.ruleta.selected", Placeholder.parsed("game", game.displayName)),
                            plugin.messageManager.parse(plugin.languageManager.get("event_manager.countdown_subtitle").replace("<game>", game.displayName)),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofSeconds(1))
                        )
                        plugin.server.onlinePlayers.forEach { p ->
                            p.showTitle(winnerTitle)
                            p.playSound(p.location, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
                        }
                        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                            if (game is SimonDice) game.mode = SimonMode.AUTOMATICO
                            plugin.eventManager.startCountdownAfterSelection(game)
                        }, 60L)
                    }, 10L)
                }
            }, delay)
        }

        spin(2L)
    }
}
