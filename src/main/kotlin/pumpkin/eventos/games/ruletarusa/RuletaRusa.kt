package pumpkin.eventos.games.ruletarusa

import dev.geco.gsit.api.GSitAPI
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
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
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.random.Random

enum class EventoRuleta { NORMAL, RECARGA_MANUAL, ESCOPETA }

class RuletaRusa(plugin: PumpkinEventos) : EventGame(plugin, "ruletarusa", "<#FF3131>Ruleta Rusa</#FF3131>") {

    var isPreparation = true
    var forzarAsientos = true

    val jugadoresSentados = mutableMapOf<Player, Location>()
    val camarasTemporales = mutableMapOf<Player, ArmorStand>()

    private var arrowDisplay: ItemDisplay? = null
    var ultimaVictima: Player? = null
    private var esperandoDecision = false

    private val cylinder = RouletteCylinder()
    private val passesRemaining = mutableMapOf<UUID, Int>()
    private val inspectionsRemaining = mutableSetOf<UUID>()
    private var roundNumber = 0
    private var balaCargadaManualmente = false

    private var eventoActual = EventoRuleta.NORMAL

    override fun getExtraPlaceholders(): Map<String, String> {
        return mapOf(
            "%vivos%" to players.size.toString(),
            "%balas%" to (if (eventoActual == EventoRuleta.ESCOPETA) "50%" else "${riskPercent()}%")
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(GameRule.FALL_DAMAGE to false) // El PVP se bloquea globalmente en GameListener
    }

    override fun onStart() {
        isPreparation = true
        forzarAsientos = true
        jugadoresSentados.clear()
        camarasTemporales.clear()
        ultimaVictima = null
        esperandoDecision = false
        roundNumber = 0
        val configuredPasses = plugin.config.getInt("ruletarusa.passes_per_player", 1).coerceAtLeast(0)
        passesRemaining.clear()
        players.forEach { passesRemaining[it.uniqueId] = configuredPasses }
        inspectionsRemaining.clear()
        inspectionsRemaining.addAll(players.map { it.uniqueId })

        recargarRevolverGlobal()

        val arena = currentArena ?: return
        val center = arena.centerLocation ?: return
        val targetWorld = gameWorld ?: players.firstOrNull()?.world ?: center.world ?: return

        val sillasDisponibles = arena.chairs.map { it.clone().apply { world = targetWorld } }.shuffled()

        for (i in players.indices) {
            val p = players[i]
            p.gameMode = GameMode.ADVENTURE
            p.inventory.clear()

            if (i < sillasDisponibles.size) {
                val loc = sillasDisponibles[i]
                val tpSeguro = loc.clone().add(0.5, 1.0, 0.5)

                p.teleportAsync(tpSeguro).thenAccept {
                    plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                        GSitAPI.createSeat(loc.block, p)
                        jugadoresSentados[p] = loc
                    }, 5L)
                }
            } else {
                eliminate(p)
            }
        }

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

    private fun recargarRevolverGlobal() {
        cylinder.reload()
    }

    private fun riskPercent(): Int = (cylinder.currentRisk * 100.0).roundToInt()

