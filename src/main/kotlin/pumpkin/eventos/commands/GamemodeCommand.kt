package pumpkin.eventos.commands

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.GameMode
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos

object GamemodeCommand {

    private val modesMap = mapOf(
        "0" to GameMode.SURVIVAL, "s" to GameMode.SURVIVAL, "survival" to GameMode.SURVIVAL,
        "1" to GameMode.CREATIVE, "c" to GameMode.CREATIVE, "creative" to GameMode.CREATIVE,
        "2" to GameMode.ADVENTURE, "a" to GameMode.ADVENTURE, "adventure" to GameMode.ADVENTURE,
        "3" to GameMode.SPECTATOR, "sp" to GameMode.SPECTATOR, "spectator" to GameMode.SPECTATOR
    )

    fun register(commands: io.papermc.paper.command.brigadier.Commands, plugin: PumpkinEventos) {
        val perm = "pumpkin.command.gamemode"

        // --- 1. COMANDOS RAÍZ: /gamemode y /gm ---
        val gmNode = Commands.literal("gamemode")
            .requires { it.sender.hasPermission(perm) }
            .then(Commands.argument("modo", StringArgumentType.word())
                .suggests { _, builder ->
                    modesMap.keys.forEach { builder.suggest(it) }
                    builder.buildFuture()
                }
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes { ctx ->
                        val target = ctx.getArgument("target", PlayerSelectorArgumentResolver::class.java).resolve(ctx.source).firstOrNull()
                        executeGM(ctx.source.sender, target, StringArgumentType.getString(ctx, "modo"), plugin)
                        1
                    }
                )
                .executes { ctx ->
                    executeGM(ctx.source.sender, ctx.source.sender as? Player, StringArgumentType.getString(ctx, "modo"), plugin)
                    1
                }
            ).build()

        commands.register(gmNode, "Cambiar modo de juego", listOf("gm"))

        // --- 2. COMANDOS DIRECTOS: /gms, /gmc, /gma, /gmsp ---
        registerShortcut(commands, plugin, "gms", "survival", perm)
        registerShortcut(commands, plugin, "gmc", "creative", perm)
        registerShortcut(commands, plugin, "gma", "adventure", perm)
        registerShortcut(commands, plugin, "gmsp", "spectator", perm)
    }

    private fun registerShortcut(commands: io.papermc.paper.command.brigadier.Commands, plugin: PumpkinEventos, label: String, targetMode: String, perm: String) {
        val node = Commands.literal(label)
            .requires { it.sender.hasPermission(perm) }
            .then(Commands.argument("target", ArgumentTypes.player())
                .executes { ctx ->
                    val target = ctx.getArgument("target", PlayerSelectorArgumentResolver::class.java).resolve(ctx.source).firstOrNull()
                    executeGM(ctx.source.sender, target, targetMode, plugin)
                    1
                }
            )
            .executes { ctx ->
                executeGM(ctx.source.sender, ctx.source.sender as? Player, targetMode, plugin)
                1
            }.build()

        commands.register(node, "Cambio rápido a $targetMode")
    }

    private fun executeGM(sender: CommandSender, target: Player?, modeStr: String, plugin: PumpkinEventos) {
        val lang = plugin.languageManager
        val mm = plugin.messageManager
        val prefix = lang.get("prefix")

        val mode = modesMap[modeStr.lowercase()]
        if (mode == null) {
            val msg = lang.get("gamemode.invalid").replace("<prefix>", prefix)
            sender.sendMessage(mm.parse(msg, Placeholder.parsed("mode", modeStr)))
            return
        }

        if (target == null) {
            val msg = lang.get("gamemode.player_not_found").replace("<prefix>", prefix)
            sender.sendMessage(mm.parse(msg, Placeholder.parsed("target", "Desconocido")))
            return
        }

        target.gameMode = mode
        val modeName = mode.name.lowercase()

        if (sender == target) {
            val msg = lang.get("gamemode.changed_self").replace("<prefix>", prefix)
            sender.sendMessage(mm.parse(msg, Placeholder.parsed("mode", modeName)))
        } else {
            val msgOther = lang.get("gamemode.changed_other").replace("<prefix>", prefix)
            sender.sendMessage(mm.parse(msgOther, Placeholder.parsed("target", target.name), Placeholder.parsed("mode", modeName)))

            val msgBy = lang.get("gamemode.changed_by_other").replace("<prefix>", prefix)
            target.sendMessage(mm.parse(msgBy, Placeholder.parsed("sender", sender.name), Placeholder.parsed("mode", modeName)))
        }
    }
}
