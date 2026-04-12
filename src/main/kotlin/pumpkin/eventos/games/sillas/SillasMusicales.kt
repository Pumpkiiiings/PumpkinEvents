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
import java.util.concurrent.TimeUnit

enum class SillasEstado { PREPARACION, MUSICA, SENTARSE }

class SillasMusicales( plugin: PumpkinEventos) : EventGame(plugin,"sillas", "<#BF00FF>Sillas Musicales</#BF00FF>") {

    var estado = SillasEstado.PREPARACION

    // Lista de sillas que siguen en el juego
    val sillasActivas = mutableListOf<Location>()

    // GSit ya maneja el montaje, así que solo usamos un Set para marcar quién está a salvo
    val jugadoresSentados = mutableSetOf<Player>()

    private var roundTimer = 10
    private var displayEstado = "<gray>Preparando...</gray>"

    // Lista de diferentes músicas
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
        displayEstado = "<gray>Preparando...</gray>"

        val arena = currentArena ?: return
        val targetWorld = players.firstOrNull()?.world ?: arena.centerLocation?.world ?: return

        players.forEach { p ->
            p.gameMode = GameMode.ADVENTURE
            p.inventory.clear()
        }

        // --- CALCULAR SILLAS NECESARIAS ---
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

        // Levantamos a todos (teletransportarlos minimamente anula la silla de GSit de forma ultra-segura)
        val toStand = jugadoresSentados.toList()
        jugadoresSentados.clear()
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
        if (estado != SillasEstado.SENTARSE) {
            p.sendMessage(plugin.messageManager.parse("<red>¡La música sigue sonando! No puedes sentarte aún.</red>"))
            return
        }

        val esSilla = sillasActivas.any { it.blockX == blockLoc.blockX && it.blockY == blockLoc.blockY && it.blockZ == blockLoc.blockZ }
        if (!esSilla) return

        // GSitAPI tiene un método perfecto para verificar si alguien está en un bloque
        if (GSitAPI.getSeatsByBlock(blockLoc.block).isNotEmpty()) {
            p.sendMessage(plugin.messageManager.parse("<red>¡Esta silla ya está ocupada!</red>"))
            return
        }

        if (jugadoresSentados.contains(p)) return

        // --- SENTAR AL JUGADOR USANDO LA API DE GSIT ---
        plugin.server.regionScheduler.run(plugin, blockLoc) { _ ->
            val seat = GSitAPI.createSeat(blockLoc.block, p)
            if (seat != null) {
                jugadoresSentados.add(p)
                p.playSound(p.location, Sound.ENTITY_HORSE_SADDLE, 1f, 1f)
                p.sendMessage(plugin.messageManager.parse("<#39FF14>✔ <b>¡Conseguiste una silla!</b></#39FF14>"))
            }
        }
    }

    private fun ejecutarPerdedores() {
        estado = SillasEstado.PREPARACION
        displayEstado = "<#FF3131>Eliminando...</#FF3131>"

        val perdedores = players.filter { !jugadoresSentados.contains(it) }

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            perdedores.forEach { p ->
                p.world.strikeLightningEffect(p.location)
                eliminate(p)
                p.sendMessage(plugin.messageManager.parse("<red>☠ <b>¡TE QUEDASTE SIN SILLA!</b></red>"))
                plugin.server.broadcast(plugin.messageManager.parse("<red>☠</red> <yellow>${p.name}</yellow> <white>fue eliminado.</white>"))
            }

            // --- REDUCIR SILLAS DINÁMICAMENTE ---
            val sillasParaSiguienteRonda = (players.size - 1).coerceAtLeast(1)
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

            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                iniciarRondaMusica()
            }, 80L)
        }
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return
        val rawWin = plugin.languageManager.get("sillas.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
        stop()
    }

    override fun onStop() {
        jugadoresSentados.clear()
        plugin.eventManager.currentGame = null

        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation

        (players + spectators).forEach { p ->
            musicDiscs.forEach { p.stopSound(it) }

            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            // Hacer TP al Lobby lo levanta automáticamente del GSit
            p.teleportAsync(lobby)
        }
    }
}
