# Reportar un problema

Incluye versión de Prismora, Minecraft, Forge y Java; sistema operativo, GPU/driver y backend efectivo (OpenGL o Vulkan).

Para rendimiento: pack y versión, resolución, perfil/escala/bloom, objetivo y estado adaptativo; FPS vanilla frente a shaders en la misma escena. Adjunta el informe JSON del probe si lo ejecutaste, y explica si VSync o un límite de FPS estaban activos.

Para errores: pasos para reproducir, detalle de error de Prismora, log de la sesión y un ejemplo mínimo del pack cuando puedas compartirlo. Indica si ocurrió al activar, aplicar opciones, recargar, cambiar de dimensión o redimensionar.

Comando de benchmark del JAR desde el repositorio: `scripts/Test-PackagedClient.ps1 -Backend opengl -Probe performance` (o vulkan). Crea una instalación de prueba en .gradle/packaged-smoke/run, ejecuta el mundo de prueba y exporta prismora-performance-report.json. No lo confundas con una prueba de otra GPU al seleccionar un preset diferente.
