package pumpkin.eventos.utils

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.regex.Pattern

class MessageManager {
    private val mm = MiniMessage.miniMessage()
    private val hasPAPI: Boolean = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
    private val legacyRegex = Regex("[&§]([0-9a-fk-or])", RegexOption.IGNORE_CASE)

    private val legacyCodes = mapOf(
        "0" to "black", "1" to "dark_blue", "2" to "dark_green", "3" to "dark_aqua",
        "4" to "dark_red", "5" to "dark_purple", "6" to "gold", "7" to "gray",
        "8" to "dark_gray", "9" to "blue", "a" to "green", "b" to "aqua",
        "c" to "red", "d" to "light_purple", "e" to "yellow", "f" to "white",
        "k" to "obfuscated", "l" to "bold", "m" to "strikethrough",
        "n" to "underlined", "o" to "italic", "r" to "reset"
    )

    fun parse(player: Player?, text: String?, vararg resolvers: TagResolver): Component {
        if (text.isNullOrEmpty()) return Component.empty()
        var msg = text
        if (hasPAPI && player != null) msg = PlaceholderAPI.setPlaceholders(player, msg)
        return mm.deserialize(preProcess(msg), *resolvers)
    }

    fun parse(text: String?, vararg resolvers: TagResolver) = parse(null, text, *resolvers)

    private fun preProcess(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var processed = text
        val vanillaHex = Pattern.compile("[&§]x[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])[&§]([A-Fa-f0-9])")
        processed = vanillaHex.matcher(processed).replaceAll("<#$1$2$3$4$5$6>")
        val simpleHex = Pattern.compile("[&§]#([A-Fa-f0-9]{6})")
        val matcher = simpleHex.matcher(processed)
        val sb = StringBuilder()
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">")
        }
        matcher.appendTail(sb)
        return translateLegacy(sb.toString())
    }

    private fun translateLegacy(text: String): String {
        return legacyRegex.replace(text) { matchResult ->
            val code = matchResult.groupValues[1].lowercase()
            val tag = legacyCodes[code]
            if (tag != null) "<$tag>" else matchResult.value
        }
    }
}
