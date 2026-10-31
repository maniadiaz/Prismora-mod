# Formato de shaderpack 1

Coloca una carpeta o ZIP directamente en `shaderpacks/`. `pack.json` debe estar en su raíz; no se admiten carpetas contenedoras adicionales en ZIP. Los ejemplos del repositorio son packs completos.

## Manifiesto

`formatVersion: 1`, `name` (1..100 caracteres), `version` informativa, `passes` (1..16).
Cada pasada define `name` único en minúsculas, `vertex`, `fragment`, `inputs` y `output`.
Los shaders usan rutas relativas `shaders/*.vsh` y `shaders/*.fsh`, también en subdirectorios y minúsculas.

- `inputs`: mapa de nombre de sampler a recurso. El GLSL declara `uniform sampler2D NombreSampler`; Prismora añade el sufijo `Sampler` al nombre del manifiesto.
- `sceneColor`: color final de Minecraft antes del efecto.
- `sceneDepth`: profundidad disponible en el target principal, dependiente del renderer vanilla; no es un G-buffer separado por material.
- Un nombre de target anterior permite leer el resultado de otra pasada. No se permiten referencias futuras, ciclos ni leer y escribir el mismo target.
- `output`: nombre de target privado o `screen`. Solo la última pasada escribe `screen`. Prismora usa un target privado y una copia final para evitar feedback.
- `targets`: objeto opcional de descriptores por nombre. `width` y `height` opcionales de 1..4096; sin ellos cada dimensión sigue la ventana. Los targets se crean con el formato de vanilla (RGBA8); no se ofrece HDR ni persistencia en formato 1.
- `uniforms`: bloques estáticos con el formato de `PostChainConfig` de Minecraft 26.3. Cada bloque es una lista ordenada de `{ "type": "float", "value": 0.35 }`, u otro tipo que acepte ese codec. El orden debe coincidir con el bloque std140 del shader.

## Datos dinámicos

Con `dynamicUniforms: true`, cada pasada recibe el sampler reservado `PrismoraData`. Incluye `#include "prismora:uniforms.glsl"` después de `#version` y las extensiones GLSL.

Funciones: `prismoraResolution()` (píxeles), `prismoraTime()` (segundos desde la inicialización del proveedor), `prismoraDayTicks()` (reloj overworld módulo 24000), `prismoraCamera()` (posición mundial float), `prismoraSunAngle()` (radianes del estado de cielo vanilla), `prismoraSunDirection()` (dirección mundial derivada de las rotaciones de cielo vanilla) y `prismoraHasWorld()`.

El reloj overworld y el cielo de una dimensión no son equivalentes: usa el ángulo solar del estado de render para el sol. En dimensiones sin cielo no se promete un sol visible. Para posiciones muy lejanas, la representación float pierde precisión; vanilla también ofrece `CameraBlockPos` y `CameraOffset` en `minecraft:globals.glsl`.

Internamente se suben 320 bytes en una textura RGBA8_UNORM de 80x1 y se reconstruyen floats por bits, sin depender de uniforms mutables de `PostPass`. No se usan llamadas OpenGL directas. La textura la administra TextureManager y se libera al desactivar.

Incluye propios: `shaders/include/foo.glsl` se publica como `prismora:foo.glsl`. El nombre `uniforms.glsl` del proveedor está reservado; evita redefinirlo.

## GLSL y límites

Los ejemplos usan GLSL 330, locations explícitos y `GL_ARB_separate_shader_objects`. ShaderC procesa tanto OpenGL como Vulkan. No copiar shaders antiguos con `#moj_import`; comprobar includes y bindings en 26.3.

Límites: manifiesto 64 KiB, archivo 1 MiB, contenido total 16 MiB, 256 archivos, 256 entradas ZIP, 512 entradas de recorrido de carpetas y 256 packs en el listado. No se extraen ZIP. Se rechazan rutas absolutas, `..`, barras inversas, enlaces simbólicos, rutas reales externas y nombres de archivos duplicados sin distinguir mayúsculas.

