***

# 🗺️ Guía de Configuración de Mapas

Esta guía explica cómo configurar los nuevos modos de juego utilizando los comandos del Core. Recuerda que para todos los mapas debes iniciar con:
`/evento arena create <nombre> <tipo>`

---

## ☁️ Skywars (Solos y Dúos)
**Tipos:** `skywars_solos` | `skywars_duos`

Este modo detecta automáticamente los cofres del mapa y les inyecta el loot votado.

1. **Spawns de Islas:** Ve a cada isla y colócate donde quieres que aparezca el jugador (o la pareja). Usa:
    - `/evento arena addspawn` (Repite esto en cada isla).
2. **Cofres:** No necesitas comandos. Simplemente coloca cofres normales o cofres trampa en el mapa. El plugin los detectará al ser abiertos por primera vez.
3. **Centro del Mapa:** Ve al centro (donde está el loot OP) y usa:
    - `/evento arena setcenter` (Esto sirve como punto de referencia para eventos finales).
4. **Finalizar:** `/evento arena save`.

> **Nota para Dúos:** El plugin enviará a los 2 compañeros de equipo al **mismo spawn** de isla. Asegúrate de que haya espacio suficiente.

---

## 🚤 Ice Boat Racing (Carreras de Botes)
**Tipo:** `iceboat`

Un sistema basado en Checkpoints obligatorios para evitar que los jugadores recorten camino.

1. **Parrilla de Salida:** Ve al inicio de la pista y coloca los spawns uno detrás de otro:
    - `/evento arena addspawn` (Pon tantos como capacidad máxima quieras).
2. **Checkpoints (Puntos de Control):** Recorre el circuito. En cada curva o tramo importante, mira al suelo y usa:
    - `/evento arena addchair`
    - *¿Cómo funciona?* Los jugadores deben pasar a menos de 15 bloques de estos puntos en orden para que la vuelta cuente.
3. **Línea de Meta:** Selecciona el ancho de la pista en la meta usando la **Varita de Posiciones** (Vara de Blaze):
    - **Click Izquierdo (Pos 1):** Un extremo de la meta.
    - **Click Derecho (Pos 2):** El otro extremo de la meta.
4. **Finalizar:** `/evento arena save`.

---

## 🧱 Mini Walls (Withers & Nexos)
**Tipo:** `miniwalls`

Configuración estricta de 4 equipos (Rojo, Azul, Amarillo, Verde).

1. **Lobby de Espera:** Antes de que el juego inicie, los jugadores eligen equipo en un área aparte. Ve allí y usa:
    - `/evento arena setdueloa`
2. **Bases de Equipos (Spawns):** Debes poner los spawns en este **orden exacto**:
    - 1er Spawn puesto -> **Equipo ROJO**
    - 2do Spawn puesto -> **Equipo AZUL**
    - 3er Spawn puesto -> **Equipo AMARILLO**
    - 4to Spawn puesto -> **Equipo VERDE**
    - *Comando:* `/evento arena addspawn`
3. **Ubicación de Withers:** El Wither (Nexo) aparecerá automáticamente 1 bloque por encima de donde pusiste el `addspawn` de cada equipo.
4. **Límite de Altura:** Ve a la altura máxima permitida para construir y usa:
    - `/evento arena setduelob`
5. **Muros:** Asegúrate de que los bloques que forman las paredes del mapa estén en la lista `wall_blocks` del `config.yml`. El plugin los borrará al empezar.
6. **Finalizar:** `/evento arena save`.

---

## 👑 Roba la Corona
**Tipo:** `corona`

Un modo de persecución frenética en un área cerrada.

1. **Punto de Inicio:** Ve al centro de la arena donde todos aparecerán al principio:
    - `/evento arena addspawn`
2. **Centro de la Arena:** Colócate en el centro exacto:
    - `/evento arena setcenter` (Aquí es donde aparecerán los Boosters de velocidad/salto).
3. **Gradas:** Si quieres un sitio para los que mueran o el ganador:
    - `/evento arena setgradas`
4. **Finalizar:** `/evento arena save`.

---

## 💡 Tips Generales de Staff

- **Varita de Posiciones:** Al crear cualquier mapa, se te dará una Vara de Blaze. Úsala para marcar `dueloA` (Click Izq) y `dueloB` (Click Der) más rápido.
- **Borrar Errores:** Si te equivocas en un paso, puedes usar `/evento arena cancel` y empezar de nuevo.
- **Visualización:** El comando `/evento arena setgradas` es muy importante en juegos como **Sumo** o **Sillas Musicales** para que los eliminados no estorben en la pista.

***
