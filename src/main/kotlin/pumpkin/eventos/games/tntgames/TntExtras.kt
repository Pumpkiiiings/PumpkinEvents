package pumpkin.eventos.games.tntgames

import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.FallingBlock
import org.bukkit.util.Vector
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import pumpkin.eventos.games.tntgames.entities.GeoffreyEXE
import kotlin.math.cos
import kotlin.math.sin

/**
 * [PUMPKIN EVENTOS] - Extras Adaptados
 * Estos métodos ahora son extensiones de EventGame para que puedan acceder a 'players' e 'isRunning'.
 */

fun EventGame.triggerLava(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.forEach {
        it.showTitle(Title.title(mm.parse("<#FF0000><b>¡EL SUELO ES LAVA!</b>"), mm.parse("<white>¡No te quedes quieto!")))
        it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f)
        it.sendMessage(mm.parse("<newline><#FF0000><b>¡EVENTO!</b> <white>El suelo comenzará a derretirse.<newline>"))
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (!isRunning || ticks >= 300) { task.cancel(); return@runAtFixedRate }
        ticks += 5

        players.forEach { p ->
            val blockLoc = p.location.clone().subtract(0.0, 0.1, 0.0)
            if (blockLoc.block.type.isSolid && blockLoc.block.type != Material.BEDROCK) {
                plugin.server.regionScheduler.runDelayed(plugin, blockLoc, { _ ->
                    if (blockLoc.block.type != Material.BEDROCK && blockLoc.block.type != Material.AIR) {
                        blockLoc.block.type = Material.LAVA
                    }
                }, 10L)
            }
        }
    }, 1L, 5L)
}

fun EventGame.triggerTornado(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.forEach {
        it.showTitle(Title.title(mm.parse("<#00FFFF><b>¡TORNADO!</b>"), mm.parse("<white>¡Huye de la corriente de aire!")))
        it.sendMessage(mm.parse("<newline><#00FFFF><b>¡EVENTO!</b> <white>Un tornado nivel F5 se aproxima.<newline>"))
        it.playSound(it.location, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.5f)
    }

    // Usamos spawnPoints de la arena actual del core
    val tornadoCenter = currentArena?.spawnPoints?.randomOrNull()?.clone() ?: return
    var ticks = 0
    var angle = 0.0
    val direction = Vector(Math.random() - 0.5, 0.0, Math.random() - 0.5).normalize().multiply(0.4)

    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (!isRunning || ticks >= 300) { task.cancel(); return@runAtFixedRate }
        ticks += 2

        tornadoCenter.add(direction)
        direction.rotateAroundY(0.1 * (Math.random() - 0.5))

        plugin.server.regionScheduler.run(plugin, tornadoCenter) { _ ->
            val world = tornadoCenter.world
            for (y in 0..15 step 2) {
                val radius = 0.5 + (y * 0.2)
                val offsetAngle = angle + y
                val cx = tornadoCenter.x + radius * cos(offsetAngle)
                val cz = tornadoCenter.z + radius * sin(offsetAngle)
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, Location(world, cx, tornadoCenter.y + y, cz), 2, 0.2, 0.2, 0.2, 0.02)
            }
            angle += 0.6

            val baseBlock = tornadoCenter.block
            if (baseBlock.type != Material.AIR && baseBlock.type != Material.BEDROCK) {
                world.spawnParticle(Particle.BLOCK, tornadoCenter, 10, baseBlock.blockData)
                baseBlock.type = Material.AIR
            }

            players.forEach { p ->
                val dist = p.location.distanceSquared(tornadoCenter)
                if (dist < 49) {
                    val flingDir = p.location.toVector().subtract(tornadoCenter.toVector()).normalize()
                    if (dist < 9) p.velocity = Vector(0.0, 1.5, 0.0)
                    else p.velocity = flingDir.multiply(1.5).setY(0.5)
                    if (ticks % 10 == 0) p.playSound(p.location, Sound.ENTITY_BAT_TAKEOFF, 1f, 0.8f)
                }
            }
        }
    }, 1L, 2L)
}

