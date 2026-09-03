package pumpkin.eventos.games

import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.FallingBlock
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.tntgames.entities.GeoffreyEXE
import kotlin.math.cos
import kotlin.math.sin

/**
 * [PUMPKIN EVENTOS] - Extras Optimizados para Folia/Paper
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
        if (!isRunning || ticks >= 150) { task.cancel(); return@runAtFixedRate } // 15 segundos
        ticks += 5

        players.toList().forEach { p ->
            val loc = p.location.clone().subtract(0.0, 0.1, 0.0)

            p.scheduler.run(plugin, { _ ->
                val block = loc.block
                if (block.type.isSolid && block.type != Material.BEDROCK && block.type != Material.LAVA) {

                    loc.world.spawnParticle(Particle.LAVA, loc.clone().add(0.5, 1.2, 0.5), 2, 0.2, 0.0, 0.2, 0.0)
                    loc.world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.8f, 2f)

                    plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                        if (block.type != Material.BEDROCK && block.type != Material.AIR) {
                            block.setType(Material.LAVA, false)
                            loc.world.playSound(loc, Sound.BLOCK_STONE_BREAK, 1f, 0.5f)
                            loc.world.spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 1.0, 0.5), 5, 0.2, 0.2, 0.2, 0.0)
                        }
                    }, 15L)
                }
            }, null)
        }
    }, 20L, 5L)
}

fun EventGame.triggerStorm(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.forEach {
        it.showTitle(Title.title(mm.parse("<#FFFF00><b>¡TORMENTA ELÉCTRICA!</b>"), mm.parse("<white>¡Esquiva los círculos!")))
        it.playSound(it.location, Sound.WEATHER_RAIN, 0.5f, 1f)
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (!isRunning || ticks >= 10) { task.cancel(); return@runAtFixedRate }
        ticks++

        val target = players.randomOrNull() ?: return@runAtFixedRate
        val strikeLoc = target.location.clone().add((Math.random() - 0.5) * 8, 0.0, (Math.random() - 0.5) * 8)

        plugin.server.regionScheduler.run(plugin, strikeLoc) { _ ->
            val world = strikeLoc.world
            strikeLoc.y = world.getHighestBlockYAt(strikeLoc.blockX, strikeLoc.blockZ).toDouble() + 1.1

            world.playSound(strikeLoc, Sound.BLOCK_BELL_RESONATE, 1f, 2f)

            var radio = 3.0
            plugin.server.regionScheduler.runAtFixedRate(plugin, strikeLoc, { animTask ->
                if (!isRunning || radio <= 0.2) {
                    animTask.cancel()
                    world.strikeLightning(strikeLoc)
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, strikeLoc, 1)
                    return@runAtFixedRate
                }

                for (i in 0..360 step 20) {
                    val rad = Math.toRadians(i.toDouble())
                    world.spawnParticle(Particle.ELECTRIC_SPARK, strikeLoc.clone().add(cos(rad) * radio, 0.0, sin(rad) * radio), 1, 0.0, 0.0, 0.0, 0.0)
                }
                radio -= 0.3

            }, 1L, 2L)
        }
    }, 20L, 25L)
}

fun EventGame.triggerAnvils(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.forEach {
        it.showTitle(Title.title(mm.parse("<#696969><b>¡LLUVIA PESADA!</b>"), mm.parse("<white>¡Mira hacia arriba!")))
        it.playSound(it.location, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f)
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (!isRunning || ticks >= 25) { task.cancel(); return@runAtFixedRate }
        ticks++

        val target = players.randomOrNull() ?: return@runAtFixedRate

        target.scheduler.run(plugin, { _ ->
            if (!isRunning || !target.isOnline) return@run

            val spawnLoc = target.location.clone().add((Math.random() - 0.5) * 6, 20.0, (Math.random() - 0.5) * 6)
            val world = spawnLoc.world
            val groundY = world.getHighestBlockYAt(spawnLoc.blockX, spawnLoc.blockZ).toDouble() + 1
            val shadowLoc = Location(world, spawnLoc.x, groundY + 0.1, spawnLoc.z)

            plugin.server.regionScheduler.runAtFixedRate(plugin, shadowLoc, { shadowTask ->
                if (!isRunning) { shadowTask.cancel(); return@runAtFixedRate }
                world.spawnParticle(Particle.SQUID_INK, shadowLoc, 5, 0.4, 0.0, 0.4, 0.0)
            }, 1L, 3L)

            world.playSound(spawnLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f)

            val anvil: FallingBlock = world.spawnFallingBlock(spawnLoc, Material.DAMAGED_ANVIL.createBlockData())
            anvil.dropItem = false
            anvil.setHurtEntities(true)
            anvil.damagePerBlock = 10.0f

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
    }, 10L, 8L)
}

fun EventGame.triggerGeoffrey(plugin: PumpkinEventos) {
    val center = currentArena?.centerLocation ?: players.randomOrNull()?.location ?: return
    plugin.server.regionScheduler.run(plugin, center) { _ ->
        val geoffrey = GeoffreyEXE(plugin, this)
        geoffrey.spawn(center.clone().add(0.0, 5.0, 0.0))
    }
}

// =====================================
// NUEVOS EXTRAS AÑADIDOS
// =====================================

fun EventGame.triggerAcidRain(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.forEach {
        it.showTitle(Title.title(mm.parse("<#32CD32><b>¡LLUVIA ÁCIDA!</b>"), mm.parse("<white>¡Busca refugio bajo un bloque!")))
        it.playSound(it.location, Sound.ENTITY_CREEPER_HURT, 1f, 0.5f)
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (!isRunning || ticks >= 15) { task.cancel(); return@runAtFixedRate } // Dura 15 segundos
        ticks++

        players.toList().forEach { p ->
            p.scheduler.run(plugin, { _ ->
                if (!p.isOnline || p.isDead) return@run

                val loc = p.location
                // Efecto de lluvia verde
                loc.world.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, loc.clone().add(0.0, 4.0, 0.0), 40, 3.0, 0.5, 3.0, 0.0)

                // Si el bloque más alto es menor o igual a la posición del jugador, significa que está expuesto al cielo
                val highestY = loc.world.getHighestBlockYAt(loc)
                if (loc.y >= highestY - 1) {
                    p.damage(2.0) // Quita 1 corazón por segundo
                    p.playSound(loc, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 0.5f, 1.5f)
                }
            }, null)
        }
    }, 20L, 20L) // Se ejecuta cada 1 segundo (20 ticks)
}

fun EventGame.triggerZeroGravity(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.toList().forEach { p ->
        p.scheduler.run(plugin, { _ ->
            p.showTitle(Title.title(mm.parse("<#ADD8E6><b>¡GRAVEDAD CERO!</b>"), mm.parse("<white>¡Hacia el infinito y más allá!")))
            p.playSound(p.location, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f)

            // Levitación por 10 segundos
            p.addPotionEffect(PotionEffect(PotionEffectType.LEVITATION, 200, 0, false, false, false))
            // Caída lenta por 15 segundos para que no mueran al caer
            p.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 300, 0, false, false, false))
        }, null)
    }
}

fun EventGame.triggerAntLife(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.toList().forEach { p ->
        p.scheduler.run(plugin, { _ ->
            p.showTitle(Title.title(mm.parse("<#FF69B4><b>¡VIDA DE HORMIGA!</b>"), mm.parse("<white>¡Cuidado donde pisas!")))
            p.playSound(p.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 2f)

            // Convertirlo a hormiga (escala 0.15)
            p.getAttribute(Attribute.SCALE)?.baseValue = 0.15
        }, null)
    }

    // Volver a la normalidad después de 15 segundos
    plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
        players.toList().forEach { p ->
            p.scheduler.run(plugin, { _ ->
                p.getAttribute(Attribute.SCALE)?.baseValue = 1.0
                p.playSound(p.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f)
            }, null)
        }
    }, 300L) // 15 segundos
}
