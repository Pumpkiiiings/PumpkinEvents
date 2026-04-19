package pumpkin.eventos.games

import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.FallingBlock
import org.bukkit.util.Vector
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.tntgames.entities.GeoffreyEXE
import kotlin.math.cos
import kotlin.math.sin

/**
 * [PUMPKIN EVENTOS] - Extras Optimizados para Folia/Paper
 * Visuales mejorados (VFX/SFX) y cero violaciones de región.
 */

fun EventGame.triggerLava(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.forEach {
        it.showTitle(Title.title(mm.parse("<#FF3131><b>¡SUELO DERRETIDO!</b>"), mm.parse("<white>¡No te quedes quieto!")))
        it.playSound(it.location, Sound.BLOCK_LAVA_EXTINGUISH, 1f, 0.5f)
        it.sendMessage(mm.parse("<newline><#FF3131><b>¡EVENTO!</b> <white>El suelo bajo tus pies comenzará a derretirse.<newline>"))
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (!isRunning || ticks >= 150) { task.cancel(); return@runAtFixedRate } // 150 ticks (aprox 15 segundos de evento)
        ticks += 5

        players.toList().forEach { p ->
            val loc = p.location.clone().subtract(0.0, 0.1, 0.0)

            // Usamos el scheduler del jugador para operaciones seguras
            p.scheduler.run(plugin, { _ ->
                val block = loc.block
                if (block.type.isSolid && block.type != Material.BEDROCK && block.type != Material.LAVA) {

                    // VFX: El bloque se está calentando
                    loc.world.spawnParticle(Particle.LAVA, loc.clone().add(0.5, 1.2, 0.5), 2, 0.2, 0.0, 0.2, 0.0)
                    loc.world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.8f, 2f)

                    // Retraso antes de derretirse
                    plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                        if (block.type != Material.BEDROCK && block.type != Material.AIR) {
                            block.setType(Material.LAVA, false) // False = 0 Lag de físicas
                            loc.world.playSound(loc, Sound.BLOCK_STONE_BREAK, 1f, 0.5f)
                            loc.world.spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 1.0, 0.5), 5, 0.2, 0.2, 0.2, 0.0)
                        }
                    }, 15L) // Tarda casi 1 segundo en derretirse, dando oportunidad de saltar
                }
            }, null)
        }
    }, 20L, 5L)
}

fun EventGame.triggerTornado(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.forEach {
        it.showTitle(Title.title(mm.parse("<#AAAAAA><b>¡TORNADO F5!</b>"), mm.parse("<white>¡Aléjate del embudo!")))
        it.sendMessage(mm.parse("<newline><#AAAAAA><b>¡EVENTO!</b> <white>Un tornado arrasará la zona.<newline>"))
        it.playSound(it.location, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.5f)
    }

    val startPoint = currentArena?.spawnPoints?.randomOrNull()?.clone() ?: return
    val targetWorld = startPoint.world

    // Variables de trayectoria
    var tx = startPoint.x
    var tz = startPoint.z
    val ty = startPoint.y

    var dx = (Math.random() - 0.5) * 0.8
    var dz = (Math.random() - 0.5) * 0.8

    var ticks = 0
    var angle = 0.0

    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (!isRunning || ticks >= 200) { task.cancel(); return@runAtFixedRate }
        ticks++

        // Matemáticas del movimiento (Curvas suaves)
        tx += dx
        tz += dz
        dx += (Math.random() - 0.5) * 0.1
        dz += (Math.random() - 0.5) * 0.1

        val tornadoLoc = Location(targetWorld, tx, ty, tz)

        // --- VFX DEL TORNADO (Global Dispatch) ---
        for (y in 0..15 step 1) {
            val radius = 0.5 + (y * 0.25)
            val offsetAngle = angle + (y * 0.5)
            val cx = tx + radius * cos(offsetAngle)
            val cz = tz + radius * sin(offsetAngle)

            val vfxLoc = Location(targetWorld, cx, ty + y, cz)
            // Polvo gris oscuro y blanco
            targetWorld.spawnParticle(Particle.DUST, vfxLoc, 3, 0.2, 0.2, 0.2, 0.0, Particle.DustOptions(Color.GRAY, 2f))
            targetWorld.spawnParticle(Particle.CLOUD, vfxLoc, 1, 0.1, 0.1, 0.1, 0.05)
        }
        angle += 0.4

        if (ticks % 10 == 0) targetWorld.playSound(tornadoLoc, Sound.ITEM_ELYTRA_FLYING, 1.5f, 0.5f)

        // --- MECÁNICA: SUCCIÓN Y LANZAMIENTO SEGURO EN FOLIA ---
        players.toList().forEach { p ->
            // Le pedimos al scheduler del jugador que maneje sus físicas para no dar warning de región
            p.scheduler.run(plugin, { _ ->
                if (!p.isOnline || p.isDead) return@run

                val dist = p.location.distanceSquared(tornadoLoc)

                if (dist < 49) { // Dentro del área de efecto
                    if (dist < 4) {
                        // En el ojo: Escupido hacia arriba violentamente
                        p.velocity = Vector(0.0, 1.8, 0.0)
                        p.playSound(p.location, Sound.ENTITY_BAT_TAKEOFF, 1f, 0.5f)
                    } else {
                        // Cerca: Absorbido hacia el centro
                        val pullDir = tornadoLoc.toVector().subtract(p.location.toVector()).normalize()
                        p.velocity = p.velocity.add(pullDir.multiply(0.2)).setY(0.1)
                    }
                }
            }, null)
        }
    }, 1L, 1L)
}

