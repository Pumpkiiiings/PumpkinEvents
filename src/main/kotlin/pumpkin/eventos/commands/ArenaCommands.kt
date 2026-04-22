package pumpkin.eventos.commands

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.arena.ArenaSetupSession

/**
 * Comandos relacionados con la gestión de arenas.
 * Extraído de EventoCommand para mayor modularidad.
 *
 * Comandos:
 *   /evento arena create <nombre> <tipo>
 *   /evento arena duplicate <origen> <nombre> <tipo>
 *   /evento arena edit <mapa>
 *   /evento arena setcenter
 *   /evento arena setgradas
 *   /evento arena setdueloa / setduelob
 *   /evento arena addspawn
 *   /evento arena addchair
 *   /evento arena save
 *   /evento arena cancel
 */
object ArenaCommands {

    private val GAME_TYPES = listOf(
        "tnttag", "tntrun", "simondice", "suelolava",
        "pillars", "sumo", "blockparty", "spleef",
        "tntspleef", "luzroja", "sillas", "ruletarusa",
        "cristales", "jalarcuerda", "skywars_solos",
        "skywars_duos", "iceboat", "miniwalls", "corona", "hideandseek"
    )

    private fun msg(sender: CommandSender, plugin: PumpkinEventos, path: String, vararg resolvers: net.kyori.adventure.text.minimessage.tag.resolver.TagResolver) {
        val prefix = plugin.languageManager.get("prefix")
        val raw = plugin.languageManager.get(path).replace("<prefix>", prefix)
        sender.sendMessage(plugin.messageManager.parse(raw, *resolvers))
    }

