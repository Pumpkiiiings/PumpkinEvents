package pumpkin.eventos.games.tntgames.entities

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/**
 * [PUMPKIN EVENTOS] - MODO TROLL SUPREMO
 * GEOFFREY 3.0: EVIL SCARY EDITION.
 * Tamaño MASIVO (x2), Manos hacia adelante, Sin collar, Terror puro.
 * TRACKING EN TIEMPO REAL: Cambia al objetivo más cercano dinámicamente (TODOS SON PRESA).
 */
class GeoffreyEXE(private val plugin: PumpkinEventos, private val game: EventGame) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = true
    private var currentTarget: Player? = null
    private var lastVictimUUID: UUID? = null

    private val teamWhite = "GeoffreyGlow"
    private val teamRed = "GeoffreyAngry"
    private var consecutiveMisses = 0

    // Constantes para la máquina de estados
    private enum class State { BUSCANDO, SALTANDO, MISIL, AEREO, FURIA }
    private var currentState = State.BUSCANDO

    fun spawn(startLoc: Location) {
        // 🔥 FIX: Asegurar que la Location tenga un mundo válido antes de intentar spawnear.
        val targetWorld = startLoc.world ?: Bukkit.getWorlds().firstOrNull()
        if (targetWorld == null) {
            plugin.server.consoleSender.sendMessage("§c[!] Error: No hay mundos cargados para spawnear a Geoffrey.")
            return
        }

        val safeLoc = startLoc.clone()
        safeLoc.world = targetWorld

        // 🔥 FIX: Usar regionScheduler en lugar de globalRegionScheduler para crear entidades (Requisito de Folia/Paper)
        plugin.server.regionScheduler.run(plugin, safeLoc) { _ ->
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                if (scoreboard.getTeam(teamWhite) == null) scoreboard.registerNewTeam(teamWhite).color(NamedTextColor.WHITE)
                if (scoreboard.getTeam(teamRed) == null) scoreboard.registerNewTeam(teamRed).color(NamedTextColor.RED)

                // =========================================================
                // 🔥 DISEÑO "EVIL SCARY" (ESCALA x2, MÁS ATERRADOR)
                // =========================================================

                val core1 = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(5.2f, 5.2f, 5.2f), Vector3f(-2.6f, 0f, -2.6f))
                val core2 = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(5.6f, 4.4f, 4.8f), Vector3f(-2.8f, 0.4f, -2.4f))
                val core3 = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(4.4f, 5.6f, 4.8f), Vector3f(-2.2f, -0.2f, -2.4f))

                val leftEye = createPart(safeLoc, Material.WHITE_CONCRETE, Vector3f(1.0f, 0.3f, 0.2f), Vector3f(-1.6f, 3.6f, 2.7f), rotZ = 0.2f)
                val rightEye = createPart(safeLoc, Material.WHITE_CONCRETE, Vector3f(1.0f, 0.3f, 0.2f), Vector3f(0.6f, 3.8f, 2.7f), rotZ = -0.2f)

                val mouthCenter = createPart(safeLoc, Material.RED_CONCRETE, Vector3f(2.8f, 0.4f, 0.2f), Vector3f(-1.4f, 1.6f, 2.7f))
                val mouthL = createPart(safeLoc, Material.RED_CONCRETE, Vector3f(0.4f, 0.8f, 0.2f), Vector3f(-1.6f, 1.6f, 2.7f), rotZ = 0.3f)
                val mouthR = createPart(safeLoc, Material.RED_CONCRETE, Vector3f(0.4f, 0.8f, 0.2f), Vector3f(1.2f, 1.8f, 2.7f), rotZ = -0.3f)
                val teeth = createPart(safeLoc, Material.WHITE_CONCRETE, Vector3f(2.0f, 0.16f, 0.22f), Vector3f(-1.0f, 1.7f, 2.72f))

                val palmL = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(1.6f, 1.6f, 0.8f), Vector3f(-6.0f, 1.2f, 1.6f))
                val f1L = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.4f, 0.3f), Vector3f(-6.4f, 2.6f, 2.0f), rotZ = 0.4f, rotX = 0.6f)
                val f2L = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.8f, 0.3f), Vector3f(-5.6f, 2.8f, 2.0f), rotZ = 0.1f, rotX = 0.6f)
                val f3L = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.4f, 0.3f), Vector3f(-4.8f, 2.6f, 2.0f), rotZ = -0.2f, rotX = 0.6f)
                val thumbL = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 1.6f, 0.3f), Vector3f(-4.0f, 1.2f, 2.0f), rotZ = -0.8f, rotX = 0.4f)

                val palmR = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(1.6f, 1.6f, 0.8f), Vector3f(4.4f, 1.2f, 1.6f))
                val f1R = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.4f, 0.3f), Vector3f(4.4f, 2.6f, 2.0f), rotZ = 0.2f, rotX = 0.6f)
                val f2R = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.8f, 0.3f), Vector3f(5.2f, 2.8f, 2.0f), rotZ = -0.1f, rotX = 0.6f)
                val f3R = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.4f, 0.3f), Vector3f(6.0f, 2.6f, 2.0f), rotZ = -0.4f, rotX = 0.6f)
                val thumbR = createPart(safeLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 1.6f, 0.3f), Vector3f(3.6f, 1.2f, 2.0f), rotZ = 0.8f, rotX = 0.4f)

                parts.addAll(listOf(
                    core1, core2, core3,
                    leftEye, rightEye,
                    mouthCenter, mouthL, mouthR, teeth,
                    palmL, f1L, f2L, f3L, thumbL,
                    palmR, f1R, f2R, f3R, thumbR
                ))

                setGlowColor(NamedTextColor.WHITE)
                plugin.server.broadcast(plugin.messageManager.parse("<red><b>[!]</b> <dark_red>ANOMALÍA DETECTADA: <b>EL ABISMO HA DESPERTADO.</b>"))

                iniciarIANativa()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createPart(loc: Location, mat: Material, scale: Vector3f, translation: Vector3f, rotZ: Float = 0f, rotX: Float = 0f): BlockDisplay {
        return loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            val leftRotation = Quaternionf().rotateX(rotX).rotateZ(rotZ)
            bd.transformation = Transformation(translation, leftRotation, scale, Quaternionf())
            bd.isPersistent = false
            bd.interpolationDuration = 1
            bd.teleportDuration = 1
            bd.brightness = Display.Brightness(15, 15)
            bd.isGlowing = true
        }
    }

    private fun setGlowColor(color: NamedTextColor) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        val targetTeam = if (color == NamedTextColor.RED) teamRed else teamWhite
        val otherTeam = if (color == NamedTextColor.RED) teamWhite else teamRed
        val team = scoreboard.getTeam(targetTeam) ?: return
        val oldTeam = scoreboard.getTeam(otherTeam)

        parts.forEach {
            val id = it.uniqueId.toString()
            oldTeam?.removeEntry(id)
            if (!team.hasEntry(id)) team.addEntry(id)
        }
    }

    private fun getClosestTarget(): Player? {
        if (parts.isEmpty()) return null
        val bodyLoc = parts[0].location

        // Filtramos solo jugadores válidos que estén vivos en el minijuego
        val validPlayers = game.players.filter { it.gameMode == GameMode.SURVIVAL }

        return validPlayers.filter { it.uniqueId != lastVictimUUID }
            .minByOrNull { it.location.distanceSquared(bodyLoc) }
            ?: validPlayers.minByOrNull { it.location.distanceSquared(bodyLoc) }
    }

    private fun iniciarIANativa() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || !game.isRunning || parts.isEmpty() || !parts[0].isValid) {
                task.cancel()
                explodeAndRemove()
                return@runAtFixedRate
            }

            val target = getClosestTarget()
            if (target == null) {
                explodeAndRemove()
                task.cancel()
                return@runAtFixedRate
            }

            if (currentState == State.BUSCANDO) {
                if (consecutiveMisses >= 5) {
                    currentState = State.FURIA
                    ejecutarModoFuria()
                } else {
                    currentState = State.SALTANDO
                    ejecutarSaltos()
                }
            }
        }, 1L, 20L)
    }

    private fun ejecutarSaltos() {
        var saltos = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            val target = getClosestTarget()

            if (!isRunning || !game.isRunning || target == null || saltos >= 5) {
                task.cancel()
                if (isRunning && game.isRunning && target != null) {
                    val isAereo = ThreadLocalRandom.current().nextInt(100) < 30
                    if (isAereo) {
                        currentState = State.AEREO
                        ejecutarAtaqueAereo()
                    } else {
                        currentState = State.MISIL
                        ejecutarAtaqueMisil()
                    }
                } else {
                    currentState = State.BUSCANDO
                }
                return@runAtFixedRate
            }

            val bodyLoc = parts[0].location
            val nextLoc = bodyLoc.add(target.location.toVector().subtract(bodyLoc.toVector()).normalize().multiply(3.0))
            moverTodo(nextLoc, target.location)
            aplicarAuraMiedo(nextLoc, 20)
            target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.8f)

            saltos++
        }, 1L, 16L)
    }

    private fun ejecutarAtaqueMisil() {
        val initialTarget = getClosestTarget() ?: return
        initialTarget.playSound(initialTarget.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f)
        initialTarget.showTitle(Title.title(
            plugin.messageManager.parse("<dark_red><bold>!!! VIENE !!!"),
            plugin.messageManager.parse("<red>¡¡¡¡corre!!!!")
        ))

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            var step = 0
            var hitAny = false

            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
                val target = getClosestTarget()
                if (!isRunning || !game.isRunning || hitAny || step >= 15 || target == null) {
                    task.cancel()
                    if (hitAny) consecutiveMisses = 0 else consecutiveMisses++
                    currentState = State.BUSCANDO
                    return@runAtFixedRate
                }

                val current = parts[0].location
                val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                val nextLoc = current.add(dir.clone().multiply(4.0))

                moverTodo(nextLoc, target.location)
                aplicarAuraMiedo(nextLoc, 40)

                target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.3f)
                nextLoc.world.spawnParticle(Particle.SOUL_FIRE_FLAME, nextLoc, 10, 0.5, 0.5, 0.5, 0.1)

                val victims = game.players.filter { it.location.distanceSquared(nextLoc) < 5.0 && it.gameMode == GameMode.SURVIVAL }
                if (victims.isNotEmpty()) {
                    victims.forEach { ejecutarMuerte(it) }
                    hitAny = true
                }

                step++
            }, 1L, 2L)
        }, 24L)
    }

    private fun ejecutarAtaqueAereo() {
        var subidas = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { taskSubida ->
            if (!isRunning || !game.isRunning || subidas >= 12) {
                taskSubida.cancel()

                plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                    var step = 0
                    var hitAny = false

                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { taskBajada ->
                        val target = getClosestTarget()
                        if (!isRunning || !game.isRunning || hitAny || step >= 30 || target == null) {
                            taskBajada.cancel()
                            if (hitAny) consecutiveMisses = 0 else consecutiveMisses++
                            currentState = State.BUSCANDO
                            return@runAtFixedRate
                        }

                        val current = parts[0].location
                        val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                        val nextLoc = current.add(dir.clone().multiply(2.0))

                        moverTodo(nextLoc, target.location)
                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f)

                        val victims = game.players.filter { it.location.distanceSquared(nextLoc) < 6.0 && it.gameMode == GameMode.SURVIVAL }
                        if (victims.isNotEmpty()) {
                            victims.forEach { ejecutarMuerte(it) }
                            hitAny = true
                        }
                        step++
                    }, 1L, 1L)

                }, 10L)
                return@runAtFixedRate
            }

            val next = parts[0].location.add(0.0, 1.5, 0.0)
            val target = getClosestTarget()

            moverTodo(next, target?.location ?: next)
            target?.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 2f)
            subidas++

        }, 1L, 2L)
    }

    private fun ejecutarModoFuria() {
        setGlowColor(NamedTextColor.RED)

        // En la lista nueva, los ojos son el índice 3 (Izquierdo) y 4 (Derecho)
        if (parts.size > 4) {
            parts[3].block = Material.RED_CONCRETE.createBlockData()
            parts[4].block = Material.RED_CONCRETE.createBlockData()
        }

        plugin.server.broadcast(plugin.messageManager.parse("<dark_red><bold>!!! GEOFFREY ESTÁ FURIOSO !!!"))

        var hasHit = false
        var ticks = 0

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
                val target = getClosestTarget()

                if (!isRunning || !game.isRunning || hasHit || ticks >= 100 || target == null) {
                    task.cancel()
                    setGlowColor(NamedTextColor.WHITE)

                    if (parts.size > 4) {
                        parts[3].block = Material.WHITE_CONCRETE.createBlockData()
                        parts[4].block = Material.WHITE_CONCRETE.createBlockData()
                    }

                    plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                        currentState = State.BUSCANDO
                    }, if (hasHit) 100L else 20L)

                    return@runAtFixedRate
                }

                val current = parts[0].location
                val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                val nextLoc = current.add(dir.multiply(1.8))

                moverTodo(nextLoc, target.location)
                aplicarAuraMiedo(nextLoc, 40)
                target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 1.5f)

                if (nextLoc.distanceSquared(target.location) < 6.50) {
                    ejecutarMuerte(target, enrage = true)
                    hasHit = true
                }
                ticks++
            }, 1L, 1L)
        }, 20L)
    }

    private fun ejecutarMuerte(victim: Player, enrage: Boolean = false) {
        lastVictimUUID = victim.uniqueId
        victim.world.spawnParticle(Particle.EXPLOSION, victim.location, 2)
        victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.5f)

        // Usamos el sistema de daño de Bukkit para que los eventos de muerte salten normalmente.
        // Si está furioso, hace daño masivo (10 corazones / Insta-Kill si no hay armadura).
        // Si no, hace daño grave (7 corazones).
        val damageAmount = if (enrage) 20.0 else 14.0
        victim.damage(damageAmount)

        // Si sobrevivió al golpe, lo empujamos y le damos efectos
        if (!victim.isDead && victim.health > 0) {
            if (parts.isNotEmpty()) {
                val empuje = victim.location.toVector().subtract(parts[0].location.toVector()).normalize().multiply(2.0).setY(0.8)
                victim.velocity = empuje
            }

            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true))
            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, false, true))
        }

        val prefix = if (enrage) "<dark_red><b>[FURIA]</b>" else "<red><b>[!]</b>"
        plugin.server.broadcast(plugin.messageManager.parse("$prefix <white>${victim.name} fue golpeado por <dark_red>GEOFFREY 3.0"))
    }

    private fun aplicarAuraMiedo(loc: Location, duration: Int) {
        game.players.forEach { p ->
            if (p.location.distanceSquared(loc) < 225.0 && p.gameMode == GameMode.SURVIVAL) { // 15 bloques
                p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, duration, 0, false, false, false))
            }
        }
    }

    private fun moverTodo(baseLoc: Location, lookAtTargetLoc: Location? = null) {
        if (parts.isEmpty() || !parts[0].isValid) return
        val newLoc = baseLoc.clone()
        if (lookAtTargetLoc != null) {
            val dir = lookAtTargetLoc.toVector().subtract(baseLoc.toVector())
            // Anulamos el eje Y para que rote solo en horizontal (Yaw) y no se desarme por mirar abajo/arriba (Pitch)
            dir.y = 0.0
            if (dir.lengthSquared() > 0.001) {
                newLoc.direction = dir
            }
        }
        parts.forEach { it.teleport(newLoc) }
    }

    fun explodeAndRemove() {
        if (parts.isNotEmpty() && parts[0].isValid) {
            val loc = parts[0].location
            loc.world.spawnParticle(Particle.EXPLOSION, loc, 5)
            loc.world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f)
        }
        remove()
    }

    fun remove() {
        isRunning = false
        parts.forEach { if (it.isValid) it.remove() }
        parts.clear()
    }
}
