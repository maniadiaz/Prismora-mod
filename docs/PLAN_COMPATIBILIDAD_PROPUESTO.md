# Prismora: plan completo

> Para la implementación específica y la entrega verificable de Complementary, seguir [PLAN_COMPLEMENTARY_100.md](planeacion/PLAN_COMPLEMENTARY_100.md). Este documento conserva el diseño general anterior; sus afirmaciones temporales deben verificarse antes de usarlas.

Cargador de shaderpacks para Minecraft Java, hecho desde cero, con interfaz gráfica en el menú del juego. Permite usar shaderpacks de otras personas (formato OptiFine/Iris) y aplicarlos al mundo.

| | |
|---|---|
| **Minecraft** | 26.3 |
| **Loader** | Forge 66.1.0 |
| **Java** | 25 |
| **Mod id** | `prismora` |
| **Lado** | Solo cliente |
| **Licencia** | Por definir (ver sección 11) |

---

## 1. Objetivo

Que el jugador pueda soltar un shaderpack `.zip` o carpeta en `shaderpacks/`, elegirlo desde *Opciones → Ajustes de vídeo → Shaders* y verlo aplicado en el mundo, sin depender de Iris, Oculus ni Embeddium.

**Qué NO es el objetivo:** crear shaders propios ni un formato de pack propio. Prismora **implementa la especificación de shaderpacks existente**.

## 2. Contexto técnico verificado

Datos confirmados en las notas oficiales y en listados de mods:

- Desde 26.3 snapshot 5, los shaders de Minecraft se compilan con **ShaderC** tanto en OpenGL como en Vulkan, de forma idéntica. Los defines pasaron de `B3D_*` a `RENDERPEARL_*`.
- Mojang planea **retirar OpenGL** cuando Vulkan esté estable. En los snapshots de 26.4, Vulkan ya es el renderer por defecto.
- Los mods de Forge para 26.3 usan **Java 25**, entrada **SDL** y Forge 26.3-66.0.x.
- **Iris** (LGPL-3.0) tiene build para 26.3 en Fabric, pero no en Forge. Sirve como referencia de cómo se implementa la especificación.

Lo que **no** está verificado y debe comprobarse en el código descompilado de 26.3 antes de implementar:

- Nombres y firmas exactas de `GpuDevice`, `RenderPipeline`, `RenderPass` y clases relacionadas.
- Qué partes del render de terreno, entidades y agua se pueden reemplazar desde Forge sin mixins.
- Si ShaderC acepta GLSL legacy o hace falta traducirlo (suposición: hace falta, ver sección 6).

## 3. Alcance

**Incluye**
- Carga de shaderpacks (carpeta y `.zip`) en formato OptiFine/Iris.
- Pantalla de selección y de opciones del pack.
- Traducción de GLSL legacy a GLSL compatible con ShaderC.
- Pasadas de postproceso, g-buffers y sombras.
- Recarga en caliente, errores visibles y fallback seguro al render vanilla.

**Excluye (por ahora)**
- Optimización del renderer de chunks (lo que hace Embeddium/Sodium).
- Soporte para servidores dedicados.
- Ray tracing y extensiones propias del formato.
- Funciones raras de OptiFine usadas por pocos packs.

## 4. Principios de diseño

1. **Nada de llamadas GL directas.** Todo pasa por la abstracción de render de Mojang, porque OpenGL se retira.
2. **Aislar el acceso al render** en una capa propia (`render/bridge`). Si Mojang cambia la API en 26.4+, solo se toca esa capa.
3. **Fallar sin crashear.** Un pack roto nunca debe tumbar el juego: se desactiva y se muestra el error.
4. **Mixins al mínimo.** Preferir eventos de Forge. Cada mixin es deuda en cada versión nueva.
5. **Probar en OpenGL y en Vulkan** desde la primera fase.

## 5. Estructura del proyecto

