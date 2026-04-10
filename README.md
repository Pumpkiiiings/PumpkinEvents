
***

# 🎃 Pumpkin Eventos - Core V2

![Version](https://img.shields.io/badge/Versión-2.0-orange.svg)
![PaperMC](https://img.shields.io/badge/PaperMC-1.21.x-black.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.x-blue.svg)
![Folia](https://img.shields.io/badge/Folia-Ready-success.svg)

**Pumpkin Eventos** es un Core de Minijuegos y Eventos de alto rendimiento diseñado exclusivamente para **Paper/Folia 1.21+**. Desarrollado en Kotlin, está construido para soportar cientos de jugadores sin latencia (0 Lag) gracias a su motor de procesamiento por *Chunks* y el uso de los modernos *RegionSchedulers*.

---

## ✨ Características Principales

- 🚀 **Folia & Paper Ready:** Adiós a los `BukkitRunnable`. Todo el core utiliza la API moderna de `GlobalRegionScheduler` y `AsyncScheduler`.
- 🌍 **AdvancedSlimePaper:** Carga de mapas instantánea en memoria RAM. Cero fugas de memoria (Memory Leaks) y reinicio de arenas en milisegundos.
- 🎨 **100% Personalizable (0 Hardcode):** Todos los mensajes, Scoreboards, BossBars y prefijos usan **MiniMessage** (Soporte para gradientes y colores HEX) configurables desde `messages.yml`.
- 📊 **HUD Dinámico:** Scoreboards y Bossbars sin parpadeo (*Flicker-free*) que cambian automáticamente según el minijuego activo.
- 🗳️ **Sistema de Votación y Ruleta:** Los jugadores pueden votar por el siguiente evento haciendo clic en el chat, o un Admin puede girar la ruleta animada con sonidos.
- ⚡ **Comandos Brigadier:** Autocompletado nativo súper rápido para todos los comandos del staff y atajos de Gamemode (`/gmc`, `/gms`).

---

## 🎮 Minijuegos Incluidos (10 Modos)

1. 🟢 **Simón Dice:** Modo Automático (con IA de retos aleatorios) o Modo Manual (GUI interactivo para el Streamer). Incluye ejecución épica ("El Cuello") para los perdedores.
2. 💣 **TNT Tag:** Pásale la TNT al compañero. A la mitad de la partida, el mapa sufre desastres naturales aleatorios (Tornados, Lluvia de Yunques, Tormentas, etc.).
3. 🏃 **TNT Run:** El suelo desaparece a tus pies. Incluye el ítem "Pluma de Salto" (Dash/Doble Salto) con Cooldown visual nativo.
4. 🌋 **El Suelo es Lava:** La lava sube implacablemente 2 bloques cada 3 segundos. Generación masiva de lava optimizada por chunks (Cero Lag).
5. 🧊 **Congelados:** Equipo Rojo vs Equipo Azul. Los rojos congelan, los azules rescatan.
6. 🥔 **Papa Caliente:** Un jugador recibe la papa y debe golpearte para pasarla antes de que explote.
7. ⚔️ **Sumo 1v1:** Torneo automático. Los jugadores esperan en las gradas y bajan de 2 en 2 a darse con palos de empuje.
8. 💃 **Block Party:** Una pista de baile gigante de colores. Párate en el color correcto o cae al vacío. (Generación/Borrado de 10,000 bloques optimizado).
9. 🏹 **TNT Spleef:** Dispara flechas explosivas al suelo de TNT para hacer caer a tus rivales.
10. 🚦 **Luz Roja, Luz Verde:** ¡No te muevas cuando la luz esté roja o te fulminará un rayo! Incluye muro de cristal automático en la salida.
11. 📦 **Pillars Of Fortune:** Estas en una torre y te da un item aleatorio cada 10 minutos.

---

## ⚙️ Comandos del Sistema

### Administración (`pumpkin.admin`)
Todos los comandos base están bajo el prefijo `/evento` (Alias: `/eventos`, `/ev`).

| Comando | Descripción |
| :--- | :--- |
| `/evento ayuda` | Muestra la lista de comandos. |
| `/evento reload` | Recarga las configuraciones y los textos de `messages.yml`. |
| `/evento lanzarvotacion` | Inicia una votación global interactiva en el chat. |
| `/evento ruleta` | Gira la ruleta de minijuegos con sonidos y títulos épicos. |
| `/evento iniciar <juego> [modo]` | Fuerza el inicio de un evento saltándose la votación. |
| `/evento detener` | Aborta el evento actual y devuelve a todos al Lobby. |
| `/evento revivir <jugador>` | Revive a un espectador y lo mete al juego. |

### Setup de Mapas (Modo Creación)
Crea mapas fácilmente directamente desde el juego.
1. `/evento arena create <Nombre> <Tipo>`
2. `/evento arena setcenter` *(Centro del mapa)*
3. `/evento arena addspawn` *(Puntos de aparición / Línea de salida)*
4. `/evento arena setgradas / setdueloa / setduelob` *(Exclusivos para Sumo/Luz Roja)*
5. `/evento arena save` *(Guarda y compila el mapa en `arenas.yml`)*

### Comandos Generales (Staff)
| Comando | Permiso | Descripción |
| :--- | :--- | :--- |
| `/gamemode <0/1/2/3> [Jugador]` | `pumpkin.command.gamemode` | Cambia el modo de juego. |
| `/gmc`, `/gms`, `/gma`, `/gmsp` | `pumpkin.command.gamemode` | Atajos rápidos de Gamemode. |
| `/votar <juego>` | *Ninguno* | Emite un voto por el minijuego favorito. |

---

## 📦 Instalación y Dependencias

Para que el Core funcione correctamente, tu servidor debe contar con los siguientes plugins instalados:

1. **[AdvancedSlimePaper (ASP)](https://github.com/InfernalSuite/AdvancedSlimePaper):** Requerido para la instanciación de mundos en memoria.
2. **[LuckPerms](https://luckperms.net/):** Requerido para la gestión de rangos, pesos en el Tablist y chat format.
3. **Triumph-GUI:** (Incluido nativamente si se compila con Gradle). Usado para el menú de Simón Dice.

### Pasos:
1. Coloca `PumpkinEventos.jar` en la carpeta `plugins`.
2. Inicia el servidor para generar las carpetas.
3. Coloca tus plantillas de mapas `.slime` dentro de `plugins/PumpkinEventos/slime_worlds`.
4. Ingresa al servidor y configura el Lobby principal con `/evento setup lobby`.
5. Comienza a crear las configuraciones de arenas usando `/evento arena create`.

---

## 👨‍💻 Créditos

- **Desarrollo Core & Lógica:** Pumpkingz
- **Para:** Lyric Network
- **Versión:** 2.0 (Paper 1.21.x API)
