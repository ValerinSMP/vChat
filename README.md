# vChat - Documentación Técnica

vChat es una solución integral de chat para servidores de Minecraft, diseñada para ofrecer una experiencia de usuario moderna, estética y altamente optimizada. Este plugin reemplaza los sistemas de chat tradicionales con funcionalidades avanzadas de formateo, moderación y feedback visual.

## Requisitos y Dependencias

Para el correcto funcionamiento del plugin, asegúrese de contar con las siguientes dependencias:

- **Java**: 17 o superior.
- **LuckPerms** (Requerido): Para la gestión de prefijos, sufijos y permisos.
- **PlaceholderAPI** (Opcional): Para el uso de variables en el chat y scoreboards.
- **ProtocolLib** (Opcional): Para mejorar la compatibilidad del autocompletado de menciones.

## Compilación

Este proyecto utiliza **Gradle** como sistema de construcción. Como no se distribuyen binarios públicos, debe compilar el código fuente manualmente.

### Instrucciones de Compilación

1.  Clone el repositorio en su entorno local:
    ```bash
    git clone https://github.com/ValerinSMP/vChat.git
    ```
2.  Navegue al directorio del proyecto y ejecute el comando de construcción:
    - **Windows**:
      ```powershell
      .\gradlew.bat clean build
      ```
    - **Linux/macOS**:
      ```bash
      ./gradlew clean build
      ```
3.  El archivo compilado (`vChat-1.0.0-SNAPSHOT.jar`) se generará en la carpeta `build/libs`.

## Características

### Sistema de Mensajería

- **Mensajes Privados**: Soporte completo para MiniMessage (gradientes, colores hex) con feedback en ActionBar.
- **SpyChat**: Sistema de monitoreo para administradores con formato diferenciado.
- **Ignore**: Permite a los usuarios bloquear mensajes privados y menciones de jugadores específicos.

### Chat Global

- **Formato Dinámico**: Integración con LuckPerms para formatos de chat basados en rangos.
- **Menciones**: Sistema de menciones (`@Usuario`) con alertas sonoras y visuales.
- **Item Display**: Funcionalidad `[item]` o `/showitem` para compartir ítems con tooltips interactivos.
- **Moderación Automática**: Filtros configurables (Anti-Spam, Anti-Caps, Groserías, Anti-Anuncios/IPs) con optimización de caché Regex.

### Feedback Visual y Sonoro

- **ActionBar**: Todas las confirmaciones de comandos (toggles) se muestran en la ActionBar para reducir el ruido en el chat.
- **Sonidos Agradables**: Se utilizan sonidos de bloques de nota (`BLOCK_NOTE_BLOCK_PLING/BASS`) para un feedback auditivo no intrusivo.

## Comandos y Permisos

### Comandos de Usuario

| Comando                | Alias             | Descripción                           | Permiso                |
| :--------------------- | :---------------- | :------------------------------------ | :--------------------- |
| `/msg <jugador> <msg>` | `w`, `tell`, `dm` | Enviar mensaje privado.               | `vchat.msg`            |
| `/reply <msg>`         | `r`               | Responder al último mensaje.          | `vchat.reply`          |
| `/ignore <jugador>`    | N/A               | Ignorar a un jugador.                 | `vchat.ignore`         |
| `/showitem`            | N/A               | Mostrar ítem en mano.                 | `vchat.showitem`       |
| `/togglemsg`           | N/A               | Activar/Desactivar mensajes privados. | `vchat.togglemsg`      |
| `/togglementions`      | N/A               | Activar/Desactivar menciones.         | `vchat.togglementions` |
| `/togglechat`          | N/A               | Ocultar chat global (Personal).       | `vchat.togglechat`     |

### Comandos de Administración

| Comando         | Descripción                                    | Permiso          |
| :-------------- | :--------------------------------------------- | :--------------- |
| `/vchat reload` | Recarga toda la configuración y cachés.        | `vchat.admin`    |
| `/vchat notify` | Activa/Desactiva notificaciones de moderación. | `vchat.notify`   |
| `/mutechat`     | Silencia el chat global para todos.            | `vchat.mutechat` |
| `/spychat`      | Activa/Desactiva el espionaje de mensajes.     | `vchat.spychat`  |

## Placeholders (PlaceholderAPI)

El plugin exporta los siguientes placeholders para su uso en mensajes, scoreboards o menús:

| Placeholder               | Retorno (Ejemplo)          | Descripción                               |
| :------------------------ | :------------------------- | :---------------------------------------- |
| `%vchat_notify_status%`   | `Activado` / `Desactivado` | Estado de notificaciones admin.           |
| `%vchat_mentions_status%` | `Activado` / `Desactivado` | Estado de recepción de menciones.         |
| `%vchat_toggle_msg%`      | `Activado` / `Desactivado` | Estado de recepción de mensajes privados. |
| `%vchat_toggle_chat%`     | `Activado` / `Desactivado` | Estado de visibilidad del chat global.    |
| `%vchat_toggle_spy%`      | `Activado` / `Desactivado` | Estado del SpyChat.                       |

_Nota: Los textos de retorno ("Activado"/"Desactivado") son configurables en `messages.yml`._

## Configuración

La configuración se divide en múltiples archivos para facilitar su gestión:

- `config.yml`: Configuración general y sonidos globales.
- `messages.yml`: Mensajes del sistema y feedback.
- `private.yml`: Formatos de mensajes privados (`<sender>`, `<receiver>`, `<message>`) y SpyChat.
- `formats.yml`: Formatos de chat global por grupo de LuckPerms.
- `filters.yml`: Configuración de filtros de moderación y listas blanca/negra.
- `mentions.yml`: Configuración de colores y sonidos de menciones.
