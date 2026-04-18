package pumpkin.eventos.games.simondice

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

enum class SimonMode { AUTOMATICO, MANUAL }

class SimonDice(plugin: PumpkinEventos) : EventGame(plugin, "simondice", "<green>Simón Dice</green>") {

    var mode = SimonMode.AUTOMATICO
    var streamer: Player? = null

    val savedPlayers = mutableSetOf<UUID>()
    val failedMathPlayers = mutableSetOf<UUID>() // NUEVO: Jugadores que respondieron mal

    var activeChallenge: String = "NINGUNO"
    var actionText = "Esperando orden..."

    var targetPhrase = ""
    var targetMathResult = 0.0
    var targetMaterial: Material? = null // Para el reto CONSIGUE

    private var bossBar: BossBar? = null
    private var autoTask: ScheduledTask? = null
    private var challengeTask: ScheduledTask? = null

    private var ultimoReto = ""
    private var phaseWaitTimer = 10
    private var isWaitingPhase = true

    private var displayTime = 10
    private var displayOrden = "Preparando..."

    // AÑADIDO: PAPACALIENTE A LA RULETA AUTOMÁTICA
    private val retosDisponibles = listOf("SALTAR", "AGACHARSE", "QUIETO", "MATES", "CAMINAR", "GOLPEAR", "CONSIGUE", "PAPACALIENTE")

    // Rastreador de golpes para el reto de Conseguir
    val hitCounter = mutableMapOf<UUID, Int>()

    override fun getExtraPlaceholders(): Map<String, String> {
        return mapOf(
            "%orden%" to displayOrden,
            "%time%" to displayTime.toString()
        )
    }

