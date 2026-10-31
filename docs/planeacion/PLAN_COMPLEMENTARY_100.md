# Plan de implementación: Complementary completo en Prismora

Fecha: 31 de octubre de 2026. Estado: planeación; no declara implementadas las etapas pendientes.

## Resultado que se va a entregar

Instalar Prismora, colocar el ZIP original de **Complementary Reimagined r5.9.3** en `shaderpacks`, seleccionarlo, entrar a un mundo y jugar con sus efectos, perfiles y ajustes. La prueba que se entregue al usuario deberá usar ese pack real; un menú funcional o un shader de diagnóstico no sustituye esa prueba.

Base fijada: Minecraft Java 26.3, Forge 66.1.0, Java 25, Windows y los backends OpenGL/Vulkan. Primera GPU disponible: RTX 5060 Ti. El mod sigue siendo independiente de Iris/Oculus/Embeddium y usa la abstracción gráfica de Mojang. Se permiten mixins documentados donde los eventos de Forge no cubran el contrato.

«100 %» significa cobertura y validación del alcance declarado para esa versión concreta de Complementary. No significa compatibilidad automática con versiones futuras, todos los shaderpacks o ausencia garantizada de cualquier bug. No se declarará completo mientras un efecto o ajuste admitido requiera una función ausente. Una función opcional no se anunciará como disponible para evitar un error de compilación.

## Dos entregas claramente diferenciadas

**Hito A — Complementary utilizable para la primera prueba del usuario.** Se completa después de las etapas E00–E09. Debe cargar el ZIP original, dibujar el mundo real con el camino base del pack, terreno, entidades, agua, cielo y sombras; funcionar en las tres dimensiones; permitir cambiar ajustes; y conservar un modo seguro de desactivación. Debe pasar una sesión con el JAR instalado, no solo `runClient`. Se entregará como experimental con una lista concreta de defectos conocidos. No se enviará al usuario únicamente para volver a encontrar el bloqueo `requires gbuffers/shadow`.

**Hito B — Cobertura completa y entrega validada.** Requiere E00–E15, todos los caminos aplicables de perfiles/efectos/opciones y la matriz de pruebas definida abajo. Las integraciones con mods externos se certifican por combinación y versión; cuando no exista un entorno compatible, constan como no evaluables y no como aprobadas. No se extrapola esa validación al render base.

Los bugs visuales menores pueden corregirse después de la primera prueba. Una pantalla negra, geometría ausente, compilación fallida del camino base, sombras inexistentes o cierres del juego bloquean el Hito A.

## Estado de partida comprobado

| Componente | Estado actual |
|---|---|
| Carpetas/ZIP y lectura de metadatos | Implementados, con límites y validación de rutas |
| Menús, perfiles, traducciones, presets y opciones por pack | Implementados; menú de Complementary probado y compactado |
| GLSL composite/final de alcance limitado | Implementado y probado con un fixture original |
| Profundidad de cámara `depthtex0` | Conexión flotante real y conversión de reverse-Z probadas |
| Proyección/vista actuales e inversas | Captura de matrices reales y transporte de floats probados |
| Geometría, materiales, MRT y sombras de Complementary | Pendientes |
| Compute, imágenes, SSBO y todas las expresiones del pack | Pendientes |
| Pruebas | 65 unitarias; fixtures en ambos backends y con nueve mods de Chill; no equivalen a render de Complementary |
| Publicación externa | Proyectos del titular todavía pendientes de creación |

El inventario local fijado está en `COMPLEMENTARY_INVENTARIO.json`. El ZIP se verificó con SHA-512. Se detectaron 436 archivos, 409 opciones, 51 pantallas y 8 perfiles. Son cifras del lector actual, que deben contrastarse con el inventario de requisitos antes de darlo por exhaustivo.

La revisión estática encontró `gbuffers_*`, `deferred1`, varias pasadas `composite*`, `final`, `shadow` y `shadowcomp.csh`; también archivos `dh_*` y `clrwl_*`. Las declaraciones de formatos incluyen float y SNORM. Hay propiedades condicionales, texturas propias y uniforms calculados. **Un hallazgo estático no implica que una rama esté activa**: E00/E01 determinarán qué usa cada configuración.

## Arquitectura necesaria

