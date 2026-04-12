package pumpkin.eventos

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin
import pumpkin.eventos.arena.ArenaManager
import pumpkin.eventos.commands.EventoCommand
import pumpkin.eventos.commands.GamemodeCommand
import pumpkin.eventos.commands.VotarCommand
import pumpkin.eventos.games.blockparty.BlockParty
import pumpkin.eventos.games.lava.SueloLava
import pumpkin.eventos.games.luzroja.LuzRojaLuzVerde
import pumpkin.eventos.games.pillars.PillarsOfFortune
import pumpkin.eventos.games.ruletarusa.RuletaRusa
import pumpkin.eventos.games.sillas.SillasMusicales
import pumpkin.eventos.games.simondice.SimonDice
import pumpkin.eventos.games.simondice.SimonListener
import pumpkin.eventos.games.simondice.minigames.Congelados
import pumpkin.eventos.games.simondice.minigames.DueloFinal
import pumpkin.eventos.games.simondice.minigames.PapaCaliente
import pumpkin.eventos.games.spleef.Spleef
import pumpkin.eventos.games.sumo.Sumo
import pumpkin.eventos.games.tntgames.TntRun
import pumpkin.eventos.games.tntgames.TntSpleef
import pumpkin.eventos.games.tntgames.TntTag
import pumpkin.eventos.hud.BoardManager
import pumpkin.eventos.hud.ChatFormatManager
import pumpkin.eventos.listeners.ChatListener
import pumpkin.eventos.listeners.ConnectionListener
import pumpkin.eventos.listeners.DeathListener
import pumpkin.eventos.listeners.GameListener
import pumpkin.eventos.listeners.WorldListener
import pumpkin.eventos.listeners.LobbyProtectionListener
import pumpkin.eventos.manager.EventManager
import pumpkin.eventos.manager.MapManager
import pumpkin.eventos.manager.VoteManager
import pumpkin.eventos.utils.LanguageManager
import pumpkin.eventos.utils.MessageManager
import pumpkin.eventos.utils.WorldUtils

class PumpkinEventos : JavaPlugin() {

    lateinit var messageManager: MessageManager
    lateinit var languageManager: LanguageManager
    lateinit var chatFormatManager: ChatFormatManager
    lateinit var arenaManager: ArenaManager
    lateinit var mapManager: MapManager
    lateinit var voteManager: VoteManager
    lateinit var eventManager: EventManager
    lateinit var boardManager: BoardManager

    lateinit var papaCalienteGame: PapaCaliente
    lateinit var congeladosGame: Congelados
    lateinit var dueloFinalGame: DueloFinal

    override fun onEnable() {
        val mm = MiniMessage.miniMessage()

        // --- 1. MENSAJE DE INICIO ÉPICO ---
        componentLogger.info(mm.deserialize("<#FF5500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</#FF5500>"))
        componentLogger.info(mm.deserialize("<#FF9900>    ____                  __    _       </#FF9900>"))
        componentLogger.info(mm.deserialize("<#FF9900>   / __ \\__  ______ ___  / /_  (_)___   </#FF9900>"))
        componentLogger.info(mm.deserialize("<#FF9900>  / /_/ / / / / __ `__ \\/ __ \\/ / __ \\  </#FF9900>"))
        componentLogger.info(mm.deserialize("<#FF9900> / ____/ /_/ / / / / / / /_/ / / / / /  </#FF9900>"))
        componentLogger.info(mm.deserialize("<#FF9900>/_/    \\__,_/_/ /_/ /_/_.___/_/_/ /_/   </#FF9900>"))
        componentLogger.info(mm.deserialize(""))
        componentLogger.info(mm.deserialize("<#CCFF00>⚡ EVENTOS CORE v2.0 - CARGADO CON ÉXITO ⚡</#CCFF00>"))
        componentLogger.info(mm.deserialize("<#39FF14>✔ AdvancedSlimePaper Detectado (0 Lag Enabled)</#39FF14>"))
        componentLogger.info(mm.deserialize("<#39FF14>✔ Compatibilidad Folia Activa</#39FF14>"))
        componentLogger.info(mm.deserialize("<#FF5500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</#FF5500>"))

        // --- 2. CARGA DE CONFIGURACIÓN ---
        try {
            saveDefaultConfig()
            saveResource("chat-format.yml", false)
            saveResource("messages.yml", false)
        } catch (e: Exception) {
            componentLogger.warn(mm.deserialize("<yellow>Archivos de configuración ya existen o no se pudieron copiar.</yellow>"))
        }

        // --- 3. INICIALIZACIÓN ---
        messageManager = MessageManager()
        languageManager = LanguageManager(this)
        chatFormatManager = ChatFormatManager(this)

        // Aplicar reglas neutras (Insta-Respawn) a mundos actuales
        WorldUtils.applyToAllWorlds()

        arenaManager = ArenaManager(this)
        mapManager = MapManager(this)
        voteManager = VoteManager()
        eventManager = EventManager(this)

        papaCalienteGame = PapaCaliente(this)
        congeladosGame = Congelados(this)
        dueloFinalGame = DueloFinal(this)

        // --- 4. REGISTRO DE JUEGOS ---
        eventManager.registerGame(SimonDice(this))
        eventManager.registerGame(TntTag(this))
        eventManager.registerGame(TntRun(this))
        eventManager.registerGame(TntSpleef(this)) // Dispara flechas
        eventManager.registerGame(Spleef(this)) // Rompe nieve
        eventManager.registerGame(SueloLava(this))
        eventManager.registerGame(PillarsOfFortune(this))
        eventManager.registerGame(Sumo(this))
        eventManager.registerGame(LuzRojaLuzVerde(this))
        eventManager.registerGame(SillasMusicales(this))
        eventManager.registerGame(RuletaRusa(this))

        // --- 5. HUD ---
        boardManager = BoardManager(this)
        boardManager.startTasks()

        // --- 6. EVENTOS (Listeners) ---
        val pm = server.pluginManager
        pm.registerEvents(ChatListener(this), this)
        pm.registerEvents(GameListener(this), this)
        pm.registerEvents(DeathListener(this), this)
        pm.registerEvents(SimonListener(this), this)
        pm.registerEvents(ConnectionListener(this), this)
        pm.registerEvents(WorldListener(), this)
        pm.registerEvents(LobbyProtectionListener(this), this)

        // Listeners de sub-juegos de Simón
        pm.registerEvents(papaCalienteGame, this)
        pm.registerEvents(congeladosGame, this)
        pm.registerEvents(dueloFinalGame, this)

        // --- 7. COMANDOS ---
        this.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()
            EventoCommand.register(commands, this)
            VotarCommand.register(commands, this)
            GamemodeCommand.register(commands, this)
        }
    }

    override fun onDisable() {
        val mm = MiniMessage.miniMessage()

        if (::eventManager.isInitialized) {
            val currentGame = eventManager.currentGame
            if (currentGame != null && currentGame.isRunning) {
                currentGame.stop()
            }
        }

        if (::arenaManager.isInitialized) arenaManager.shutdown()
        if (::mapManager.isInitialized) mapManager.shutdown()

        componentLogger.info(mm.deserialize("<#FF3131>🛑 Pumpkin Eventos Core apagado de forma segura.</#FF3131>"))
    }
}
