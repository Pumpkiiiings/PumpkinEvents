package pumpkin.eventos.commands

import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerGameModeChangeEvent
import pumpkin.eventos.PumpkinEventos

class SpectateCommand(private val plugin: PumpkinEventos) : CommandExecutor, TabCompleter, Listener {

    init {
        // Registramos el comando y el listener interno al inicializar
        plugin.getCommand("espectear")?.setExecutor(this)
        plugin.getCommand("espectear")?.tabCompleter = this
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return true

        val game = plugin.eventManager.currentGame

        // 1. Validar que hay un evento activo
        if (game == null || !game.isRunning) {
            sender.sendMessage(plugin.messageManager.parse("<red>❌ No hay ningún evento activo para espectear.</red>"))
            return true
        }

        // 2. Seguridad: Si el jugador está VIVO en el evento, no puede espectar (para evitar que se salga del juego)
        if (game.players.contains(sender)) {
            sender.sendMessage(plugin.messageManager.parse("<red>❌ ¡Estás participando en el evento! No puedes espectar hasta morir.</red>"))
            return true
        }

        // 3. Buscar un objetivo válido (Alguien vivo en el mapa del juego)
        val target = game.players.randomOrNull()

        if (target == null) {
            sender.sendMessage(plugin.messageManager.parse("<red>❌ No hay jugadores vivos en este momento.</red>"))
            return true
        }

        // 4. Proceso de teletransporte al mundo del juego (SlimeWorld)
        sender.gameMode = GameMode.SPECTATOR

        // Lo añadimos a la lista de espectadores del juego si no estaba
        if (!game.spectators.contains(sender)) {
            game.spectators.add(sender)
        }

        sender.teleportAsync(target.location).thenAccept {
            sender.sendMessage(plugin.messageManager.parse("<#00FFFF>👀 <b>MODO ESPECTADOR:</b> <white>Especteando a <yellow>${target.name}</yellow>"))
            sender.playSound(sender.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
        }

        return true
    }

    // --- LISTENER DE SEGURIDAD ---

    @EventHandler
    fun onGameModeChange(e: PlayerGameModeChangeEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame ?: return

        // Si es un espectador del evento activo, no le dejamos ponerse en Survival/Creative
        // (A menos que sea un Admin con permiso)
        if (game.isRunning && game.spectators.contains(p) && !p.hasPermission("pumpkin.admin")) {
            if (e.newGameMode != GameMode.SPECTATOR) {
                e.isCancelled = true
                p.sendMessage(plugin.messageManager.parse("<red>⚠️ No puedes cambiar tu modo de juego mientras espectas el evento.</red>"))
            }
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        return mutableListOf() // No necesita argumentos
    }
}