El camino propio de postprocesado se conserva como regresión. Complementary necesita un runtime de mundo con recursos y orden de ejecución propios: no cabe correctamente en una cadena de postproceso que solo escribe una textura RGBA8 por pasada.

Separación propuesta de responsabilidades, cuyos nombres podrán ajustarse a las APIs verificadas:

- `compat`: contrato evaluado del pack, preprocesador, traducción y diagnósticos de origen.
- `render/bridge`: acceso a extracción de escena, pipelines, buffers de vértices y frame graph de Mojang; mixins pequeños y verificables.
- `render/runtime`: orden de pasadas, adjuntos, recursos persistentes, barreras y recargas transaccionales.
- `render/geometry`: formatos/atributos y selección del programa para cada tipo de dibujo.
- `render/shadow`: cámara de luz, extracción/culling y dibujo de sombras reales.
- `render/uniforms`: snapshot coherente del frame, historia temporal y expresiones del pack.

El render básico de referencia se prueba desde el comienzo en ambos backends. No se usará GL directo ni se rellenarán uniforms desconocidos con ceros o matrices inventadas para aparentar compatibilidad.

## Etapas de ejecución

### E00 — Congelar el objetivo y construir el inventario ejecutable

**Trabajo:** fijar hash/versión del ZIP; inventariar programas, includes, uniforms, samplers, atributos, adjuntos, formatos y propiedades. Evaluar requisitos para valores por defecto, los ocho perfiles y cambios que activen nuevas ramas. Incluir fallback de programas, dimensiones, compute y prefijos de integraciones. Identificar expresamente lo que el lector actual todavía ignora.

**Entrega:** informe de capacidades por configuración y checklist de requisitos enlazado a archivo/programa. Guardar metadatos y hashes; no redistribuir el código ni el ZIP de Complementary.

**Cierre/pruebas:** ninguna dependencia activa queda sin clasificar; las ramas opcionales quedan separadas de los requisitos base. La inspección actual es una entrada para esta etapa, no su cierre completo.

### E01 — Completar preprocesado, propiedades y resolución del contrato

**Depende de:** E00.

**Trabajo:** macros reales de versión/backend/GPU, includes y guardas, condiciones de `shaders.properties`, activación de programas, opciones repetidas y sus conflictos, perfiles/herencia, dimensiones/fallbacks y directivas de adjuntos. Mantener diferencias entre parseo de menús y evaluación de propiedades de ejecución. Tratar límites y diagnósticos de entrada inválida.

**Cierre/pruebas:** fixtures pequeños cubren cada construcción usada por el pack; se generan contratos deterministas del ZIP real para cada perfil/dimensión. Ningún programa se pierde silenciosamente por un nombre no reconocido.

### E02 — Runtime de recursos GPU y adjuntos múltiples

**Depende de:** E01.

**Trabajo:** texturas color/profundidad, vistas y samplers; adjuntos lógicos `colortex*` y sus alias; MRT; formatos float/SNORM; clears, filtrado, mipmaps y tamaños relativos. Implementar ping-pong, buffers persistentes y reglas de flip. Mantener la precisión de profundidad. Detectar lectura/escritura ilegal de un mismo recurso y sincronizar sus transiciones.

Mapear formatos no nativos solo mediante una representación equivalente verificada: no degradar HDR, canales firmados o profundidad a RGBA8. Evaluar presupuestos con las ramas activas; no asignar simultáneamente todos los tamaños encontrados en el texto del pack.

**Cierre/pruebas:** lecturas GPU comprueban distintos adjuntos, valores negativos, precisión HDR, flip entre pasadas y preservación entre frames. Redimensionar/recargar/desactivar libera recursos sin fugas. Verificar capacidades efectivas de cada backend y errores claros ante límites reales.

### E03 — Compilador y enlaces GLSL del mundo

**Depende de:** E01 y E02.

**Trabajo:** ampliar la traducción de vértices/fragmentos, varyings y locations, atributos legacy, salidas MRT, arrays y tipos de uniforms, bloques y enlaces de samplers. Resolver símbolos de compatibilidad y extensiones utilizadas por las ramas activas. Preservar archivo/línea de includes. Preparar contratos de compute sin fingir su ejecución.

