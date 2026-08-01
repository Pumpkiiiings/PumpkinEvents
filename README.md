
***

# 🎃 Pumpkin Events - Core de Eventos

![Version](https://img.shields.io/badge/Versión-3.3.9-orange.svg)
![PaperMC](https://img.shields.io/badge/PaperMC-1.21.4-black.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-blue.svg)
![Java](https://img.shields.io/badge/Java-21-red.svg)

**Pumpkin Eventos** es un Core de Minijuegos y Eventos de alto rendimiento para **Paper 1.21.4**. Desarrollado en Kotlin, está construido para soportar cientos de jugadores gracias a su procesamiento por *chunks* y al uso de los *RegionSchedulers* modernos.

---

## ✨ Características Principales

- 🚀 **Schedulers modernos:** Sin `BukkitRunnable`. El core usa `GlobalRegionScheduler` y `AsyncScheduler`.
- 🌍 **AdvancedSlimePaper:** Carga de mapas en memoria RAM y reinicio de arenas en milisegundos.
- 🎨 **Textos externalizados:** Mensajes, Scoreboards, BossBars y prefijos vía **MiniMessage** (gradientes y HEX) configurables desde `messages.yml` y `scoreboards.yml`.
- 📊 **HUD Dinámico:** Scoreboards y Bossbars sin parpadeo que cambian según el minijuego activo.
- 🗳️ **Votaciones múltiples:** minijuego, PvP, efectos de poción y desastres (extras), todas por clic en el chat.
- ⚡ **Comandos Brigadier:** Autocompletado nativo para los comandos de staff y atajos de Gamemode.
- 🏆 **Sistema de puntajes** con holograma persistente (`PuntajeHoloManager`) y expansión de PlaceholderAPI.

---

## 🎮 Minijuegos Incluidos

26 modos registrados (algunos son variantes solo/dúos del mismo juego). El `id` es el que se usa en `/evento iniciar <id>` y como `<tipo>` al crear la arena.

| # | Juego | `id` |
| :-- | :--- | :--- |
| 1 | Simón Dice | `simondice` |
| 2 | TNT Tag | `tnttag` |
| 3 | TNT Run | `tntrun` |
| 4 | TNT Spleef | `tntspleef` |
| 5 | Spleef Clásico | `spleef` |
| 6 | El Suelo es Lava | `suelolava` |
| 7 | Pillars of Fortune | `pillars` |
| 8 | Sumo 1v1 | `sumo` |
| 9 | Luz Roja, Luz Verde | `luzroja` |
| 10 | Sillas Musicales | `sillas` |
| 11 | Ruleta Rusa | `ruletarusa` |
| 12 | Puente de Cristal | `cristales` |
| 13 | Jalar la Cuerda | `jalarcuerda` |
| 14 | Hide & Seek | `hideandseek` |
| 15 | Roba la Corona | `corona` |
| 16 | Skywars Solos | `skywars_solos` |
| 17 | Skywars Dúos | `skywars_duos` |
| 18 | Mini Walls | `miniwalls` |
| 19 | Ice Boat Racing | `iceboat` |
| 20 | Buscar el Botón | `findbutton` |
| 21 | Build Battle | `buildbattle_solo` |
| 22 | Build Battle Equipos | `buildbattle_teams` |
| 23 | Battle Royale | `battleroyale` |
| 24 | Battle Royale Dúos | `battleroyale_duos` |
| 25 | Parkour | `parkour` |
| 26 | Parkour Dúos | `parkour_duos` |

> Descripciones detalladas de cada modo en [`DOCS_ES/Lista_juegos.md`](DOCS_ES/Lista_juegos.md).

---

## ⚙️ Comandos del Sistema

### Administración (`pumpkin.admin` o `pumpkin.evento.*`)
Prefijo `/evento` (Alias: `/eventos`, `/ev`).

| Comando | Descripción |
| :--- | :--- |
| `/evento ayuda` | Muestra la lista de comandos. |
| `/evento reload` | Recarga configuraciones y textos. |
| `/evento lanzarvotacion` | Inicia una votación global interactiva en el chat. |
| `/evento ruleta` | Gira la ruleta de minijuegos con sonidos y títulos. |
| `/evento iniciar <juego> [modo] [streamer]` | Fuerza el inicio de un evento saltándose la votación. |
| `/evento detener` | Aborta el evento actual y devuelve a todos al Lobby. |
| `/evento revivir <jugador>` | Revive a un espectador y lo mete al juego. |
| `/evento narrator` | Marca al emisor como narrador (entra en modo espectador). |
| `/evento setup lobby \| waiting` | Define el lobby principal / spawn de espera. |

### Setup de Mapas (`/evento arena ...`)
1. `/evento arena create <Nombre> <Tipo>`
2. `/evento arena setcenter` *(Centro del mapa)*
3. `/evento arena addspawn` *(Puntos de aparición / Línea de salida)*
4. `/evento arena setgradas` · `setdueloa` · `setduelob` *(Sumo / Luz Roja)*
5. `/evento arena addchair` *(Sillas Musicales)*
6. `/evento arena scanbuildbattle` *(Detecta los Bloques de Oro colocados con WorldEdit)*
7. `/evento arena edit <Nombre>` · `duplicate` · `cancel`
8. `/evento arena save` *(Guarda y compila el mapa en `arenas.yml`)*

> Guía paso a paso en [`DOCS_ES/Guia_setup_mapas.md`](DOCS_ES/Guia_setup_mapas.md).

### Comandos Generales
| Comando | Permiso | Descripción |
| :--- | :--- | :--- |
| `/gamemode <0/1/2/3> [Jugador]` | `pumpkin.command.gamemode` | Cambia el modo de juego. |
| `/gmc`, `/gms`, `/gma`, `/gmsp` | `pumpkin.command.gamemode` | Atajos rápidos de Gamemode. |
| `/votar <juego>` | *Ninguno* | Vota por el minijuego favorito. |
| `/pvote <efecto>` | *Ninguno* | Vota por un efecto de poción. |
| `/pvpvote <si\|no>` | *Ninguno* | Vota si la partida lleva PvP. |
| `/evote <desastre>` | *Ninguno* | Vota por un desastre (extras). |
| `/extras <start\|startall\|vote>` | `pumpkin.evento.extras` | Fuerza desastres o lanza su votación. |
| `/puntaje` (`/puntos`, `/score`) | *Ninguno* | Sistema de puntajes del servidor. |
| `/espectear` | *Ninguno* | Vuelve al mapa del evento como espectador. |

---

## 📦 Instalación y Dependencias

**Requeridas** (`depend` — el plugin no arranca sin ellas):
- **[LuckPerms](https://luckperms.net/)** — rangos, pesos del Tablist y chat format.
- **[GSit](https://www.spigotmc.org/resources/gsit.62325/)** — usado por Sillas Musicales y Ruleta Rusa.

**Opcionales** (`softdepend`):
- **[AdvancedSlimePaper (ASP)](https://github.com/InfernalSuite/AdvancedSlimePaper)** — instanciación de mundos en memoria.
- **[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)** — registra `%eventos_team%`.
- **LibsDisguises** — disfraces en Hide & Seek.

Incluidas en el jar vía shadow: Kotlin stdlib, kotlinx-coroutines, PacketEvents, Triumph-GUI.

### Pasos
1. Coloca `PumpkinEvents-<version>.jar` en la carpeta `plugins`.
2. Inicia el servidor para generar las carpetas y los YAML por defecto.
3. Coloca tus plantillas de mapas `.slime` en `plugins/PumpkinEventos/slime_worlds`.
4. Configura el Lobby principal con `/evento setup lobby`.
5. Crea las arenas con `/evento arena create`.

---

## 🔨 Compilación

```bash
./gradlew shadowJar
```

El jar sale en `build/libs/PumpkinEvents-<version>.jar`.

> ⚠️ **Requiere JDK 21.** Gradle 8.14 no soporta Java 25 y falla con un error opaco (`25.0.3`); actualizar a Gradle 9 tampoco es opción porque Shadow 8.1.1 es incompatible. Por eso `gradle.properties` fija `org.gradle.java.home` al JDK 21 — **ajusta esa ruta si tu JDK está en otro sitio**.

---

## 📁 Estructura

```
pumpkin/eventos/
├── PumpkinEventos.kt   Punto de entrada: registra managers, juegos, listeners y comandos
├── arena/              ArenaManager + setup in-game
├── commands/           Comandos Brigadier + /puntaje
├── games/              EventGame (base abstracta) + un paquete por minijuego
├── hooks/              LuckPerms · PlaceholderAPI
├── hud/                Scoreboard, Bossbar y formato de chat
├── listeners/          Listeners globales (chat, muerte, conexión, daño, lobby)
├── manager/            Evento, mapas, votaciones, puntajes, menús
└── utils/              Utilidades de mundo, mensajes e idiomas
```

Cada minijuego es una subclase de `EventGame` (`onStart`/`onStop`/`checkWinner`) con su propio `Listener`, y se da de alta con `eventManager.registerGame(...)` en `PumpkinEventos.onEnable()`.

---

## 👨‍💻 Créditos

- **Desarrollo Core & Lógica:** Pumpkingz
- **Para:** Lyric Network
