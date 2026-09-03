package pumpkin.eventos.games.simondice

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Location
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
    val failedMathPlayers = mutableSetOf<UUID>()

    // Variable para la lista de retos
    var activeChallenges = mutableListOf<String>()

    var actionText = "Esperando orden..."

    var targetPhrase = ""
    var targetMathResult = 0.0
    var targetMaterial: Material? = null

    private var bossBar: BossBar? = null
    private var autoTask: ScheduledTask? = null
    private var challengeTask: ScheduledTask? = null

    private var ultimoReto = ""
    private var phaseWaitTimer = 10
    private var isWaitingPhase = true

    var displayTime = 10
    var displayOrden = "Preparando..."

    private val retosSimples = listOf("SALTAR", "AGACHARSE", "QUIETO", "MATES", "CAMINAR", "GOLPEAR", "CONSIGUE", "PAPACALIENTE")

    private val combosPermitidos = listOf(
        listOf("SALTAR", "CAMINAR"),
        listOf("AGACHARSE", "CAMINAR"),
        listOf("SALTAR", "GOLPEAR"),
        listOf("CAMINAR", "GOLPEAR"),
        listOf("AGACHARSE", "GOLPEAR")
    )

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
        activeChallenges.clear()
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

        bossBar = BossBar.bossBar(plugin.languageManager.component("simon_game.bossbar",
            Placeholder.parsed("order", displayOrden), Placeholder.unparsed("alive", getJugadoresVivosReales().toString())), 1f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_6)

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            players.forEach { p ->
                p.inventory.clear()
                bossBar?.let { bar -> p.showBossBar(bar) }
            }

            if (mode == SimonMode.MANUAL) {
                streamer?.let { s ->
                    if (!s.isOnline) return@let

                    s.gameMode = GameMode.CREATIVE
                    s.isFlying = true

                    s.inventory.clear()
                    val star = ItemStack(Material.NETHER_STAR)
                    val meta = star.itemMeta
                    meta.displayName(plugin.languageManager.component("simon_game.item.command_star"))
                    star.itemMeta = meta
                    s.inventory.addItem(star)
                    plugin.languageManager.send(s, "simon_game.message.command_kit")
                    s.playSound(s.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                }
            } else {
                startAutoLoop()
            }
        }, 20L)
    }

    private fun getJugadoresValidos(): List<Player> {
        return players.filter { it != streamer }
    }

    private fun getJugadoresVivosReales(): Int {
        return getJugadoresValidos().size
    }

    private fun startAutoLoop() {
        autoTask = runAtFixedRate( { task ->

            if (getJugadoresVivosReales() <= 1) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                task.cancel()
                return@runAtFixedRate
            }

            if (activeChallenges.isNotEmpty()) return@runAtFixedRate

            if (isWaitingPhase) {
                phaseWaitTimer--
                displayTime = phaseWaitTimer
                displayOrden = "Pensando orden..."
                updateBossBar(BossBar.Color.WHITE)

                if (phaseWaitTimer in 1..5) {
                    val color = if (phaseWaitTimer <= 3) "<red>" else "<yellow>"
                    val title = Title.title(
                        plugin.languageManager.component("simon_game.title.waiting_main", Placeholder.parsed("countdown", "$color<bold>$phaseWaitTimer</bold>")),
                        plugin.languageManager.component("simon_game.title.waiting_subtitle")
                    )
                    getJugadoresValidos().forEach {
                        it.showTitle(title)
                        it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f)
                    }
                }

                if (phaseWaitTimer <= 0) {
                    isWaitingPhase = false

                    // --- LÓGICA DE COMBOS AUTOMÁTICOS ---
                    // 30% de probabilidad de que salga un combo de 2 acciones
                    val esCombo = Math.random() < 0.30

                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        if (esCombo) {
                            val combo = combosPermitidos.random()
                            val texto = "${combo[0]} y ${combo[1]}"
                            startChallenge(combo, "🔥 <#BF00FF>COMBO: $texto</#BF00FF>", 15)
                        } else {
                            // Reto Simple
                            var elegido = retosSimples.random()
                            while (elegido == ultimoReto) { elegido = retosSimples.random() }
                            ultimoReto = elegido

                            when (elegido) {
                                "SALTAR" -> startChallenge(listOf("SALTAR"), "▲ <green>¡SALTEN!</green>", 10)
                                "AGACHARSE" -> startChallenge(listOf("AGACHARSE"), "▼ <yellow>¡AGÁCHENSE!</yellow>", 10)
                                "CAMINAR" -> startChallenge(listOf("CAMINAR"), "⇄ <aqua>¡CAMINEN!</aqua>", 10)
                                "QUIETO" -> startChallenge(listOf("QUIETO"), "⏹ <red>¡QUIETOS!</red>", 10)
                                "GOLPEAR" -> startChallenge(listOf("GOLPEAR"), "⚔ <gold>¡GOLPEEN A ALGUIEN!</gold>", 10)
                                "MATES" -> {
                                    failedMathPlayers.clear()
                                    val num1 = (1..20).random(); val num2 = (1..20).random()
                                    targetPhrase = "$num1+$num2"; targetMathResult = (num1 + num2).toDouble()
                                    startChallenge(listOf("MATES"), "⌗ CUÁNTO ES: <white>$targetPhrase</white>", 15)
                                }
                                "CONSIGUE" -> lanzarRetoConsigue()
                                "PAPACALIENTE" -> startChallenge(listOf("PAPACALIENTE"), "🥔 <gold>LA PAPA CALIENTE</gold>", 1)
                            }
                        }
                    }
                }
            }
        }, 20L, 20L)
    }

    fun lanzarRetoConsigue() {
        hitCounter.clear()
        val itemsPosibles = listOf(Material.APPLE, Material.DIAMOND, Material.BONE, Material.EMERALD, Material.FEATHER)
        targetMaterial = itemsPosibles.random()

        val cantidad = maxOf(1, getJugadoresVivosReales() - 1)
        val arena = currentArena ?: return
        val world = arena.centerLocation?.world ?: players.firstOrNull()?.world ?: return

        var spawneados = 0

        fun intentarSpawnear() {
            if (spawneados >= cantidad || !isRunning) return

            val targetPlayer = getJugadoresValidos().randomOrNull() ?: return
            val centerLoc = targetPlayer.location

            val offsetX = (Math.random() * 24 - 12)
            val offsetZ = (Math.random() * 24 - 12)
            val searchLoc = centerLoc.clone().add(offsetX, 0.0, offsetZ)

            plugin.server.regionScheduler.run(plugin, searchLoc) { _ ->
                var highestY = searchLoc.blockY
                var foundSolid = false

                for (y in (searchLoc.blockY + 15) downTo (searchLoc.blockY - 20)) {
                    val b = world.getBlockAt(searchLoc.blockX, y, searchLoc.blockZ)
                    if (b.type.isSolid) {
                        highestY = y
                        foundSolid = true
                        break
                    }
                }

                if (foundSolid) {
                    val finalLoc = Location(world, searchLoc.x, highestY + 1.2, searchLoc.z)
                    val drop = world.dropItem(finalLoc, ItemStack(targetMaterial!!))
                    drop.velocity = Vector(0.0, -0.1, 0.0)
                    drop.setGlowing(true)

                    world.spawnParticle(Particle.HAPPY_VILLAGER, finalLoc, 15, 0.5, 0.5, 0.5)
                    world.playSound(finalLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f)

                    spawneados++
                }
                intentarSpawnear()
            }
        }

        intentarSpawnear()

        val nombreTraducido = targetMaterial!!.name.replace("_", " ").lowercase()
        startChallenge(listOf("CONSIGUE"), "🎁 <aqua>¡CONSIGUE Y SOSTÉN: $nombreTraducido!</aqua>", 30)
    }

    fun startChallenge(retos: List<String>, msg: String, tiempo: Int) {
        savedPlayers.clear()
        activeChallenges = retos.toMutableList()
        displayOrden = msg
        displayTime = tiempo

        if (activeChallenges.contains("QUIETO")) getJugadoresValidos().forEach { savedPlayers.add(it.uniqueId) }

        if (activeChallenges.contains("PAPACALIENTE")) {
            plugin.languageManager.broadcast("simon_game.message.hot_potato_start")
            plugin.papaCalienteGame.iniciar(this, 30)
            activeChallenges = mutableListOf("MINIJUEGO")
            return
        }

        plugin.languageManager.broadcast("simon_game.message.order", Placeholder.parsed("order", msg))
        updateBossBar(BossBar.Color.GREEN)

        getJugadoresValidos().forEach { p ->
            p.showTitle(Title.title(plugin.languageManager.component("simon_game.title.new_order_main"), plugin.messageManager.parse(msg)))
            p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f)
        }

        var restante = tiempo

        challengeTask = runAtFixedRate( { task ->
            if (!isRunning || activeChallenges.isEmpty() || activeChallenges.contains("MINIJUEGO")) {
                task.cancel(); return@runAtFixedRate
            }

            restante--
            displayTime = restante
            updateBossBar(BossBar.Color.GREEN)

            if (activeChallenges.contains("CONSIGUE")) {
                getJugadoresValidos().forEach { p ->
                    if (!savedPlayers.contains(p.uniqueId)) {
                        // REVISIÓN INTELIGENTE: Escaneamos todo el inventario, no solo la mano
                        if (p.inventory.contains(targetMaterial!!)) {
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
                    plugin.languageManager.component("simon_game.title.follow_main", Placeholder.parsed("countdown", "$color<bold>$restante</bold>")),
                    plugin.languageManager.component("simon_game.title.follow_subtitle")
                )
                getJugadoresValidos().forEach { it.showTitle(titleAviso); it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f) }
            }

        }, 20L, 20L)
    }

    private fun endChallenge() {
        displayOrden = "<red>¡TIEMPO AGOTADO!</red>"
        displayTime = 0
        updateBossBar(BossBar.Color.RED)

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            if (activeChallenges.contains("AGACHARSE")) {
                getJugadoresValidos().forEach { p ->
                    if (p.isSneaking && isPlayerCompletingOtherCombos(p)) savedPlayers.add(p.uniqueId)
                    else savedPlayers.remove(p.uniqueId)
                }
            }

            getJugadoresValidos().forEach { p ->
                p.showTitle(Title.title(plugin.languageManager.component("simon_game.title.timeout_main"), plugin.languageManager.component("simon_game.title.timeout_subtitle")))
                p.playSound(p.location, Sound.BLOCK_BELL_USE, 1f, 0.5f)
            }

            val perdedores = getJugadoresValidos().filter { !savedPlayers.contains(it.uniqueId) || failedMathPlayers.contains(it.uniqueId) }

            perdedores.forEach { aplicarCuello(it) }

            if (activeChallenges.contains("CONSIGUE")) {
                val center = currentArena?.centerLocation
                center?.world?.getNearbyEntities(center, 40.0, 40.0, 40.0)?.filterIsInstance<Item>()?.forEach { it.remove() }
                getJugadoresValidos().forEach { it.inventory.clear() }
            }

            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                activeChallenges.clear()

                if (mode == SimonMode.MANUAL) {
                    displayOrden = "Esperando al Host..."
                    isWaitingPhase = false
                } else {
                    displayOrden = "Pensando orden..."
                    phaseWaitTimer = 10
                    isWaitingPhase = true
                }

                updateBossBar(BossBar.Color.WHITE)
                getJugadoresValidos().forEach { it.isGlowing = false }
            }, 60L)
        }
    }

    fun markSaved(p: Player) {
        if (p == streamer) return
        if (!savedPlayers.contains(p.uniqueId)) {
            savedPlayers.add(p.uniqueId)
            plugin.languageManager.send(p, "simon_game.message.saved")
            p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            p.isGlowing = true
            p.world.spawnParticle(Particle.HAPPY_VILLAGER, p.location.add(0.0, 1.5, 0.0), 15, 0.4, 0.4, 0.4, 0.1)
        }
    }

    private fun isPlayerCompletingOtherCombos(p: Player): Boolean {
        if (activeChallenges.contains("AGACHARSE") && !p.isSneaking) return false

        if (activeChallenges.contains("SALTAR") && p.isOnGround) {
            return false
        }

        return true
    }
    fun removeSaved(p: Player) {
        if (p == streamer) return
        if (savedPlayers.contains(p.uniqueId)) {
            savedPlayers.remove(p.uniqueId)
            p.isGlowing = false
            plugin.languageManager.send(p, "simon_game.message.stopped_obeying")
            p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
        }
    }

    fun aplicarCuello(p: Player) {
        if (p == streamer) return
        eliminate(p)
        p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 1, false, false))
        p.isGlowing = true
        p.showTitle(Title.title(
            plugin.languageManager.component("simon_game.title.eliminated_main"),
            plugin.languageManager.component("simon_game.title.eliminated_subtitle"),
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
            plugin.languageManager.broadcast("simon_game.message.eliminated",
                Placeholder.unparsed("player", p.name), Placeholder.unparsed("executioner", verdugo))
        }, 22L)
    }

    private fun updateBossBar(color: BossBar.Color) {
        val titleText = plugin.languageManager.component("simon_game.bossbar",
            Placeholder.parsed("order", displayOrden), Placeholder.unparsed("alive", getJugadoresVivosReales().toString()))
        bossBar?.name(titleText)
        bossBar?.color(color)
    }

    override fun checkWinner() {
        val winner = getJugadoresValidos().firstOrNull() ?: return
        plugin.languageManager.broadcast("simon_game.message.winner", Placeholder.unparsed("player", winner.name))
        winner.world.spawnParticle(Particle.FIREWORK, winner.location, 100)
        winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        awardVictory(winner, 10)
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

        val allPlayers = allParticipants()
        allPlayers.forEach { p ->
            p.getAttribute(Attribute.SCALE)?.baseValue = 1.0
        }

        currentArena?.centerLocation?.world?.let { w ->
            currentArena?.centerLocation?.let { loc ->
                w.getNearbyEntities(loc, 40.0, 40.0, 40.0).filterIsInstance<Item>().forEach { it.remove() }
            }
        }
        returnToLobby(allPlayers)
    }
}
