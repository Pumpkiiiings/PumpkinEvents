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
                    sender.sendMessage(mm.parse("<red>Jugador no encontrado en la base de datos.</red>"))
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
        if (args.size == 1) {
            val list = mutableListOf("top")
            if (sender.hasPermission("pumpkin.admin")) {
                list.addAll(listOf("add", "remove", "clear", "clearall"))
            }
            return list.filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }
}
