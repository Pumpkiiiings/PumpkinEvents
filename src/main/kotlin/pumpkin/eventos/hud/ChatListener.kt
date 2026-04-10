package pumpkin.eventos.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.hooks.LuckPermsHook

class ChatListener(private val plugin: PumpkinEventos) : Listener {
    private val lp = LuckPermsHook()
    private val formatManager = plugin.chatFormatManager

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val group = lp.getPrimaryGroup(player)

        val rawFormat = formatManager.getFormat(group)
        val clickCmd = formatManager.getClickAction(group).replace("<player>", player.name)
        val hoverLinesText = formatManager.getHover(group).joinToString("<newline>") { it.replace("<player>", player.name) }

        val prefixComp = plugin.messageManager.parse(player, lp.getPrefix(player))
        val suffixComp = plugin.messageManager.parse(player, lp.getSuffix(player))
        val playerComp = Component.text(player.name)

        val messageText = PlainTextComponentSerializer.plainText().serialize(event.message())
        val messageComp = Component.text(messageText)

        val chatComponent = plugin.messageManager.parse(
            player,
            rawFormat,
            Placeholder.component("prefix", prefixComp),
            Placeholder.component("suffix", suffixComp),
            Placeholder.component("player", playerComp),
            Placeholder.component("message", messageComp)
        )

        val hoverComponent = plugin.messageManager.parse(player, hoverLinesText)

        val interactiveComponent = chatComponent
            .clickEvent(ClickEvent.suggestCommand(clickCmd))
            .hoverEvent(HoverEvent.showText(hoverComponent))

        // ¡Paper Render API nativa!
        event.renderer { _, _, _, _ -> interactiveComponent }
    }
}
