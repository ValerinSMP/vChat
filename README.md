<div align="center">

# vChat

### Chat moderno, moderación y comunicación de red para ValerinSMP

[![Paper](https://img.shields.io/badge/Paper-1.21.11%2B-222222?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-E76F00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![MiniMessage](https://img.shields.io/badge/text-MiniMessage-7B5CFA?style=for-the-badge)](https://docs.advntr.dev/minimessage/)
[![Version](https://img.shields.io/badge/version-1.0.0-7B5CFA?style=for-the-badge)](https://github.com/ValerinSMP/vChat)

</div>

**vChat** ofrece una experiencia de chat completa para ValerinSMP: formatos por
rango, mensajes privados, menciones, moderación, ítems interactivos y sincronización
entre servidores.

## ⭐ Características

- **Formatos dinámicos:** prefijos y sufijos obtenidos desde LuckPerms.
- **MiniMessage:** colores HEX, gradientes y componentes interactivos.
- **Mensajes privados:** `/msg`, respuestas, bloqueo y social spy.
- **Menciones:** resaltado, sonidos y preferencias por jugador.
- **Ítems en el chat:** comparte el objeto de la mano con hover interactivo.
- **Moderación:** filtros de spam, mayúsculas, publicidad y lenguaje.
- **Control personal:** desactiva mensajes privados, menciones o el chat global.
- **Discord bridge:** rutas configurables mediante JDA.
- **Cross-server:** propagación de eventos mediante Redis.
- **PlaceholderAPI:** estados y preferencias disponibles como placeholders.

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

## ⚙️ Configuración

- `config.yml`: ajustes generales y sonidos.
- `messages.yml`: textos y feedback.
- `private.yml`: mensajes privados y social spy.
- `formats.yml`: formatos por grupo.
- `filters.yml`: filtros y listas.
- `mentions.yml`: presentación y sonidos de menciones.

## 🛠️ Compilación

```powershell
.\gradlew.bat clean test build
```

El build genera `build/libs/vChat-1.0.0.jar`.
