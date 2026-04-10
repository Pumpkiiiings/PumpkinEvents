package pumpkin.eventos

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin
import pumpkin.eventos.arena.ArenaManager
import pumpkin.eventos.commands.EventoCommand
import pumpkin.eventos.commands.GamemodeCommand
import pumpkin.eventos.commands.VotarCommand
import pumpkin.eventos.games.blockparty.BlockParty
import pumpkin.eventos.games.lava.SueloLava
import pumpkin.eventos.games.pillars.PillarsOfFortune
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
import pumpkin.eventos.listeners.DeathListener
import pumpkin.eventos.listeners.GameListener
import pumpkin.eventos.manager.EventManager
import pumpkin.eventos.manager.MapManager
import pumpkin.eventos.manager.VoteManager
import pumpkin.eventos.utils.LanguageManager
import pumpkin.eventos.utils.MessageManager

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
        // --- 1. CARGA DE CONFIGURACIÓN ---
        try {
            saveDefaultConfig()
            saveResource("chat-format.yml", false)
            saveResource("messages.yml", false)
        } catch (e: Exception) {
            logger.warning("No se pudo crear algún archivo de configuración (quizás ya existe).")
        }

        // --- 2. INICIALIZACIÓN ---
        messageManager = MessageManager()
        languageManager = LanguageManager(this)
        chatFormatManager = ChatFormatManager(this)
        pumpkin.eventos.utils.WorldUtils.applyToAllWorlds()

        arenaManager = ArenaManager(this)
        mapManager = MapManager(this)
        voteManager = VoteManager()
        eventManager = EventManager(this)

        papaCalienteGame = PapaCaliente(this)
        congeladosGame = Congelados(this)
        dueloFinalGame = DueloFinal(this)

        // --- 3. REGISTRO DE JUEGOS ---
        eventManager.registerGame(SimonDice(this))
        eventManager.registerGame(TntTag(this))
        eventManager.registerGame(TntRun(this))
        eventManager.registerGame(TntSpleef(this)) // El que dispara flechas
        eventManager.registerGame(Spleef(this)) // El clásico con pala
        eventManager.registerGame(SueloLava(this))
        eventManager.registerGame(PillarsOfFortune(this))
        eventManager.registerGame(Sumo(this))
        eventManager.registerGame(BlockParty(this))
        eventManager.registerGame(pumpkin.eventos.games.luzroja.LuzRojaLuzVerde(this))

        // --- 4. HUD ---
        boardManager = BoardManager(this)
        boardManager.startTasks()

        // --- 5. EVENTOS ---
        val pm = server.pluginManager
        pm.registerEvents(ChatListener(this), this)
        pm.registerEvents(GameListener(this), this)
        pm.registerEvents(DeathListener(this), this)
        pm.registerEvents(SimonListener(this), this)
        pm.registerEvents(papaCalienteGame, this)
        pm.registerEvents(congeladosGame, this)
        pm.registerEvents(dueloFinalGame, this)
        server.pluginManager.registerEvents(pumpkin.eventos.listeners.WorldListener(), this)
        pm.registerEvents(pumpkin.eventos.listeners.ConnectionListener(this), this)

        // --- 6. COMANDOS ---
        this.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()
            EventoCommand.register(commands, this)
            VotarCommand.register(commands, this)
            GamemodeCommand.register(commands, this)
        }

        logger.info("⚡ ¡Pumpkin Eventos (Core V2) ha sido activado correctamente!")
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

        logger.info("🛑 Pumpkin Eventos Core apagado de forma segura.")
    }
}