fun EventGame.triggerStorm(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.forEach {
        it.showTitle(Title.title(mm.parse("<#FFFF00><b>¡TORMENTA ELÉCTRICA!</b>"), mm.parse("<white>¡Esquiva las marcas del suelo!")))
        it.sendMessage(mm.parse("<newline><#FFFF00><b>¡EVENTO!</b> <white>Mira el piso y esquiva los rayos.<newline>"))
        it.playSound(it.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.5f)
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (!isRunning || ticks >= 400) { task.cancel(); return@runAtFixedRate }
        ticks += 15

        val target = players.randomOrNull() ?: return@runAtFixedRate
        val strikeLoc = target.location.clone().add((Math.random() - 0.5) * 6, 0.0, (Math.random() - 0.5) * 6)
        strikeLoc.y = strikeLoc.world.getHighestBlockYAt(strikeLoc.blockX, strikeLoc.blockZ).toDouble() + 1

        plugin.server.regionScheduler.run(plugin, strikeLoc) { _ ->
            val world = strikeLoc.world
            for (i in 0..360 step 30) {
                val rad = Math.toRadians(i.toDouble())
                world.spawnParticle(Particle.FLAME, strikeLoc.clone().add(cos(rad) * 1.5, 0.1, sin(rad) * 1.5), 1, 0.0, 0.0, 0.0, 0.0)
            }
            world.playSound(strikeLoc, Sound.ENTITY_CREEPER_PRIMED, 1f, 0.5f)

            plugin.server.regionScheduler.runDelayed(plugin, strikeLoc, { _ ->
                if (isRunning) world.strikeLightning(strikeLoc)
            }, 20L)
        }
    }, 10L, 15L)
}

fun EventGame.triggerAnvils(plugin: PumpkinEventos) {
    val mm = plugin.messageManager
    players.forEach {
        it.showTitle(Title.title(mm.parse("<#A9A9A9><b>¡LLUVIA DE YUNQUES!</b>"), mm.parse("<white>¡Mira hacia arriba!")))
        it.sendMessage(mm.parse("<newline><#A9A9A9><b>¡EVENTO!</b> <white>Cuidado con la cabeza...<newline>"))
        it.playSound(it.location, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f)
    }

    var ticks = 0
    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
        if (!isRunning || ticks >= 300) { task.cancel(); return@runAtFixedRate }
        ticks += 10

        val target = players.randomOrNull() ?: return@runAtFixedRate
        val spawnLoc = target.location.clone().add((Math.random() - 0.5) * 5, 15.0, (Math.random() - 0.5) * 5)

        plugin.server.regionScheduler.run(plugin, spawnLoc) { _ ->
            val world = spawnLoc.world
            val groundY = world.getHighestBlockYAt(spawnLoc.blockX, spawnLoc.blockZ).toDouble() + 1
            val shadowLoc = Location(world, spawnLoc.x, groundY, spawnLoc.z)

            world.spawnParticle(Particle.SMOKE, shadowLoc, 20, 0.5, 0.1, 0.5, 0.0)
            world.playSound(shadowLoc, Sound.ENTITY_TNT_PRIMED, 0.5f, 2f)

            val anvil: FallingBlock = world.spawnFallingBlock(spawnLoc, Material.DAMAGED_ANVIL.createBlockData())
            anvil.dropItem = false
            anvil.setHurtEntities(true)

            plugin.server.regionScheduler.runDelayed(plugin, spawnLoc, { _ ->
                if (anvil.isValid && !anvil.isDead) anvil.remove()
                val landedBlock = world.getBlockAt(shadowLoc)
                if (landedBlock.type.name.contains("ANVIL")) {
                    landedBlock.type = Material.AIR
                    world.spawnParticle(Particle.BLOCK, shadowLoc, 15, Material.DAMAGED_ANVIL.createBlockData())
                    world.playSound(shadowLoc, Sound.BLOCK_ANVIL_DESTROY, 1f, 1f)
                }
            }, 60L)
        }
    }, 10L, 10L)
}

fun EventGame.triggerGeoffrey(plugin: PumpkinEventos) {
    val center = currentArena?.centerLocation ?: players.randomOrNull()?.location ?: return
    val geoffrey = GeoffreyEXE(plugin, this)
    geoffrey.spawn(center.clone().add(0.0, 5.0, 0.0))
}
