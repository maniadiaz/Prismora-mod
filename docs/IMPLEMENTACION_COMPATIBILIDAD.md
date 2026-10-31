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
