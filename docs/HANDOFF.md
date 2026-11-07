# Traspaso: dónde nos quedamos

Última actualización: 2026-11-07.

Este documento permite retomar el proyecto sin la conversación anterior. Si el dueño dice "dónde nos quedamos" o "sigue", resume este estado y continúa por el [issue de seguimiento #23](https://github.com/adrielorihuela/ProxyPulse/issues/23). Los issues son la fuente de verdad de lo pendiente: consúltalos con `gh issue list`, porque este archivo puede quedar atrás.

## Resumen en cinco líneas

- ProxyPulse v1.0.0 está publicado, compila y pasa 43 tests.
- **No está listo para producción:** ningún jugador se ha conectado nunca a un proxy con el plugin.
- Una auditoría del código encontró bugs serios en el scoreboard y en otras áreas.
- El siguiente paso es responder [#22](https://github.com/adrielorihuela/ProxyPulse/issues/22) y corregir los P0.
- La próxima versión debe ser la v1.0.1.

## Qué pidió el dueño

Un plugin para la última versión de Velocity que:

- Reemplace `%animation:nombre%` en el texto que envían los backends, con todas las animaciones en un solo archivo en el proxy y formato compatible con TAB.
- Funcione "en todo lugar" (tab, scoreboard, boss bar, títulos y demás), pero **nunca en el chat**: un jugador no debe poder escribir una animación ahí.
- Tenga placeholders de red que Paper no ofrezca: servidor actual, jugadores totales y por servidor, ping y otros.
- Incluya un tab global con todos los jugadores y su servidor, que bloquee el tab del backend, tenga excepciones por servidor y venga **desactivado por defecto**.
- Funcione con un solo `.jar`, sin instalar dependencias.

Debía publicarse en un repo público con documentación, un commit y push por cada paso en `main`, y un release v1.0.0 con el jar.

Objetivo técnico: Velocity 4.0.0 y superiores, en especial su fork [HybridVelocity](https://github.com/adrielorihuela/HybridVelocity) 4.1.0-SNAPSHOT, con Java 25. El dueño prefiere comunicarse en español.

## Qué está hecho

| Área | Estado |
|---|---|
| Animaciones (`animations.yml`, frames por reloj, ticker único) | Implementado, con tests |
| Placeholders de red | Implementado, ver [#9](https://github.com/adrielorihuela/ProxyPulse/issues/9) y [#12](https://github.com/adrielorihuela/ProxyPulse/issues/12) |
| Header, footer y nombres del tab | Implementado, sin probar con un jugador |
| Títulos, subtítulos y action bar | Implementado, ver [#5](https://github.com/adrielorihuela/ProxyPulse/issues/5) y [#11](https://github.com/adrielorihuela/ProxyPulse/issues/11) |
| Boss bar | Implementado, sin probar con un jugador |
| Scoreboard | **Defectuoso:** [#1](https://github.com/adrielorihuela/ProxyPulse/issues/1), [#2](https://github.com/adrielorihuela/ProxyPulse/issues/2), [#3](https://github.com/adrielorihuela/ProxyPulse/issues/3), [#4](https://github.com/adrielorihuela/ProxyPulse/issues/4) |
| Exclusión del chat | Implementada, con tests |
| Tab global | Implementado, sin tests ni pruebas reales: [#7](https://github.com/adrielorihuela/ProxyPulse/issues/7), [#8](https://github.com/adrielorihuela/ProxyPulse/issues/8), [#15](https://github.com/adrielorihuela/ProxyPulse/issues/15) |
| Comando `/proxypulse` | Probado desde la consola |
| Documentación y release v1.0.0 | Publicados |
| LeanFlow | Instalado, preset 2026.11.06 |

## Qué se verificó y qué no

**Verificado:**
- `./gradlew clean build` pasa y los 43 tests están en verde.
- El jar tiene las dependencias relocalizadas.
- En Velocity 4.0.0 real: carga, genera la configuración, responde a `status`, `animations`, `placeholders` y `reload` desde la consola, y se apaga sin excepciones.
- `PlayerSession.rewrite()` llamado directamente con buffers construidos en tests.

**Sin verificar:**
- Cualquier cosa con un jugador conectado.
- El handler de Netty en un pipeline real.
- `refresh()`: el reenvío de frames no tiene tests.
- `GlobalTabView`.
- Backend Paper detrás del proxy.
- Cualquier ejecución en HybridVelocity.
- Clientes de versiones antiguas.

Mensajes anteriores y notas del release dieron una impresión más sólida de lo real. El [README](../README.md) afirmaba haber probado forks y soporte desde 1.8; eso se corrigió.

## Decisiones tomadas y por qué

- **Nombre:** ProxyPulse, porque el proxy marca el pulso de las animaciones.
- **PacketEvents descartado:** el dueño lo eligió al inicio, pero la prueba en Velocity 4 mostró que su serializador de Adventure 4 no carga con Adventure 5 ([retrooper/packetevents#1500](https://github.com/retrooper/packetevents/issues/1500)). Se usa el plan B previsto: clases internas del proxy. Ver los commits `028bf1c` y `76de213`.
- **Jar de Velocity solo para compilar:** no está publicado en Maven, así que Gradle lo descarga fijado por SHA-256 y no se empaqueta.
- **Java 25:** coincide con el mínimo de Velocity 4.
- **Tabla manual de IDs del scoreboard:** Velocity no registra esos paquetes. Las filas salen de los enums de ViaVersion.
- **Sin Mockito:** no puede simular `Player` en Java 25.

La arquitectura está en [architecture.md](architecture.md).

## Desviaciones del plan original

No existen, y falta decidir si se hacen ([#22](https://github.com/adrielorihuela/ProxyPulse/issues/22)):
- `tablist.yml`: la configuración del tab vive en `config.yml`.
- `/pp tab on|off` y `/pp debug`: se usa `debug` en `config.yml`.

Otras diferencias:
- `BackendTabSuppressor` y `TabFormatter` no existen como clases; su lógica está en `PlayerSession` y `GlobalTabView`.
- Existe el comando `preview`, que no estaba planeado.
- No hay caché de placeholders con TTL individual. El ahorro viene de la firma de cada elemento y de la caché de `TextParser`.

## Cómo continuar

Sigue el orden de [#23](https://github.com/adrielorihuela/ProxyPulse/issues/23):

1. Pregunta al dueño por [#22](https://github.com/adrielorihuela/ProxyPulse/issues/22). No bloquea los bugs P0, pero sí parte del alcance.
2. Corrige [#4](https://github.com/adrielorihuela/ProxyPulse/issues/4), [#2](https://github.com/adrielorihuela/ProxyPulse/issues/2), [#1](https://github.com/adrielorihuela/ProxyPulse/issues/1), [#3](https://github.com/adrielorihuela/ProxyPulse/issues/3), [#5](https://github.com/adrielorihuela/ProxyPulse/issues/5) y [#9](https://github.com/adrielorihuela/ProxyPulse/issues/9), con un test que falle antes de cada arreglo.
3. Crea la base de pruebas de [#21](https://github.com/adrielorihuela/ProxyPulse/issues/21).
4. Haz la prueba real de [#6](https://github.com/adrielorihuela/ProxyPulse/issues/6).
5. Termina el resto de la v1.0.1 y publícala **solo con evidencia real**.

Al cerrar un issue, indica en él el commit y cómo se verificó, y actualiza este archivo si cambia el estado general.

## Entorno local

`testenv/` está gitignored y **no se clona desde GitHub**. Existe solo en este PC, en `C:\Users\lizzl\IdeaProjects\ProxyPulse\testenv`.

- `testenv/proxy`: Velocity 4.0.0 build 6, puerto 25565, con el servidor `test` en `127.0.0.1:25566` y `player-info-forwarding` sin configurar.
- `testenv/backend`: Paper 26.2 build 112, puerto 25566, `online-mode=false`.
- `eula.txt` del backend contiene `eula=true`. Claude no lo cambió, y aún no se ha arrancado Paper con ese valor.
- No se ha comprobado que Velocity 4.0.0 acepte clientes 26.2. Con ese cliente, el scoreboard está afectado por [#4](https://github.com/adrielorihuela/ProxyPulse/issues/4).
- Claude no puede abrir un cliente de Minecraft: el dueño debe conectarse o proporcionar un cliente de pruebas.

Si la carpeta no existe, recréala con la [skill `test-on-proxy`](../.claude/skills/test-on-proxy/SKILL.md) como referencia.

El proyecto se llamó antes `minecraft-proxy-animation-plugin`; la carpeta se renombró a `ProxyPulse`.

## Pendientes fuera del código

- El dueño tiene una configuración de OpenAI Codex en `~/.codex`. Se ofreció importarla con `/import` y no hubo respuesta.
- Los commits y el push fueron directos a `main`, a petición del dueño.