```
prismora/
├── build.gradle
├── gradle.properties
├── docs/
│   ├── spec-notes.md          # notas de la especificación de OptiFine
│   └── compat-matrix.md       # packs probados y su estado
├── src/main/java/com/prismora/
│   ├── Prismora.java
│   ├── client/
│   │   ├── gui/
│   │   │   ├── ShaderPackScreen.java
│   │   │   ├── ShaderPackList.java
│   │   │   ├── PackOptionsScreen.java
│   │   │   └── ErrorOverlay.java
│   │   ├── config/PrismoraConfig.java
│   │   └── events/ClientEvents.java
│   ├── pack/
│   │   ├── ShaderPackManager.java
│   │   ├── ShaderPack.java
│   │   ├── PackSource.java            # carpeta o .zip
│   │   ├── PropertiesParser.java      # shaders.properties, block.properties
│   │   ├── OptionParser.java          # #define y sliders
│   │   └── IncludeResolver.java       # #include
│   ├── glsl/
│   │   ├── Preprocessor.java
│   │   ├── LegacyTranslator.java      # GLSL legacy -> ShaderC
│   │   └── CompileResult.java
│   ├── render/
│   │   ├── bridge/                    # única capa que toca las clases de Mojang
│   │   ├── PassGraph.java
│   │   ├── RenderTargets.java
│   │   ├── GBufferPrograms.java
│   │   ├── ShadowPass.java
│   │   └── uniforms/
│   │       ├── UniformProvider.java
│   │       └── CommonUniforms.java
│   └── compat/
│       └── KnownIssues.java
└── src/main/resources/
    ├── META-INF/mods.toml
    ├── pack.mcmeta
    └── assets/prismora/lang/{es_mx,en_us}.json
```

## 6. Piezas clave

### 6.1 Formato de shaderpack (lo que hay que leer)

```
shaderpacks/MiPack/
├── shaders/
│   ├── shaders.properties
│   ├── block.properties
│   ├── lang/*.lang
│   ├── gbuffers_terrain.vsh / .fsh
│   ├── gbuffers_water.vsh / .fsh
│   ├── shadow.vsh / .fsh
│   ├── composite.vsh / .fsh   (y composite1..N)
│   ├── deferred.vsh / .fsh
│   ├── final.vsh / .fsh
│   ├── lib/                    (archivos para #include)
│   └── world-1/, world1/       (Nether y End)
```

### 6.2 Traductor de GLSL

Muchos packs usan GLSL legacy (`gl_Vertex`, `gl_Color`, `gl_MultiTexCoord0`, `gl_FragData`, `gl_ModelViewMatrix`...). El renderer de 26.3 espera shaders compilables con ShaderC con reglas tipo Vulkan.

El traductor debe:
- Resolver `#include`, `#define` y `#ifdef` antes de traducir.
- Reemplazar las variables built-in legacy por entradas, salidas y uniforms explícitos.
- Añadir `location` en entradas y salidas cuando se compila para Vulkan (nota oficial: solo especificar locations cuando la macro `VULKAN` está definida).
- Mantener nombres idénticos entre salidas de vértice y entradas de fragmento (se emparejan por nombre).
- Reportar errores con **archivo y línea del pack original**, no del código traducido.

### 6.3 Uniforms y atributos

Lista inicial a proveer (se amplía según los packs que se prueben): resolución, tiempo del mundo, posición del sol y la luna, matrices de cámara y proyección (actuales y previas), matrices de sombra, posición de cámara, color de niebla y cielo, clima, `mc_Entity`, `at_tangent` y similares.

### 6.4 Grafo de pasadas

Orden esperado de ejecución por frame: `shadow` → `gbuffers_*` → `deferred` → `composite*` → `final`. Los `colortex0-N` se declaran con su formato (`RGBA8`, `RGBA16F`...) y se reutilizan entre pasadas.

## 7. Fases

Cada fase tiene un entregable y un criterio de aceptación. No se pasa a la siguiente sin cumplirlo.

### Fase 0: Proyecto base
- [ ] Gradle con ForgeGradle 7.x y Java 25.
- [ ] `mods.toml` declarado solo cliente.
- [ ] Mod que arranca limpio en dev y en un cliente normal.
- [ ] **Estudiar el código descompilado de 26.3**: render, pipelines y compilación de shaders. Anotar clases reales en `docs/spec-notes.md`.
- **Aceptación:** el juego abre con el mod, un log confirma el arranque, y existe un documento con las clases de render que se usarán.