    fun build(plugin: PumpkinEventos): com.mojang.brigadier.tree.LiteralCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> {
        return Commands.literal("arena")
            // --- CREATE ---
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests { _, builder ->
                            GAME_TYPES.forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val sender = ctx.source.sender as? Player ?: return@executes 0
                            val name = StringArgumentType.getString(ctx, "name")
                            val type = StringArgumentType.getString(ctx, "type")
                            if (plugin.arenaManager.getArena(name) != null) {
                                msg(sender, plugin, "commands.arena.already_exists", Placeholder.parsed("map", name))
                                return@executes 0
                            }
                            plugin.arenaManager.activeSetups[sender.uniqueId] = ArenaSetupSession(name, type)
                            val lines = plugin.languageManager.getList("commands.arena.creation_started")
                            val prefix = plugin.languageManager.get("prefix")
                            lines.forEach { line ->
                                sender.sendMessage(plugin.messageManager.parse(
                                    line.replace("<prefix>", prefix),
                                    Placeholder.parsed("map", name), Placeholder.parsed("type", type)
                                ))
                            }
                            1
                        }
                    )
                )
            )
            // --- DUPLICATE ---
            .then(Commands.literal("duplicate")
                .then(Commands.argument("source", StringArgumentType.word())
                    .suggests { _, builder ->
                        plugin.arenaManager.getAvailableArenas().forEach { builder.suggest(it.id) }
                        builder.buildFuture()
                    }
                    .then(Commands.argument("newname", StringArgumentType.word())
                        .then(Commands.argument("newtype", StringArgumentType.word())
                            .suggests { _, builder ->
                                GAME_TYPES.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .executes { ctx ->
                                val sender = ctx.source.sender
                                val source = StringArgumentType.getString(ctx, "source")
                                val newName = StringArgumentType.getString(ctx, "newname")
                                val newType = StringArgumentType.getString(ctx, "newtype")

                                if (plugin.arenaManager.getArena(source) == null) {
                                    msg(sender, plugin, "commands.arena.duplicate_not_found", Placeholder.parsed("map", source))
                                    return@executes 0
                                }
                                if (plugin.arenaManager.getArena(newName) != null) {
                                    msg(sender, plugin, "commands.arena.duplicate_already_exists", Placeholder.parsed("map", newName))
                                    return@executes 0
                                }

                                val success = plugin.arenaManager.duplicateArena(source, newName, newType)
                                if (success) {
                                    msg(sender, plugin, "commands.arena.duplicate_success",
                                        Placeholder.parsed("source", source),
                                        Placeholder.parsed("target", newName),
                                        Placeholder.parsed("type", newType))
                                }
                                1
                            }
                        )
                    )
                )
            )
            // --- EDIT ---
            .then(Commands.literal("edit")
                .then(Commands.argument("map", StringArgumentType.word())
                    .suggests { _, builder ->
                        plugin.arenaManager.getAvailableArenas().forEach { builder.suggest(it.id) }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val sender = ctx.source.sender as? Player ?: return@executes 0
                        val mapName = StringArgumentType.getString(ctx, "map")

                        val session = plugin.arenaManager.loadSetupFromArena(mapName)
                        if (session == null) {
                            msg(sender, plugin, "commands.arena.edit_not_found", Placeholder.parsed("map", mapName))
                            return@executes 0
                        }
                        plugin.arenaManager.activeSetups[sender.uniqueId] = session
                        msg(sender, plugin, "commands.arena.edit_started", Placeholder.parsed("map", mapName))
                        1
                    }
                )
            )
            // --- SETCENTER ---
            .then(Commands.literal("setcenter").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { msg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }
                session.center = sender.location
                msg(sender, plugin, "commands.arena.center_set", Placeholder.parsed("map", session.name))
                1
            })
            // --- SETGRADAS ---
            .then(Commands.literal("setgradas").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { msg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }
                session.gradas = sender.location
                msg(sender, plugin, "commands.arena.gradas_set", Placeholder.parsed("map", session.name))
                1
            })
            // --- SETDUELOA ---
            .then(Commands.literal("setdueloa").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { msg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }
                session.dueloA = sender.location
                msg(sender, plugin, "commands.arena.dueloa_set", Placeholder.parsed("map", session.name))
                1
            })
            // --- SETDUELOB ---
            .then(Commands.literal("setduelob").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { msg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }
                session.dueloB = sender.location
                msg(sender, plugin, "commands.arena.duelob_set", Placeholder.parsed("map", session.name))
                1
            })
            // --- ADDSPAWN ---
            .then(Commands.literal("addspawn").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { msg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }
                session.spawns.add(sender.location)
                msg(sender, plugin, "commands.arena.spawn_added",
                    Placeholder.parsed("map", session.name),
                    Placeholder.parsed("count", session.spawns.size.toString()))
                1
            })
            // --- ADDCHAIR ---
            .then(Commands.literal("addchair").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { msg(sender, plugin, "commands.arena.not_creating"); return@executes 0 }
                val block = sender.getTargetBlockExact(5)
                if (block == null || block.type.isAir) {
                    msg(sender, plugin, "commands.arena.no_block_aimed")
                    return@executes 0
                }
                session.chairs.add(block.location)
                msg(sender, plugin, "commands.arena.chair_added",
                    Placeholder.parsed("map", session.name),
                    Placeholder.parsed("count", session.chairs.size.toString()))
                1
            })
            // --- SAVE ---
            .then(Commands.literal("save").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.arenaManager.activeSetups[sender.uniqueId]
                if (session == null) { msg(sender, plugin, "commands.arena.no_creation_pending"); return@executes 0 }
                if (session.spawns.isEmpty()) { msg(sender, plugin, "commands.arena.no_spawns"); return@executes 0 }
                plugin.arenaManager.saveSetupSession(session)
                plugin.arenaManager.activeSetups.remove(sender.uniqueId)
                msg(sender, plugin, "commands.arena.saved", Placeholder.parsed("map", session.name))
                1
            })
            // --- CANCEL ---
            .then(Commands.literal("cancel").executes { ctx ->
                val sender = ctx.source.sender as? Player ?: return@executes 0
                if (plugin.arenaManager.activeSetups.remove(sender.uniqueId) != null) {
                    msg(sender, plugin, "commands.arena.cancelled")
                } else {
                    msg(sender, plugin, "commands.arena.not_creating")
                }
                1
            })
            .build()
    }
}
