# Prismora — Planeación de desarrollo

Mod cliente de shaders para Minecraft Java, con selección de shaderpacks desde el juego.

## Objetivo y alcance

Crear un mod independiente de Iris, Oculus y Embeddium. Esta independencia es una decisión del proyecto; no presupone que esos mods estén disponibles o ausentes para la versión objetivo.

La primera entrega funcional permitirá seleccionar un pack propio y aplicar una pasada de postproceso. La compatibilidad con packs de OptiFine/Iris queda fuera del alcance inicial. Geometría, sombras, agua y cielo requieren una investigación posterior y no se consideran garantizados.

## Base técnica

| Componente | Objetivo | Estado |
|---|---|---|
| Minecraft Java | 26.3 | Versión objetivo |
| Forge | 66.1.0 | Versión recomendada consultada; fijar en el build |
| Java | 25 | Instalado localmente; verificar toolchain del MDK |
| Mod id | `prismora` | Definido |
| Ejecución | Solo cliente | Implementar y verificar con el mecanismo real de Forge |
| Licencia | Pendiente | Resolver antes de publicar |

Usar el MDK oficial de la versión elegida y su Gradle Wrapper. Comprobar la versión de ForgeGradle en el MDK en lugar de asumir 7.x. No copiar firmas de clases ni eventos de versiones anteriores sin verificarlas.

Minecraft 26.3 compila shaders mediante ShaderC en OpenGL y Vulkan. Evitar llamadas GL directas; utilizar las abstracciones reales de Mojang y aislar su integración en una capa de render.

Evaluar primero el efecto vanilla `minecraft:end_of_frame`, disponible mediante recursos, para aplicar el postproceso. Es una alternativa que debe prototiparse: no demuestra por sí sola que selección dinámica, recarga y convivencia con otros efectos funcionen como necesita Prismora. Si no cubre los requisitos, documentar la causa y evaluar un punto de integración propio.

Verificar en las fuentes las abstracciones de GPU, los eventos de pantalla y teclado, el registro de recursos y el manejo de errores. Los nombres `GpuDevice`, `RenderPipeline`, `RenderPass` y `ScreenEvent.Init.Post` son candidatos, no contratos confirmados.

## Arquitectura propuesta

- `client/gui`: selección de packs, opciones y errores visibles.
- `client/config`: selección y opciones persistentes.
- `client/events`: integración de menús y tecla configurable.
- `pack`: descubrimiento, lectura, validación y ciclo de vida de carpetas y ZIP.
- `render`: adaptación a Mojang, compilación, pasadas, recursos GPU y uniforms.
- `assets/prismora/lang`: traducciones `es_mx` y `en_us`.

La estructura exacta, los metadatos del mod y los recursos se ajustarán al MDK. No cargar clases de cliente en el servidor dedicado. La declaración de compatibilidad de red no sustituye esta separación.

## Contrato inicial de shaderpacks

Formato propio, versionado y documentado antes de implementar el cargador. Estructura propuesta:

```text
MiPack/
  pack.json
  shaders/
    post.vsh
    post.fsh
```

Borrador de manifiesto, sujeto a la prueba de render:

```json
{
  "formatVersion": 1,
  "name": "Mi Pack",
  "version": "1.0.0",
  "passes": [
    {
      "name": "post",
      "vertex": "shaders/post.vsh",
      "fragment": "shaders/post.fsh",
      "inputs": { "SceneColor": "sceneColor" },
      "output": "screen"
    }
  ]
}
```

Definir explícitamente:

- Versión de formato soportada y campos obligatorios.
- Recursos de entrada, salida y reglas de encadenamiento; evitar leer y escribir la misma textura durante una pasada.
- Convenciones GLSL, includes, bindings y uniforms según la API verificada.
- Resolución y formato de targets, texturas externas, parámetros, valores predeterminados y rangos cuando esas capacidades se incorporen.
- Política de carpetas y ZIP: manifiesto en la raíz del pack, sin extracción necesaria; indicar claramente cualquier variante admitida.
- Límites de tamaño de manifiesto, shaders, entradas y datos descomprimidos; rechazar rutas absolutas, recorridos `..`, rutas externas por enlaces y entradas ambiguas o duplicadas.
- Errores comprensibles para JSON inválido, versión incompatible, archivos ausentes y compilación fallida.