**Cierre/pruebas:** fixtures por construcción y compilación real de los programas gráficos activos de Complementary en OpenGL/Vulkan. Cada error apunta al archivo original. Que un shader compile todavía no cuenta como escena dibujada.

### E04 — Extracción de terreno y atributos de materiales

**Depende de:** E02 y E03.

**Trabajo:** conectar geometría sólida y cutout a los programas correctos; soportar dibujo directo e indirecto o un camino directo funcional medido. Generar normales, tangentes/orientación, coordenadas UV, luz, color, AO y atributos adicionales realmente utilizados. Interpretar `block.properties`, estados de bloque y atributos como `mc_Entity`, `at_tangent` y coordenadas centrales cuando los exija el inventario. Conservar atlas, animaciones y contexto de sección/cámara.

**Cierre/pruebas:** cubos, escaleras, losas, hojas y plantas en posiciones/chunks distintos; lectura de buffers de materiales y captura de mundo con el programa real `gbuffers_terrain`. IDs, tangentes o normales constantes no satisfacen el cierre. Probar reconstrucción de chunks y distancias grandes.

### E05 — Entidades, bloques entidad, mano y efectos de mundo

**Depende de:** E04.

**Trabajo:** conectar entidades opacas/translúcidas, bloques entidad, armaduras y glint, ojos/emisión, entidades con brillo, mano/objetos, partículas, líneas, selección, daño de bloques, rayos, faros y efectos restantes inventariados. Proporcionar identificadores y datos de cada categoría. Definir fallbacks correctos para categorías sin programa propio y para contenido de otros mods.

**Cierre/pruebas:** escena con jugador, mobs, cofres, carteles, armaduras, objetos en ambas manos y partículas. No desaparece contenido ni se aplica un programa del mundo a la GUI. Verificar primera/tercera persona, inventario y vista de espectador.

### E06 — Agua, transparencias, cielo, clima y profundidades separadas

**Depende de:** E04 y E05.

**Trabajo:** ordenar fases opacas, deferred y transparentes como las espera el pack. Conectar `gbuffers_water`, cristal, entidades translúcidas, cielo básico/texturizado, sol/luna, estrellas, nubes y clima. Implementar blending/alpha-test por programa y por adjunto, culling y overlays. Capturar las diferentes profundidades en los momentos correctos, incluida la separación de transparencias/mano que establezca el contrato. Resolver la interacción con OIT de Minecraft sin mezclar dos pipelines incompatibles.

**Cierre/pruebas:** agua y cristal superpuestos, visión desde encima/debajo del agua, lluvia/nieve y cambios de día/noche. Las distintas `depthtex*` deben tener contenidos correspondientes a sus etapas; no pueden ser alias indiscriminados de `depthtex0`.

### E07 — Sombras reales del sol y la luna

**Depende de:** E04–E06; en paralelo con E08 después de E06.

**Trabajo:** construir cámara/proyección de la luz, extracción y culling independientes de la cámara del jugador; dibujar terreno, cutouts y categorías configuradas por el pack. Generar profundidades y colores de sombra, transparencias y samplers necesarios. Respetar distancias, resolución, filtros, offsets y propiedades de culling; enlazar matrices y posiciones de luz coherentes.

**Cierre/pruebas:** un objeto fuera de la vista del jugador proyecta una sombra visible; hojas recortadas no dibujan un rectángulo sólido; entidades y jugador siguen la configuración; sombras cambian con sol/luna. Medir estabilidad al mover cámara y origen de chunks. Reutilizar profundidad de pantalla no cuenta como mapa de sombras.

### E08 — Uniforms completos, texturas y estado temporal

**Depende de:** E06.

**Trabajo:** completar los uniforms del inventario con fuentes reales: frame/tiempo, cámara actual/anterior, matrices actuales/anteriores, estado del jugador y mano, iluminación, clima, biomas, niebla, visión bajo fluidos, efectos y dimensiones. Evaluar `uniform.*` y `variable.*`, incluidas funciones y estados `smooth` usados por el pack. Cargar sus texturas de ruido y materiales y respetar enlaces por etapa. Mantener historial temporal y recursos persistentes sin contaminación entre mundos.

**Cierre/pruebas:** movimiento, pausa, teletransporte, portal, cambio de dimensión, muerte, recarga y reconexión. Reset correcto de historia; no hay NaN ni matrices antiguas presentadas como actuales. Cada uniform activo tiene tipo, procedencia y actualización documentados.

