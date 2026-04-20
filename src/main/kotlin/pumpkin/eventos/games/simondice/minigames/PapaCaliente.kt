package pumpkin.eventos.games.simondice.minigames

import net.kyori.adventure.title.Title
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.simondice.SimonDice
import java.util.UUID
import java.util.concurrent.TimeUnit

class PapaCaliente(private val plugin: PumpkinEventos) : Listener {

    var activo = false
    private var victimaActual: UUID? = null
    private var gameInstance: SimonDice? = null

    fun iniciar(game: SimonDice, tiempoSegundos: Int) {
        // Filtrar jugadores válidos (Sin el streamer)
        val jugadoresValidos = game.players.filter { it != game.streamer }

        if (jugadoresValidos.size < 2) {
            plugin.server.broadcast(plugin.messageManager.parse("<red>❌ <bold>ERROR:</bold> <white>No hay suficientes jugadores para la Papa Caliente."))
            game.activeChallenges.clear() // Desbloquear Simón Dice
            return
        }

        this.gameInstance = game
        activo = true

        // 1. Elegir primera víctima al azar
        val primero = jugadoresValidos.random()
        darPapa(primero)

        val title = Title.title(
            plugin.messageManager.parse("<red>🥔 <bold>PAPA CALIENTE</bold> 🔥"),
            plugin.messageManager.parse("<white>¡Golpea a alguien para pasarla!")
        )
        game.players.forEach { it.showTitle(title) }

        var restante = tiempoSegundos
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!activo || !game.isRunning) {
                task.cancel()
                return@runAtFixedRate
            }

            if (restante <= 0) {
                task.cancel()
                plugin.server.globalRegionScheduler.run(plugin) { _ -> finalizarJuego(game) }
                return@runAtFixedRate
            }

            val portador = plugin.server.getPlayer(victimaActual ?: UUID.randomUUID())
            val nombrePortador = portador?.name ?: "Nadie"

            // Actualizar visuales en Simón Dice (Scoreboard y BossBar)
            game.displayOrden = "🥔 <#FF5F1F>$nombrePortador <gray>|</gray> <white>⏳ ${restante}s"

            if (restante <= 5) {
                val titleAviso = Title.title(plugin.messageManager.parse("<red><bold>⏳ $restante"), plugin.messageManager.parse("<white>¡La papa va a explotar!"))
                game.players.forEach { it.showTitle(titleAviso) }
            }

            if (portador != null) {
                plugin.server.regionScheduler.run(plugin, portador.location) { _ ->
                    portador.world.spawnParticle(Particle.FLAME, portador.location.add(0.0, 1.5, 0.0), 5, 0.2, 0.2, 0.2, 0.05)
                    portador.world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, portador.location.add(0.0, 2.0, 0.0), 3, 0.1, 0.1, 0.1, 0.02)

                    val pitch = (2.0 - (restante / tiempoSegundos.toDouble())).toFloat()
                    portador.world.playSound(portador.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, pitch)
                }
            }

            restante--
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun darPapa(p: Player) {
        victimaActual = p.uniqueId
        val papa = ItemStack(Material.POISONOUS_POTATO)
        val meta = papa.itemMeta
        meta.displayName(plugin.messageManager.parse("<dark_red><bold>🥔 PAPA CALIENTE 🔥"))
        papa.itemMeta = meta

        p.inventory.addItem(papa)
        p.isGlowing = true
        p.sendMessage(plugin.messageManager.parse("<red>⚠️ <b>¡TIENES LA PAPA!</b> <white>Pásala rápido golpeando a otro."))
        p.playSound(p.location, Sound.ENTITY_CREEPER_PRIMED, 1f, 1f)
    }

    private fun quitarPapa(p: Player) {
        p.inventory.remove(Material.POISONOUS_POTATO)
        p.isGlowing = false
        p.sendMessage(plugin.messageManager.parse("<aqua>💨 <b>¡TE LIBRASTE!</b> <white>¡Corre!"))
    }

    @EventHandler
    fun alPegar(e: EntityDamageByEntityEvent) {
        if (!activo) return
        val atacante = e.damager as? Player ?: return
        val victima = e.entity as? Player ?: return
        val game = gameInstance ?: return

        // Solo el portador puede pasar la papa, y no puede dársela al streamer
        if (atacante.uniqueId == victimaActual && victima != game.streamer && game.players.contains(victima)) {
            e.damage = 0.001
            quitarPapa(atacante)
            darPapa(victima)

            atacante.playSound(atacante.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
            victima.playSound(victima.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.5f)

            plugin.server.broadcast(plugin.messageManager.parse("<#FF5F1F>🔥 <b>${atacante.name}</b> <gray>le pasó la papa a <white>${victima.name}"))
        }
    }

    @EventHandler
    fun alTirarPapa(e: PlayerDropItemEvent) {
        if (activo && e.player.uniqueId == victimaActual) {
            if (e.itemDrop.itemStack.type == Material.POISONOUS_POTATO) {
                e.isCancelled = true
                e.player.sendMessage(plugin.messageManager.parse("<red>❌ <b>¡PROHIBIDO!</b> <white>No puedes tirar la papa, ¡pásala!"))
            }
        }
    }

    private fun finalizarJuego(game: SimonDice) {
        activo = false
        val perdedor = plugin.server.getPlayer(victimaActual ?: UUID.randomUUID())

        if (perdedor != null && game.players.contains(perdedor)) {
            quitarPapa(perdedor)
            val titleBoom = Title.title(plugin.messageManager.parse("<red>💣 <b>¡BOOM!</b> 💣"), plugin.messageManager.parse("<white>${perdedor.name} explotó con la papa."))
            game.players.forEach { it.showTitle(titleBoom) }

            // Usamos la animación de muerte oficial
            game.aplicarCuello(perdedor)
        }

        // --- FIX DE REINICIO ---
        // Limpiamos la lista de retos para que el modo automático de Simón Dice siga pensando
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            game.activeChallenges.clear()
            game.displayOrden = "Esperando orden..."
        }, 60L) // 3 segundos de pausa dramática
    }
}