No prometer bloom completo con una sola pasada. La entrega inicial puede usar corrección de color o tonemapping; bloom con reducción y desenfoque se abordará en múltiples pasadas.

## Etapas y criterios de cierre

### Etapa 0 — Base e investigación

- [x] Incorporar MDK oficial, Wrapper, toolchain y versiones fijadas.
- [x] Configurar identidad, metadatos y separación de cliente.
- [x] Compilar y arrancar el cliente de desarrollo.
- [x] Verificar comportamiento en servidor dedicado: no cargar código de cliente ni provocar fallos por su ausencia.
- [x] Inspeccionar fuentes y registrar firmas y puntos de integración reales.
- [x] Prototipar `end_of_frame` con un efecto mínimo y evaluar selección, recarga y conflicto con otro pack que lo defina.
- [x] Investigar tempranamente la viabilidad de interceptar geometría y sombras; registrar límites sin ampliar esta etapa a una implementación completa.

Cierre: build reproducible, evidencia del arranque y documento de decisiones de render. Si una prueba no puede ejecutarse, registrarla como pendiente; no marcar la etapa completa.

### Etapa 1 — Interfaz y gestión de packs

- [x] Descubrir carpetas y ZIP en `shaderpacks/`.
- [x] Validar el manifiesto y las rutas con límites documentados.
- [x] Crear lista, selección, desactivación, recarga y acceso a la carpeta.
- [x] Integrar botón en ajustes de vídeo y tecla configurable usando la API confirmada.
- [x] Persistir selección y opciones; recuperar una selección cuyo pack desapareció.
- [x] Incluir textos en español e inglés y errores visibles.

Cierre: selección persistente tras reiniciar; packs válidos, inválidos, ausentes y ZIP comprobados. La interfaz debe indicar que seleccionar no implica render activo hasta la etapa 2.

### Etapa 2 — Postproceso funcional

- [x] Cargar shaders propios e integrarlos mediante la vía probada en la etapa 0.
- [x] Aplicar una pasada de corrección de color o tonemapping al frame.
- [x] Incluir un pack de ejemplo mínimo y documentado.
- [x] Mostrar errores de compilación y conservar el juego utilizable ante un pack fallido.
- [x] Liberar recursos al cambiar de pack, redimensionar o cerrar.
- [x] Validar activación, desactivación, recarga y convivencia con efectos vanilla.

Cierre: efecto visible y reversible dentro del mundo, sin reiniciar el juego; registrar backend, hardware y resultado. Verificar OpenGL y Vulkan antes de afirmar compatibilidad con ambos.

### Etapa 3 — Múltiples pasadas

- [x] Implementar targets propios y encadenamiento declarado en el manifiesto.
- [x] Validar referencias, orden, ciclos y accesos incompatibles a texturas.
- [x] Definir uniforms de resolución y tiempo; incorporar hora del mundo, cámara y sol cuando la API permita obtenerlos correctamente.
- [x] Manejar cambios de tamaño, entrada y salida del mundo y liberación de GPU.
- [x] Añadir un ejemplo de varias pasadas, como bloom con desenfoque.

Cierre: cadena validada, resultado visual correcto y medición de coste de GPU/memoria frente a vanilla.

### Etapa 4 — Geometría y sombras, condicionada a viabilidad

Resultado: investigación y prototipo ejecutados; se conserva postproceso. G-buffer y sombras no implementados: véase `docs/GEOMETRIA.md`.

- [x] Revisar el prototipo y las restricciones detectadas en la etapa 0.
- [x] Priorizar eventos o extensiones estables; justificar y aislar cualquier mixin necesario.
- [ ] Prototipar un G-buffer limitado antes de abarcar terreno, entidades y agua.
- [ ] Implementar mapa de sombras y luego filtrado de sombras si la integración es viable.
- [ ] Incorporar agua, nubes y cielo como incrementos separados.

Cierre: alcance demostrado con pruebas visuales y rendimiento medido. Si la API impide una solución mantenible, documentar la limitación y conservar el producto de postproceso funcional.

### Etapa 5 — Pulido y distribución