### E09 — Integrar el frame completo y entregar el Hito A

**Depende de:** E07 y E08.

**Trabajo:** ejecutar el orden completo de programas de la configuración base: sombras y etapas aplicables, geometría, deferred, transparencias, composite y final. Resolver qué compute/funciones avanzadas son obligatorias para esa configuración; si lo son, adelantar su implementación desde E10 antes de cerrar esta etapa. Respetar render targets, exposición, color y presentación final. Sustituir el rechazo genérico solo cuando el contrato requerido esté satisfecho.

**Cierre obligatorio:** JAR instalado en una instancia aislada selecciona el ZIP real, entra al mundo y muestra Complementary en Overworld, Nether y End, con agua, entidades y sombras. Ejecutar una sesión de al menos 30 minutos en cada backend con cambios de opciones, desactivación y reactivación. Guardar capturas, logs, versiones y limitaciones. Un parche externo al ZIP o un shader propio que simule su apariencia no sustituye esta prueba.

**Entrega al usuario:** JAR experimental identificado, hash del ZIP objetivo, instalación, ajustes de partida, qué probar y lista de bugs. A partir de aquí el usuario puede probar Complementary mientras se corrigen defectos y se completa el resto del alcance.

### E10 — Caminos avanzados: compute, imágenes y SSBO

**Depende de:** E02, E03 y E08; puede adelantarse cuando E09 lo necesite.

**Trabajo:** ejecutar `shadowcomp.csh` y cualquier compute activado por el inventario; tamaños de trabajo, image load/store, custom images, SSBO, bindings, límites y barreras. Cubrir los atributos/capacidades opcionales declarados por el pack cuando se implementen. Resolver rutas que activen las opciones avanzadas en lugar de deshabilitarlas permanentemente para declarar éxito.

**Cierre/pruebas:** comprobar resultados escritos/leídos por GPU entre compute y dibujo, sincronización y estabilidad de varios frames. Evaluar los consumos según las opciones reales y la VRAM. No anunciar `CUSTOM_IMAGES`/`SSBO` u otra capacidad hasta que funcione su contrato completo en el backend correspondiente.

### E11 — Todos los perfiles, opciones y efectos del pack base

**Depende de:** E09 y E10.

**Trabajo:** ejecutar los ocho perfiles detectados; clasificar las opciones que cambian compilación, recursos y solo valores. Cubrir efectos disponibles de iluminación/materiales, reflejos, PBR/resource packs, agua, nubes/atmósfera, antialiasing y filtros temporales, cámara, bloom/exposición y cualquier otro del inventario. Verificar que cambiar un control cambia el efecto correspondiente y se conserva al reiniciar.

**Cierre/pruebas:** probar cada valor admitido al menos en una combinación válida y cada rama de capacidades, más combinaciones de riesgo identificadas por dependencias. Guardar los casos ejecutados; probar únicamente Low/High no equivale a probar todas las opciones. No se promete enumerar el producto cartesiano de cientos de opciones: se documenta la cobertura y sus límites.

### E12 — Integraciones externas aplicables

**Depende de:** E11.

**Trabajo:** evaluar `dh_*`, `clrwl_*` y otras rutas del ZIP contra versiones reales de los mods que las activan. Implementar los puentes necesarios, datos de distancia/instancias y capacidades correspondientes. Probar texturas/resource packs opcionales de forma separada del renderer base. Si un mod no está instalado, no activar sus programas. Si no existe una versión compatible con la plataforma fijada, registrar el impedimento verificable y mantener esa certificación pendiente.

**Cierre/pruebas:** cada combinación declarada compatible tiene versiones, fixture, captura y resultado. «No instalado», «no evaluable» y «aprobado» son estados distintos. El certificado del modo base no implica compatibilidad con Distant Horizons, Create/Flywheel u otros renderers.

### E13 — Robustez, bugs y coexistencia

**Depende de:** E09; continúa durante E10–E12.

**Trabajo:** recarga transaccional, cancelación y liberación de recursos; redimensionado/fullscreen, pérdida/recreación de superficie, cambio de idioma/GUI y resource packs. Resolver fallos repetidos, cierres y corrupción de estado. Probar mod limpio y la instancia Chill mediante copias aisladas; añadir otros mods según los puentes tocados.

