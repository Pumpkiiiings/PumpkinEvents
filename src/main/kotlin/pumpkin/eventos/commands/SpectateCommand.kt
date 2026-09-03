package pumpkin.eventos.commands

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
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
            plugin.languageManager.send(sender, "commands.espectear.no_active")
            return true
        }

        // 2. Seguridad: Si el jugador está VIVO en el evento, no puede espectar (para evitar que se salga del juego)
        if (game.players.contains(sender)) {
            plugin.languageManager.send(sender, "commands.espectear.still_playing")
            return true
        }

        // 3. Buscar un objetivo válido (Alguien vivo en el mapa del juego)
        val target = game.players.randomOrNull()

        if (target == null) {
            plugin.languageManager.send(sender, "commands.espectear.no_players")
            return true
        }

        // 4. Proceso de teletransporte al mundo del juego (SlimeWorld)
        sender.gameMode = GameMode.SPECTATOR

        // Lo añadimos a la lista de espectadores del juego si no estaba
        if (!game.spectators.contains(sender)) {
            game.spectators.add(sender)
        }

        sender.teleportAsync(target.location).thenAccept {
            plugin.languageManager.send(sender, "commands.espectear.watching", Placeholder.unparsed("player", target.name))
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
                plugin.languageManager.send(p, "commands.espectear.gamemode_locked")
            }
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        return mutableListOf() // No necesita argumentos
    }
}
