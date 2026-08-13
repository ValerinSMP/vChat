<div align="center">

# vChat

### Chat moderno, moderación y comunicación de red para ValerinSMP

[![Paper](https://img.shields.io/badge/Paper-1.21.11%2B-222222?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-E76F00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![MiniMessage](https://img.shields.io/badge/text-MiniMessage-7B5CFA?style=for-the-badge)](https://docs.advntr.dev/minimessage/)
[![Version](https://img.shields.io/badge/version-1.1.0-7B5CFA?style=for-the-badge)](https://github.com/ValerinSMP/vChat)

[Características](#-características) • [Comandos](#-comandos-principales) • [Configuración](#️-configuración) • [Desarrollo](#️-compilación)

</div>

**vChat** ofrece una experiencia de chat completa para ValerinSMP: formatos por
rango, mensajes privados, menciones, moderación, ítems interactivos y sincronización
entre servidores.

## ⭐ Características

- **⭐ Chat por rangos** — Prefijos, sufijos y formatos obtenidos desde LuckPerms.
- **⭐ Interacción moderna** — MiniMessage, colores HEX, hover, click e ítems compartidos.
- **⭐ Comunicación privada** — `/msg`, respuestas, bloqueos, menciones y social spy.
- **⭐ Moderación configurable** — Filtros de spam, mayúsculas, publicidad y lenguaje.
- **⭐ Preferencias por jugador** — Controles para chat global, mensajes privados y menciones.
- **⭐ Red fiable** — Chat, mensajes con ACK, preferencias durables y presencia TTL mediante MySQL + Redis.

## 💬 Experiencia de chat

Los mensajes usan Adventure y MiniMessage. Las confirmaciones breves pueden aparecer
en action bar, mientras la información persistente utiliza componentes de chat con
hover, click y colores configurables.

## 🎮 Comandos principales

| Comando | Descripción |
| --- | --- |
| `/msg <jugador> <mensaje>` | Envía un mensaje privado. |
| `/reply <mensaje>` | Responde al último mensaje privado. |
| `/ignore <jugador>` | Alterna el bloqueo de un jugador. |
| `/showitem` | Comparte el ítem de la mano. |
| `/togglemsg` | Activa o desactiva mensajes privados. |
| `/togglementions` | Activa o desactiva menciones. |
| `/togglechat` | Oculta el chat global para el jugador. |
| `/spychat` | Alterna el social spy administrativo. |
| `/mutechat` | Silencia globalmente el chat. |
| `/vchatadmin reload` | Recarga configuración y cachés. |
| `/vchatadmin bridge ...` | Administra el bridge de Discord. |

## 🧰 Requisitos

| Paper | Java requerida | Folia |
| :---: | :---: | :---: |
| 1.21.11 | 21 | ❌ |
| 26.1 en adelante | 25 | ❌ |

Requerido:

- [LuckPerms](https://luckperms.net/)

Opcionales:

- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
- [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)
- Nexo

Redis y JDA son necesarios únicamente para sus funciones correspondientes.

## 📦 Instalación

1. Instala LuckPerms y las integraciones opcionales que vayas a utilizar.
2. Copia `vChat-1.1.0.jar` dentro de `plugins/`.
3. Reinicia Paper y ajusta los archivos generados.
4. Usa `/vchatadmin reload` para aplicar cambios compatibles de configuración.

## ⚙️ Configuración

- `config.yml`: ajustes generales y sonidos.
- `messages.yml`: textos, feedback y prefijo `<dark_gray>[<#00FB9A>vChat</#00FB9A><dark_gray>]</dark_gray>`.
- `private.yml`: mensajes privados y social spy.
- `formats.yml`: formatos por grupo.
- `filters.yml`: filtros y listas.
- `mentions.yml`: presentación y sonidos de menciones.

SQLite es el modo local predeterminado. Para varios servidores, configura
`storage.type: mysql`, un `redis.network-id` común y un `redis.server-id` único por
backend. Redis transporta eventos y presencia efímera; MySQL conserva preferencias,
ignores, mute global y el registro de jugadores.

## 🛠️ Compilación

```powershell
.\gradlew.bat clean test build
```

El build genera `build/libs/vChat-1.1.0.jar`.

## 🔗 Enlaces

- [Repositorio](https://github.com/ValerinSMP/vChat)
- [Organización ValerinSMP](https://github.com/ValerinSMP)
