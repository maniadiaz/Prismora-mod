# Changelog

## 0.2.0 — Perfiles de calidad y rendimiento

- Presets Entrada, Baja, Media y Alta; escala de postproceso y ajustes personalizados persistentes.
- Poda de pasadas opcionales de bloom con bypass validado; buffers reducidos y escalado bilineal.
- Sliders, toggles y elecciones por pack; aplicar cambios por lote.
- Adaptación con tiempos reales de GPU/frame, calentamiento, histéresis, cooldown y fallback opcional a vanilla sin perder selección.
- Benchmark optativo de vanilla y perfiles, resolución/hardware/backend explícitos y exportación JSON.
- Pruebas deterministas de presión/cooldown/CPU, errores de grafo, migración y controles; pruebas del JAR en ambos backends.
- La compatibilidad OptiFine/Iris, G-buffers y sombras del nuevo plan continúan pendientes. Esta versión no anuncia soporte para ellos ni garantiza 30 FPS en hardware no probado.

## 0.1.0 — Primera versión de postproceso

- Base Forge 26.3, Java 25 y mod solo cliente.
- Selección de carpetas y ZIP desde Ajustes de vídeo y tecla F6 configurable.
- Carga acotada, validación de rutas, referencias y manifiestos, y errores visibles.
- Compilación ShaderC y postproceso con recursos administrados por Mojang.
- Varias pasadas, targets privados, resolución automática y dimensiones fijas opcionales.
- Datos dinámicos: cámara, reloj, ángulo solar, resolución y tiempo.
- Ejemplos de corrección de color, bloom y diagnóstico de GPU.
- Opciones por pack persistentes, recarga manual y automática de archivos.
- Pruebas unitarias, lectura GPU en OpenGL/Vulkan, recuperación de errores y prueba del JAR aislado.

Geometría avanzada y sombras solares quedan fuera de esta versión; la investigación y el probe de Forge están documentados.
