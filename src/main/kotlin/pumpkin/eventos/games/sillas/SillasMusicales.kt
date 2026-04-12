package pumpkin.eventos.games.sillas

import dev.geco.gsit.api.GSitAPI
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class SillasEstado { PREPARACION, MUSICA, SENTARSE }

class SillasMusicales(plugin: PumpkinEventos) : EventGame(plugin,"sillas", "<#BF00FF>Sillas Musicales</#BF00FF>") {

    var estado = SillasEstado.PREPARACION

    val sillasActivas = mutableListOf<Location>()

    val jugadoresSentados = ConcurrentHashMap.newKeySet<Player>()
    val sillasReclamadas = ConcurrentHashMap.newKeySet<Location>()

    private var roundTimer = 10
    private var displayEstado = "<gray>Preparando...</gray>"

    private val musicDiscs = listOf(
        Sound.MUSIC_DISC_CAT, Sound.MUSIC_DISC_BLOCKS, Sound.MUSIC_DISC_CHIRP,
        Sound.MUSIC_DISC_FAR, Sound.MUSIC_DISC_MALL, Sound.MUSIC_DISC_MELLOHI,
        Sound.MUSIC_DISC_STAL, Sound.MUSIC_DISC_STRAD, Sound.MUSIC_DISC_WARD,
        Sound.MUSIC_DISC_WAIT, Sound.MUSIC_DISC_PIGSTEP, Sound.MUSIC_DISC_OTHERSIDE,
        Sound.MUSIC_DISC_11
    )
    private var currentMusic: Sound = Sound.MUSIC_DISC_CAT

    override fun getExtraPlaceholders(): Map<String, String> {
        return mapOf(
            "%estado%" to displayEstado,
            "%sillas%" to sillasActivas.size.toString()
        )
    }

    override fun onStart() {
        estado = SillasEstado.PREPARACION
        sillasActivas.clear()
        jugadoresSentados.clear()
        sillasReclamadas.clear()
        displayEstado = "<gray>Preparando...</gray>"

        val arena = currentArena ?: return
        val targetWorld = players.firstOrNull()?.world ?: arena.centerLocation?.world ?: return

        players.forEach { p ->
            p.gameMode = GameMode.ADVENTURE
            p.inventory.clear()
        }

        val sillasClonadas = arena.chairs.map { it.clone().apply { world = targetWorld } }.shuffled()
        val sillasNecesarias = (players.size - 1).coerceAtLeast(1)

        for (i in sillasClonadas.indices) {
            val loc = sillasClonadas[i]
            if (i < sillasNecesarias) {
                sillasActivas.add(loc)
            } else {
                plugin.server.regionScheduler.run(plugin, loc) { _ ->
                    loc.block.setType(Material.AIR, false)
                }
            }
        }

        iniciarRondaMusica()
    }