## Convivencia

Mientras está activo, el pack interno de Prismora tiene prioridad sobre otros packs que definan `minecraft:end_of_frame`. No combina esos efectos automáticamente. Al desactivar se retira ese pack; otros efectos de jugador/espectador siguen gestionados por vanilla.

Los errores de carga o compilación se muestran en la pantalla de Prismora; los detalles se consultan en el botón de errores y `logs/latest.log`. Ante compilación fallida se desactiva el shader. Los packs OptiFine/Iris no se convierten automáticamente.

## Opciones por pack

`options` es un objeto de hasta 16 parámetros float. Cada clave usa minúsculas y guiones bajos (máximo 40 caracteres). Ejemplo:

```json
"options": {
  "bloom_strength": {
    "label": "Bloom strength",
    "min": 0.0, "max": 1.0, "step": 0.05, "default": 0.35,
    "pass": "composite", "block": "BloomOptions", "index": 0
  }
}
```

El destino debe ser un uniform float existente en el bloque indicado. La pantalla Opciones permite editar valores con el widget declarado y aplicar por lote; se guarda por nombre de archivo/carpeta de pack y se recompila. Los valores guardados fuera de rango se acotan al aplicar. La etiqueta pertenece al pack; el resto de la interfaz tiene traducciones español/inglés.

La recarga automática observa el directorio raíz y los subdirectorios del pack seleccionado, agrupa eventos durante 600 ms y reemplaza la instantánea de recursos completa. No deja abiertos los ZIP.


## Extensiones de calidad desde Prismora 0.2

Una pasada puede declarar `"feature":"bloom", "bypass":"SceneColor"`. `bypass` nombra un sampler presente en `inputs`: cuando bloom está apagado, la salida se convierte en alias de esa entrada y la pasada no se compila ni ejecuta. El bypass debe ser color, nunca `sceneDepth`. Las referencias posteriores se resuelven a través del alias. Un pack compuesto solo por bloom puede resultar en una cadena vacía, sin targets privados.

`scale` opcional de pasada: 0.25..1 relativo al ajuste global. El bloom de ejemplo usa 0.5 en extracción/desenfoque y 1 en composición. Cada dimensión final es `ceil(dimensión de ventana o declarada × escala global × escala de pasada)`, con mínimo 1 y máximo 8192. Al redimensionar se agrupan eventos durante 600 ms y se recarga una vez estable. Los samplers de color y la copia final usan filtrado bilineal; la profundidad conserva lectura sin interpolación de color.

Las opciones pueden tener `widget: "slider"` (predeterminado), `"toggle"` o `"choice"`. Toggle exige rango 0..1, step 1 y default 0/1. Choice añade `values` (2..16 números únicos del rango, incluyendo default) y `labels` opcional de igual longitud. Los valores se normalizan antes de compilar. `Prismora-Color` demuestra toggle de tinte y choice de exposición. La pantalla conserva un borrador y solo guarda/recompila al pulsar Aplicar.

Los perfiles acotan el tamaño nominal total de los targets privados (color más profundidad, 8 bytes/píxel): Entrada 64 MiB, Baja 96 MiB, Media/Personalizado 192 MiB, Alta 384 MiB. Si se supera, la carga falla antes de crear buffers de GPU y se muestra el error; no se afirma que ese presupuesto equivalga a VRAM libre.

Las posiciones 0–15 conservan el contrato anterior. Las posiciones 16–79 transportan proyección de mundo, inversa de proyección, vista e inversa de vista. `prismoraMatrix(slot)` reconstruye una matriz column-major a partir de 16 floats; los inicios son 16, 32, 48 y 64. La proyección se captura después del bob/daño/portal y convierte reverse-Z al clip legacy [-1,1] en ambos backends.
