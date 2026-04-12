package pumpkin.eventos.games.ruletarusa

import dev.geco.gsit.api.GSitAPI
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.atan2

class RuletaRusa(plugin: PumpkinEventos) : EventGame(plugin, "ruletarusa", "<#FF3131>Ruleta Rusa</#FF3131>") {

    var isPreparation = true
    var forzarAsientos = true

    val jugadoresSentados = mutableMapOf<Player, Location>()
    val camarasTemporales = mutableMapOf<Player, ArmorStand>()

    private val penalizaciones = mutableMapOf<Player, Double>()

    private var arrowDisplay: ItemDisplay? = null
    var ultimaVictima: Player? = null
    private var esperandoDecision = false

    override fun onStart() {
        isPreparation = true
        forzarAsientos = true
        jugadoresSentados.clear()
        camarasTemporales.clear()
        penalizaciones.clear()
        ultimaVictima = null
        esperandoDecision = false

        val arena = currentArena ?: return
        val center = arena.centerLocation ?: return
        val targetWorld = players.firstOrNull()?.world ?: center.world ?: return

        // Aquí se mezclan al azar, ¡pero lo importante es que luego guardamos qué silla le tocó a quién!
        val sillasDisponibles = arena.chairs.map { it.clone().apply { world = targetWorld } }.shuffled()

        // 1. Sentar a los jugadores
        for (i in players.indices) {
            val p = players[i]
            p.gameMode = GameMode.ADVENTURE
            p.inventory.clear()
            penalizaciones[p] = 0.0

            if (i < sillasDisponibles.size) {
                val loc = sillasDisponibles[i]

                val tpSeguro = loc.clone().add(0.5, 1.0, 0.5)

                p.teleportAsync(tpSeguro).thenAccept {
                    plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                        GSitAPI.createSeat(loc.block, p)

                        // Guardamos SU silla exacta en el mapa
                        jugadoresSentados[p] = loc
                    }, 5L)
                }
            } else {
                eliminate(p)
            }
        }

        // 2. Crear Flecha
        plugin.server.regionScheduler.run(plugin, center) { _ ->
            val arrowLoc = center.clone().apply { world = targetWorld }.add(0.5, 1.2, 0.5)
            arrowDisplay = targetWorld.spawn(arrowLoc, ItemDisplay::class.java)
            arrowDisplay?.setItemStack(ItemStack(Material.SPECTRAL_ARROW))

            val transform = arrowDisplay!!.transformation
            val rotation = Quaternionf().rotateX(Math.toRadians(90.0).toFloat())
            arrowDisplay!!.transformation = Transformation(transform.translation, rotation, Vector3f(2f, 2f, 2f), transform.rightRotation)
        }

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            isPreparation = false
            girarRuleta()
        }, 100L)
    }

    private fun girarRuleta() {
        if (!isRunning) return
        if (players.size <= 1) { checkWinner(); return }

        val lang = plugin.languageManager
        val mm = plugin.messageManager

        // ¡LIMPIEZA GENERAL 1! Garantizamos que nadie tenga el arma de la ronda anterior
        players.forEach { p ->
            p.inventory.clear()
            p.sendActionBar(mm.parse(lang.get("ruletarusa.actionbar.spin")))
        }

        var victima = players.random()
        if (players.size > 1 && victima == ultimaVictima) {
            victima = players.filter { it != ultimaVictima }.random()
        }
        ultimaVictima = victima

        val locVictima = victima.location
        val locFlecha = arrowDisplay?.location ?: return

        plugin.server.regionScheduler.run(plugin, locFlecha) { _ ->
            val dx = locVictima.x - locFlecha.x
            val dz = locVictima.z - locFlecha.z
            val angleToPlayer = atan2(dz, dx) - (Math.PI / 2)
            val rotacionFinal = angleToPlayer + (Math.PI * 10)

            arrowDisplay?.interpolationDuration = 60
            val transform = arrowDisplay!!.transformation
            val nuevaRotacion = Quaternionf().rotateX(Math.toRadians(90.0).toFloat()).rotateY(rotacionFinal.toFloat())
            arrowDisplay!!.transformation = Transformation(transform.translation, nuevaRotacion, Vector3f(2f, 2f, 2f), transform.rightRotation)

            var ticks = 0
            plugin.server.regionScheduler.runAtFixedRate(plugin, locFlecha, { task ->
                if (!isRunning) { task.cancel(); return@runAtFixedRate }

                if (ticks >= 60) {
                    task.cancel()
                    val timesFinal = Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
                    val finalTitle = Title.title(
                        mm.parse("<#FF3131><bold>${victima.name}</bold></#FF3131>"),
                        mm.parse("<white>¡Es el turno de la suerte!</white>"),
                        timesFinal
                    )
                    players.forEach {
                        it.showTitle(finalTitle)
                        it.playSound(it.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f)
                    }

                    plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                        cinematicaTension(victima)
                    }, 40L)
                    return@runAtFixedRate
                }

                if (ticks % 4 == 0) {
                    val randomPlayer = players.random()
                    val timesRapido = Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO)
                    val spinTitle = Title.title(
                        mm.parse("<yellow>${randomPlayer.name}</yellow>"),
                        mm.parse("<gray>Sorteando...</gray>"),
                        timesRapido
                    )
                    players.forEach {
                        it.showTitle(spinTitle)
                        it.playSound(it.location, Sound.UI_BUTTON_CLICK, 1f, 1.5f)
                    }
                    locFlecha.world.playSound(locFlecha, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f)
                }
                ticks++
            }, 1L, 1L)
        }
    }

    private fun cinematicaTension(victima: Player) {
        val lang = plugin.languageManager
        val mm = plugin.messageManager

        forzarAsientos = false

        players.forEach { p ->
            p.sendActionBar(mm.parse(lang.get("ruletarusa.actionbar.turn"), Placeholder.parsed("player", victima.name)))
        }

        val faceLoc = victima.eyeLocation.clone().add(victima.eyeLocation.direction.multiply(1.5))
        faceLoc.yaw = victima.location.yaw + 180f
        faceLoc.pitch = 0f

        players.forEach { p ->
            if (p != victima) {
                p.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 9999, 1, false, false, false))

                p.leaveVehicle()

                p.teleportAsync(faceLoc).thenAccept {
                    plugin.server.regionScheduler.runDelayed(plugin, faceLoc, { _ ->
                        val camSeat = faceLoc.world.spawnEntity(faceLoc.clone().add(0.0, -1.0, 0.0), EntityType.ARMOR_STAND) as ArmorStand
                        camSeat.isInvisible = true
                        camSeat.isMarker = true
                        camSeat.setGravity(false)
                        camSeat.addPassenger(p)
                        camarasTemporales[p] = camSeat
                    }, 5L)
                }
            }
        }

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            // ¡LIMPIEZA GENERAL 2! Aseguramos una vez más que solo 1 persona tenga el arma
            players.forEach { it.inventory.clear() }

            val gun = ItemStack(Material.IRON_HORSE_ARMOR)
            val meta = gun.itemMeta
            meta.displayName(mm.parse("<#FF3131><b>Revólver del Destino</b></#FF3131>"))
            gun.itemMeta = meta

            victima.inventory.setItem(4, gun)
            victima.inventory.heldItemSlot = 4

            val decisionTitle = Title.title(
                mm.parse("<red>¡Tienes el arma!</red>"),
                mm.parse("<white>[Clic Der Dispara] [Q - Pasar al siguiente]</white>"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(10), Duration.ofMillis(500))
            )
            victima.showTitle(decisionTitle)

            esperandoDecision = true
            iniciarRelojDecision(victima)

        }, 10L)
    }

    private fun iniciarRelojDecision(victima: Player) {
        var latidos = 0
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || !esperandoDecision) { task.cancel(); return@runAtFixedRate }

            if (latidos >= 10) {
                task.cancel()
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    // Si el tiempo se acaba, forzamos que sea el jugador correcto el que dispare
                    procesarEleccion(victima, true)
                }
                return@runAtFixedRate
            }

            players.forEach { p ->
                p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
                plugin.server.asyncScheduler.runDelayed(plugin, { _ ->
                    p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f)
                }, 200, TimeUnit.MILLISECONDS)
            }
            latidos++
        }, 0, 1, TimeUnit.SECONDS)
    }

    // Cambié el nombre del parámetro a "jugador" para poder validar si es la víctima correcta
    fun procesarEleccion(jugador: Player, decidioDisparar: Boolean) {
        // 1. Limpiamos inventario si se llama fuera de tiempo
        if (!esperandoDecision) {
            jugador.inventory.clear()
            return
        }

        // 2. SEGURIDAD: Si un espectador de alguna forma usa un revólver fantasma, no dejamos que robe el turno
        if (jugador != ultimaVictima) {
            jugador.inventory.clear() // Le quitamos el revólver al impostor
            return // Ignoramos su acción
        }

        // Si pasamos los bloqueos, significa que es la víctima oficial a tiempo
        esperandoDecision = false
        jugador.inventory.clear() // Le quitamos el revólver de inmediato al jugador actual
        jugador.resetTitle()

        val currentSlimeWorld = jugador.world

        // Devolver a los ESPECTADORES (Los que miraban) a sus sillas originales
        players.forEach { p ->
            if (p != jugador) {
                camarasTemporales.remove(p)?.remove()
                p.removePotionEffect(PotionEffectType.INVISIBILITY)

                val originalLoc = jugadoresSentados[p]
                if (originalLoc != null) {
                    originalLoc.world = currentSlimeWorld
                    val tpSeguro = originalLoc.clone().add(0.5, 1.0, 0.5)

                    p.teleportAsync(tpSeguro).thenAccept {
                        plugin.server.regionScheduler.runDelayed(plugin, originalLoc, { _ ->
                            GSitAPI.createSeat(originalLoc.block, p)
                        }, 5L)
                    }
                }
            }
        }

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            forzarAsientos = true
        }, 15L)

        if (decidioDisparar) {
            val probabilidadMuerte = 0.25 + (penalizaciones[jugador] ?: 0.0)
            val muere = Math.random() < probabilidadMuerte

            if (muere) {
                jugador.world.playSound(jugador.location, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1f)
                val lang = plugin.languageManager
                plugin.server.broadcast(plugin.messageManager.parse(lang.get("ruletarusa.broadcast.died"), Placeholder.parsed("player", jugador.name)))

                jugadoresSentados.remove(jugador)
                jugador.leaveVehicle()
                aplicarCuelloSangriento(jugador)

                plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> girarRuleta() }, 80L)

            } else {
                jugador.world.playSound(jugador.location, Sound.ENTITY_PLAYER_BREATH, 1f, 1f)
                jugador.world.spawnParticle(Particle.SPLASH, jugador.eyeLocation, 30, 0.3, 0.5, 0.3, 0.0)

                val lang = plugin.languageManager
                plugin.server.broadcast(plugin.messageManager.parse(lang.get("ruletarusa.broadcast.saved"), Placeholder.parsed("player", jugador.name)))

                penalizaciones[jugador] = 0.0

                // Si disparó y se salvó, SE QUEDA en la silla, no hace falta teletransportarlo
                plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> girarRuleta() }, 40L)
            }
        } else {
            // SI DECIDIÓ PASAR (Presionó Q)
            jugador.sendMessage(plugin.messageManager.parse("<red>Has decidido pasar tu turno. Tu probabilidad de morir ha aumentado un 10%.</red>"))
            jugador.world.playSound(jugador.location, Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 1f)

            val penalidadActual = penalizaciones[jugador] ?: 0.0
            penalizaciones[jugador] = penalidadActual + 0.10

            plugin.server.broadcast(plugin.messageManager.parse("<yellow><b>${jugador.name}</b></yellow> <white>fue un cobarde y pasó el arma...</white>"))

            // El jugador sigue físicamente en su silla (porque nunca fue movido a cámara).
            // No hacemos TP, simplemente giramos la ruleta de nuevo.
            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> girarRuleta() }, 40L)
        }
    }

    private fun aplicarCuelloSangriento(p: Player) {
        p.isGlowing = true
        p.showTitle(Title.title(plugin.messageManager.parse("<red><bold>☠ ¡ELIMINADO! ☠</bold></red>"), plugin.messageManager.parse("<dark_red>La suerte no estuvo de tu lado...</dark_red>")))

        p.world.playSound(p.location, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.5f)
        p.velocity = Vector(0.0, 4.0, 0.0)

        var i = 0
        plugin.server.regionScheduler.runAtFixedRate(plugin, p.location, { task ->
            if (i >= 22 || !p.isOnline || p.isDead) { task.cancel(); return@runAtFixedRate }
            p.world.spawnParticle(Particle.BLOCK, p.location, 15, 0.3, 0.3, 0.3, 0.1, Material.REDSTONE_BLOCK.createBlockData())
            p.world.spawnParticle(Particle.ITEM, p.location, 10, 0.3, 0.3, 0.3, 0.1, ItemStack(Material.BONE_MEAL))
            i++
        }, 1L, 1L)

        plugin.server.regionScheduler.runDelayed(plugin, p.location, { _ ->
            if (!p.isOnline) return@runDelayed
            p.world.spawnParticle(Particle.EXPLOSION_EMITTER, p.location, 3)
            p.world.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.2f, 1f)

            val skull = p.world.spawn(p.location, ItemDisplay::class.java)
            skull.setItemStack(ItemStack(Material.SKELETON_SKULL))
            plugin.server.regionScheduler.runDelayed(plugin, p.location, { _ -> skull.remove() }, 60L)

            p.health = 0.0
            p.isGlowing = false
            eliminate(p)
        }, 22L)
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return
        val rawWin = plugin.languageManager.get("ruletarusa.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
        winner.world.spawnParticle(Particle.FIREWORK, winner.location, 100)
        winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        plugin.puntajeManager.addPoints(winner, 10, "¡Victoria conseguida!")
        stop()
    }

    override fun onStop() {
        arrowDisplay?.remove()
        camarasTemporales.values.forEach { it.remove() }

        arrowDisplay = null
        camarasTemporales.clear()
        jugadoresSentados.clear()

        plugin.eventManager.currentGame = null
        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation

        (players + spectators).forEach { p ->
            p.leaveVehicle()
            p.removePotionEffect(PotionEffectType.INVISIBILITY)
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}
