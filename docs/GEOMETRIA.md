# Geometría: resultado de la investigación de Forge 66.1.0

## Prueba ejecutada

`gradlew.bat runClient -PprismoraRenderSmoke=true -PprismoraSmokePack=Prismora-Multipass -PprismoraGeometryProbe=true`

El probe registra una pasada con `AddFramePassEvent`, declara lectura/escritura del target principal y se ejecuta dentro del frame graph. Resultado: `PRISMORA_GEOMETRY_PROBE_PASS: target=854x480, entities=0, depth=true, camera=(-533.5, 67.0, 56.5)`. La misma sesión pasa lectura GPU, desactivación, recuperación de errores y bloom. El probe es optativo y no añade una pasada a partidas normales.

## Qué está disponible

- Inserción de pasadas con dependencias de targets.
- Estado extraído de cámara, entidades, cielo y bloques entidad.
- Color y profundidad finales para postproceso.

## Qué no resuelve ese punto de extensión

El evento inserta pasadas después de la construcción de las pasadas de LevelRenderer. No ofrece un contrato público para sustituir todos los pipelines de terreno/entidades, añadir salidas MRT a sus shaders o extraer/volver a dibujar la escena desde otra cámara con culling y visibilidad independientes.

La implementación de terreno usa pipelines específicos y comandos MultiDrawIndirect. Un mapa solar necesita una segunda extracción de visibilidad y render, no basta con reutilizar la profundidad de cámara. Los eventos de entidades no cubren conjuntamente terreno, agua y nubes. FramePassManager advierte además que el orden debe resolverse mediante dependencias de targets.

## Decisión de esta entrega

Se conserva el producto de postproceso funcional según el cierre condicionado de la etapa 4. No se implementan G-buffer por material, mapa solar, sombras suaves ni sustitución de agua/nubes/cielo. No se presenta profundidad de pantalla ni bloom como sombras geométricas.

Para continuar esa parte hace falta diseñar una integración más invasiva: mixins/accesos a extracción, culling y construcción de pipelines, o una extensión de Forge que exponga esos contratos. La ausencia de un evento público no demuestra imposibilidad absoluta; sí impide cerrar esta etapa como una implementación mantenible usando únicamente los puntos probados. Esa ampliación requiere su propia matriz visual, de rendimiento y compatibilidad, además de pruebas de escenas fuera de cámara.