    private fun iniciarRondaMusica() {
        if (players.size <= 1) { checkWinner(); return }

        val toStand = jugadoresSentados.toList()
        jugadoresSentados.clear()
        sillasReclamadas.clear()

        toStand.forEach { p ->
            p.teleportAsync(p.location.clone().add(0.0, 1.0, 0.0))
        }

        estado = SillasEstado.MUSICA
        displayEstado = "<#39FF14>¡MÚSICA! 🎵</#39FF14>"

        roundTimer = (5..15).random()

        currentMusic = musicDiscs.random()
        players.forEach { it.playSound(it.location, currentMusic, 1f, 1f) }

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            if (estado == SillasEstado.MUSICA) {
                roundTimer--
                val rawActionBar = plugin.languageManager.get("sillas.actionbar.music")
                players.forEach { it.sendActionBar(plugin.messageManager.parse(rawActionBar)) }

                if (roundTimer <= 0) {
                    task.cancel()
                    detenerMusica()
                }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun detenerMusica() {
        estado = SillasEstado.SENTARSE
        displayEstado = "<#FF3131>¡SIÉNTATE!</#FF3131>"
        roundTimer = 5

        players.forEach { p ->
            p.stopSound(currentMusic)
            p.playSound(p.location, Sound.BLOCK_ANVIL_LAND, 1f, 1f)
            p.sendActionBar(plugin.messageManager.parse(plugin.languageManager.get("sillas.actionbar.sit")))
            p.showTitle(Title.title(plugin.messageManager.parse("<#FF3131><bold>¡SIÉNTATE!</bold></#FF3131>"), plugin.messageManager.parse("<white>¡Haz clic derecho en una silla!")))
        }

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            roundTimer--
            if (roundTimer <= 0) {
                task.cancel()
                ejecutarPerdedores()
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    fun intentarSentarse(p: Player, blockLoc: Location) {
        // SEGURIDAD: Evita que los espectadores roben las sillas.
        if (!players.contains(p)) return

        if (estado != SillasEstado.SENTARSE) {
            p.sendMessage(plugin.messageManager.parse("<red>¡La música sigue sonando! No puedes sentarte aún.</red>"))
            return
        }

        val sillaValida = sillasActivas.find { it.blockX == blockLoc.blockX && it.blockY == blockLoc.blockY && it.blockZ == blockLoc.blockZ }
        if (sillaValida == null) return

        if (jugadoresSentados.contains(p)) return

        plugin.server.regionScheduler.run(plugin, blockLoc) { _ ->

            // Control lógico ABSOLUTO: Ya no confiamos en la API de GSit para chequear
            // si la silla está ocupada. Usamos 100% las variables oficiales del evento.
            if (sillasReclamadas.contains(sillaValida)) {
                p.sendMessage(plugin.messageManager.parse("<red>¡Esta silla ya está ocupada!</red>"))
                return@run
            }

            sillasReclamadas.add(sillaValida)
            jugadoresSentados.add(p)

            val seat = GSitAPI.createSeat(blockLoc.block, p)
            p.playSound(p.location, Sound.ENTITY_HORSE_SADDLE, 1f, 1f)

            if (seat != null) {
                p.sendMessage(plugin.messageManager.parse("<#39FF14>✔ <b>¡Conseguiste una silla!</b></#39FF14>"))
            } else {
                // FALLBACK: Si GSit la rechaza (por ejemplo, porque el jugador saltó en el aire antes de tocarla)
                // la silla ya está asegurada de manera lógica para el jugador, así que sobrevive sí o sí.
                p.teleportAsync(blockLoc.clone().add(0.5, 1.0, 0.5))
                p.sendMessage(plugin.messageManager.parse("<#39FF14>✔ <b>¡Conseguiste una silla! (Modo Seguro)</b></#39FF14>"))
            }
        }
    }

    private fun ejecutarPerdedores() {
        estado = SillasEstado.PREPARACION
        displayEstado = "<#FF3131>Eliminando...</#FF3131>"

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            val perdedores = players.filter { !jugadoresSentados.contains(it) }

            val sobrevivientes = players.size - perdedores.size
            val sillasParaSiguienteRonda = (sobrevivientes - 1).coerceAtLeast(1)
            val sillasARemover = sillasActivas.size - sillasParaSiguienteRonda

            if (sillasARemover > 0) {
                for (i in 0 until sillasARemover) {
                    if (sillasActivas.isEmpty()) break
                    val silla = sillasActivas.random()
                    sillasActivas.remove(silla)

                    plugin.server.regionScheduler.runDelayed(plugin, silla, { _ ->
                        silla.block.setType(Material.AIR, false)
                        silla.world.spawnParticle(org.bukkit.Particle.EXPLOSION, silla.clone().add(0.5, 0.5, 0.5), 2)
                        silla.world.playSound(silla, Sound.BLOCK_WOOD_BREAK, 1f, 1f)
                    }, 40L)
                }
            }

            perdedores.forEach { p ->
                plugin.server.regionScheduler.run(plugin, p.location) { _ ->
                    p.world.strikeLightningEffect(p.location)
                    eliminate(p)
                    p.sendMessage(plugin.messageManager.parse("<red>☠ <b>¡TE QUEDASTE SIN SILLA!</b></red>"))
                }
                plugin.server.broadcast(plugin.messageManager.parse("<red>☠</red> <yellow>${p.name}</yellow> <white>fue eliminado.</white>"))
            }

            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                iniciarRondaMusica()
            }, 80L)
        }
    }

    override fun checkWinner() {
        val winner = players.firstOrNull()
        if (winner != null) {
            val rawWin = plugin.languageManager.get("sillas.broadcast.winner")
            plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
            plugin.puntajeManager.addPoints(winner, 10, "¡Victoria conseguida!")
        }

        stop()
    }

    override fun onStop() {
        jugadoresSentados.clear()
        sillasReclamadas.clear()
        plugin.eventManager.currentGame = null

        sillasActivas.forEach { silla ->
            plugin.server.regionScheduler.run(plugin, silla) { _ ->
                silla.block.setType(Material.AIR, false)
            }
        }
        sillasActivas.clear()

        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation

        (players + spectators).forEach { p ->
            musicDiscs.forEach { p.stopSound(it) }

            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}
