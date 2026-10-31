# Validación y decisiones de integración

## Base

- MDK oficial `26.3-66.1.0`; Java 25; ForgeGradle fijado en 7.0.40; Gradle Wrapper 9.7.1.
- `gradlew.bat build`: correcto. Las pruebas de packs e interfaz se incorporan en la entrega siguiente.
- `gradlew.bat runClient -PprismoraSmoke=true`: cliente real, menú principal, 160 ticks y 176 frames; marcador `PRISMORA_SMOKE_PASS` en `run/logs/latest.log`.
- El probe está desactivado en partidas normales. Los errores de autenticación de Realms del perfil de desarrollo no impidieron el arranque.
- Separación de cliente mediante `@Mod.EventBusSubscriber(value=Dist.CLIENT)` y `clientSideOnly=true`; queda por ejecutar la comprobación de servidor dedicado.

## Fuentes verificadas

- `Minecraft.gui.screen()` y `Minecraft.gui.setScreen(...)` sustituyen los accesos antiguos.
- La interfaz usa `GuiGraphicsExtractor.extractRenderState`; apertura de carpetas con `Blaze3D.openPath`.
- `GameRenderer` solicita `minecraft:end_of_frame` antes de efectos de jugador y espectador.
- `PostChainConfig` define targets, shaders, samplers y uniform blocks. `PostChain` construye `RenderPipeline` y administra targets a través del frame graph.
- `AddPackFindersEvent.BUS` permite registrar una fuente de recursos propia.
- `AddFramePassEvent` y `FramePassManager.PassDefinition` permiten añadir pasadas a `LevelRenderer`. No prueban la sustitución de los pipelines de terreno/entidades ni la disponibilidad de geometría para una segunda cámara solar. Forge advierte que el orden de pasadas requiere dependencias de targets.

## Decisión provisional

Prototipar un pack de recursos interno de prioridad alta y solo presente cuando el shader está activo. `end_of_frame` es un recurso único: otro pack que lo defina no se combina automáticamente. Prismora debe documentar esta precedencia y al desactivar retirar su pack. Las pruebas de compilación, aplicación en mundo, recarga y backends siguen pendientes al crear esta base.

## Interfaz y packs

- Build y 10 pruebas JUnit correctos (8 de packs y 2 de configuración).
- Cliente real con '-PprismoraSmoke=true -PprismoraUiSmoke=true': 134 frames de la pantalla de Prismora, regreso al menú y cierre limpio.
- Los botones muestran selección persistente; el render se integra en la siguiente entrega.

## Postproceso y recuperación

- Build correcto y 11 pruebas JUnit sin fallos.
- Cliente real en OpenGL (NVIDIA 621.01) y Vulkan (1.4.357, NVIDIA 621.01): mundo PrismoraSmoke cargado, efecto compilado y aplicado, lectura GPU `ff32ca65` dentro de tolerancia del color diagnóstico, desactivación y eliminación del pack interno.
- GLSL deliberadamente inválido: error informado al controlador, pack retirado y cliente todavía operativo (`PRISMORA_ERROR_RECOVERY_PASS`). La UI permite leer los diagnósticos en una pantalla paginada.
- Se suprime `end_of_frame` durante carga/validación para impedir que vanilla active un shader sin validar y dispare recuperación global de recursos.
- Al desactivar, Prismora deja de proporcionar `end_of_frame`; un recurso de otro pack vuelve a estar disponible. Si dos packs lo definen simultáneamente, gana el pack interno superior de Prismora; no hay composición automática entre ambos.
- `scripts/Test-Prismora.ps1 -Backend opengl` o `-Backend vulkan` reproduce build, tests y prueba de GPU. Comprueba el backend efectivo para detectar fallback.
- Captura de diagnóstico local: `run/prismora-diagnostic.png` (no versionada).

## Múltiples pasadas

- 14 pruebas JUnit correctas: targets, referencias, persistencia, límites, compilación del manifiesto y roundtrip de bits de floats.
- OpenGL y Vulkan: cadena de tres pasadas con target 256x256 y otros que siguen la ventana; color final leído de GPU dentro de tolerancia. `dynamicUniforms` comprobado desde el shader (resolución, tiempo y presencia de mundo).
- Bloom: extracción de brillo, desenfoque horizontal, vertical y composición; cuatro pasadas de pack más copia final segura. Ambos backends compilan y producen una imagen de mundo no uniforme (`PRISMORA_BLOOM_PASS`); captura inspeccionada visualmente.
- Resolución de prueba: 854x480. Bloom mantiene cuatro targets privados: estimación nominal 12.5 MiB a 8 bytes/píxel entre color y profundidad, más 64 bytes de datos dinámicos; excluye pool de recursos y overhead del driver. Esta cifra es un cálculo, no una lectura de VRAM.
- El probe usa `TimerQuery` de Mojang entre RenderTick.Pre/Post para medir el render completo y reporta medianas con `PRISMORA_GPU_BENCH`. La generación/carga de chunks y el estado del mundo afectan las medidas; no son garantías de rendimiento ni aíslan el tiempo exclusivo del shader.

## Separación de servidor y geometría

