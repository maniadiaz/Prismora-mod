# Ejecución del plan de Complementary

## Contrato e inventario ejecutable (E00/E01, en curso)

Implementado `auditShaderPack`: evalúa condiciones e includes del ZIP real, perfiles, variantes estructurales y escenarios de capacidades. Registra programas, uniforms, atributos, formatos, adjuntos, bloques y operaciones requeridas. Los escenarios `requested_features*` son análisis de requisitos; no anuncian capacidades de Prismora ni prueban un renderer instalado.

Resultado: 71 configuraciones analizadas (valor por defecto y ocho perfiles en cinco escenarios, más 26 variantes estructurales), tres dimensiones, cero errores de preprocesado o roles de programa desconocidos en esos casos. `docs/pruebas/complementary/requirements-summary.json` conserva metadatos y símbolos; el informe privado completo queda en `.gradle/test-packs` y no se distribuyen fuentes del pack.

Correcciones derivadas de ejecutar el pack real:

- Perfiles con `OPCION=valor`, además de `OPCION:valor` e herencia.
- El valor por defecto se conserva aunque falte en el rango comentado: esto recupera, entre otras, `COLORED_LIGHTING=0`.
- Los defines de contexto de wrappers (dimensión/etapa/programa) no aparecen como opciones ni contaminan condiciones globales. Se migran los valores internos guardados por versiones previas.
- `shaders.properties` se evalúa según opciones y defines locales antes de decidir programas activos.
- Catálogo de programas que incluye compute con sufijos, Distant Horizons/Flywheel y nombres desconocidos explícitos.
- Fallbacks de categorías y herencia de etapas por dimensión; el adaptador de postproceso utiliza el contrato evaluado.
- Directivas MRT en ramas inactivas no rechazan un shader de color; compute activo no se omite silenciosamente.
- IDs de archivos deterministas; errores para `#else` duplicado/`#elif` posterior y límites de anidamiento.

Esto no cierra todo E00/E01: faltan la revisión semántica de requisitos generados por macros de GLSL, cobertura exhaustiva de ramas de opciones y enlaces completos de ejecución. Un preprocesado correcto no demuestra compilación ShaderC ni render de Complementary.

Reproducir con el ZIP oficial instalado localmente:

```powershell
.\gradlew.bat auditShaderPack '-PshaderPack=.gradle/test-packs/ComplementaryReimagined_r5.9.3.zip' '-PauditOutput=.gradle/test-packs/complementary-audit.json' '-PauditSummary=.gradle/test-packs/complementary-audit-summary.json'
```

El comando guarda los informes y falla si quedan errores de configuración/preprocesado o programas sin clasificar.

## Hallazgo para E10

Las fuentes de la plataforma fijada exponen únicamente VERTEX y FRAGMENT en `com/mojang/renderpearl/api/pipeline/ShaderType.java`. `GpuDevice`/`CommandEncoder` no exponen dispatch de compute; `UniformType` ofrece sampler, uniform buffer y texel buffer, sin contrato SSBO/storage-image. Por tanto, compute/imágenes/SSBO requieren ampliar el puente, no activar flags falsos. Este hallazgo no se marca como una implementación de E10 ni impide continuar el camino gráfico base.
