# Matriz de compatibilidad

| Pack / formato | Estado |
|---|---|
| Prismora Color (propio) | Postproceso y controles toggle/choice probados en cliente real |
| Prismora Bloom (propio) | Perfiles, escalado y eliminación de bloom probados |
| Prismora Diagnostic / Multipass (propios) | Pruebas de GPU y regresión |
| PostFixture (fixture original, GLSL 120) | Composite/final, includes, opciones y errores de origen probados en GPU OpenGL/Vulkan con JAR aislado |
| Complementary Reimagined r5.9.3 | ZIP oficial inspeccionado; 409 opciones, 51 pantallas y 8 perfiles. Menú probado en cliente real. Render no soportado todavía: requiere geometría, MRT, materiales y sombras |
| Cursed Fog 1.0.10 | ZIP oficial inspeccionado: 50 opciones, 6 pantallas y 3 perfiles. Render todavía no soportado |
| Otros packs OptiFine/Iris | Detección y lector disponibles; compatibilidad de render depende de los contratos usados. No hay soporte universal |

Las opciones del formato propio se leen de pack.json. El lector legacy interpreta shaders.properties, opciones #define/const, perfiles, pantallas y traducciones; la interfaz cambia según el pack. El puente de ejecución acepta actualmente postprocesado de color composite/final con uniforms soportados y rechaza contratos pendientes explícitamente.

OpenGL y Vulkan se verifican localmente en Windows con RTX 5060 Ti. CI en Linux cubre compilación y pruebas unitarias, no render gráfico. El fixture legacy pasa con copias de los nueve mods de Chill en OpenGL y Vulkan; inventario y hashes en docs/pruebas/RESULTADOS-PROFUNDIDAD-MATRICES.json. Esto no valida todas las funciones de esos mods. No se ha probado con otros mods que reemplacen el renderer ni 30 FPS en tarjetas de entrada.

Los ZIP/códigos de terceros no se distribuyen. El fixture de pruebas es código original del proyecto.
