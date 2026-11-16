# Ejecución del plan de Complementary

## Contrato e inventario ejecutable (E00/E01, en curso)

Implementado `auditShaderPack`: evalúa condiciones e includes del ZIP real, perfiles, variantes estructurales y escenarios de capacidades. Registra programas, uniforms, atributos, formatos, adjuntos, bloques y operaciones requeridas. Los escenarios `requested_features*` son análisis de requisitos; no anuncian capacidades de Prismora ni prueban un renderer instalado.

Resultado: 71 configuraciones analizadas (valor por defecto y ocho perfiles en cinco escenarios, más 26 variantes estructurales), tres dimensiones, cero errores de preprocesado o roles de programa desconocidos en esos casos. `docs/pruebas/complementary/requirements-summary.json` conserva metadatos y símbolos; el informe privado completo queda en `.gradle/test-packs` y no se distribuyen fuentes del pack.

Correcciones derivadas de ejecutar el pack real:

- Perfiles con `OPCION=valor`, además de `OPCION:valor` e herencia.
- El valor por defecto se conserva aunque falte en el rango comentado: esto recupera, entre otras, `COLORED_LIGHTING=0`.
- Los defines de contexto de wrappers (dimensión/etapa/programa) no aparecen como opciones ni contaminan condiciones globales. Se migran los valores internos guardados por versiones previas.
- `shaders.properties` se evalúa según opciones y defines locales antes de decidir programas activos.
- Catálogo de programas que incluye compute con sufijos, Distant Horizons/Flywheel y nombres desconocidos explícitos.
- Fallbacks de categorías y herencia de etapas por dimensión; el adaptador de postproceso utiliza el contrato evaluado.
- Directivas MRT en ramas inactivas no rechazan un shader de color; compute activo no se omite silenciosamente.
- IDs de archivos deterministas; errores para `#else` duplicado/`#elif` posterior y límites de anidamiento.

Esto no cierra todo E00/E01: faltan la revisión semántica de requisitos generados por macros de GLSL, cobertura exhaustiva de ramas de opciones y enlaces completos de ejecución. Un preprocesado correcto no demuestra compilación ShaderC ni render de Complementary.

Reproducir con el ZIP oficial instalado localmente:

```powershell
.\gradlew.bat auditShaderPack '-PshaderPack=.gradle/test-packs/ComplementaryReimagined_r5.9.3.zip' '-PauditOutput=.gradle/test-packs/complementary-audit.json' '-PauditSummary=.gradle/test-packs/complementary-audit-summary.json'
```

El comando guarda los informes y falla si quedan errores de configuración/preprocesado o programas sin clasificar.

## Recursos del cuadro (E02, en curso)

Se verificaron ocho formatos de color, MRT, alternancia de buffers, persistencia, redimensionado y liberación desde el JAR empaquetado. El mismo ensayo dibuja cuatro profundidades con el estado reverse-Z de Minecraft y comprueba por lectura GPU la oclusión y los valores independientes de las capturas anteriores a transparencia y mano en el escenario de prueba. Pasó en OpenGL y Vulkan.

`auditShaderPack` verifica también la asignación ordenada de ubicaciones de salida a `colortex`, incluidas las ubicaciones descartadas. Se ejecutó contra el ZIP de Complementary Reimagined r5.9.3 de la instancia Chill. Resultado de las 71 configuraciones y las tres dimensiones: cero problemas de análisis. SHA-256: `FED6C879E732CF7FC7BC6570EBB1DEF43235AA7E96F62859FBDBDBFA0B484B38`.

Faltan asociar las directivas de formato, tamaño, limpieza y mipmaps con el grafo de renderizado del mundo, y tomar las capturas de profundidad en los pases reales de Minecraft.

## Traducción GLSL (E03, en curso)

El traductor de pases de pantalla puede emitir varias salidas legacy de `gl_FragData` según la directiva del fragmento. El ensayo del JAR usa un shader sintético con `RENDERTARGETS: 12,0`: traduce GLSL, compila en ShaderC, enlaza texturas según el orden indicado y lee los valores HDR y SNORM correctos en GPU. Pasó en OpenGL y Vulkan. También se verificó el shaderpack de regresión legacy en ambos backends durante esta etapa.

Faltan el traductor de vértices del mundo, uniforms y samplers del pack, y la prueba de compilación de sus programas reales.

La prueba del JAR también localiza y preprocesa el vertex shader y fragment shader reales de `gbuffers_terrain` del Overworld del ZIP de Chill. El traductor adapta los atributos legacy, asigna ubicaciones a las entradas y salidas entre etapas, declara los uniforms en std140, conserva los valores iniciales declarados y convierte la profundidad de clip a la convención del backend. Los dos programas compilaron y enlazaron en OpenGL y Vulkan, con las salidas `[0, 6, 4]`, 82 uniforms y 23 samplers declarados.

El shader real se compila sin emitir llamadas de dibujo. Queda proveerle el formato de vértices completo y los datos y texturas activos en el cuadro. La verificación de píxeles sigue usando el shader de diagnóstico.

## Terreno real (E04, en curso)

Un pase de prueba optativo vuelve a dibujar los chunks opacos que Minecraft preparó para el cuadro. Reutiliza las mallas, el atlas de bloques y el shader de vértices de Minecraft. Escribe en dos texturas de color y una de profundidad con un fragmento de diagnóstico. La lectura GPU verifica la cobertura coincidente en los dos adjuntos. Se probó el JAR con un mundo local en OpenGL y Vulkan en la RTX 5060 Ti.