### Fase 1: Interfaz y gestión de packs
- [ ] Botón "Shaders" en *Ajustes de vídeo* (`ScreenEvent.Init.Post`) y tecla configurable.
- [ ] `ShaderPackScreen` con lista, botón de activar, recargar y abrir carpeta.
- [ ] `ShaderPackManager`: escaneo de `shaderpacks/`, carpetas y `.zip`.
- [ ] Config persistente con el pack seleccionado.
- [ ] Textos en `es_mx` y `en_us`.
- **Aceptación:** se ven los packs de la carpeta, se selecciona uno y la selección sobrevive al reiniciar. Todavía no renderiza nada.

### Fase 2: Parser y preprocesador
- [ ] Parser de `shaders.properties` y `block.properties`.
- [ ] Resolver `#include`, `#define`, `#ifdef` y opciones del pack.
- [ ] Detección de dimensiones (`world-1`, `world1`).
- **Aceptación:** al seleccionar un pack, un log muestra sus programas detectados, opciones y errores de formato.

### Fase 3: Compilación y traductor de GLSL
- [ ] `LegacyTranslator` para un subconjunto básico.
- [ ] Compilar el shader `final` de un pack simple con ShaderC vía la abstracción de Mojang.
- [ ] Errores con archivo y línea del pack original.
- **Aceptación:** un shader `final` mínimo compila en OpenGL y en Vulkan; uno con error muestra un mensaje legible en pantalla.

### Fase 4: Postproceso (primer hito visible)
- [ ] `RenderTargets` y `PassGraph` para `composite*` y `final`.
- [ ] Uniforms básicos (resolución, tiempo, textura de color y de profundidad).
- **Aceptación:** un pack de solo postproceso (por ejemplo, bloom o tonemapping) se ve aplicado al juego. **Este es el primer entregable publicable como beta.**

### Fase 5: G-buffers
- [ ] Reemplazo de pipelines de terreno, entidades, partículas y agua por los programas `gbuffers_*` del pack.
- [ ] Atributos de vértice (`mc_Entity`, tangentes) y mapeo de bloques desde `block.properties`.
- [ ] `deferred`.
- **Aceptación:** un pack con iluminación diferida muestra el mundo con su propia iluminación, sin artefactos graves en terreno y entidades.
- **Riesgo alto:** depende de cuánto del render de vanilla pueda sustituirse desde Forge. Prototipar temprano (idealmente en paralelo a la Fase 4).

### Fase 6: Sombras
- [ ] `ShadowPass` con matrices del sol y la luna.
- [ ] Texturas de sombra disponibles en los `gbuffers_*` y `composite*`.
- **Aceptación:** un pack con sombras muestra sombras proyectadas por terreno y entidades.

### Fase 7: Opciones del pack
- [ ] `PackOptionsScreen`: sliders, toggles y desplegables definidos en `shaders.properties`.
- [ ] Guardado de valores por pack.
- [ ] Recarga en caliente al aplicar cambios.
- **Aceptación:** cambiar una opción de un pack recompila y se ve el efecto sin reiniciar.

### Fase 8: Robustez
- [ ] Fallback al render vanilla ante cualquier error.
- [ ] `ErrorOverlay` con el detalle del fallo.
- [ ] Manejo de cambio de dimensión, de tamaño de ventana y de resolución.
- [ ] Liberación correcta de recursos al desactivar un pack.
- **Aceptación:** un pack roto, un cambio de dimensión y un redimensionado no producen crash ni fugas visibles.

### Fase 9: Compatibilidad y publicación
- [ ] Matriz de compatibilidad con packs populares en `docs/compat-matrix.md`.
- [ ] Lista de incompatibilidades conocidas con otros mods de render.
- [ ] Pruebas en OpenGL y Vulkan, en Windows y Linux.
- [ ] Publicación en Modrinth y CurseForge.
- **Aceptación:** al menos varios packs de uso común funcionan sin glitches graves, y la matriz está documentada con honestidad.

## 8. Estrategia de pruebas

- **Packs de prueba por fase:** empezar con uno mínimo propio (solo para validar, no para distribuir) y luego packs reales de complejidad creciente.
- **Doble backend:** cada fase se valida con OpenGL y con Vulkan.
- **Pruebas de regresión:** capturas de referencia por pack y comparación visual al cambiar de versión de Minecraft o de Forge.
- **Pruebas de error:** packs con sintaxis rota, archivos faltantes y opciones inválidas.
- **Rendimiento:** medir ms por pasada y FPS contra el juego sin shaders.

## 9. Riesgos y mitigación

