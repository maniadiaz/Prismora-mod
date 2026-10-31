# Ampliación de rendimiento

Objetivo: controles comparables a otros cargadores de shaders y perfiles Entrada/Baja/Media/Alta, con objetivo adaptativo de 30 FPS en Entrada. No se garantiza ese resultado sin probar hardware real de esas gamas. Prismora no sustituye el renderer de chunks: reducir coste de shaders no resuelve un cuello de botella vanilla/CPU.

1. Configuración: presets, preferencias persistentes y migración desde 0.1.0. Pruebas de límites y persistencia.
2. Render: resolución de postproceso, targets escalados, filtrado bilineal, eliminación de pasadas opcionales y recarga al redimensionar. UI de rendimiento y controles del pack. Pruebas del grafo y píxeles reales.
3. Adaptación: medición de frames válidos, ventanas de muestreo, calentamiento, histéresis y cooldown. Reducir escala y efectos; fallback opcional al render sin shaders cuando no se cumple el presupuesto. Mostrar la decisión y permitir reactivar. No interpretar un límite de FPS/VSync como una GPU lenta sin contrastar tiempo GPU.
4. Validación y distribución: JUnit, OpenGL y Vulkan, JAR aislado, comparación controlada de perfiles y registro de hardware/resolución. Los resultados de RTX 5060 Ti se etiquetan como tales; otras gamas quedan sin verificar hasta obtener mediciones reales.

Cada etapa se valida y sube con commit independiente. El plan adjunto de compatibilidad se conserva en PLAN_COMPATIBILIDAD_PROPUESTO.md. Sus fases de traducción GLSL, G-buffers y sombras son una ampliación separada; los perfiles no hacen compatibles packs OptiFine/Iris por sí solos. Las referencias del adjunto deben comprobarse antes de tratarlas como contratos implementados.