**Cierre/pruebas:** 20 ciclos de activar/desactivar y recargar, 10 viajes de dimensión, 10 redimensionados y una sesión prolongada de 2 horas por backend; verificar memoria y logs. ZIP inválido o GLSL erróneo permiten recuperar el juego. Ninguna prueba modifica los mundos reales del usuario. Los bugs pendientes tienen pasos, severidad y evidencia.

### E14 — Rendimiento y matriz de hardware

**Depende de:** E11 y E13; se mide también desde E04 para detectar regresiones temprano.

**Trabajo:** medir coste por pasada, CPU/GPU, memoria de adjuntos/SSBO y compilación. Optimizar culling, cambios de estado, resolución y recursos, sin sustituir el shader por otro efecto. Relacionar perfiles de Prismora con las opciones reales del pack y aplicar adaptación con consentimiento/configuración del jugador. Proteger opciones elegidas por el usuario.

**Cierre/pruebas:** pruebas reproducibles con GPU de entrada, baja, media y alta; resolución, perfil, escena, chunks y driver publicados. En entrada, objetivo inicial medible de 30 FPS a 1280x720 con el perfil acordado y escena definida, registrando media, percentiles y mínimos. No prometer un mínimo absoluto en todos los mundos. Si falla el objetivo, documentar la medición y seguir optimizando; la RTX 5060 Ti no representa otras gamas. Linux con GPU requiere una prueba real adicional, no solo CI de compilación.

### E15 — Certificación de alcance, distribución y publicación

**Depende de:** E00–E14 para el Hito B dentro de su alcance declarado.

**Trabajo:** revisar cobertura de requisitos, cerrar bloqueantes, versionar artefactos, generar checksums, changelog, instrucciones y matriz de compatibilidad. Preparar reportes de bugs y rollback. Publicar en Modrinth/CurseForge cuando el titular haya creado los proyectos y proporcionado lo necesario; mantener la licencia vigente hasta que decida otra.

**Cierre/pruebas:** instalar el JAR distribuido en una instancia limpia y ejecutar el protocolo de aceptación con Complementary real. La versión publicada coincide con la probada. El ZIP del shader se obtiene por separado del autor, salvo permiso expreso de distribución. Los perfiles/capacidades y entornos no certificados se señalan, sin convertirlos en aprobados.

## Orden de dependencias y puntos de control

```text
E00 → E01 → E02 → E03 → E04 → E05 → E06
                                      ├→ E07 ─┐
                                      └→ E08 ─┴→ E09 → HITO A
                       E02/E03/E08 → E10 ─────────┘ si el camino base lo exige
E09 + E10 → E11 → E12
E09 → E13 (continúa durante E10–E12)
E11 + E13 → E14
E00–E14 → E15 → HITO B
```

La rama de E10 no puede omitirse cuando un perfil la requiera. Un problema de API en E02/E04 se resuelve en el puente; no transforma la etapa de implementación en una etapa «terminada como investigación».

## Escenas y protocolo que permitirán probarlo

Antes de invertir en toda la integración se resuelven estos riesgos mediante prototipos ejecutados, no solo lectura de APIs:

| Riesgo | Comprobación temprana y resolución |
|---|---|
| Acceso insuficiente a pasadas/escena | En E02/E04, sustituir un pipeline de terreno y dibujar MRT real desde la escena; usar el puente y hooks necesarios |
| Compute/imágenes/SSBO no expuestos o con límites del backend | En E02/E03, comprobar compilación, dispatch y sincronización mínimos; identificar la ampliación necesaria del puente antes de anunciar capacidades |
| Formatos/precisión incompatibles | Probar escritura y lectura GPU de cada formato requerido; usar una representación equivalente demostrada, sin degradación silenciosa |
| Cambios de Mojang u otros mixins | Fijar versiones, validar los puntos de inserción y probar mod limpio y combinaciones declaradas |
| Falta de GPU o mod externo para una prueba | Registrar exactamente el entorno faltante; no convertir la ausencia de prueba en éxito ni en una capacidad simulada |

Si una limitación exige revisar la plataforma fijada o el diseño, se documentará la decisión y su impacto antes de cambiar el objetivo. El plan no presupone que una API exista solo porque el efecto sea necesario.

