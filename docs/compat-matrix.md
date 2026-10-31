# Matriz de compatibilidad

| Pack / formato | Estado |
|---|---|
| Prismora Color (propio) | Postproceso y controles toggle/choice probados en cliente real |
| Prismora Bloom (propio) | Perfiles, escalado y eliminación de bloom probados |
| Prismora Diagnostic / Multipass (propios) | Pruebas de GPU y regresión |
| OptiFine/Iris de terceros | No soportado por el cargador actual; no se ha implementado el traductor ni G-buffers del plan propuesto |
| Packs populares de terceros | Sin pruebas de compatibilidad; no se distribuyen ni se presentan como compatibles |

La mejora de rendimiento no cambia el formato aceptado. Las opciones del pack se leen de pack.json; shaders.properties, opciones #define, atributos y sombras del formato OptiFine/Iris siguen siendo trabajo pendiente.

OpenGL y Vulkan se verifican localmente en Windows con RTX 5060 Ti. CI en Linux cubre compilación y pruebas unitarias, no render gráfico. No se ha probado coexistencia con otros mods que reemplacen el renderer.