    private fun girarRuleta() {
        if (!isRunning) return
        if (players.size <= 1) { checkWinner(); return }

        val lang = plugin.languageManager
        val mm = plugin.messageManager

        players.forEach { p ->
            p.inventory.clear()
            p.sendActionBar(lang.component("ruletarusa.actionbar.spin"))
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

            val spinTicks = if (roundNumber++ == 0) 40 else 20
            arrowDisplay?.interpolationDuration = spinTicks
            val transform = arrowDisplay!!.transformation
            val nuevaRotacion = Quaternionf().rotateX(Math.toRadians(90.0).toFloat()).rotateY(rotacionFinal.toFloat())
            arrowDisplay!!.transformation = Transformation(transform.translation, nuevaRotacion, Vector3f(2f, 2f, 2f), transform.rightRotation)

            var ticks = 0
            plugin.server.regionScheduler.runAtFixedRate(plugin, locFlecha, { task ->

                if (ticks >= spinTicks) {
                    task.cancel()
                    val timesFinal = Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
                    val finalTitle = Title.title(
                        plugin.languageManager.component("ruletarusa.title.chosen_main", Placeholder.unparsed("player", victima.name)),
                        plugin.languageManager.component("ruletarusa.title.chosen_subtitle"),
                        timesFinal
                    )
                    players.forEach {
                        it.showTitle(finalTitle)
                        it.playSound(it.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f)
                    }

                    plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                        cinematicaTension(victima)
                    }, 10L)
                    return@runAtFixedRate
                }

                if (ticks % 4 == 0) {
                    val randomPlayer = players.random()
                    val timesRapido = Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO)
                    players.forEach {
                        it.showTitle(Title.title(
                            plugin.languageManager.component("ruletarusa.title.spinning_main", Placeholder.unparsed("player", randomPlayer.name)),
                            plugin.languageManager.component("ruletarusa.title.spinning_subtitle"),
                            timesRapido
                        ))
                        it.playSound(it.location, Sound.UI_BUTTON_CLICK, 1f, 1.5f)
                    }
                    locFlecha.world.playSound(locFlecha, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f)
                }
                ticks++
            }, 1L, 1L)
        }
    }

    private fun cinematicaTension(victima: Player) {
        val mm = plugin.messageManager

        forzarAsientos = false

        players.forEach { p ->
            p.sendActionBar(plugin.languageManager.component("ruletarusa.actionbar.turn", Placeholder.unparsed("player", victima.name)))
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
                        camSeat.isInvisible = true; camSeat.isMarker = true; camSeat.setGravity(false)
                        camSeat.addPassenger(p)
                        camarasTemporales[p] = camSeat
                    }, 5L)
                }
            }
        }

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            prepararArmaEventos(victima)
        }, 10L)
    }

    private fun prepararArmaEventos(victima: Player) {
        val mm = plugin.messageManager
        players.forEach { it.inventory.clear() }

        val chance = (1..100).random()
        balaCargadaManualmente = false

        when {
            chance <= 15 -> {
                eventoActual = EventoRuleta.ESCOPETA
                val arma = ItemStack(Material.IRON_HORSE_ARMOR).apply { itemMeta = itemMeta?.apply { displayName(plugin.languageManager.component("ruletarusa.item.shotgun")) } }
                victima.inventory.setItem(4, arma)
                plugin.languageManager.broadcast("ruletarusa.message.shotgun")
            }
            chance in 16..30 -> {
                eventoActual = EventoRuleta.RECARGA_MANUAL
                val arma = ItemStack(Material.IRON_HORSE_ARMOR).apply { itemMeta = itemMeta?.apply { displayName(plugin.languageManager.component("ruletarusa.item.empty_revolver")) } }
                val bala = ItemStack(Material.GUNPOWDER).apply { itemMeta = itemMeta?.apply { displayName(plugin.languageManager.component("ruletarusa.item.bullet")) } }
                victima.inventory.setItem(3, bala)
                victima.inventory.setItem(4, arma)
                plugin.languageManager.broadcast("ruletarusa.message.manual_reload")
            }
            else -> {
                eventoActual = EventoRuleta.NORMAL
                val arma = ItemStack(Material.IRON_HORSE_ARMOR).apply { itemMeta = itemMeta?.apply { displayName(plugin.languageManager.component("ruletarusa.item.revolver")) } }
                victima.inventory.setItem(4, arma)
                val inspect = ItemStack(Material.SPYGLASS).apply { itemMeta = itemMeta?.apply { displayName(plugin.languageManager.component("ruletarusa.item.inspect")) } }
                victima.inventory.setItem(2, inspect)
            }
        }

        victima.inventory.heldItemSlot = 4

        val decisionTitle = Title.title(
            plugin.languageManager.component("ruletarusa.title.decision_main"),
            if (eventoActual == EventoRuleta.NORMAL) {
                plugin.languageManager.component(
                    "ruletarusa.title.decision_normal",
                    Placeholder.unparsed("risk", riskPercent().toString()),
                    Placeholder.unparsed("passes", (passesRemaining[victima.uniqueId] ?: 0).toString())
                )
            } else plugin.languageManager.component("ruletarusa.title.decision_forced"),
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(plugin.config.getInt("ruletarusa.decision_seconds", 12).toLong()), Duration.ofMillis(200))
        )
        victima.showTitle(decisionTitle)

        esperandoDecision = true
        iniciarRelojDecision(victima)
    }

    private fun iniciarRelojDecision(victima: Player) {
        var tickTimer = plugin.config.getInt("ruletarusa.decision_seconds", 12).coerceAtLeast(5)
        runAtFixedRate( { task ->
            if (!isRunning || !esperandoDecision) { task.cancel(); return@runAtFixedRate }

            if (tickTimer <= 0) {
                task.cancel()
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    plugin.languageManager.broadcast("ruletarusa.message.timeout", Placeholder.unparsed("player", victima.name))
                    ejecutarMuerte(victima)
                }
                return@runAtFixedRate
            }

            players.forEach { p ->
                p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
                plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f) }, 4L)
                p.sendActionBar(plugin.languageManager.component(
                    "ruletarusa.actionbar.timer",
                    Placeholder.unparsed("time", tickTimer.toString().padStart(2, '0')),
                    Placeholder.unparsed("risk", if (eventoActual == EventoRuleta.ESCOPETA) "50" else riskPercent().toString())
                ))
            }
            tickTimer--
        }, 1L, 20L)
    }

    /** Llamado desde RuletaRusaListener cuando el jugador hace clic izquierdo con la bala */
    fun recargarManual(jugador: Player) {
        if (!esperandoDecision || eventoActual != EventoRuleta.RECARGA_MANUAL || balaCargadaManualmente) return
        balaCargadaManualmente = true
        jugador.inventory.remove(org.bukkit.Material.GUNPOWDER)
        jugador.playSound(jugador.location, org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN, 1f, 2f)
        plugin.languageManager.send(jugador, "ruletarusa.message.manually_loaded")
        val arma = jugador.inventory.getItem(4)
        arma?.itemMeta = arma?.itemMeta?.apply {
            displayName(plugin.languageManager.component("ruletarusa.item.loaded_revolver"))
        }
    }

    fun inspeccionarCamara(jugador: Player) {
        if (!esperandoDecision || jugador != ultimaVictima || eventoActual != EventoRuleta.NORMAL ||
            !inspectionsRemaining.remove(jugador.uniqueId)) {
            plugin.languageManager.send(jugador, "ruletarusa.message.inspect_unavailable")
            return
        }

        val accuracy = plugin.config.getDouble("ruletarusa.inspection_accuracy", 0.70).coerceIn(0.5, 1.0)
        val reportsDanger = if (Random.nextDouble() <= accuracy) cylinder.currentChamberLoaded else !cylinder.currentChamberLoaded
        plugin.languageManager.send(jugador, if (reportsDanger) "ruletarusa.message.inspect_danger" else "ruletarusa.message.inspect_safe")
        jugador.playSound(jugador.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, if (reportsDanger) 0.6f else 1.5f)
        jugador.inventory.setItem(2, null)
    }

    fun procesarEleccion(jugador: Player, decidioDisparar: Boolean) {
        if (!esperandoDecision) return
        if (!decidioDisparar) {
            val passes = passesRemaining[jugador.uniqueId] ?: 0
            if (eventoActual != EventoRuleta.NORMAL || passes <= 0) {
                plugin.languageManager.send(jugador, "ruletarusa.message.pass_unavailable")
                return
            }
            passesRemaining[jugador.uniqueId] = passes - 1
        }
        esperandoDecision = false
        jugador.inventory.clear()
        jugador.resetTitle()

        val currentSlimeWorld = jugador.world

        // --- FIX: LIMPIEZA TOTAL (Vivos + Espectadores) ---
        (players + spectators).forEach { p ->
            // 1. Quitar siempre la invisibilidad
            p.removePotionEffect(PotionEffectType.INVISIBILITY)

            // 2. Borrar su cámara si existía
            camarasTemporales.remove(p)?.remove()

            // 3. Devolver a su silla si es un jugador vivo
            if (p != jugador) {
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

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> forzarAsientos = true }, 15L)

        if (decidioDisparar) {
            var muere = false
            var normalRisk = 0
            when (eventoActual) {
                EventoRuleta.ESCOPETA -> muere = Math.random() < 0.50
                EventoRuleta.RECARGA_MANUAL -> {
                    if (!balaCargadaManualmente) {
                        plugin.languageManager.broadcast("ruletarusa.message.fired_unloaded", Placeholder.unparsed("player", jugador.name))
                        ejecutarMuerte(jugador); return
                    }
                    muere = Math.random() < (1.0 / 6.0)
                }
                EventoRuleta.NORMAL -> {
                    val result = cylinder.pullTrigger()
                    muere = result.fired
                    normalRisk = (result.riskBeforeShot * 100.0).roundToInt()
                }
            }

            if (muere) {
                plugin.languageManager.broadcast("ruletarusa.broadcast.died", Placeholder.unparsed("player", jugador.name))
                ejecutarMuerte(jugador)
            } else {
                if (eventoActual == EventoRuleta.NORMAL && normalRisk >= 25) {
                    val reward = (normalRisk / 20).coerceAtLeast(1)
                    val reason = plugin.languageManager.get("ruletarusa.message.risk_reward").replace("<risk>", normalRisk.toString())
                    plugin.puntajeManager.addPoints(jugador, reward, reason)
                }
                jugador.world.playSound(jugador.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                jugador.world.spawnParticle(Particle.SPLASH, jugador.eyeLocation, 30, 0.3, 0.5, 0.3, 0.0)
                plugin.languageManager.broadcast("ruletarusa.broadcast.saved", Placeholder.unparsed("player", jugador.name))
                plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> girarRuleta() }, 40L)
            }
        } else {
            // SI DECIDIÓ PASAR (Q)
            cylinder.pass()
            plugin.languageManager.broadcast("ruletarusa.message.passed", Placeholder.unparsed("player", jugador.name))
            jugador.world.playSound(jugador.location, Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 1f)
            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> girarRuleta() }, 20L)
        }
    }

    private fun ejecutarMuerte(p: Player) {
        jugadoresSentados.remove(p)
        p.leaveVehicle()

        p.world.playSound(p.location, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1f)
        p.world.playSound(p.location, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
        p.world.spawnParticle(Particle.BLOCK, p.location.add(0.0, 1.0, 0.0), 100, 0.5, 0.5, 0.5, 0.1, Material.REDSTONE_BLOCK.createBlockData())

        plugin.server.regionScheduler.run(plugin, p.location) { _ ->
            val w = p.world
            val loc = p.location.clone().add(0.0, 1.0, 0.0)

            val displays = mutableListOf<ItemDisplay>()

            val skull = w.spawn(loc, ItemDisplay::class.java)
            skull.setItemStack(ItemStack(Material.SKELETON_SKULL))
            displays.add(skull)

            for (i in 1..3) {
                val carne = w.spawn(loc, ItemDisplay::class.java)
                carne.setItemStack(ItemStack(Material.REDSTONE_BLOCK))
                val t = carne.transformation
                carne.transformation = Transformation(t.translation, t.leftRotation, Vector3f(0.4f, 0.4f, 0.4f), t.rightRotation)
                displays.add(carne)
            }

            for (i in 1..2) {
                val hueso = w.spawn(loc, ItemDisplay::class.java)
                hueso.setItemStack(ItemStack(Material.BONE))
                displays.add(hueso)
            }

            displays.forEach { d ->
                val vx = (Math.random() - 0.5) * 1.5
                val vy = (Math.random() * 1.0) + 0.5
                val vz = (Math.random() - 0.5) * 1.5
                d.velocity = Vector(vx, vy, vz)
            }

            var tick = 0
            plugin.server.regionScheduler.runAtFixedRate(plugin, loc, { task ->
                if (!isRunning || tick > 60) {
                    displays.forEach { if (!it.isDead) it.remove() }
                    task.cancel()
                    return@runAtFixedRate
                }

                displays.forEach { d ->
                    if (!d.isDead) {
                        val cLoc = d.location
                        d.velocity = d.velocity.subtract(Vector(0.0, 0.08, 0.0))

                        val trans = d.transformation
                        trans.rightRotation.rotateX(0.2f).rotateY(0.2f)
                        d.transformation = trans

                        val bBelow = w.getBlockAt(cLoc.blockX, cLoc.blockY - 1, cLoc.blockZ)
                        if (bBelow.type.isSolid) {
                            d.velocity = Vector(0,0,0)
                            w.spawnParticle(Particle.BLOCK, cLoc, 2, 0.1, 0.0, 0.1, Material.REDSTONE_BLOCK.createBlockData())
                        }
                    }
                }
                tick++
            }, 1L, 1L)
        }

        p.health = 0.0
        eliminate(p)
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> girarRuleta() }, 80L)
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return
        plugin.languageManager.broadcast("ruletarusa.broadcast.winner", Placeholder.unparsed("player", winner.name))
        winner.world.spawnParticle(Particle.FIREWORK, winner.location, 100)
        winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        awardVictory(winner, 10)
        stop()
    }

    override fun onStop() {
        arrowDisplay?.remove()
        camarasTemporales.values.forEach { it.remove() }

        arrowDisplay = null
        camarasTemporales.clear()
        jugadoresSentados.clear()
        passesRemaining.clear()
        inspectionsRemaining.clear()
        returnToLobby(beforeReset = { p ->
            p.leaveVehicle()
            p.removePotionEffect(PotionEffectType.INVISIBILITY)
        })
    }
}
