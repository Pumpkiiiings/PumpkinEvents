package pumpkin.eventos.games.sumo

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame

class Sumo(private val plugin: PumpkinEventos) : EventGame("sumo", "<#FF9900>Sumo 1v1</#FF9900>") {

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
        val arena = currentArena ?: return
        val mm = plugin.messageManager

        // --- DEFINIR PUNTOS Y ARREGLAR MUNDOS ---
        // Como el EventManager ya clonó y teletransportó al primer jugador,
        // su mundo actual es el SlimeWorld cargado en RAM.
        val targetWorld = arena.centerLocation?.world ?: players.firstOrNull()?.world ?: return

        // Extraemos y clonamos las ubicaciones para no dañar la plantilla
        val gradaLoc = arena.gradas?.clone() ?: arena.centerLocation?.clone()?.add(0.0, 10.0, 0.0) ?: return
        val dueloA = arena.dueloA?.clone() ?: arena.spawnPoints.getOrNull(0)?.clone() ?: return
        val dueloB = arena.dueloB?.clone() ?: arena.spawnPoints.getOrNull(1)?.clone() ?: return

        // ¡MAGIA!: Inyectamos el mundo clonado a todas las ubicaciones
        gradaLoc.world = targetWorld
        dueloA.world = targetWorld
        dueloB.world = targetWorld

        // Calculamos que si caen 2 bloques por debajo de la plataforma de duelo, pierden
        alturaCaida = dueloA.y - 2.0

        // --- PREPARAR A TODOS ---
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            players.forEach { p ->
                p.gameMode = GameMode.ADVENTURE
                p.inventory.clear()
                p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
                p.teleportAsync(gradaLoc)
                p.sendMessage(mm.parse("<#00FFFF>🍿 <b>GRADAS:</b> <white>El torneo de Sumo va a comenzar..."))
            }

            // Iniciar el loop de chequeo de caídas
            iniciarDetectorCaidas()

            // Iniciar la primera ronda después de 3 segundos
            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                siguienteRonda()
            }, 60L)
        }
    }

    private fun siguienteRonda() {
        if (!isRunning) return

        // --- REVISAR SI HAY GANADOR ---
        if (players.size <= 1) {
            checkWinner()
            return
        }

        // --- ELEGIR LUCHADORES ---
        val vivos = players.shuffled()
        luchador1 = vivos[0]
        luchador2 = vivos[1]

        val l1 = luchador1 ?: return
        val l2 = luchador2 ?: return

        val arena = currentArena ?: return
        val targetWorld = l1.world

        // Volvemos a clonar e inyectar el mundo a los puntos de duelo
        val spawnA = arena.dueloA?.clone() ?: arena.spawnPoints.getOrNull(0)?.clone() ?: return
        val spawnB = arena.dueloB?.clone() ?: arena.spawnPoints.getOrNull(1)?.clone() ?: return
        spawnA.world = targetWorld
        spawnB.world = targetWorld

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            // Teletransportar
            l1.teleportAsync(spawnA)
            l2.teleportAsync(spawnB)

            // Dar Equipamiento
            darPalo(l1)
            darPalo(l2)

            // Anunciar
            val mm = plugin.messageManager
            val title = Title.title(mm.parse("<#FF3131>⚔️ <b>¡A LUCHAR!</b> ⚔️"), mm.parse("<white>${l1.name} <red>vs <white>${l2.name}"))

            players.forEach { it.showTitle(title) }
            spectators.forEach { it.showTitle(title) }

            plugin.server.onlinePlayers.forEach { it.playSound(it.location, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f) }

            rondaActiva = true
        }
    }

    private fun darPalo(p: Player) {
        p.inventory.clear()
        val stick = ItemStack(Material.STICK)
        val meta = stick.itemMeta
        meta.displayName(plugin.messageManager.parse("<#FF9900><b>Palo de Sumo</b></#FF9900>"))
        meta.addEnchant(Enchantment.KNOCKBACK, 1, true)
        stick.itemMeta = meta
        p.inventory.addItem(stick)

        p.health = 20.0
    }

    private fun iniciarDetectorCaidas() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }
            if (!rondaActiva) return@runAtFixedRate

            val l1 = luchador1
            val l2 = luchador2

            // Verificar si alguno cayó por debajo del límite de la plataforma o al agua/vacío
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

        }, 5L, 5L) // Chequea cada 5 ticks (1/4 de segundo)
    }

    private fun procesarVictoriaRonda(ganador: Player, perdedor: Player) {
        val mm = plugin.messageManager
        val lang = plugin.languageManager

        // Anunciar victoria de la ronda
        val rawMsg = lang.get("sumo.round_winner")
        plugin.server.broadcast(mm.parse(rawMsg, Placeholder.parsed("winner", ganador.name), Placeholder.parsed("loser", perdedor.name)))

        ganador.playSound(ganador.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)

        // Limpiar ganador y mandarlo a gradas temporalmente
        ganador.inventory.clear()

        val arena = currentArena
        val gradaLoc = arena?.gradas?.clone() ?: arena?.centerLocation?.clone()
        gradaLoc?.world = ganador.world

        if (gradaLoc != null) ganador.teleportAsync(gradaLoc)

        // Eliminar al perdedor (El método de EventGame lo manda a spectators y le pone GameMode Spectator)
        eliminate(perdedor)

        // Siguiente ronda en 3 segundos
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

    // Funciones públicas para que el GameListener sepa quién puede pegar
    fun isLuchador(p: Player): Boolean {
        return p == luchador1 || p == luchador2
    }
}