| Riesgo | Impacto | Mitigación |
|---|---|---|
| El renderer de vanilla no permite reemplazar pipelines desde Forge sin mixins frágiles | Alto | Prototipar la Fase 5 pronto; encapsular en `render/bridge` |
| El traductor de GLSL crece sin control | Alto | Empezar por subconjunto; ampliar solo según packs reales |
| Mojang cambia la API de render en 26.4+ | Alto | Capa `bridge` aislada; fijar versión por rama |
| Retiro de OpenGL | Medio | No usar GL directo; probar con Vulkan desde el inicio |
| Packs usan funciones raras de OptiFine | Medio | Documentar en la matriz y priorizar por popularidad |
| Alcance enorme | Alto | Entregar beta en la Fase 4; el resto es incremental |
| Conflictos con otros mods de render | Medio | Detección al arrancar y lista de incompatibilidades |

## 10. Hitos publicables

- **Alpha 0.1:** Fases 0 a 3 (gestión de packs y compilación).
- **Beta 0.2:** Fase 4 (postproceso visible).
- **Beta 0.3:** Fases 5 y 6 (g-buffers y sombras).
- **Release 1.0:** Fases 7 a 9 (opciones, robustez y compatibilidad).

## 11. Licencia y aspectos legales

- Iris es **LGPL-3.0**. Se puede estudiar su código como referencia de cómo implementa la especificación.
- Si se copia código de Iris, hay que respetar la licencia: código abierto y créditos. Para evitar problemas, lo más limpio es **implementar desde la especificación y las pruebas**, y consultar Iris solo como comparación de comportamiento.
- Los shaderpacks tienen cada uno su propia licencia. Prismora **no distribuye** packs de terceros.
- Elegir licencia del mod antes de publicar (si se reutiliza código LGPL, esa decisión queda condicionada).

## 12. Documentación a producir

- `README.md`: instalación, uso y requisitos.
- `docs/compat-matrix.md`: packs probados y su estado.
- `docs/spec-notes.md`: decisiones sobre la especificación y clases reales de 26.3.
- `CHANGELOG.md` y guía para reportar bugs (incluir pack, GPU, backend y log).

## 13. Primeros pasos concretos

1. Crear el repositorio y montar el proyecto con ForgeGradle 7.x y Java 25.
2. Hacer que el mod arranque y confirmar versión de Forge contra 66.1.0.
3. Descompilar 26.3 y documentar las clases de render y compilación de shaders.
4. Construir la Fase 1 completa (interfaz y gestión de packs).
5. En paralelo, prototipar una pasada de pantalla completa para validar la Fase 4 lo antes posible.

## 14. Calidad y rendimiento añadidos al plan

- UI con perfiles Entrada/Baja/Media/Alta y configuración personalizada; opciones de pack con sliders, toggles y elecciones. Aplicación por lote y persistencia.
- Objetivo de Entrada: presupuesto de frame de 33.33 ms (30 FPS) con menor resolución de postproceso, efectos opcionales eliminados y fallback a vanilla si la GPU sigue excediendo el presupuesto mínimo. Es una meta, no una garantía universal.
- Calidad adaptativa con mediciones de GPU y frame, calentamiento, presión sostenida y cooldown. Ignorar AFK, ventanas inactivas, menús, recargas y límites aislados de CPU/VSync. Sin modificar preferencias de distancia de render o resolución del jugador.
- Presupuestos nominales de buffers: Entrada 64 MiB, Baja 96 MiB, Media/Personalizado 192 MiB, Alta 384 MiB. Rechazo previo a asignación si el pack excede el presupuesto, con error visible; no son lecturas ni garantías de VRAM disponible.
- Pruebas físicas separadas para GPU integrada/entrada, baja, media y alta a resoluciones declaradas; medir vanilla, tiempo GPU y percentiles de frame. No presentar cambiar un preset en RTX 5060 Ti como emulación de otra GPU.
- Benchmarks reproducibles exportados a JSON con GPU, backend, resolución, límite de FPS y descarte de outliers. Verificar ambos backends y cada etapa en Git.

Estado: esta ampliación se implementa en 0.2.0 sobre el postproceso existente. Las fases del documento relacionadas con formato OptiFine/Iris, traducción GLSL legacy, G-buffers y sombras todavía no se consideran implementadas ni verificadas por esta entrega.
