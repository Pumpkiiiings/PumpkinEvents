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
import java.util.concurrent.TimeUnit
import kotlin.math.atan2

enum class EventoRuleta { NORMAL, RECARGA_MANUAL, ESCOPETA }

class RuletaRusa(plugin: PumpkinEventos) : EventGame(plugin, "ruletarusa", "<#FF3131>Ruleta Rusa</#FF3131>"), Listener {

    var isPreparation = true
    var forzarAsientos = true

    val jugadoresSentados = mutableMapOf<Player, Location>()
    val camarasTemporales = mutableMapOf<Player, ArmorStand>()

    private val penalizaciones = mutableMapOf<Player, Double>()

    private var arrowDisplay: ItemDisplay? = null
    var ultimaVictima: Player? = null
    private var esperandoDecision = false

    // --- MECÁNICA DE REVÓLVER REAL ---
    private var cilindro = BooleanArray(6) { false } // 6 recámaras
    private var posicionCilindro = 0
    private var balaCargadaManualmente = false

    private var eventoActual = EventoRuleta.NORMAL

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun getExtraPlaceholders(): Map<String, String> {
        return mapOf(
            "%vivos%" to players.size.toString(),
            "%balas%" to (if (eventoActual == EventoRuleta.ESCOPETA) "50%" else "1/6")
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
        cilindro = BooleanArray(6) { false }
        val balaPos = (0..5).random()
        cilindro[balaPos] = true
        posicionCilindro = 0
    }

    private fun girarRuleta() {
        if (!isRunning) return
        if (players.size <= 1) { checkWinner(); return }

        val lang = plugin.languageManager
        val mm = plugin.messageManager

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
                    players.forEach {
                        it.showTitle(Title.title(mm.parse("<yellow>${randomPlayer.name}</yellow>"), mm.parse("<gray>Sorteando...</gray>"), timesRapido))
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
            p.sendActionBar(mm.parse(plugin.languageManager.get("ruletarusa.actionbar.turn"), Placeholder.parsed("player", victima.name)))
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

        // SORTEO DE EVENTOS
        val chance = (1..100).random()
        balaCargadaManualmente = false

        when {
            chance <= 15 -> { // 15% ESCOPETA
                eventoActual = EventoRuleta.ESCOPETA
                val arma = ItemStack(Material.IRON_HORSE_ARMOR).apply { itemMeta = itemMeta?.apply { displayName(mm.parse("<#FF5500><b>Escopeta Recortada (50% Muerte)</b></#FF5500>")) } }
                victima.inventory.setItem(4, arma)
                plugin.server.broadcast(mm.parse("<newline><red><b>¡El revólver se encasquilló!</b></red> <white>Te han dado una escopeta... tienes un <red>50%</red> de probabilidad de volar en pedazos.</white><newline>"))
            }
            chance in 16..30 -> { // 15% RECARGA MANUAL
                eventoActual = EventoRuleta.RECARGA_MANUAL
                val arma = ItemStack(Material.IRON_HORSE_ARMOR).apply { itemMeta = itemMeta?.apply { displayName(mm.parse("<#AAAAAA><b>Revólver Vacío</b></#AAAAAA>")) } }
                val bala = ItemStack(Material.GUNPOWDER).apply { itemMeta = itemMeta?.apply { displayName(mm.parse("<yellow><b>Bala Solitaria</b></yellow>")) } }
                victima.inventory.setItem(3, bala)
                victima.inventory.setItem(4, arma)
                plugin.server.broadcast(mm.parse("<newline><yellow><b>¡RECARGA MANUAL!</b></yellow> <white>¡Debes hacer <b>Clic Izquierdo</b> (Pegar al aire) para recargar el arma antes de disparar o morirás de los nervios!</white><newline>"))
            }
            else -> { // 70% NORMAL
                eventoActual = EventoRuleta.NORMAL
                val arma = ItemStack(Material.IRON_HORSE_ARMOR).apply { itemMeta = itemMeta?.apply { displayName(mm.parse("<#FF3131><b>Revólver del Destino</b></#FF3131>")) } }
                victima.inventory.setItem(4, arma)
            }
        }

        victima.inventory.heldItemSlot = 4

        val decisionTitle = Title.title(
            mm.parse("<red>¡Tienes el arma!</red>"),
            mm.parse("<white>Tienes <red><b>6 SEGUNDOS</b></red> para disparar o morir.</white>"),
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(6), Duration.ofMillis(200))
        )
        victima.showTitle(decisionTitle)

        esperandoDecision = true
        iniciarRelojDecision(victima)
    }

    private fun iniciarRelojDecision(victima: Player) {
        var tickTimer = 6 // ¡Solo 6 segundos!
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || !esperandoDecision) { task.cancel(); return@runAtFixedRate }

            if (tickTimer <= 0) {
                task.cancel()
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    plugin.server.broadcast(plugin.messageManager.parse("<red><b>¡TIEMPO AGOTADO!</b></red> <white>${victima.name} no se atrevió a disparar y su cabeza explotó por la presión.</white>"))
                    ejecutarMuerte(victima)
                }
                return@runAtFixedRate
            }

