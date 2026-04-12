package pumpkin.eventos.games.sumo

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import kotlin.math.abs

// Implementamos Listener para el KB personalizado
class Sumo(plugin: PumpkinEventos) : EventGame(plugin, "sumo", "<#FF9900>Sumo 1v1</#FF9900>"), Listener {

    var luchador1: Player? = null
    var luchador2: Player? = null
    var rondaActiva = false

    private var alturaCaida = 0.0

    override fun getExtraPlaceholders(): Map<String, String> {
        return mapOf(
            "%luchador1%" to (luchador1?.name ?: "Nadie"),
            "%luchador2%" to (luchador2?.name ?: "Nadie")
        )
    }

    override fun onStart() {
        // Registramos los eventos para el knockback extra
        plugin.server.pluginManager.registerEvents(this, plugin)

        val arena = currentArena ?: return
        val mm = plugin.messageManager

        val targetWorld = arena.centerLocation?.world ?: players.firstOrNull()?.world ?: return

        val gradaLoc = arena.gradas?.clone() ?: arena.centerLocation?.clone()?.add(0.0, 10.0, 0.0) ?: return
        val dueloA = arena.dueloA?.clone() ?: arena.spawnPoints.getOrNull(0)?.clone() ?: return
        val dueloB = arena.dueloB?.clone() ?: arena.spawnPoints.getOrNull(1)?.clone() ?: return

        gradaLoc.world = targetWorld
        dueloA.world = targetWorld
        dueloB.world = targetWorld

        alturaCaida = dueloA.y - 2.0

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            players.forEach { p ->
                p.gameMode = GameMode.ADVENTURE
                p.inventory.clear()
                p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
                p.teleportAsync(gradaLoc)
                p.sendMessage(mm.parse("<#00FFFF>🍿 <b>GRADAS:</b> <white>El torneo de Sumo va a comenzar..."))
            }

            iniciarDetectorCaidas()

            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                siguienteRonda()
            }, 60L)
        }
    }

    private fun siguienteRonda() {
        if (!isRunning) return

        if (players.size <= 1) {
            checkWinner()
            return
        }

        // --- NUEVO SISTEMA DE EMPAREJAMIENTO POR PING ---
        val vivos = players.toMutableList()

        // 1. Elegimos un jugador al azar para iniciar el emparejamiento
        val l1 = vivos.random()
        vivos.remove(l1)

        // 2. Buscamos al jugador con el ping más parecido
        val l2 = vivos.minByOrNull { p -> abs(p.ping - l1.ping) }

        luchador1 = l1
        luchador2 = l2

        val arena = currentArena ?: return
        val targetWorld = l1.world

        val spawnA = arena.dueloA?.clone() ?: arena.spawnPoints.getOrNull(0)?.clone() ?: return
        val spawnB = arena.dueloB?.clone() ?: arena.spawnPoints.getOrNull(1)?.clone() ?: return
        spawnA.world = targetWorld
        spawnB.world = targetWorld

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            l1.teleportAsync(spawnA)
            l2?.teleportAsync(spawnB)

            darPalo(l1)
            l2?.let { darPalo(it) }

            val mm = plugin.messageManager
            val title = Title.title(
                mm.parse("<#FF3131>⚔️ <b>¡A LUCHAR!</b> ⚔️"),
                mm.parse("<white>${l1.name} <gray>(${l1.ping}ms)</gray> <red>vs <white>${l2?.name} <gray>(${l2?.ping}ms)</gray>")
            )

            players.forEach { it.showTitle(title) }
            spectators.forEach { it.showTitle(title) }

            plugin.server.onlinePlayers.forEach { it.playSound(it.location, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f) }

            rondaActiva = true
        }
    }

    private fun darPalo(p: Player) {
        p.inventory.clear()

        // --- PALO SIN ENCANTAMIENTOS ---
        val stick = ItemStack(Material.STICK)
        val meta = stick.itemMeta
        meta.displayName(plugin.messageManager.parse("<#FF9900><b>Palo de Sumo</b></#FF9900>"))
        stick.itemMeta = meta

        p.inventory.addItem(stick)
        p.health = 20.0
    }

    // --- NUEVO: SISTEMA DE KNOCKBACK EXTRA (LIGERO Y CONTROLADO) ---
    @EventHandler
    fun onPlayerHit(event: EntityDamageByEntityEvent) {
        if (!rondaActiva) return

        val victim = event.entity as? Player ?: return
        val attacker = event.damager as? Player ?: return

        // Verificamos que ambos sean los luchadores actuales
        if ((victim == luchador1 && attacker == luchador2) || (victim == luchador2 && attacker == luchador1)) {

            val knockbackDirection = attacker.location.direction.setY(0.0).normalize()

            // Un multiplicador de 0.4 es un empujón suave extra
            val extraKnockback = knockbackDirection.multiply(0.4).setY(0.15)

            // Aplicamos la velocidad 1 tick después
            plugin.server.regionScheduler.runDelayed(plugin, victim.location, { _ ->
                victim.velocity = victim.velocity.add(extraKnockback)
            }, 1L)
        }
    }

    private fun iniciarDetectorCaidas() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }
            if (!rondaActiva) return@runAtFixedRate

            val l1 = luchador1
            val l2 = luchador2

            var perdedor: Player? = null
            var ganador: Player? = null

            if (l1 != null && (l1.location.y <= alturaCaida || l1.isDead)) {
                perdedor = l1
                ganador = l2
            } else if (l2 != null && (l2.location.y <= alturaCaida || l2.isDead)) {
                perdedor = l2
                ganador = l1
            }

            if (perdedor != null && ganador != null) {
                rondaActiva = false
                procesarVictoriaRonda(ganador, perdedor)
            }

        }, 5L, 5L)
    }

    private fun procesarVictoriaRonda(ganador: Player, perdedor: Player) {
        val mm = plugin.messageManager
        val lang = plugin.languageManager

        val rawMsg = lang.get("sumo.round_winner")
        plugin.server.broadcast(mm.parse(rawMsg, Placeholder.parsed("winner", ganador.name), Placeholder.parsed("loser", perdedor.name)))

        ganador.playSound(ganador.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)

        ganador.inventory.clear()

        val arena = currentArena
        val gradaLoc = arena?.gradas?.clone() ?: arena?.centerLocation?.clone()
        gradaLoc?.world = ganador.world

        if (gradaLoc != null) ganador.teleportAsync(gradaLoc)

        eliminate(perdedor)

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            siguienteRonda()
        }, 60L)
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return
        val rawWin = plugin.languageManager.get("sumo.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
        winner.world.spawnParticle(org.bukkit.Particle.FIREWORK, winner.location, 100)
        winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        stop()
    }

    override fun onStop() {
        rondaActiva = false
        plugin.eventManager.currentGame = null

        HandlerList.unregisterAll(this)

        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation

        val todos = players + spectators

        todos.forEach { p ->
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }

    fun isLuchador(p: Player): Boolean {
        return p == luchador1 || p == luchador2
    }
}