```powershell
.\scripts\Test-PackagedClient.ps1 -Backend opengl -Probe terrain
.\scripts\Test-PackagedClient.ps1 -Backend vulkan -Probe terrain
```

El `gbuffers_terrain` del Overworld en Complementary r5.9.3 declara `colortex0`, `colortex6` y `colortex4` para la configuración base auditada. Falta entregar sus atributos y uniformes reales y ejecutar el GLSL del ZIP sobre las mallas. Este ensayo deja la activación del pack pendiente.

El ensayo optativo con el ZIP real resuelve 32 194 estados de bloque contra el registro y las etiquetas de Minecraft. Distingue las hojas de roble secas (10009) y anegadas (10011). Captura todos los quads que emite el compilador de modelos de bloque para una sección real y guarda sus posiciones relativas a la sección, UV, IDs, normales, tangentes y centros UV en un buffer de vértices. Un pase de diagnóstico escribe esos valores en cuatro adjuntos RGBA32F. La comparación GPU verificó todos los vértices capturados: 904 de 226 caras en OpenGL y 3 120 de 780 caras en Vulkan.

Un segundo pase usa el mismo buffer con los índices de quads de Minecraft y su atlas de bloques. Escribe color, material y normales en el orden lógico 0, 6, 4 y usa un adjunto de profundidad. La lectura GPU verificó cobertura coincidente de los cuatro adjuntos en 2 408 píxeles de OpenGL y 1 780 de Vulkan. Las secciones de ambos ensayos se eligieron durante la compilación del mundo.

El pase indexado ya usa la proyección nativa que Minecraft envía a la GPU y la rotación y posición de su cámara. La prueba elige una sección que intersecta la vista, entrega la transformación por uniform buffer y comprueba cobertura coincidente en 438 píxeles en OpenGL y 1 739 en Vulkan. Se probó la resta de la posición de cámara antes de convertir a float para secciones lejos del origen.

El ensayo de terreno aún usa GLSL de diagnóstico. Quedan el envío normal de todos los chunks, los fluidos y la ejecución de gbuffers_terrain de Complementary con sus recursos reales.

```powershell
.\scripts\Test-PackagedClient.ps1 -Backend opengl -Probe terrain -ComplementaryPack $zip -FreshWorld
.\scripts\Test-PackagedClient.ps1 -Backend vulkan -Probe terrain -ComplementaryPack $zip -FreshWorld
```

El buffer de terreno y el compilador de `gbuffers_terrain` ya comparten el mismo formato de vértices. El buffer conserva el color por esquina y las dos coordenadas de luz con la emisión aplicada por Minecraft. Se verificaron por lectura GPU los ocho atributos del formato, incluidos RGBA y UV2, en 3 104 vértices de OpenGL y 3 120 de Vulkan. El GLSL del pack enlaza con ese formato. Queda completar las matrices, uniforms y texturas reales del programa antes de dibujar con él.

La declaración GLSL y los offsets Java se generan a partir del mismo layout std140. Un pase con esa declaración verificó por lectura GPU la posición de cámara, las matrices de vista, proyección, normales e inversas, y las matrices de coordenadas de texturas. La prueba convierte la proyección legacy de vuelta a la profundidad nativa y compara el resultado con la proyección que Minecraft usó para la sección. Pasó en OpenGL y Vulkan. El shader real de Complementary también volvió a compilar; quedan sus uniforms de escena restantes, samplers y la llamada de dibujo.

El mismo bloque std140 se probó con la resolución, relación de aspecto, rango de cámara, tiempo, día, fase lunar, lluvia y colores de cielo y niebla del mundo. La lectura GPU también comprobó las dimensiones del atlas, una muestra de la textura de piedra y la diferencia entre muestras de luz mínima y máxima del lightmap de Minecraft. El ensayo pasó en OpenGL y Vulkan. El programa que verifica la lectura usa GLSL de diagnóstico. Quedan los recursos específicos del pack, entre ellos sombras y texturas de sus pases, y la prueba de dibujo con el programa real.

## Sombras (E07, en curso)

El JAR tradujo y enlazó los archivos `world0/shadow.vsh` y `world0/shadow.fsh` de Complementary r5.9.3 con las dos salidas [0, 1] en OpenGL y Vulkan. El traductor adapta `ftransform()` y la comparación de profundidad de `shadow2D`. RenderPearl permite leer la profundidad con un sampler regular, así que la comparación legacy se reproduce con cuatro texels y filtrado bilineal. La prueba cuantitativa verificó los resultados iluminado (1), ocluido (0) y filtrado (0.5) desde una textura D32 en ambos backends.

El resultado GPU se obtuvo con profundidades sintéticas. Falta generar las matrices de luz, dibujar los bloques y demás objetos del mundo con `shadow` y entregar sus mapas al terreno de Complementary.

## Hallazgo para E10

Las fuentes de la plataforma fijada exponen únicamente VERTEX y FRAGMENT en `com/mojang/renderpearl/api/pipeline/ShaderType.java`. `GpuDevice`/`CommandEncoder` no exponen dispatch de compute; `UniformType` ofrece sampler, uniform buffer y texel buffer, sin contrato SSBO/storage-image. Por tanto, compute/imágenes/SSBO requieren ampliar el puente, no activar flags falsos. Este hallazgo no se marca como una implementación de E10 ni impide continuar el camino gráfico base.
