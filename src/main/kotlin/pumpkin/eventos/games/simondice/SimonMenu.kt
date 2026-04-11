package pumpkin.eventos.games.simondice

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.components.GuiAction
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import pumpkin.eventos.PumpkinEventos
import java.util.*

object SimonMenu {

    val esperandoInput = mutableMapOf<UUID, String>()
    val seleccionCombo = mutableMapOf<UUID, MutableList<String>>()
    private val modoComboActivo = mutableSetOf<UUID>()
    val comboPendienteLanzamiento = mutableMapOf<UUID, List<String>>()

    fun abrirPanelPrincipal(plugin: PumpkinEventos, streamer: Player, game: SimonDice) {
        val uuid = streamer.uniqueId
        val mm = plugin.messageManager

        val gui = Gui.gui()
            .title(Component.text("§8Eventos - Simón Dice..."))
            .rows(6)
            .disableAllInteractions()
            .create()

        // --- FILA 2: MOVIMIENTO Y ACCIÓN ---
        gui.setItem(11, crearBoton(plugin, Material.LIME_WOOL, "<#39FF14><b>▲ SALTAR</b>", "<gray>Orden: Todos deben saltar.") {
            manejarSeleccion(plugin, streamer, "SALTAR") { game.startChallenge("SALTAR", "▲ <#39FF14>¡SALTEN!</#39FF14>", 10); gui.close(streamer) }
        })
        gui.setItem(12, crearBoton(plugin, Material.YELLOW_WOOL, "<#CCFF00><b>▼ AGACHARSE</b>", "<gray>Orden: Todos al suelo.") {
            manejarSeleccion(plugin, streamer, "AGACHARSE") { game.startChallenge("AGACHARSE", "▼ <#CCFF00>¡AGÁCHENSE!</#CCFF00>", 10); gui.close(streamer) }
        })
        gui.setItem(13, crearBoton(plugin, Material.LEATHER_BOOTS, "<#00FFFF><b>⇄ CAMINAR</b>", "<gray>Orden: Nadie puede estar quieto.") {
            manejarSeleccion(plugin, streamer, "CAMINAR") { game.startChallenge("CAMINAR", "⇄ <#00FFFF>¡CAMINEN!</#00FFFF>", 10); gui.close(streamer) }
        })
        gui.setItem(14, crearBoton(plugin, Material.RED_WOOL, "<#FF3131><b>⏹ QUIETOS</b>", "<gray>Orden: El que se mueva muere.") {
            manejarSeleccion(plugin, streamer, "QUIETO") { game.startChallenge("QUIETO", "⏹ <#FF3131>¡QUIETOS!</#FF3131>", 10); gui.close(streamer) }
        })

        // --- NUEVO BOTÓN: GOLPEAR ---
        gui.setItem(15, crearBoton(plugin, Material.WOODEN_SWORD, "<#FFA500><b>⚔ GOLPEAR</b>", "<gray>Orden: Golpea a alguien o algo.") {
            manejarSeleccion(plugin, streamer, "GOLPEAR") { game.startChallenge("GOLPEAR", "⚔ <#FFA500>¡GOLPEEN A ALGUIEN!</#FFA500>", 10); gui.close(streamer) }
        })

        // --- FILA 3: CHAT Y LÓGICA ---
        gui.setItem(20, crearBoton(plugin, Material.FEATHER, "<#00FFFF><b>✎ FRASE EXACTA</b>", "<yellow>✎ Escribe la frase en el chat.") {
            manejarSeleccionInput(plugin, streamer, "FRASE", "<#00FFFF>✎ <b>Simón dice:</b> <white>Escribe la frase exacta en el chat...", gui)
        })
        gui.setItem(21, crearBoton(plugin, Material.BOOK, "<#CCFF00><b>⌗ CÁLCULO MENTAL</b>", "<yellow>✎ Escribe la operación.") {
            manejarSeleccionInput(plugin, streamer, "MATES", "<#CCFF00>⌗ <b>Simón dice:</b> <white>Escribe la operación...", gui)
        })

        // --- FILA 5: MINIJUEGOS (Opcionales / Extras) ---
        gui.setItem(38, crearBoton(plugin, Material.RED_BED, "<#FF3131><b>🛏️ ROMPECAMA</b>", "<gray>Carrera de puentes competitiva.") {
            streamer.sendMessage(mm.parse("<red>Minijuego en desarrollo...</red>")); gui.close(streamer)
        })
        gui.setItem(39, crearBoton(plugin, Material.POISONOUS_POTATO, "<#FF3131><b>🥔 PAPA CALIENTE</b>", "<gray>¡Golpea para pasar la papa!") {
            plugin.papaCalienteGame.iniciar(game, 30); gui.close(streamer)
        })
        gui.setItem(40, crearBoton(plugin, Material.ICE, "<#00FFFF><b>❄️ CONGELADOS</b>", "<gray>Rojos vs Azules con rescate.") {
            plugin.congeladosGame.iniciar(game, 60); gui.close(streamer)
        })
        gui.setItem(41, crearBoton(plugin, Material.NETHERITE_SWORD, "<#FF3131><b>⚔️ DUELO FINAL</b>", "<gray>Torneo 1vs1 automático.") {
            streamer.sendMessage(mm.parse("<red>Minijuego en desarrollo...</red>")); gui.close(streamer)
        })

        // --- LATERALES: CONSTRUCTOR DE COMBOS ---
        gui.setItem(17, crearBoton(plugin, Material.LEVER, "<#BF00FF><b>🧩 MODO COMBO</b>", "<gray>Activa/Desactiva el constructor.") {
            if (modoComboActivo.contains(uuid)) {
                modoComboActivo.remove(uuid)
                seleccionCombo.remove(uuid)
                streamer.sendMessage(mm.parse("<red>⌘ <b>COMBO:</b> <white>Constructor desactivado.</red>"))
                streamer.playSound(streamer.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
            } else {
                modoComboActivo.add(uuid)
                seleccionCombo[uuid] = mutableListOf()
                streamer.sendMessage(mm.parse("<#BF00FF>⌘ <b>COMBO:</b> <white>Constructor activo. Haz click en los retos...</white>"))
                streamer.playSound(streamer.location, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1.5f)
            }
        })

        gui.setItem(26, crearBoton(plugin, Material.TNT, "<#39FF14><b>🧨 ¡LANZAR COMBO!</b>", "<gray>Manda las órdenes seleccionadas.") {
            val combo = seleccionCombo[uuid] ?: mutableListOf()
            if (combo.isEmpty()) {
                streamer.sendMessage(mm.parse("<red>⚠️ <b>AVISO:</b> Combo vacío.</red>"))
                return@crearBoton
            }
            gui.close(streamer)
            // Aquí iría la lógica de iniciar tu "Combo" en la clase RetosManager (Aún no la migramos, así que por ahora mandamos un mensaje)
            streamer.sendMessage(mm.parse("<green>Lanzando combo: ${combo.joinToString(", ")}</green>"))

            modoComboActivo.remove(uuid)
            seleccionCombo.remove(uuid)
            streamer.playSound(streamer.location, Sound.ENTITY_TNT_PRIMED, 1f, 1f)
        })

        // --- SÓTANO: UTILIDADES ---
        gui.setItem(49, crearBoton(plugin, Material.MILK_BUCKET, "<white><b>✨ LIMPIAR ITEMS</b>", "<gray>Borra inventarios de participantes.") {
            game.players.forEach { it.inventory.clear() }
            streamer.sendMessage(mm.parse("<#00FFFF>✨ <b>LIMPIEZA:</b> <white>Inventarios vaciados.</white>"))
            streamer.playSound(streamer.location, Sound.ENTITY_ITEM_PICKUP, 1f, 1f)
        })

        gui.setItem(50, crearBoton(plugin, Material.BARRIER, "<red><b>🛑 DETENER TODO</b>", "<white><b>¡BOTÓN DE PÁNICO!</b>\n<gray>Finaliza el evento.") {
            game.stop()
            plugin.eventManager.currentGame = null
            streamer.inventory.clear()
            gui.close(streamer)
        })

        gui.open(streamer)
    }

    private fun crearBoton(plugin: PumpkinEventos, mat: Material, nombre: String, lore: String, accion: GuiAction<InventoryClickEvent>): GuiItem {
        val loreList = lore.split("\n").map { plugin.messageManager.parse(it) }
        return ItemBuilder.from(mat)
            .name(plugin.messageManager.parse(nombre))
            .lore(loreList)
            .flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            .asGuiItem(accion)
    }

    private fun manejarSeleccionInput(plugin: PumpkinEventos, streamer: Player, idAccion: String, mensajePrompt: String, gui: Gui) {
        val uuid = streamer.uniqueId
        if (modoComboActivo.contains(uuid)) {
            agregarAlCombo(plugin, streamer, idAccion, uuid)
        } else {
            esperandoInput[uuid] = idAccion
            streamer.sendMessage(plugin.messageManager.parse(mensajePrompt))
            gui.close(streamer)
        }
    }

    private fun manejarSeleccion(plugin: PumpkinEventos, streamer: Player, idAccion: String, accionUnica: () -> Unit) {
        val uuid = streamer.uniqueId
        if (modoComboActivo.contains(uuid)) {
            agregarAlCombo(plugin, streamer, idAccion, uuid)
        } else {
            accionUnica()
        }
    }

    private fun agregarAlCombo(plugin: PumpkinEventos, streamer: Player, idAccion: String, uuid: UUID) {
        val combo = seleccionCombo.getOrPut(uuid) { mutableListOf() }
        if (combo.contains(idAccion)) {
            streamer.sendMessage(plugin.messageManager.parse("<red>⚠️ Esta orden ya está en el combo.</red>"))
        } else if (combo.size >= 4) {
            streamer.sendMessage(plugin.messageManager.parse("<red>⚠️ Máximo 4 órdenes por combo.</red>"))
        } else {
            combo.add(idAccion)
            streamer.sendMessage(plugin.messageManager.parse("<#39FF14>⌘ Añadido: <white>$idAccion <gray>(${combo.size}/4)</gray>"))
            streamer.playSound(streamer.location, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.5f)
        }
    }
}
