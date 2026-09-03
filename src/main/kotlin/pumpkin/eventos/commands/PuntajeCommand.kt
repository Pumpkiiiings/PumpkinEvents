package pumpkin.eventos.commands

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos

class PuntajeCommand(private val plugin: PumpkinEventos) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val lang = plugin.languageManager
        val pm = plugin.puntajeManager

        if (args.isEmpty()) {
            if (sender !is Player) return true
            val pts = pm.getPoints(sender)
            lang.send(sender, "commands.puntaje.own", Placeholder.unparsed("points", pts.toString()))
            return true
        }

        when (args[0].lowercase()) {
            "top" -> {
                lang.send(sender, "commands.puntaje.top_title")
                val top = pm.getTop10()
                if (top.isEmpty()) {
                    lang.send(sender, "commands.puntaje.top_empty")
                } else {
                    top.forEachIndexed { index, score ->
                        lang.send(sender, "commands.puntaje.top_entry",
                            Placeholder.unparsed("rank", (index + 1).toString()),
                            Placeholder.unparsed("player", score.name),
                            Placeholder.unparsed("points", score.points.toString()))
                    }
                }
            }

            // --- SISTEMA DE HOLOGRAMA PERSONALIZABLE ---
            "holo" -> {
                if (!sender.hasPermission("pumpkin.admin")) return true
                if (sender !is Player) return true

                // Crear o mover el holograma: /puntaje holo
                if (args.size == 1) {
                    plugin.puntajeHoloManager.spawnHolograma(sender.location)
                    lang.send(sender, "commands.puntaje.holo_created")
                    return true
                }

                // Editar propiedades: /puntaje holo <propiedad> <valor>
                val propiedad = args[1].lowercase()
                if (args.size < 3) {
                    lang.send(sender, "commands.puntaje.holo_usage", Placeholder.unparsed("property", propiedad))
                    return true
                }
                val valor = args[2]

                when (propiedad) {
                    "billboard" -> {
                        val validos = listOf("CENTER", "FIXED", "VERTICAL", "HORIZONTAL")
                        if (!validos.contains(valor.uppercase())) {
                            lang.send(sender, "commands.puntaje.invalid_value", Placeholder.unparsed("values", validos.joinToString()))
                            return true
                        }
                        plugin.config.set("holograma.billboard", valor.uppercase())
                    }
                    "shadow" -> {
                        plugin.config.set("holograma.shadow", valor.toBoolean())
                    }
                    "background" -> {
                        if (!valor.startsWith("#") || valor.length < 7) {
                            lang.send(sender, "commands.puntaje.invalid_background")
                            return true
                        }
                        plugin.config.set("holograma.background_color", valor)
                    }
                    "scale" -> {
                        val num = valor.toDoubleOrNull() ?: 1.5
                        plugin.config.set("holograma.scale", num)
                    }
                    else -> {
                        lang.send(sender, "commands.puntaje.unknown_property")
                        return true
                    }
                }

                plugin.saveConfig()
                plugin.puntajeHoloManager.actualizarVisuales()
                lang.send(sender, "commands.puntaje.property_updated",
                    Placeholder.unparsed("property", propiedad), Placeholder.unparsed("value", valor))
            }

            "holoremove" -> {
                if (!sender.hasPermission("pumpkin.admin")) return true
                plugin.puntajeHoloManager.eliminarHolograma()
                lang.send(sender, "commands.puntaje.holo_removed")
            }

            "clearall" -> {
                if (!sender.hasPermission("pumpkin.admin")) return true
                pm.clearAll()
                lang.send(sender, "commands.puntaje.all_cleared")
            }

            "add" -> {
                if (!sender.hasPermission("pumpkin.admin")) return true
                if (args.size < 3) {
                    lang.send(sender, "commands.puntaje.add_usage")
                    return true
                }
                val target = args[1]
                val amount = args[2].toIntOrNull() ?: return true
                if (pm.addPointsAdmin(target, amount)) {
                    lang.send(sender, "commands.puntaje.added", Placeholder.unparsed("amount", amount.toString()), Placeholder.unparsed("player", target))
                } else {
                    lang.send(sender, "commands.puntaje.player_not_found")
                }
            }

            "remove" -> {
                if (!sender.hasPermission("pumpkin.admin")) return true
                if (args.size < 3) {
                    lang.send(sender, "commands.puntaje.remove_usage")
                    return true
                }
                val target = args[1]
                val amount = args[2].toIntOrNull() ?: return true
                if (pm.removePointsAdmin(target, amount)) {
                    lang.send(sender, "commands.puntaje.removed", Placeholder.unparsed("amount", amount.toString()), Placeholder.unparsed("player", target))
                } else {
                    lang.send(sender, "commands.puntaje.player_not_found")
                }
            }

            "clear" -> {
                if (!sender.hasPermission("pumpkin.admin")) return true
                if (args.size < 2) {
                    lang.send(sender, "commands.puntaje.clear_usage")
                    return true
                }
                val target = args[1]
                if (pm.clearPoints(target)) {
                    lang.send(sender, "commands.puntaje.cleared", Placeholder.unparsed("player", target))
                } else {
                    lang.send(sender, "commands.puntaje.player_not_found")
                }
            }

            else -> {
                val target = args[0]
                val pts = pm.getPointsByName(target)
                if (pts != null) {
                    lang.send(sender, "commands.puntaje.target", Placeholder.unparsed("player", target), Placeholder.unparsed("points", pts.toString()))
                } else {
                    lang.send(sender, "commands.puntaje.target_unregistered")
                }
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("pumpkin.admin")) return if (args.size == 1) listOf("top") else emptyList()

        return when (args.size) {
            1 -> listOf("top", "add", "remove", "clear", "clearall", "holo", "holoremove").filter { it.startsWith(args[0].lowercase()) }
            2 -> {
                if (args[0].lowercase() == "holo") listOf("billboard", "shadow", "background", "scale").filter { it.startsWith(args[1].lowercase()) }
                else emptyList()
            }
            3 -> {
                if (args[0].lowercase() == "holo") {
                    when (args[1].lowercase()) {
                        "billboard" -> listOf("CENTER", "FIXED", "VERTICAL", "HORIZONTAL")
                        "shadow" -> listOf("true", "false")
                        "background" -> listOf("#00000000", "#80000000", "#FF000000")
                        "scale" -> listOf("1.0", "1.5", "2.0")
                        else -> emptyList()
                    }
                } else emptyList()
            }
            else -> emptyList()
        }
    }
}
