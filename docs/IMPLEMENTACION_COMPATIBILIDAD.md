# Implementación de compatibilidad — etapas 1 a 7

## Etapa 1: lector

Implementado lector compartido de archivos con snapshot acotado: 1 MiB por archivo, 64 MiB total y 8192 entradas; rechaza enlaces/rutas externas y ZIP ambiguos. Detecta formato propio o estructura OptiFine/Iris, carpetas y ZIP con una única carpeta contenedora.

Lee programas por dimensión, opciones #define y constantes con rangos, shaders.properties, sliders, pantallas, perfiles con herencia, traducciones .lang y block.properties. La lectura no implica que un pack se pueda renderizar.

Validación: 43 pruebas unitarias sin fallos. Inspección local del pack oficial Complementary Reimagined r5.9.3: 436 archivos, 409 opciones, 51 pantallas, 8 perfiles y variantes world-1/world0/world1. Detecta correctamente que requiere geometría. El ZIP y su código no se versionan ni distribuyen.

Referencia de comportamiento: documentación oficial de OptiFine:
- https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/shaders.txt
- https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/shaders.properties

Comando de inspección: `gradlew.bat inspectShaderPack -PshaderPack=<ruta>`.

## Etapas siguientes

2. Menús específicos, perfiles, traducciones, reset y persistencia por pack.
3. Preprocesado, traducción GLSL y errores con origen.
4. Integración de render/geometría y targets.
5. Sombras.
6. Matriz de compatibilidad, robustez y hardware.
7. Artefactos y publicación (requiere cuenta/proyecto y licencia del titular).

Ninguna etapa se marca como completa solo porque sus archivos existan. Las funciones no probadas se mantienen identificadas.


## Etapa 2: menús específicos

Implementados submenús y columnas definidos por el pack, perfiles heredados, traducciones y comentarios, sliders/interruptores/listas, borrador compartido entre categorías, reset e importación/exportación de presets JSON vinculados al pack. Las preferencias mantienen nombres y valores del pack (incluidas mayúsculas y valores no numéricos), separadas de las opciones nativas y de otros packs.

Prueba real de cliente con Complementary r5.9.3: se abrió su raíz, se navegó a Performance Settings y se renderizaron sus controles. Capturas inspeccionables en docs/screenshots/legacy. Esta prueba verifica el menú, no el render de Complementary. El puente de render sigue rechazándolo explícitamente en este punto por requerir geometría.

Los presets se exportan/importan desde config/prismora-presets/shader-<hash-del-pack>.json. Importar un preset de otro pack o valores fuera de su lista produce error y conserva el borrador anterior.

## Etapa 3: compilación legacy de postprocesado (alcance parcial)

Implementados resolución segura de includes, condiciones y macros, sustitución de opciones, versión GLSL original y referencias de archivo/línea. Se traduce el contrato de pantalla completa de composite/final a ShaderC/Mojang: varyings, posiciones, sampler2D, salidas y uniforms dinámicos soportados. Los contratos desconocidos, geometría/compute, MRT y samplers de sombras se rechazan con error explícito. No es todavía un traductor universal de shaderpacks.

El fixture original del proyecto en src/test/resources/legacy/PostFixture verifica dos pasadas GLSL 120, include, toggle y exposición. Se comprueban píxeles en GPU y recompilación tras cambiar opciones. La prueba negativa modifica temporalmente un include y exige el archivo original y su línea 2 antes de restaurarlo y cerrar el cliente.

Reproducir con JAR instalado en un directorio aislado:

```powershell
.\scripts\Test-PackagedClient.ps1 -Backend opengl -Probe legacy
.\scripts\Test-PackagedClient.ps1 -Backend vulkan -Probe legacy
```

Validación local: 60 pruebas unitarias; pruebas de desarrollo OpenGL/Vulkan y JAR aislado en ambos backends, Windows 11 / RTX 5060 Ti. No demuestra render de Complementary, sombras ni compatibilidad en otras GPU.

## Estado real restante

- Etapas 1 y 2: lector y menú probados con Complementary r5.9.3.
- Etapa 3: subset de postprocesado ejecutado; faltan contratos GLSL de geometría, materiales, matrices, MRT y sombras.
- Etapas 4 y 5: integración real del mundo y mapa solar/lunar pendientes; el probe anterior no los implementa.
- Etapa 6: JUnit y GPU local disponibles; faltan otras GPU, Linux con GPU y combinaciones de mods.
- Etapa 7: preparación existente; el titular indicó que aún creará los proyectos Modrinth/CurseForge. No se ha publicado allí; se mantiene la licencia existente.
