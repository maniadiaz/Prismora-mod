# Calidad y rendimiento de Prismora 0.2.0

## Qué cambia cada control

Los perfiles ajustan el coste del postproceso; no sustituyen el renderer de chunks ni cambian la resolución de la ventana o las distancias de render del jugador. Escala 50% significa la mitad de ancho y alto de cada target de shader: un cuarto de píxeles frente a escala 100%. La imagen se reescala con filtrado bilineal y puede perder detalle.

Bloom solo se elimina si el pack declara sus pasadas opcionales y un bypass de color válido. En el ejemplo Bloom, Entrada elimina las cuatro pasadas y la copia final, con cero targets privados. En un pack con efectos obligatorios, Entrada sigue ejecutando esos efectos a menor resolución.

Presupuestos nominales de targets: Entrada 64 MiB, Baja 96 MiB, Media/Personalizado 192 MiB, Alta 384 MiB. Se calculan con 8 bytes/píxel para color y profundidad, no como lectura de VRAM libre; no incluyen el mundo, texturas del juego, caches ni overhead del driver.

## Objetivo de 30 FPS

El presupuesto de 30 FPS es 33.33 ms por frame. El modo adaptativo observa tiempo de frame y GPU con ventanas acotadas, calentamiento, presión sostenida y cooldown de 30 s. No se aplica durante menús, carga, AFK o ventana inactiva. No rebaja calidad ante un límite aislado de CPU/VSync. Las reducciones son por etapas y recargan recursos; no son escalado dinámico continuo sin pausas.

Si el mínimo sigue excediendo el presupuesto y el fallback está permitido, se pausa el shader, se conserva la selección y se informa en la pantalla de Prismora. Recargar o aplicar un perfil lo reactiva. Las reducciones automáticas no sobrescriben el preset guardado; al reiniciar se usa la preferencia del jugador.

**No hay garantía universal de 30 FPS.** Si el mundo vanilla ya está por debajo, reducir el postproceso puede no resolverlo. Cambiar de perfil en una RTX 5060 Ti no emula una GPU integrada ni una tarjeta de entrada.

## Metodología

`scripts/Test-PackagedClient.ps1 -Backend opengl -Probe performance` (o vulkan) compila/tests, instala el JAR en una carpeta aislada, carga el mismo mundo de prueba y ejecuta los controles y el benchmark.

Se compara vanilla sin pack interno con Entrada/Baja/Media/Alta del ejemplo Bloom, a 1280x720, en la misma posición de cámara. Se omiten recargas y menús; hay 90 consultas de calentamiento y 180 muestras por perfil. GPU: TimerQuery de Mojang alrededor de GameRenderer completo. Frame: intervalo de pared; incluye el límite de FPS/VSync que exista. Los intervalos fuera de 0.1..250 ms se excluyen y se cuentan; los percentiles no representan las pausas de carga.

El probe evita la reducción por inactividad llamando al contador de entrada del cliente durante la medición, sin mover al jugador ni generar carga gráfica ficticia. Registra GPU, backend, driver, resolución, límite de FPS, foco y outliers en `prismora-performance-report.json`. No modifica esa política en partidas normales.

El fallback se prueba aparte con presión sintética de la política y desactivación real del shader. Esa prueba no se mezcla con el benchmark ni demuestra comportamiento térmico o rendimiento de una GPU lenta real.

## Matriz física

| Entorno | Estado |
|---|---|
| Windows, RTX 5060 Ti, OpenGL | Cliente y JAR probado |
| Windows, RTX 5060 Ti, Vulkan | Cliente y JAR probado |
| GPU integrada / entrada | Sin hardware físico probado |
| Otras GPU de gama baja, media o alta | Sin mediciones físicas |
| Linux, render gráfico | No probado; CI solo cubre build y unit tests |

## Alcance

Los informes JSON de `docs/pruebas/rendimiento/` distinguen la medición inicial limitada por AFK de las mediciones finales. No se atribuye una mejora de FPS cuando el límite del juego mantiene iguales los FPS entre perfiles. La reducción de targets se demuestra por dimensiones reales y número de pasadas; los tiempos totales pueden estar dominados por el mundo vanilla y variar por clocks/caches.

La compatibilidad OptiFine/Iris, GLSL legacy, G-buffers y sombras del plan propuesto no se implementa con estos controles. Véase compat-matrix.md.

## Resultados medidos en RTX 5060 Ti

Límite del juego registrado: 120 FPS. Ninguna fila demuestra un mínimo universal en otras escenas o GPU.

| Backend | Perfil | FPS desde mediana | Frame p95 (ms) | GPU p50 (ms) | Píxeles privados |
|---|---|---:|---:|---:|---:|
| OpenGL | vanilla | 120.0 | 10.00 | 0.248 | 0 |
| OpenGL | ENTRY | 120.0 | 10.00 | 0.305 | 0 |
| OpenGL | LOW | 120.0 | 9.93 | 0.541 | 403,200 |
| OpenGL | MEDIUM | 120.0 | 9.82 | 0.534 | 907,200 |
| OpenGL | HIGH | 119.9 | 9.98 | 0.522 | 1,612,800 |
| Vulkan | vanilla | 120.0 | 9.77 | 0.085 | 0 |
| Vulkan | ENTRY | 120.0 | 9.71 | 0.099 | 0 |
| Vulkan | LOW | 120.0 | 9.48 | 0.148 | 403,200 |
| Vulkan | MEDIUM | 120.0 | 9.41 | 0.153 | 907,200 |
| Vulkan | HIGH | 120.1 | 9.43 | 0.160 | 1,612,800 |


Baja utiliza 403,200 píxeles privados frente a 1,612,800 de Alta en esta resolución (75% menos), y Entrada usa 0 en el pack Bloom. Es una diferencia de targets; no una medición de toda la VRAM del juego ni una promesa proporcional de FPS. Los tiempos GPU son del render completo y las diferencias pequeñas pueden ser ruido.
