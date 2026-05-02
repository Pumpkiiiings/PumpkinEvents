package pumpkin.eventos

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin
import pumpkin.eventos.arena.ArenaManager
import pumpkin.eventos.arena.ArenaSetupListener
import pumpkin.eventos.commands.EventoCommand
import pumpkin.eventos.commands.GamemodeCommand
import pumpkin.eventos.commands.PuntajeCommand
import pumpkin.eventos.commands.VotarCommand
import pumpkin.eventos.commands.SpectateCommand
import pumpkin.eventos.commands.ExtrasCommand // <-- NUEVO COMANDO EXTRAS
import pumpkin.eventos.games.corona.RobaLaCorona
import pumpkin.eventos.games.corona.RobaLaCoronaListener
import pumpkin.eventos.games.cristales.Cristales
import pumpkin.eventos.games.cristales.CristalesListener
import pumpkin.eventos.games.jalarcuerda.JalarCuerda
import pumpkin.eventos.games.jalarcuerda.JalarCuerdaListener
import pumpkin.eventos.games.lava.SueloLava
import pumpkin.eventos.games.lava.SueloLavaListener
import pumpkin.eventos.games.luzroja.LuzRojaLuzVerde
import pumpkin.eventos.games.luzroja.LuzRojaListener
import pumpkin.eventos.games.pillars.PillarsOfFortune
import pumpkin.eventos.games.pillars.PillarsListener
import pumpkin.eventos.games.ruletarusa.RuletaRusa
import pumpkin.eventos.games.ruletarusa.RuletaRusaListener
import pumpkin.eventos.games.sillas.SillasMusicales
import pumpkin.eventos.games.sillas.SillasMusicalesListener
import pumpkin.eventos.games.sillas.SillasListener
import pumpkin.eventos.games.simondice.SimonDice
import pumpkin.eventos.games.simondice.SimonDiceGameListener
import pumpkin.eventos.games.simondice.SimonListener
import pumpkin.eventos.games.simondice.minigames.Congelados
import pumpkin.eventos.games.simondice.minigames.DueloFinal
import pumpkin.eventos.games.simondice.minigames.PapaCaliente
import pumpkin.eventos.games.spleef.Spleef
import pumpkin.eventos.games.spleef.SpleefListener
import pumpkin.eventos.games.sumo.Sumo
import pumpkin.eventos.games.sumo.SumoListener
import pumpkin.eventos.games.tntgames.TntRun
import pumpkin.eventos.games.tntgames.listeners.TntRunListener
import pumpkin.eventos.games.tntgames.TntSpleef
import pumpkin.eventos.games.tntgames.listeners.TntSpleefListener
import pumpkin.eventos.games.tntgames.TntTag
import pumpkin.eventos.games.tntgames.listeners.TntTagListener
import pumpkin.eventos.games.skywars.Skywars
import pumpkin.eventos.games.skywars.SkywarsListener
import pumpkin.eventos.games.skywars.SkywarsTntFireballListener
import pumpkin.eventos.games.iceboat.IceBoatRacing
import pumpkin.eventos.games.hideandseek.HideAndSeek
import pumpkin.eventos.games.hideandseek.HideAndSeekListener
import pumpkin.eventos.games.iceboat.IceBoatListener
import pumpkin.eventos.games.findbutton.FindTheButton
import pumpkin.eventos.games.findbutton.FindTheButtonListener
import pumpkin.eventos.games.buildbattle.BuildBattle
import pumpkin.eventos.games.buildbattle.BuildBattleListener
import pumpkin.eventos.games.miniwalls.MiniWalls
import pumpkin.eventos.games.miniwalls.MiniWallsListener
import pumpkin.eventos.games.battleroyale.BattleRoyale
import pumpkin.eventos.games.battleroyale.BattleRoyaleListener
import pumpkin.eventos.games.battleroyale.GlowPacketListener
import pumpkin.eventos.games.parkour.Parkour
import pumpkin.eventos.games.parkour.ParkourListener