- [x] Completar recarga en caliente y opciones por pack según el formato definido.
- [x] Ejecutar matriz OpenGL/Vulkan, cambios de resolución, reinicios y errores de packs.
- [x] Documentar instalación, creación de packs, requisitos e incompatibilidades comprobadas.
- [ ] Confirmar licencia de publicación con el titular. Los avisos originales del MDK están conservados y el código propio mantiene All Rights Reserved.
- [x] Preparar artefactos, changelog y verificar el JAR en instalación aislada.
- [ ] Publicar en Modrinth/CurseForge: requiere cuentas/proyectos y datos de publicación del titular.

Cierre: artefacto instalable probado fuera del entorno de desarrollo y documentación coherente con las capacidades realmente verificadas. La publicación en plataformas se gestiona como entrega separada de los pushes a Git.

## Git y seguimiento por etapa

Repositorio: https://github.com/maniadiaz/Prismora-mod.git

1. Implementar un incremento concreto y revisar el diff.
2. Ejecutar build y comprobaciones pertinentes; registrar resultados y pendientes.
3. Actualizar esta planeación y el changelog sin marcar capacidades no probadas.
4. Crear un commit descriptivo y hacer push al cerrar cada etapa. Los incrementos independientes pueden tener commits intermedios.
5. Registrar hash y estado del push. No reescribir historial ni forzar pushes como procedimiento normal.

No versionar credenciales, mundos, logs, cachés, directorios de build ni packs de terceros sin permiso.

## Riesgos principales

| Riesgo | Tratamiento |
|---|---|
| Cambios de API | Versiones fijadas, firmas verificadas y adaptación de render aislada |
| Conflicto de `end_of_frame` | Prototipo de prioridad y convivencia; documentar la política elegida |
| Packs inválidos o ZIP abusivos | Validación de rutas, tamaños y estructura antes de cargar |
| Error de compilación o recursos GPU | Recuperación visible, desactivación segura y liberación explícita |
| Geometría demasiado invasiva | Prueba temprana y alcance condicionado a su resultado |
| Rendimiento | Medir por efecto y backend, con referencia vanilla |

## Referencias y estado inicial

- Forge 26.3: https://files.minecraftforge.net/net/minecraftforge/forge/index_26.3.html
- Cambios de render y postproceso de Minecraft 26.3: https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3
- MDK y fuentes de la versión fijada: fuente de verdad para firmas y configuración del proyecto.

Al preparar esta planeación se encontró el repositorio vacío y Java 25 instalado. Ninguna etapa de implementación se considera terminada. La disponibilidad de otros mods y la compatibilidad práctica de Prismora siguen sin verificarse.

## Avance de implementación

Base y selección subidas a Git. Postproceso validado en cliente real con OpenGL y Vulkan, lectura de píxeles, desactivación y recuperación de GLSL inválido. Véase `docs/VALIDACION.md`. Etapas restantes en curso; no implica publicación en plataformas.


### Estado de esta entrega

Etapas 0–3 implementadas y verificadas. Etapa 4 cerrada como investigación con limitación documentada; no se han implementado G-buffer ni sombras solares. Etapa 5 incluye opciones, recarga automática, matriz de render, pruebas del JAR y distribución local; publicación externa y decisión final de licencia pendientes. Véanse README.md, docs/VALIDACION.md y docs/PUBLICACION.md.


## Ampliación 0.2.0 — Rendimiento

Perfiles Entrada/Baja/Media/Alta, escala de postproceso, bloom opcional, sliders/toggles/choices, política adaptativa con fallback, presupuestos nominales de targets y benchmark reproducible. Plan y estado: docs/PLAN_RENDIMIENTO.md. Resultados físicos: docs/RENDIMIENTO.md. El nuevo plan OptiFine/Iris propuesto está guardado y ampliado en docs/PLAN_COMPATIBILIDAD_PROPUESTO.md; no se declara implementado por estos ajustes de rendimiento.

## Continuación del plan de compatibilidad

El seguimiento vigente de los siete puntos solicitados está en docs/IMPLEMENTACION_COMPATIBILIDAD.md. El cierre como investigación de la etapa 4 del plan inicial no cuenta como implementación de geometría/sombras del nuevo alcance. Se han añadido lectura y menús legacy, ejecución composite/final, profundidad flotante y captura de matrices; el render de packs con gbuffers, materiales, MRT y sombras sigue pendiente.
