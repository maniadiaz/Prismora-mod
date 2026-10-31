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
