package pumpkin.eventos.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos

class PuntajeCommand(private val plugin: PumpkinEventos) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val mm = plugin.messageManager
        val pm = plugin.puntajeManager

        if (args.isEmpty()) {
            if (sender !is Player) return true
            val pts = pm.getPoints(sender)
            sender.sendMessage(mm.parse("<#39FF14>🏆 Tus puntos:</#39FF14> <white>$pts</white>"))
            return true
        }

        when (args[0].lowercase()) {
            "top" -> {
                sender.sendMessage(mm.parse("<#BF00FF><bold>⭐ TOP 10 PUNTAJES ⭐</bold></#BF00FF>"))
                val top = pm.getTop10()
                if (top.isEmpty()) {
                    sender.sendMessage(mm.parse("<gray>Aún no hay puntajes registrados.</gray>"))
                } else {
                    top.forEachIndexed { index, score ->
                        sender.sendMessage(mm.parse("<yellow>${index + 1}.</yellow> <aqua>${score.name}</aqua> <gray>-</gray> <#39FF14>${score.points} pts</#39FF14>"))
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
                    sender.sendMessage(mm.parse("<#39FF14>✔</#39FF14> <white>Holograma de puntajes creado/movido a tu posición.</white>"))
                    return true
                }

                // Editar propiedades: /puntaje holo <propiedad> <valor>
                val propiedad = args[1].lowercase()
                if (args.size < 3) {
                    sender.sendMessage(mm.parse("<red>Uso: /puntaje holo $propiedad <valor></red>"))
                    return true
                }
                val valor = args[2]

                when (propiedad) {
                    "billboard" -> {
                        val validos = listOf("CENTER", "FIXED", "VERTICAL", "HORIZONTAL")
                        if (!validos.contains(valor.uppercase())) {
                            sender.sendMessage(mm.parse("<red>Valor inválido. Usa: $validos</red>"))
                            return true
                        }
                        plugin.config.set("holograma.billboard", valor.uppercase())
                    }
                    "shadow" -> {
                        plugin.config.set("holograma.shadow", valor.toBoolean())
                    }
                    "background" -> {
                        if (!valor.startsWith("#") || valor.length < 7) {
                            sender.sendMessage(mm.parse("<red>Usa formato Hex ARGB (Ej: #80000000)</red>"))
                            return true
                        }
                        plugin.config.set("holograma.background_color", valor)
                    }
                    "scale" -> {
                        val num = valor.toDoubleOrNull() ?: 1.5
                        plugin.config.set("holograma.scale", num)
                    }
                    else -> {
                        sender.sendMessage(mm.parse("<red>Propiedad desconocida.</red>"))
                        return true
                    }
                }

                plugin.saveConfig()
                plugin.puntajeHoloManager.actualizarVisuales()
                sender.sendMessage(mm.parse("<#39FF14>✔</#39FF14> <white>Propiedad <yellow>$propiedad</yellow> actualizada a <yellow>$valor</yellow>.</white>"))
            }

            "holoremove" -> {
                if (!sender.hasPermission("pumpkin.admin")) return true
                plugin.puntajeHoloManager.eliminarHolograma()
                sender.sendMessage(mm.parse("<#FF3131>✘</#FF3131> <white>Holograma de puntajes eliminado correctamente.</white>"))
            }

            "clearall" -> {
                if (!sender.hasPermission("pumpkin.admin")) return true
                pm.clearAll()
                sender.sendMessage(mm.parse("<red>Todos los puntajes han sido reiniciados a 0.</red>"))
            }

            "add" -> {
                if (!sender.hasPermission("pumpkin.admin")) return true
                if (args.size < 3) {
                    sender.sendMessage(mm.parse("<red>Uso: /puntaje add <jugador> <cantidad></red>"))
                    return true
                }
                val target = args[1]
                val amount = args[2].toIntOrNull() ?: return true
                if (pm.addPointsAdmin(target, amount)) {
                    sender.sendMessage(mm.parse("<#39FF14>Se añadieron $amount puntos a $target.</#39FF14>"))
                } else {
                    sender.sendMessage(mm.parse("<red>Jugador no encontrado.</red>"))
                }
            }

            "remove" -> {
                if (!sender.hasPermission("pumpkin.admin")) return true
                if (args.size < 3) {
                    sender.sendMessage(mm.parse("<red>Uso: /puntaje remove <jugador> <cantidad></red>"))
                    return true
                }
                val target = args[1]
                val amount = args[2].toIntOrNull() ?: return true
                if (pm.removePointsAdmin(target, amount)) {
                    sender.sendMessage(mm.parse("<#FF3131>Se quitaron $amount puntos a $target.</#FF3131>"))
                } else {
                    sender.sendMessage(mm.parse("<red>Jugador no encontrado.</red>"))
                }
            }

            "clear" -> {
                if (!sender.hasPermission("pumpkin.admin")) return true
                if (args.size < 2) {
                    sender.sendMessage(mm.parse("<red>Uso: /puntaje clear <jugador></red>"))
                    return true
                }
                val target = args[1]
                if (pm.clearPoints(target)) {
                    sender.sendMessage(mm.parse("<green>Puntaje de $target reiniciado a 0.</green>"))
                } else {
                    sender.sendMessage(mm.parse("<red>Jugador no encontrado.</red>"))
                }
            }

            else -> {
                val target = args[0]
                val pts = pm.getPointsByName(target)
                if (pts != null) {
                    sender.sendMessage(mm.parse("<#39FF14>🏆 Puntos de $target:</#39FF14> <white>$pts</white>"))
                } else {
                    sender.sendMessage(mm.parse("<red>Ese jugador no tiene puntaje registrado.</red>"))
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
