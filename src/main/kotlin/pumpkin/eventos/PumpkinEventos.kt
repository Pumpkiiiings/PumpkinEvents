package pumpkin.eventos

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin
import pumpkin.eventos.arena.ArenaManager
import pumpkin.eventos.commands.EventoCommand
import pumpkin.eventos.commands.GamemodeCommand
import pumpkin.eventos.commands.PuntajeCommand
import pumpkin.eventos.commands.VotarCommand
import pumpkin.eventos.commands.SpectateCommand
import pumpkin.eventos.games.cristales.Cristales
import pumpkin.eventos.games.jalarcuerda.JalarCuerda
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
import pumpkin.eventos.games.skywars.Skywars
import pumpkin.eventos.games.skywars.SkywarsListener
import pumpkin.eventos.games.iceboat.IceBoatRacing
import pumpkin.eventos.games.iceboat.IceBoatListener
import pumpkin.eventos.games.miniwalls.MiniWalls
import pumpkin.eventos.games.miniwalls.MiniWallsListener
import pumpkin.eventos.hud.BoardManager
import pumpkin.eventos.hud.ChatFormatManager
import pumpkin.eventos.listeners.ChatListener
import pumpkin.eventos.listeners.ConnectionListener
import pumpkin.eventos.listeners.DeathListener
import pumpkin.eventos.listeners.GameListener
import pumpkin.eventos.listeners.LobbyProtectionListener
import pumpkin.eventos.listeners.WorldListener
import pumpkin.eventos.manager.EventManager
import pumpkin.eventos.manager.MapManager
import pumpkin.eventos.manager.PuntajeManager
import pumpkin.eventos.manager.VoteManager
import pumpkin.eventos.manager.PuntajeHoloManager
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
    lateinit var puntajeManager: PuntajeManager
    lateinit var puntajeHoloManager: PuntajeHoloManager

    lateinit var papaCalienteGame: PapaCaliente
    lateinit var congeladosGame: Congelados
    lateinit var dueloFinalGame: DueloFinal

    override fun onEnable() {
        val mm = MiniMessage.miniMessage()

        // --- 1. MENSAJE DE INICIO ÉPICO ---
        componentLogger.info(mm.deserialize("<#FF5500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</#FF5500>"))
        componentLogger.info(mm.deserialize("<#CCFF00>⚡ EVENTOS CORE v3.1.0 - CARGANDO ⚡</#CCFF00>"))
        componentLogger.info(mm.deserialize("<#FF5500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</#FF5500>"))

        // --- 2. CARGA DE CONFIGURACIÓN ---
        try {
            saveDefaultConfig()
            saveResource("chat-format.yml", false)
            saveResource("messages.yml", false)
        } catch (e: Exception) {}

        // --- 3. INICIALIZACIÓN DE MANAGERS ---
        messageManager = MessageManager()
        languageManager = LanguageManager(this)
        chatFormatManager = ChatFormatManager(this)

        WorldUtils.applyToAllWorlds()

        arenaManager = ArenaManager(this)
        mapManager = MapManager(this)
        voteManager = VoteManager()
        eventManager = EventManager(this)

        puntajeManager = PuntajeManager(this)
        puntajeHoloManager = PuntajeHoloManager(this)

        papaCalienteGame = PapaCaliente(this)
        congeladosGame = Congelados(this)
        dueloFinalGame = DueloFinal(this)

        // --- 4. REGISTRO DE JUEGOS ---
        eventManager.registerGame(SimonDice(this))
        eventManager.registerGame(TntTag(this))
        eventManager.registerGame(TntRun(this))
        eventManager.registerGame(TntSpleef(this))
        eventManager.registerGame(Spleef(this))
        eventManager.registerGame(SueloLava(this))
        eventManager.registerGame(PillarsOfFortune(this))
        eventManager.registerGame(Sumo(this))
        eventManager.registerGame(LuzRojaLuzVerde(this))
        eventManager.registerGame(SillasMusicales(this))
        eventManager.registerGame(RuletaRusa(this))
        eventManager.registerGame(Cristales(this))
        eventManager.registerGame(JalarCuerda(this))
        eventManager.registerGame(pumpkin.eventos.games.corona.RobaLaCorona(this))
        eventManager.registerGame(Skywars(this, false)) // Skywars Solos
        eventManager.registerGame(Skywars(this, true))  // Skywars Duos

        // Instanciar y registrar Mini Walls para el listener
        val miniWallsGame = MiniWalls(this)
        eventManager.registerGame(miniWallsGame)

        // Registrar Ice Boat
        eventManager.registerGame(IceBoatRacing(this))

        // --- 5. HUD Y SCOREBOARD ---
        boardManager = BoardManager(this)
        boardManager.startTasks()

        // --- 6. EVENTOS (LISTENERS) ---
        val pm = server.pluginManager
        pm.registerEvents(puntajeHoloManager, this)
        pm.registerEvents(ChatListener(this), this)
        pm.registerEvents(GameListener(this), this)
        pm.registerEvents(DeathListener(this), this)
        pm.registerEvents(SimonListener(this), this)
        pm.registerEvents(ConnectionListener(this), this)
        pm.registerEvents(WorldListener(), this)
        pm.registerEvents(LobbyProtectionListener(this), this)
        pm.registerEvents(pumpkin.eventos.utils.VoidUtil(this), this)

        // Listeners específicos de juegos
        pm.registerEvents(SkywarsListener(this), this)
        pm.registerEvents(IceBoatListener(this), this)
        pm.registerEvents(MiniWallsListener(this, miniWallsGame), this)

        // Listeners de sub-juegos de Simón
        pm.registerEvents(papaCalienteGame, this)
        pm.registerEvents(congeladosGame, this)
        pm.registerEvents(dueloFinalGame, this)

        // --- 7. COMANDOS ---
        val puntajeCmd = PuntajeCommand(this)
        getCommand("puntaje")?.setExecutor(puntajeCmd)
        getCommand("puntaje")?.tabCompleter = puntajeCmd

        this.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()
            EventoCommand.register(commands, this)
            VotarCommand.register(commands, this)
            GamemodeCommand.register(commands, this)
            SpectateCommand(this)
        }
    }

    override fun onDisable() {
        if (::eventManager.isInitialized) {
            val currentGame = eventManager.currentGame
            if (currentGame != null && currentGame.isRunning) {
                currentGame.stop()
            }
        }
        if (::arenaManager.isInitialized) arenaManager.shutdown()
        if (::mapManager.isInitialized) mapManager.shutdown()
    }
}