fun EventGame.triggerStorm(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.forEach {
        it.showTitle(Title.title(mm.parse("<#FFFF00><b>¡TORMENTA ELÉCTRICA!</b>"), mm.parse("<white>¡Esquiva los círculos!")))
        it.sendMessage(mm.parse("<newline><#FFFF00><b>¡EVENTO!</b> <white>Peligro de alto voltaje.<newline>"))
        it.playSound(it.location, Sound.WEATHER_RAIN, 0.5f, 1f)
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (!isRunning || ticks >= 10) { task.cancel(); return@runAtFixedRate } // 10 Rayos en total
        ticks++

        val target = players.randomOrNull() ?: return@runAtFixedRate
        val strikeLoc = target.location.clone().add((Math.random() - 0.5) * 8, 0.0, (Math.random() - 0.5) * 8)

        plugin.server.regionScheduler.run(plugin, strikeLoc) { _ ->
            val world = strikeLoc.world
            strikeLoc.y = world.getHighestBlockYAt(strikeLoc.blockX, strikeLoc.blockZ).toDouble() + 1.1

            // Sonido de estática previo
            world.playSound(strikeLoc, Sound.BLOCK_BELL_RESONATE, 1f, 2f)

            // Animación: Círculo eléctrico que se encoge
            var radio = 3.0
            plugin.server.regionScheduler.runAtFixedRate(plugin, strikeLoc, { animTask ->
                if (!isRunning || radio <= 0.2) {
                    animTask.cancel()
                    // CAE EL RAYO
                    world.strikeLightning(strikeLoc)
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, strikeLoc, 1)
                    return@runAtFixedRate
                }

                for (i in 0..360 step 20) {
                    val rad = Math.toRadians(i.toDouble())
                    world.spawnParticle(Particle.ELECTRIC_SPARK, strikeLoc.clone().add(cos(rad) * radio, 0.0, sin(rad) * radio), 1, 0.0, 0.0, 0.0, 0.0)
                }
                radio -= 0.3 // Se cierra rápido

            }, 1L, 2L) // Animación dura aprox 1 segundo
        }
    }, 20L, 25L) // Caen cada segundo y medio
}

fun EventGame.triggerAnvils(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.forEach {
        it.showTitle(Title.title(mm.parse("<#696969><b>¡LLUVIA PESADA!</b>"), mm.parse("<white>¡Mira hacia arriba!")))
        it.sendMessage(mm.parse("<newline><#696969><b>¡EVENTO!</b> <white>Cuidado con la cabeza...<newline>"))
        it.playSound(it.location, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f)
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (!isRunning || ticks >= 25) { task.cancel(); return@runAtFixedRate } // 25 Yunques
        ticks++

        val target = players.randomOrNull() ?: return@runAtFixedRate

        // Ejecutamos en el hilo de la víctima para generar la locación exacta
        target.scheduler.run(plugin, { _ ->
            if (!isRunning || !target.isOnline) return@run

            val spawnLoc = target.location.clone().add((Math.random() - 0.5) * 6, 20.0, (Math.random() - 0.5) * 6)
            val world = spawnLoc.world
            val groundY = world.getHighestBlockYAt(spawnLoc.blockX, spawnLoc.blockZ).toDouble() + 1
            val shadowLoc = Location(world, spawnLoc.x, groundY + 0.1, spawnLoc.z)

            // --- VFX: Sombra en el suelo y sonido de misil cayendo ---
            plugin.server.regionScheduler.runAtFixedRate(plugin, shadowLoc, { shadowTask ->
                if (!isRunning) { shadowTask.cancel(); return@runAtFixedRate }
                world.spawnParticle(Particle.SQUID_INK, shadowLoc, 5, 0.4, 0.0, 0.4, 0.0) // Sombra negra
            }, 1L, 3L)

            world.playSound(spawnLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f) // Silbido

            // Generamos el yunque
            val anvil: FallingBlock = world.spawnFallingBlock(spawnLoc, Material.DAMAGED_ANVIL.createBlockData())
            anvil.dropItem = false
            anvil.setHurtEntities(true)
            anvil.damagePerBlock = 10.0f // Daño letal si te golpea

            // Limpieza a los 3 segundos
            plugin.server.regionScheduler.runDelayed(plugin, spawnLoc, { _ ->
                if (anvil.isValid && !anvil.isDead) anvil.remove()

                val landedBlock = world.getBlockAt(shadowLoc.clone().subtract(0.0, 1.0, 0.0))
                if (landedBlock.type.name.contains("ANVIL")) {
                    landedBlock.setType(Material.AIR, false)
                    world.spawnParticle(Particle.BLOCK, shadowLoc, 25, Material.DAMAGED_ANVIL.createBlockData())
                    world.playSound(shadowLoc, Sound.BLOCK_ANVIL_DESTROY, 1.5f, 0.8f)
                }
            }, 60L)

        }, null)
    }, 10L, 8L) // Caen bastante rápido
}

fun EventGame.triggerGeoffrey(plugin: PumpkinEventos) {
    val center = currentArena?.centerLocation ?: players.randomOrNull()?.location ?: return
    plugin.server.regionScheduler.run(plugin, center) { _ ->
        val geoffrey = GeoffreyEXE(plugin, this)
        geoffrey.spawn(center.clone().add(0.0, 5.0, 0.0))
    }
}
