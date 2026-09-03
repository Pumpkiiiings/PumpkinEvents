package pumpkin.eventos.arena

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import pumpkin.eventos.PumpkinEventos

class ArenaSetupListener(private val plugin: PumpkinEventos) : Listener {

    @EventHandler
    fun onBlockPlace(e: BlockPlaceEvent) {
        val p = e.player
        val session = plugin.arenaManager.activeSetups[p.uniqueId] ?: return
        
        // Solo verificamos si estamos configurando buildbattle
        if (!session.type.contains("buildbattle", ignoreCase = true)) return
        
        // Verificamos si el bloque es un bloque de oro
        if (e.blockPlaced.type != Material.GOLD_BLOCK) return
        
        // Agregamos la ubicación del bloque como spawn de la parcela
        session.spawns.add(e.blockPlaced.location.clone())
        
        // Mensaje de confirmación (usando el formato de ArenaCommands)
        val prefix = plugin.languageManager.get("prefix")
        val rawMsg = plugin.languageManager.get("commands.arena.spawn_added").replace("<prefix>", prefix)
        p.sendMessage(plugin.messageManager.parse(rawMsg, 
            Placeholder.parsed("map", session.name),
            Placeholder.parsed("count", session.spawns.size.toString())
        ))
    }
}