| Escena | Qué debe permitir verificar |
|---|---|
| Exterior de día | Terreno, hojas/cutout, luz, sombras y objeto fuera de cámara |
| Exterior nocturno | Luna, estrellas, fuentes emisivas y luz de objetos |
| Agua y cristal | Reflejos, refracción, capas transparentes, bajo agua y profundidades |
| Cueva | Oscuridad, luces, niebla, ojos/brillo y transiciones de exposición |
| Zona con entidades | Mobs, jugador, armaduras, mano, carteles/cofres y partículas |
| Lluvia/nieve y biomas | Clima, nubes, niebla y uniforms calculados |
| Nether y End | Programas de dimensión, efectos y reinicio de historia temporal |
| Teletransporte/recarga | Estabilidad de matrices, buffers persistentes y recuperación |

Cada caso registra versión del mod/pack, hash del ZIP, backend efectivo, driver/GPU, resolución, escala, perfil, resource packs, otros mods, coordenadas/semilla y resultado. Para comparación visual se usa, cuando esté disponible, una ejecución de referencia autorizada y compatible; una versión distinta se identifica y no demuestra equivalencia exacta. Las capturas promocionales sirven como orientación, no como prueba numérica de fidelidad.

Protocolo mínimo del usuario en el Hito A: iniciar instancia de prueba → seleccionar el ZIP original → entrar al mundo → verificar día/noche y sombras → caminar/nadar → cambiar perfil y una opción visible → visitar Nether/End → desactivar/reactivar → reiniciar y verificar persistencia. Entregar junto al JAR dónde encontrar `latest.log` y qué datos adjuntar al reportar un bug.

## Gestión de bugs y criterio de salida

| Severidad | Ejemplos | Tratamiento |
|---|---|---|
| Bloqueante | No carga, pantalla negra, crash, terreno/entidades ausentes, datos GPU inválidos, sombras requeridas sin implementar | Resolver antes del Hito A o retirar esa build de prueba |
| Alta | Agua/transparencias rotas, dimensión incorrecta, recarga que deja el render inutilizable, consumo sin límite | Resolver para la combinación que se vaya a entregar |
| Media/baja | Artefacto localizado, parpadeo ocasional, etiqueta/tooltip, diferencia estética acotada | Puede acompañar una build experimental si consta y es reproducible |

El Hito B requiere cierre de bloqueantes/altas del alcance y cobertura completa de sus capacidades. Los defectos menores conocidos permanecen visibles; no se afirmará que un software está libre de todo bug.

## Disciplina de entrega por etapa

Para cada etapa: implementar → ejecutar sus pruebas pertinentes y regresiones afectadas → guardar evidencia y actualizar estado → commit descriptivo → push a Git. No subir un checkbox de implementación completa por tener interfaces, archivos vacíos, shaders simulados o un documento de investigación.

Un fallo de hardware/entorno se registra como pendiente; no se marca aprobado por semejanza. No se exigen todas las pruebas gráficas en cada cambio documental. El usuario recibe el JAR de Complementary en el Hito A, con mejoras sucesivas hasta el Hito B, sin esperar a perfeccionar todos los defectos cosméticos.

Seguimiento de las etapas en `COMPLEMENTARY_ETAPAS.csv`. Actualizarlo con commit, prueba y evidencia al terminar cada una. Las estimaciones de tiempo se harán después de E00 y la prueba de integración de E02/E04; no se fija una fecha sin conocer esos resultados.

## Fuentes y documentos relacionados

- Inventario local del ZIP oficial r5.9.3 y SHA-512: `COMPLEMENTARY_INVENTARIO.json`.
- Contrato de programas/adjuntos: https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/shaders.txt
- Propiedades y configuración: https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/shaders.properties
- APIs concretas: fuentes de Minecraft/Forge fijadas en este repositorio; verificar firmas antes de cada hook.
- Estado implementado y pruebas anteriores: `../IMPLEMENTACION_COMPATIBILIDAD.md`, `../VALIDACION.md` y `../pruebas/`.
- Plan general anterior: `../PLAN_COMPATIBILIDAD_PROPUESTO.md`. Este documento dirige la implementación específica de Complementary y evita interpretar el cierre de investigación anterior como soporte de render.
