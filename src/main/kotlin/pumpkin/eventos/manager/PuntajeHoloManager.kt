package pumpkin.eventos.manager

import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.util.Transformation
import org.joml.Vector3f
import pumpkin.eventos.PumpkinEventos
import java.util.UUID

class PuntajeHoloManager(private val plugin: PumpkinEventos) : Listener {

    private var displayUUID: UUID? = null
    private var interactionUUID: UUID? = null
    private var holoLocation: Location? = null // Guardamos la locación para los schedulers

    private var paginaActual = 0
    private var wasEmpty = true // Controla si el mundo no tenía a nadie

    init {
        cargarHolograma()
        iniciarTareaActualizacion()
    }

    // --- TAREA EN TIEMPO REAL ---
    private fun iniciarTareaActualizacion() {
        // Se ejecuta cada 5 segundos (100 ticks)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            val loc = holoLocation ?: return@runAtFixedRate

            // Ejecutamos en la región del holograma
            plugin.server.regionScheduler.run(plugin, loc) { _ ->
                val displayId = displayUUID ?: return@run
                val entity = plugin.server.getEntity(displayId) as? TextDisplay ?: return@run

                val worldPlayers = entity.world.players.size

                if (worldPlayers > 0) {
                    if (wasEmpty) {
                        // Alguien entró al mundo: Mostrar "Cargando..."
                        wasEmpty = false
                        entity.text(plugin.languageManager.component("hologram.loading"))

                        // Retrasamos la carga real 1.5 segundos (30 ticks)
                        plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                            actualizarTexto()
                        }, 30L)
                    } else {
                        // Ya había gente, se actualiza en tiempo real de forma invisible
                        actualizarTexto()
                    }
                } else {
                    // Si no hay nadie, no hacemos nada, pero marcamos que quedó vacío
                    wasEmpty = true
                }
            }
        }, 100L, 100L)
    }

    fun spawnHolograma(loc: Location) {
        eliminarHolograma()
        holoLocation = loc

        plugin.server.regionScheduler.run(plugin, loc) { _ ->
            val display = loc.world.spawnEntity(loc, EntityType.TEXT_DISPLAY) as TextDisplay
            display.isPersistent = true
            displayUUID = display.uniqueId

            val interaction = loc.world.spawnEntity(loc.clone().subtract(0.0, 0.5, 0.0), EntityType.INTERACTION) as Interaction
            interaction.interactionWidth = 3f
            interaction.interactionHeight = 4f
            interactionUUID = interaction.uniqueId

            guardarHolograma()
            actualizarVisuales()
            actualizarTexto()
        }
    }

    fun actualizarVisuales() {
        val id = displayUUID ?: return
        val entity = plugin.server.getEntity(id) as? TextDisplay ?: return
        val config = plugin.config

        plugin.server.regionScheduler.run(plugin, entity.location) { _ ->
            val bbType = config.getString("holograma.billboard") ?: "CENTER"
            try {
                entity.billboard = Display.Billboard.valueOf(bbType.uppercase())
            } catch (e: Exception) {
                entity.billboard = Display.Billboard.CENTER
            }

            entity.isShadowed = config.getBoolean("holograma.shadow", true)

            val colorHex = config.getString("holograma.background_color") ?: "#00000000"
            try {
                val cleanHex = if (colorHex.startsWith("#")) colorHex.substring(1) else colorHex
                val argb = cleanHex.toLong(16)
                val alpha = ((argb shr 24) and 0xFF).toInt()
                val red = ((argb shr 16) and 0xFF).toInt()
                val green = ((argb shr 8) and 0xFF).toInt()
                val blue = (argb and 0xFF).toInt()
                entity.backgroundColor = Color.fromARGB(alpha, red, green, blue)
            } catch (e: Exception) {
                entity.backgroundColor = Color.fromARGB(0, 0, 0, 0)
            }

            val scale = config.getDouble("holograma.scale", 1.5).toFloat()
            val transform = entity.transformation
            entity.transformation = Transformation(transform.translation, transform.leftRotation, Vector3f(scale, scale, scale), transform.rightRotation)
        }
    }

    private fun actualizarTexto() {
        val displayId = displayUUID ?: return
        val entity = plugin.server.getEntity(displayId) as? TextDisplay ?: return
        val lang = plugin.languageManager

        plugin.server.regionScheduler.run(plugin, entity.location) { _ ->
            val scores = plugin.puntajeManager.getAllSorted()
            val totalPaginas = if (scores.isEmpty()) 1 else ((scores.size - 1) / 10) + 1

            // Ajustar página por si eliminaron jugadores de la base de datos
            if (paginaActual >= totalPaginas) paginaActual = 0
            if (paginaActual < 0) paginaActual = totalPaginas - 1

            val start = paginaActual * 10
            val end = (start + 10).coerceAtMost(scores.size)
            val subList = if (scores.isNotEmpty() && start < scores.size) scores.subList(start, end) else emptyList()

            val lines = mutableListOf<String>()
            lines.add(lang.get("hologram.title"))
            lines.add(lang.get("hologram.page_info")
                .replace("<page>", (paginaActual + 1).toString())
                .replace("<total>", totalPaginas.toString()))
            lines.add("")

            if (subList.isEmpty()) {
                lines.add(lang.get("hologram.empty"))
            } else {
                val entryTemplate = lang.get("hologram.entry")
                subList.forEachIndexed { index, score ->
                    val rank = start + index + 1
                    val rankColor = if (rank <= 3) lang.get("hologram.rank_colors.$rank") else lang.get("hologram.rank_colors.default")

                    lines.add(entryTemplate
                        .replace("<rank_color>", rankColor)
                        .replace("<rank>", rank.toString())
                        .replace("<name>", score.name)
                        .replace("<points>", score.points.toString()))
                }
            }
            lang.getList("hologram.footer").forEach { lines.add(it) }

            // TextDisplay se actualiza al instante
            entity.text(plugin.messageManager.parse(lines.joinToString("<newline>")))
        }
    }

    // --- CLICK DERECHO (PÁGINA SIGUIENTE) ---
    @EventHandler
    fun alHacerClicDerecho(e: PlayerInteractEntityEvent) {
        val uuid = interactionUUID ?: return
        if (e.rightClicked.uniqueId == uuid) {
            e.isCancelled = true
            paginaActual++
            actualizarTexto() // Actualiza inmediatamente
            e.player.playSound(e.player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f)
            e.player.sendActionBar(plugin.languageManager.component(e.player, "hologram.next_page"))
        }
    }

    // --- CLICK IZQUIERDO (PÁGINA ANTERIOR) ---
    // En las entidades Interaction, el Click Izquierdo llama al evento de Daño
    @EventHandler
    fun alHacerClicIzquierdo(e: EntityDamageByEntityEvent) {
        val uuid = interactionUUID ?: return
        if (e.entity.uniqueId == uuid) {
            e.isCancelled = true
            val p = e.damager as? Player ?: return

            paginaActual--
            actualizarTexto() // Actualiza inmediatamente
            p.playSound(p.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 0.8f)
            p.sendActionBar(plugin.languageManager.component(p, "hologram.previous_page"))
        }
    }

    private fun cargarHolograma() {
        val config = plugin.config
        val dUUID = config.getString("holograma.display_uuid") ?: ""
        val iUUID = config.getString("holograma.interaction_uuid") ?: ""

        if (dUUID.isNotEmpty() && iUUID.isNotEmpty()) {
            displayUUID = UUID.fromString(dUUID)
            interactionUUID = UUID.fromString(iUUID)

            // Recuperar la locación para el scheduler si el servidor reinició
            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                val entity = plugin.server.getEntity(displayUUID!!)
                if (entity != null) {
                    holoLocation = entity.location
                    actualizarVisuales()
                    actualizarTexto()
                }
            }, 40L)
        }
    }

    private fun guardarHolograma() {
        plugin.config.set("holograma.display_uuid", displayUUID?.toString() ?: "")
        plugin.config.set("holograma.interaction_uuid", interactionUUID?.toString() ?: "")
        plugin.saveConfig()
    }

    fun eliminarHolograma() {
        displayUUID?.let { plugin.server.getEntity(it)?.remove() }
        interactionUUID?.let { plugin.server.getEntity(it)?.remove() }
        displayUUID = null
        interactionUUID = null
        holoLocation = null
        plugin.config.set("holograma.display_uuid", "")
        plugin.config.set("holograma.interaction_uuid", "")
        plugin.saveConfig()
    }
}