    override fun onStart() {
        savedPlayers.clear()
        failedMathPlayers.clear()
        hitCounter.clear()
        activeChallenge = "NINGUNO"
        ultimoReto = ""

        if (mode == SimonMode.MANUAL && streamer == null) {
            streamer = players.firstOrNull()
        }

        if (mode == SimonMode.MANUAL) {
            displayOrden = "Esperando al Host..."
            displayTime = 0
            isWaitingPhase = false
        } else {
            displayOrden = "Pensando orden..."
            displayTime = 10
            isWaitingPhase = true
            phaseWaitTimer = 10
        }

        bossBar = BossBar.bossBar(plugin.messageManager.parse("⚡ <yellow>Simón Dice</yellow> <dark_gray>|</dark_gray> <white>$displayOrden"), 1f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_6)

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            players.forEach { p ->
                p.inventory.clear()
                bossBar?.let { bar -> p.showBossBar(bar) }
            }

            if (mode == SimonMode.MANUAL) {
                streamer?.let { s ->
                    if (!s.isOnline) return@let
                    s.inventory.clear()
                    val star = ItemStack(Material.NETHER_STAR)
                    val meta = star.itemMeta
                    meta.displayName(plugin.messageManager.parse("<gold><b>⭐ ESTRELLA DE MANDO ⭐</b></gold>"))
                    star.itemMeta = meta
                    s.inventory.addItem(star)
                    s.sendMessage(plugin.messageManager.parse("<green>⭐ ¡Kit de mando entregado! Clic derecho para abrir el menú de órdenes.</green>"))
                    s.playSound(s.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                }
            } else {
                startAutoLoop()
            }
        }, 20L)
    }

    private fun startAutoLoop() {
        autoTask = plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            if (players.size <= 1) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                task.cancel()
                return@runAtFixedRate
            }

            if (activeChallenge != "NINGUNO") return@runAtFixedRate

            if (isWaitingPhase) {
                phaseWaitTimer--
                displayTime = phaseWaitTimer
                displayOrden = "Pensando orden..."
                updateBossBar(BossBar.Color.WHITE)

                if (phaseWaitTimer in 1..5) {
                    val color = if (phaseWaitTimer <= 3) "<red>" else "<yellow>"
                    val title = Title.title(
                        plugin.messageManager.parse("$color<bold>$phaseWaitTimer</bold>"),
                        plugin.messageManager.parse("<white>Simón está por dar la orden...")
                    )
                    players.forEach {
                        it.showTitle(title)
                        it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f)
                    }
                }

                if (phaseWaitTimer <= 0) {
                    isWaitingPhase = false

                    var elegido = retosDisponibles.random()
                    while (elegido == ultimoReto) { elegido = retosDisponibles.random() }
                    ultimoReto = elegido

                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        when (elegido) {
                            "SALTAR" -> startChallenge("SALTAR", "▲ <green>¡SALTEN!</green>", 10)
                            "AGACHARSE" -> startChallenge("AGACHARSE", "▼ <yellow>¡AGÁCHENSE!</yellow>", 10)
                            "CAMINAR" -> startChallenge("CAMINAR", "⇄ <aqua>¡CAMINEN!</aqua>", 10)
                            "QUIETO" -> startChallenge("QUIETO", "⏹ <red>¡QUIETOS!</red>", 10)
                            "GOLPEAR" -> startChallenge("GOLPEAR", "⚔ <gold>¡GOLPEEN A ALGUIEN!</gold>", 10)
                            "MATES" -> {
                                failedMathPlayers.clear() // Limpiamos los que fallaron mates la vez anterior
                                val num1 = (1..20).random(); val num2 = (1..20).random()
                                targetPhrase = "$num1+$num2"; targetMathResult = (num1 + num2).toDouble()
                                startChallenge("MATES", "⌗ CUÁNTO ES: <white>$targetPhrase</white>", 15)
                            }
                            "CONSIGUE" -> lanzarRetoConsigue()
                            "PAPACALIENTE" -> startChallenge("PAPACALIENTE", "🥔 <gold>LA PAPA CALIENTE</gold>", 1)
                        }
                    }
                }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    fun lanzarRetoConsigue() {
        hitCounter.clear() // Reiniciamos el contador de golpes
        val itemsPosibles = listOf(Material.APPLE, Material.DIAMOND, Material.BONE, Material.EMERALD, Material.FEATHER)
        targetMaterial = itemsPosibles.random()

        val cantidad = maxOf(1, players.size - 1)
        val arena = currentArena ?: return
        val center = arena.centerLocation ?: return
        val world = center.world

        plugin.server.regionScheduler.run(plugin, center) { _ ->
            for (i in 1..cantidad) {
                val angle = Math.random() * Math.PI * 2
                val radius = Math.random() * 10.0
                val loc = center.clone().add(cos(angle) * radius, 10.0, sin(angle) * radius)

                val drop = world.dropItem(loc, ItemStack(targetMaterial!!))
                drop.velocity = Vector(0.0, -0.2, 0.0)
                drop.setGlowing(true)
            }
        }

        val nombreTraducido = targetMaterial!!.name.replace("_", " ").lowercase()
        startChallenge("CONSIGUE", "🎁 <aqua>¡CONSIGUE Y SOSTÉN: $nombreTraducido!</aqua>", 15)
    }

    fun startChallenge(id: String, msg: String, tiempo: Int) {
        savedPlayers.clear()
        activeChallenge = id
        displayOrden = msg
        displayTime = tiempo

        if (id == "QUIETO") players.forEach { savedPlayers.add(it.uniqueId) }

        if (id == "PAPACALIENTE") {
            plugin.server.broadcast(plugin.messageManager.parse("<newline><#FF4500><b>¡MINIJUEGO DE SIMÓN: LA PAPA CALIENTE!</b></#FF4500><newline>"))
            plugin.papaCalienteGame.iniciar(this,120)
            activeChallenge = "MINIJUEGO" // Ponemos al bot en pausa
            return
        }

        plugin.server.broadcast(plugin.messageManager.parse("<newline><yellow><b>¡SIMÓN DICE!</b></yellow> <white>» $msg<newline>"))
        updateBossBar(BossBar.Color.GREEN)

        players.forEach { p ->
            p.showTitle(Title.title(plugin.messageManager.parse("<#39FF14><b>¡NUEVA ORDEN!</b></#39FF14>"), plugin.messageManager.parse(msg)))
            p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f)
        }

        var restante = tiempo

        challengeTask = plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || activeChallenge == "NINGUNO" || activeChallenge == "MINIJUEGO") {
                task.cancel(); return@runAtFixedRate
            }

            restante--
            displayTime = restante
            updateBossBar(BossBar.Color.GREEN)

            if (activeChallenge == "CONSIGUE") {
                players.forEach { p ->
                    if (!savedPlayers.contains(p.uniqueId)) {
                        if (p.inventory.itemInMainHand.type == targetMaterial) {
                            plugin.server.globalRegionScheduler.run(plugin) { _ -> markSaved(p) }
                        }
                    }
                }
            }

            if (restante <= 0) {
                task.cancel()
                endChallenge()
                return@runAtFixedRate
            }

            if (restante <= 5) {
                val color = if (restante <= 3) "<red>" else "<yellow>"
                val titleAviso = Title.title(
                    plugin.messageManager.parse("$color<bold>$restante</bold>"),
                    plugin.messageManager.parse("<white>¡Cumple la orden!</white>")
                )
                players.forEach { it.showTitle(titleAviso); it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f) }
            }

        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun endChallenge() {
        displayOrden = "<red>¡TIEMPO AGOTADO!</red>"
        displayTime = 0
        updateBossBar(BossBar.Color.RED)

        players.forEach { p ->
            p.showTitle(Title.title(plugin.messageManager.parse("<red><b>¡TIEMPO AGOTADO!</b></red>"), plugin.messageManager.parse("<white>La guadaña está pasando...</white>")))
            p.playSound(p.location, Sound.BLOCK_BELL_USE, 1f, 0.5f)
        }

        // Determinar perdedores: Los que no se salvaron, o los que fallaron las matemáticas
        val perdedores = players.filter { !savedPlayers.contains(it.uniqueId) || failedMathPlayers.contains(it.uniqueId) }

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            perdedores.forEach { aplicarCuello(it) }

            if (activeChallenge == "CONSIGUE") {
                val center = currentArena?.centerLocation
                center?.world?.getNearbyEntities(center, 30.0, 30.0, 30.0)?.filterIsInstance<Item>()?.forEach { it.remove() }
                players.forEach { it.inventory.clear() }
            }

            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                activeChallenge = "NINGUNO"

                if (mode == SimonMode.MANUAL) {
                    displayOrden = "Esperando al Host..."
                    isWaitingPhase = false
                } else {
                    displayOrden = "Pensando orden..."
                    phaseWaitTimer = 10
                    isWaitingPhase = true
                }

                updateBossBar(BossBar.Color.WHITE)
                players.forEach { it.isGlowing = false }
            }, 60L)
        }
    }

    fun markSaved(p: Player) {
        if (!savedPlayers.contains(p.uniqueId)) {
            savedPlayers.add(p.uniqueId)
            p.sendMessage(plugin.messageManager.parse("<green>✔ <b>¡SALVADO!</b></green> <white>Misión completada.</white>"))
            p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            p.isGlowing = true
            p.world.spawnParticle(Particle.HAPPY_VILLAGER, p.location.add(0.0, 1.5, 0.0), 15, 0.4, 0.4, 0.4, 0.1)
        }
    }

    fun aplicarCuello(p: Player) {
        eliminate(p)
        p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 1, false, false))
        p.isGlowing = true
        p.showTitle(Title.title(
            plugin.messageManager.parse("<red><bold>☠ ¡ELIMINADO! ☠</bold></red>"),
            plugin.messageManager.parse("<dark_red>Tu alma ha sido reclamada...</dark_red>"),
            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2000), Duration.ofMillis(500))
        ))

        p.getAttribute(Attribute.SCALE)?.baseValue = 0.35
        p.world.playSound(p.location, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.5f)
        p.world.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.5f)
        p.velocity = Vector(0.0, 4.0, 0.0)

        var i = 0
        plugin.server.regionScheduler.runAtFixedRate(plugin, p.location, { task ->
            if (i >= 22 || !p.isOnline || p.isDead) { task.cancel(); return@runAtFixedRate }
            val l = p.location
            l.world.spawnParticle(Particle.SOUL, l, 5, 0.2, 0.1, 0.2, 0.02)
            l.world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, l, 10, 0.1, 0.1, 0.1, 0.05)
            l.world.spawnParticle(Particle.DUST, l, 8, 0.2, 0.2, 0.2, 0.0, Particle.DustOptions(Color.fromRGB(255, 20, 147), 1.5f))
            i++
        }, 1L, 1L)

        plugin.server.regionScheduler.runDelayed(plugin, p.location, { _ ->
            if (!p.isOnline) return@runDelayed
            val loc = p.location
            loc.world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3)
            loc.world.spawnParticle(Particle.SONIC_BOOM, loc, 1)
            loc.world.spawnParticle(Particle.GUST_EMITTER_LARGE, loc, 5)

            p.world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.2f, 1f)
            p.world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f)

            p.health = 0.0
            p.isGlowing = false
            p.getAttribute(Attribute.SCALE)?.baseValue = 1.0

            val verdugo = if (mode == SimonMode.MANUAL) streamer?.name ?: "Simón" else "Simón"
            plugin.server.broadcast(plugin.messageManager.parse("<red>☠</red> <yellow>${p.name}</yellow> <white>fue pulverizado por <green>$verdugo.</green>"))
        }, 22L)
    }

    private fun updateBossBar(color: BossBar.Color) {
        val titleText = plugin.messageManager.parse("⚡ <yellow>Simón Dice</yellow> <dark_gray>|</dark_gray> <white>$displayOrden <dark_gray>|</dark_gray> <green>⭐ Vivos: ${players.size}</green>")
        bossBar?.name(titleText)
        bossBar?.color(color)
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return
        plugin.server.broadcast(plugin.messageManager.parse("<newline><yellow><b>SIMÓN DICE</b></yellow> <white>» <color:#67FF00>${winner.name}</color> es el ganador definitivo!<newline>"))
        winner.world.spawnParticle(Particle.FIREWORK, winner.location, 100)
        winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        plugin.puntajeManager.addPoints(winner, 10, "¡Victoria conseguida!")
        stop()
    }

    override fun onStop() {
        autoTask?.cancel()
        challengeTask?.cancel()

        if (bossBar != null) {
            players.forEach { p -> p.hideBossBar(bossBar!!) }
            spectators.forEach { p -> p.hideBossBar(bossBar!!) }
            bossBar = null
        }

        val allPlayers = players + spectators
        allPlayers.forEach { p ->
            p.getAttribute(Attribute.SCALE)?.baseValue = 1.0
        }

        currentArena?.centerLocation?.world?.let { w ->
            currentArena?.centerLocation?.let { loc ->
                w.getNearbyEntities(loc, 40.0, 40.0, 40.0).filterIsInstance<Item>().forEach { it.remove() }
            }
        }

        plugin.eventManager.currentGame = null
        var lobby = plugin.arenaManager.mainLobby

        if (lobby == null || lobby.world == null) {
            lobby = plugin.server.worlds[0].spawnLocation
        }

        allPlayers.forEach { p ->
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = org.bukkit.GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}
