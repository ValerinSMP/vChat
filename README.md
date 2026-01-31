# 📘 vChat - Wiki de Documentación

Bienvenido a **vChat**, un plugin de chat avanzado para servidores modernos de Minecraft, diseñado para ser estético, funcional y altamente configurable.

---

## 🛠 Instalación

1. Descarga el archivo `vChat-x.x.x.jar`.
2. Colócalo en la carpeta `/plugins` de tu servidor.
3. Asegúrate de tener **LuckPerms** instalado (Dependencia requerida) y **PlaceholderAPI** (Opcional, pero recomendado).
4. Reinicia tu servidor.

---

## 🚀 Características Principales

### 📨 Mensajería Privada (Aesthetic)

Envía mensajes privados con un diseño visualmente atractivo y feedback instantáneo.

- **Comandos**:
  - `/msg <jugador> <mensaje>` (Alias: `/w`, `/tell`, `/dm`)
  - `/reply <mensaje>` (Alias: `/r`)
- **Feedback**:
  - Sonidos personalizados al enviar/recibir.
  - Actionbar visual para no perder ningún mensaje.
  - Colores pastel (Amarillo para mejor legibilidad).
- **Control**:
  - `/togglemsg`: Activa/Desactiva recibir mensajes privados.

### 🕵️ SpyChat (Moderación)

Los administradores pueden supervisar conversaciones privadas.

- **Comando**: `/spychat` (Alias: `/spy`)
- **Visualización**: Formato diferenciado en tonos morados/rosas para identificar rápidamente mensajes espiados.
- **Toggle**: Activa/Desactiva el modo espía individualmente.

### 🚫 Sistema de Ignore

Permite a los jugadores bloquear la comunicación con usuarios molestos.

- **Comando**: `/ignore <jugador>`
  - Funciona como un interruptor (Toggle): Si ya lo ignoras, lo dejará de ignorar.
- **Efecto**:
  - Bloquea mensajes en el **Chat Público**.
  - Bloquea **Mensajes Privados** (/msg).

### 📢 Chat Global & Moderación

- **Menciones**: Usa `@Jugador` para mencionar.
  - Alerta sonora para el mencionado.
  - Color destacado en el chat.
  - Comandos:
    - `/togglementions`: Activa/Desactiva recibir menciones.
- **Visualización de Ítems**: Usa `[item]` o `/showitem` para mostrar el ítem de tu mano en el chat con tooltip interactivo.
- **Filtros**: Anti-Spam, Anti-Caps, Anti-Groserías y Anti-Anuncios integrados.
- **Control Global**:
  - `/togglechat`: Oculta el chat global solo para ti (Personal).
  - `/mutechat`: Silencia el chat global para **todos** los usuarios (Admin).

---

## 📜 Permisos (Permissions)

### Usuario (Default: True)

| Permiso           | Descripción                   |
| :---------------- | :---------------------------- |
| `vchat.msg`       | Enviar mensajes privados.     |
| `vchat.reply`     | Responder mensajes privados.  |
| `vchat.togglemsg` | Usar /togglemsg.              |
| `vchat.mention`   | Mencionar a otros (@Usuario). |
| `vchat.showitem`  | Usar [item] o /showitem.      |
| `vchat.ignore`    | Usar /ignore.                 |

### Administrador (Default: OP)

| Permiso            | Descripción                       |
| :----------------- | :-------------------------------- |
| `vchat.admin`      | Acceso total.                     |
| `vchat.reload`     | Usar `/vchat reload`.             |
| `vchat.spychat`    | Usar `/spychat`.                  |
| `vchat.togglechat` | Usar `/togglechat` (Chat Global). |
| `vchat.bypass.*`   | Ignorar filtros y restricciones.  |

---

## 🎨 Configuración (Archivos)

El plugin genera carpetas separadas para una organización limpia:

- **config.yml**: Opciones generales.
- **messages.yml**: Todos los textos del sistema (Traducible).
- **formats.yml**: Diseño del chat público (Rangos, Prefijos).
- **private.yml**: Diseño de mensajes privados y sonidos.
- **mentions.yml**: Configuración de menciones.
- **filters.yml**: Configuración de filtros de chat.

---

_Desarrollado con ❤️ para servidores que buscan calidad visual._
