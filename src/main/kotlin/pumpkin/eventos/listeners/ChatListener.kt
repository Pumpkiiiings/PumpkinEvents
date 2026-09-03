package pumpkin.eventos.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.simondice.SimonDice
import pumpkin.eventos.hooks.LuckPermsHook

class ChatListener(private val plugin: PumpkinEventos) : Listener {
    private val lp = LuckPermsHook()
    private val formatManager = plugin.chatFormatManager

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        var messageComponent = event.message()
        val game = plugin.eventManager.currentGame as? SimonDice

        if (game != null && game.isRunning && game.activeChallenges.contains("MATES") && game.players.contains(player)) {
            val msgText = PlainTextComponentSerializer.plainText().serialize(event.message()).lowercase().trim()
            val resultNum = game.targetMathResult.toInt().toString()
            val resultText = convertirALetras(game.targetMathResult.toInt())

            // Filtro para detectar v3int3, d0s, etc.
            val msgFiltrado = msgText.replace("3", "e")
                .replace("1", "i")
                .replace("0", "o")
                .replace("4", "a")
                .replace("5", "s")

            val esCorrecto = msgText.contains(resultNum) ||
                    msgText.contains(resultText) ||
                    msgFiltrado.contains(resultNum) ||
                    msgFiltrado.contains(resultText)

            if (esCorrecto) {
                messageComponent = plugin.languageManager.component("simon_chat.hidden_answer")

                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    game.markSaved(player)
                }
            } else {
                if (!game.savedPlayers.contains(player.uniqueId)) {
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        game.failedMathPlayers.add(player.uniqueId)
                        plugin.languageManager.send(player, "simon_chat.incorrect_answer")
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    }
                }
            }
        }

        // --- 2. LÓGICA DE FORMATO DE CHAT (TU CÓDIGO ORIGINAL ADAPTADO) ---
        val group = lp.getPrimaryGroup(player)
        val rawFormat = formatManager.getFormat(group)
        val clickCmd = formatManager.getClickAction(group).replace("<player>", player.name)
        val hoverLinesText = formatManager.getHover(group).joinToString("<newline>") { it.replace("<player>", player.name) }

        val prefixComp = plugin.messageManager.parse(player, lp.getPrefix(player))
        val suffixComp = plugin.messageManager.parse(player, lp.getSuffix(player))
        val playerComp = Component.text(player.name)

        // Usamos el messageComponent (que puede estar ofuscado por Simón)
        val messageText = PlainTextComponentSerializer.plainText().serialize(messageComponent)
        val finalMessageComp = if (messageText.contains("HIDDEN_ANSWER")) messageComponent else Component.text(messageText)

        val chatComponent = plugin.messageManager.parse(
            player,
            rawFormat,
            Placeholder.component("prefix", prefixComp),
            Placeholder.component("suffix", suffixComp),
            Placeholder.component("player", playerComp),
            Placeholder.component("message", finalMessageComp)
        )

        val hoverComponent = plugin.messageManager.parse(player, hoverLinesText)

        val interactiveComponent = chatComponent
            .clickEvent(ClickEvent.suggestCommand(clickCmd))
            .hoverEvent(HoverEvent.showText(hoverComponent))

        // Aplicamos el renderizado final
        event.renderer { _, _, _, _ -> interactiveComponent }
    }

    private fun convertirALetras(num: Int): String {
        val nombres = mapOf(
            1 to "uno", 2 to "dos", 3 to "tres", 4 to "cuatro", 5 to "cinco", 6 to "seis", 7 to "siete", 8 to "ocho", 9 to "nueve", 10 to "diez",
            11 to "once", 12 to "doce", 13 to "trece", 14 to "catorce", 15 to "quince", 16 to "dieciseis", 17 to "diecisiete", 18 to "dieciocho", 19 to "diecinueve", 20 to "veinte",
            21 to "veintiuno", 22 to "veintidos", 23 to "veintitres", 24 to "veinticuatro", 25 to "veinticinco", 26 to "veintiseis", 27 to "veintisiete", 28 to "veintiocho", 29 to "veintinueve", 30 to "treinta",
            31 to "treinta y uno", 32 to "treinta y dos", 33 to "treinta y tres", 34 to "treinta y cuatro", 35 to "treinta y cinco", 36 to "treinta y seis", 37 to "treinta y siete", 38 to "treinta y ocho", 39 to "treinta y nueve", 40 to "cuarenta"
        )
        return nombres[num] ?: num.toString()
    }
}