            players.forEach { p ->
                p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
                plugin.server.asyncScheduler.runDelayed(plugin, { _ -> p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f) }, 200, TimeUnit.MILLISECONDS)
                p.sendActionBar(plugin.messageManager.parse("<red><b>00:0$tickTimer</b></red>"))
            }
            tickTimer--
        }, 0, 1, TimeUnit.SECONDS)
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        if (!isRunning || plugin.eventManager.currentGame != this || !esperandoDecision) return
        if (p != ultimaVictima) return

        if (e.action == Action.LEFT_CLICK_AIR || e.action == Action.LEFT_CLICK_BLOCK) {
            if (eventoActual == EventoRuleta.RECARGA_MANUAL && !balaCargadaManualmente) {
                balaCargadaManualmente = true
                p.inventory.remove(Material.GUNPOWDER)
                p.playSound(p.location, Sound.BLOCK_IRON_DOOR_OPEN, 1f, 2f)
                p.sendMessage(plugin.messageManager.parse("<green>Has cargado la bala... ¡Ahora dispara (Click Derecho)!</green>"))
                val arma = p.inventory.getItem(4)
                val meta = arma?.itemMeta
                meta?.displayName(plugin.messageManager.parse("<#FF3131><b>Revólver Cargado</b></#FF3131>"))
                arma?.itemMeta = meta
            }
        }

        if (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK) {
            if (e.item?.type == Material.IRON_HORSE_ARMOR) {
                e.isCancelled = true
                procesarEleccion(p, true)
            }
        }
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        val p = e.player
        if (!isRunning || plugin.eventManager.currentGame != this) return

        if (p == ultimaVictima && esperandoDecision) {
            e.isCancelled = true
            if (e.itemDrop.itemStack.type == Material.IRON_HORSE_ARMOR) {
                procesarEleccion(p, false)
            }
        }
    }

    private fun procesarEleccion(jugador: Player, decidioDisparar: Boolean) {
        if (!esperandoDecision) return
        esperandoDecision = false
        jugador.inventory.clear()
        jugador.resetTitle()

        val currentSlimeWorld = jugador.world

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

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> forzarAsientos = true }, 15L)

        if (decidioDisparar) {
            var muere = false

            when (eventoActual) {
                EventoRuleta.ESCOPETA -> {
                    muere = Math.random() < 0.50
                }
                EventoRuleta.RECARGA_MANUAL -> {
                    if (!balaCargadaManualmente) {
                        plugin.server.broadcast(plugin.messageManager.parse("<red><b>¡IDIOTA!</b></red> <white>${jugador.name} intentó disparar un arma descargada. El francotirador lo aniquiló por tonto.</white>"))
                        ejecutarMuerte(jugador)
                        return
                    } else {
                        muere = Math.random() < (1.0 / 6.0)
                    }
                }
                EventoRuleta.NORMAL -> {
                    val hayBala = cilindro[posicionCilindro]
                    if (hayBala) {
                        muere = true
                        recargarRevolverGlobal()
                    } else {
                        posicionCilindro++
                        if (posicionCilindro >= 6) recargarRevolverGlobal()
                    }
                }
            }

            if (muere) {
                plugin.server.broadcast(plugin.messageManager.parse(plugin.languageManager.get("ruletarusa.broadcast.died"), Placeholder.parsed("player", jugador.name)))
                ejecutarMuerte(jugador)
            } else {
                jugador.world.playSound(jugador.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                jugador.world.playSound(jugador.location, Sound.ENTITY_PLAYER_BREATH, 1f, 1f)
                jugador.world.spawnParticle(Particle.SPLASH, jugador.eyeLocation, 30, 0.3, 0.5, 0.3, 0.0)

                plugin.server.broadcast(plugin.messageManager.parse(plugin.languageManager.get("ruletarusa.broadcast.saved"), Placeholder.parsed("player", jugador.name)))
                plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> girarRuleta() }, 40L)
            }
        } else {
            if (eventoActual == EventoRuleta.ESCOPETA) {
                jugador.sendMessage(plugin.messageManager.parse("<red>¡No puedes pasar la Escopeta! Se te disparó en las manos.</red>"))
                ejecutarMuerte(jugador)
                return
            }

            plugin.server.broadcast(plugin.messageManager.parse("<yellow><b>${jugador.name}</b></yellow> <white>fue un cobarde y pasó el arma... La ruleta gira.</white>"))
            jugador.world.playSound(jugador.location, Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 1f)

            posicionCilindro++
            if (posicionCilindro >= 6) recargarRevolverGlobal()

            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> girarRuleta() }, 40L)
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
        val rawWin = plugin.languageManager.get("ruletarusa.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
        winner.world.spawnParticle(Particle.FIREWORK, winner.location, 100)
        winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        plugin.puntajeManager.addPoints(winner, 10, "¡Sobreviviente de Ruleta!")
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