import pumpkin.eventos.hud.BoardManager
import pumpkin.eventos.hud.ChatFormatManager
import pumpkin.eventos.listeners.ChatListener
import pumpkin.eventos.listeners.ConnectionListener
import pumpkin.eventos.listeners.DeathListener
import pumpkin.eventos.listeners.GlobalDamageListener
import pumpkin.eventos.listeners.LobbyProtectionListener
import pumpkin.eventos.listeners.WorldListener
import pumpkin.eventos.manager.EventManager
import pumpkin.eventos.manager.MapManager
import pumpkin.eventos.manager.MenuManager
import pumpkin.eventos.manager.PotionVoteManager
import pumpkin.eventos.manager.PuntajeManager
import pumpkin.eventos.manager.PvpVoteManager
import pumpkin.eventos.manager.VoteManager
import pumpkin.eventos.manager.PuntajeHoloManager
import pumpkin.eventos.manager.ExtrasVoteManager // <-- NUEVO MANAGER EXTRAS
import pumpkin.eventos.utils.LanguageManager
import pumpkin.eventos.utils.MessageManager
import pumpkin.eventos.utils.WorldUtils
import pumpkin.eventos.hooks.EventosExpansion

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder

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
    lateinit var menuManager: MenuManager
    lateinit var potionVoteManager: PotionVoteManager
    lateinit var pvpVoteManager: PvpVoteManager
    lateinit var extrasVoteManager: ExtrasVoteManager // <-- AÑADIDO

    lateinit var tntTagListener: TntTagListener
    lateinit var tntRunGame: TntRun

    lateinit var papaCalienteGame: PapaCaliente
    lateinit var congeladosGame: Congelados
    lateinit var dueloFinalGame: DueloFinal

    // VARIABLE MÁGICA: Lee la versión directamente de tu plugin.yml
    val pluginVersion: String
        get() = pluginMeta.version

    override fun onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        PacketEvents.getAPI().init()
        PacketEvents.getAPI().eventManager.registerListener(GlowPacketListener(this))
        val mm = MiniMessage.miniMessage()

        // Usamos $pluginVersion para que se actualice solo
        componentLogger.info(mm.deserialize("<#FF5500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</#FF5500>"))
        componentLogger.info(mm.deserialize("<#CCFF00>⚡ EVENTOS CORE v$pluginVersion - CARGANDO ⚡</#CCFF00>"))
        componentLogger.info(mm.deserialize("<#FF5500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</#FF5500>"))

        // --- 2. CARGA DE CONFIGURACIÓN ---
        try {
            saveDefaultConfig()
            saveResource("chat-format.yml", false)
            saveResource("messages.yml", false)
            saveResource("scoreboards.yml", false)
        } catch (e: Exception) {}

        // --- 3. INICIALIZACIÓN DE MANAGERS ---
        messageManager = MessageManager()
        languageManager = LanguageManager(this)
        chatFormatManager = ChatFormatManager(this)

        server.messenger.registerOutgoingPluginChannel(this, "openboatutils:settings")

        WorldUtils.applyToAllWorlds()

        arenaManager = ArenaManager(this)
        mapManager = MapManager(this)
        voteManager = VoteManager()
        eventManager = EventManager(this)

        puntajeManager = PuntajeManager(this)
        puntajeHoloManager = PuntajeHoloManager(this)

        menuManager = MenuManager(this)
        potionVoteManager = PotionVoteManager(this)
        pvpVoteManager = PvpVoteManager(this)
        extrasVoteManager = ExtrasVoteManager(this) // <-- INICIALIZADO

        tntTagListener = TntTagListener(this)

        papaCalienteGame = PapaCaliente(this)
        congeladosGame = Congelados(this)
        dueloFinalGame = DueloFinal(this)

        // --- 4. REGISTRO DE JUEGOS ---
        eventManager.registerGame(SimonDice(this))
        eventManager.registerGame(TntTag(this))
        tntRunGame = TntRun(this)
        eventManager.registerGame(tntRunGame)
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
        eventManager.registerGame(HideAndSeek(this))
        eventManager.registerGame(RobaLaCorona(this))
        eventManager.registerGame(Skywars(this, false))
        eventManager.registerGame(Skywars(this, true))

        val miniWallsGame = MiniWalls(this)
        eventManager.registerGame(miniWallsGame)
        eventManager.registerGame(IceBoatRacing(this))
        eventManager.registerGame(FindTheButton(this))
        eventManager.registerGame(BuildBattle(this, false))  // Solo
        eventManager.registerGame(BuildBattle(this, true))   // Equipos
        eventManager.registerGame(BattleRoyale(this, false)) // Battle Royale Solo
        eventManager.registerGame(BattleRoyale(this, true))  // Battle Royale Duos
        eventManager.registerGame(Parkour(this, false))       // Parkour Solo
        eventManager.registerGame(Parkour(this, true))        // Parkour Dúos

        // --- 5. HUD Y SCOREBOARD ---
        boardManager = BoardManager(this)
        boardManager.startTasks()

        // --- 5b. PLACEHOLDERAPI ---
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            EventosExpansion(this).register()
            componentLogger.info(mm.deserialize("<green>[PumpkinEventos] PlaceholderAPI detectado - %eventos_team% registrado.</green>"))
        }

        // --- 6. LISTENERS ---
        val pm = server.pluginManager
        pm.registerEvents(puntajeHoloManager, this)
        pm.registerEvents(ChatListener(this), this)
        pm.registerEvents(DeathListener(this), this)
        pm.registerEvents(SimonListener(this), this)
        pm.registerEvents(ConnectionListener(this), this)
        pm.registerEvents(WorldListener(), this)
        pm.registerEvents(LobbyProtectionListener(this), this)
        pm.registerEvents(pumpkin.eventos.utils.VoidUtil(this), this)
        pm.registerEvents(ArenaSetupListener(this), this)

        // GlobalDamageListener — lógica de muerte letal, bloqueo global de bloques, drop
        pm.registerEvents(GlobalDamageListener(this), this)

        // ─── Listeners individuales por juego ───────────────────────────────
        pm.registerEvents(tntTagListener, this)               // TntTag
        pm.registerEvents(TntRunListener(this), this)         // TntRun (listener general)
        pm.registerEvents(tntRunGame, this)                   // TntRun (uso pluma feather)
        pm.registerEvents(TntSpleefListener(this), this)      // TntSpleef
        pm.registerEvents(SpleefListener(this), this)         // Spleef
        pm.registerEvents(SueloLavaListener(this), this)      // SueloLava
        pm.registerEvents(PillarsListener(this), this)        // PillarsOfFortune
        pm.registerEvents(SumoListener(this), this)           // Sumo
        pm.registerEvents(LuzRojaListener(this), this)        // LuzRoja
        pm.registerEvents(SillasListener(this), this)         // Sillas (interact)
        pm.registerEvents(SillasMusicalesListener(this), this)// Sillas (GSit/sneak/dmg)
        pm.registerEvents(RuletaRusaListener(this), this)     // RuletaRusa
        pm.registerEvents(CristalesListener(this), this)      // Cristales
        pm.registerEvents(JalarCuerdaListener(this), this)    // JalarCuerda
        pm.registerEvents(HideAndSeekListener(this), this)    // HideAndSeek
        pm.registerEvents(RobaLaCoronaListener(this), this)   // RobaLaCorona
        pm.registerEvents(SimonDiceGameListener(this), this)  // SimonDice (damage/drop)
        pm.registerEvents(SkywarsListener(this), this)        // Skywars
        pm.registerEvents(SkywarsTntFireballListener(this), this) // Skywars TNT/Fireball
        pm.registerEvents(IceBoatListener(this), this)        // IceBoat
        pm.registerEvents(MiniWallsListener(this, miniWallsGame), this) // MiniWalls
        pm.registerEvents(FindTheButtonListener(this), this)  // FindTheButton
        pm.registerEvents(BuildBattleListener(this), this)    // BuildBattle
        pm.registerEvents(BattleRoyaleListener(this), this)   // BattleRoyale
        pm.registerEvents(ParkourListener(this), this)         // Parkour

        // MenuManager como listener (clicks en inventarios)
        pm.registerEvents(menuManager, this)

        // Sub-juegos de Simón Dice
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
            ExtrasCommand.register(commands, this) // <-- REGISTRADO AQUÍ
        }

        // Usamos la misma variable para el mensaje final
        componentLogger.info(mm.deserialize("<#39FF14>✔ PumpkinEventos v$pluginVersion cargado correctamente.</#39FF14>"))
    }

    override fun onDisable() {
        if (::eventManager.isInitialized) {
            val currentGame = eventManager.currentGame
            if (currentGame != null && currentGame.isRunning) currentGame.stop()
        }
        if (::arenaManager.isInitialized) arenaManager.shutdown()
        if (::mapManager.isInitialized) mapManager.shutdown()

        PacketEvents.getAPI().terminate()
    }
}
