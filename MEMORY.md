# vChat — memoria de desarrollo

## Rol

Referencia técnica actual de ValerinSMP para mensajes interactivos y comunicación
cross-server. Paper será la única plataforma oficial en la arquitectura nueva.

## Base actual

- Java 21 y Gradle Kotlin DSL 9.1.0.
- Un único artefacto oficial para Paper.
- API mínima Paper 1.21.11 y bytecode Java 21.
- Adventure/MiniMessage, LuckPerms, PlaceholderAPI, ProtocolLib, Nexo y JDA.
- Redis/Jedis para eventos entre servidores.
- Cinco clases de pruebas; baseline ejecutado: 14 casos, 0 fallos.
- La línea moderna comienza en `1.0.0`. Gradle es la fuente canónica y
  `plugin.yml` recibe la versión durante `processResources`.

## Riesgos y trabajo pendiente

1. Documentar el envelope y las garantías de los eventos Redis.
2. Probar idempotencia, reconexión, duplicados y eventos fuera de orden.
3. Separar formato visual de transporte y reglas de moderación.
4. Convertir su sistema visual en la referencia del resto de plugins.
5. Mantener bytecode Java 21 y probar también la última versión estable con su Java.

Redis no debe ser la fuente de verdad de preferencias o datos durables. Los eventos
sensibles necesitan identificador, versión, origen y estrategia de deduplicación.