- `runServer`: Forge informa que omite el mod por `clientSideOnly` antes de iniciar Minecraft; no carga las clases de cliente. El proceso termina correctamente en la comprobación de EULA. No se aceptó la EULA ni se arrancó un mundo dedicado; la prueba demuestra exclusión del mod en ese entorno.
- Probe de geometría: ejecutado dentro del frame graph con target de color/profundidad y estado de cámara. Limitaciones y decisión detalladas en `GEOMETRIA.md`.
- Medición exploratoria OpenGL a 854x480: mediana del render completo 0.813 ms vanilla (266 muestras), 0.643 ms bloom (351). La sesión carga chunks al inicio: la diferencia no permite atribuir coste incremental al bloom y no indica que acelere el juego. Memoria nominal de targets calculada en la sección anterior.

## Verificación final de la entrega 0.1.0

- 21 pruebas JUnit, cero fallos, errores u omisiones. Incluyen el codec real de PostChainConfig para todos los ejemplos, opciones, persistencia y observación de archivos, además de las pruebas anteriores.
- JAR en instalación aislada sin fuentes del mod: OpenGL y Vulkan efectivos, NVIDIA GeForce RTX 5060 Ti, Windows 11. Ambos completan restauración de selección al arrancar, lectura de GPU, múltiples pasadas, error de GLSL y recuperación, bloom, opciones persistentes, recarga automática y cambio a 960x540 con efecto activo.
- Se encontró y corrigió una carrera al salir: el último tick podía consultar un WatchService cerrado. La prueba de regresión verifica que consultar/cerrar de nuevo sea seguro; ambos clientes empaquetados cierran limpiamente.
- Los scripts verifican el backend efectivo. Un fallo del script de prueba al escribir opciones sin versión/BOM se corrigió; no se acepta fallback OpenGL como prueba de Vulkan.
- `docs/pruebas/RESULTADOS.json` conserva el resumen de pruebas y marcadores, sin versionar los mundos ni logs completos.
- GitHub Actions incluye build y tests unitarios en cada push/PR; su estado remoto debe consultarse en GitHub. Los probes de GPU se ejecutan localmente y no se atribuyen a CI.
- Publicación en plataformas no ejecutada. Código original con All Rights Reserved y avisos del MDK conservados; la elección definitiva de licencia corresponde al titular.

- Integración final de UI: botón real de Ajustes de vídeo invocado y pantalla de Prismora abierta; tecla configurable registrada una sola vez. Se renderizaron 125 frames de la pantalla antes de regresar al menú y cerrar. Capturas inspeccionadas en `docs/screenshots/`.


## Ampliación 0.2.0

- 37 pruebas JUnit locales: persistencia/migración, perfiles, escala, límites de memoria, bypass de pasadas, tipos de widget y política adaptativa (presión sostenida, calentamiento, cooldown y límites aislados de CPU/VSync), además de regresiones anteriores.
- JAR aislado Windows, OpenGL y Vulkan: buffers Alta/Baja comprobados, lectura de imagen escalada, Entrada con cero pasadas de bloom, preset persistido, fallback real solicitado por política con presión sintética, selección conservada y reactivación manual. Toggle/choice del pack Color cambian uniforms compilados; pantallas inspeccionadas mediante capturas.
- Benchmark de vanilla y cuatro perfiles: 1280x720 en RTX 5060 Ti, muestras reales con TimerQuery, sin presión sintética durante las mediciones; informes y límites en RENDIMIENTO.md. El límite AFK inicial se identificó, se documentó y se evitó en las mediciones finales.
- Regresión de shader inválido, recarga, persistencia, edición de archivos y redimensionado: scripts del JAR en ambos backends. Los mensajes de error de GLSL durante la prueba negativa son esperados.
- CI añade Linux para build/unit tests; no equivale a probar render en Linux ni otras GPU. Las mediciones de entrada/baja/media/alta corresponden a presets en el mismo hardware físico, no a tarjetas distintas.

- CI Windows y Ubuntu finalizó correctamente en la ejecución 41160406783. El probe visual adicional renderizó la pantalla Rendimiento sin overlay de carga; la captura final se incluye en docs/screenshots/performance.png. Resumen de la ampliación: docs/pruebas/RESULTADOS-0.2.0.json.

## Menús compactos

El menú de opciones legacy limita su área a 420x300 unidades GUI y la centra, con columnas y controles de pie ajustados a ese ancho. La pantalla de errores limita el texto a un panel de 420x240 y ajusta sus líneas. Ambos se reducen con la ventana; no se modifica la escala GUI de la instalación del jugador.

Validación: build y 65 pruebas unitarias aprobadas. Probe visual de cliente con Complementary r5.9.3, 1920x1080 y 854x480 (escala GUI 2 en el entorno de prueba): navegación a Performance Settings, límites de controles, centrado y ausencia de solapamientos. Marcador PRISMORA_COMPACT_UI_PASS. Capturas inspeccionadas en docs/screenshots/compact.

Reproducir: `gradlew.bat runClient -PprismoraLegacyUiSmoke=true -PprismoraCompactUiSmoke=true` con el ZIP oficial instalado en run/shaderpacks. Este probe verifica el menú, no el render del shaderpack.
