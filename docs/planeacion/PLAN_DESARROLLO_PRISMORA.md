# Plan de desarrollo de Prismora

## Objetivo

Entregar un JAR de Prismora para Minecraft Java 26.3 y Forge 66.1.0 que permita jugar con Complementary Reimagined r5.9.3 en OpenGL y Vulkan. Mantener el selector de packs, aprovechar los programas que declara cada pack y reducir el costo de renderizar el terreno. La meta para el perfil de entrada es 30 FPS sostenidos en hardware representativo, con la resolución y la escena de prueba registradas.

## Referencias

- Estudiar en Oculus cómo se resuelven los programas de un shaderpack y se coordinan los pases, las texturas de color y las copias de profundidad. Usar como referencia su objetivo de compatibilidad con los shaderpacks de ShadersMod y OptiFine. Registrar la rama, el commit y el archivo consultado cuando sus decisiones orienten una implementación. [Código de Oculus](https://github.com/Asek3/Oculus)
- Estudiar en Embeddium el renderizado por secciones, el ciclo de compilación y subida de mallas, y la ordenación de transparencias. Evaluar esas ideas con el renderizador y los recursos GPU de la versión objetivo de Prismora. [Código de Embeddium](https://github.com/FiniteReality/embeddium)

## Punto de partida

El JAR activa y desactiva Complementary desde el menú, permite cambiar a otro pack y restaura la selección y la saturación tras reiniciar. Las pruebas verificaron agua, vidrio teñido, entidades, objetos, mano y partículas en el Overworld, así como el recorrido corto por las tres dimensiones y la reapertura del mundo, en ambos backends. El recorrido corto usó 960 × 540 y tres chunks. Quedan pendientes los recorridos de 30 minutos, la inspección visual completa de la atmósfera y la prueba de rendimiento en hardware de entrada. [Registro de ejecución](EJECUCION_COMPLEMENTARY.md)

## Etapa 1. Establecer una base de estabilidad y rendimiento

- Ejecutar el JAR actual durante 30 minutos efectivos por backend. Recorrer Overworld, Nether y End, aplicar un ajuste desde el menú, salir y volver a abrir el mismo mundo. Registrar el commit, la versión del pack, el hardware, la resolución, la distancia y los logs.
- Añadir contadores de tiempo de frame, reconstrucciones de malla, secciones visibles, bytes subidos a GPU y tiempo de ordenación de transparencias. Medir una cámara fija, un recorrido de chunks y la edición de un bloque.
- Ejecutar la misma escena con Prismora desactivado y activado. Conservar datos y capturas que permitan repetir la comparación.

**Se cierra cuando** ambos recorridos completan los 30 minutos, el mundo vuelve a renderizar con el ajuste aplicado y hay una medición reproducible para cada escenario. El recorrido corto sirve para comprobar la ruta antes de la prueba larga.

## Etapa 2. Completar el contrato de shaderpacks con Oculus como referencia

- Comparar la resolución de variantes, los programas opcionales, el orden de pases y los recursos que declara Complementary en cada dimensión. Reemplazar las suposiciones sobre una lista fija de pases donde el contrato del pack ya da la respuesta.
- Verificar los formatos, los mipmaps y el historial, y suministrar la profundidad apropiada antes y después de dibujar transparencias y mano. Revisar el sol y la luna texturizados, la precipitación, las nubes y la niebla.
- Probar el perfil base y un perfil de bajo costo de Complementary. Usar Cursed Fog como segundo pack para contrastar el menú y el contrato de carga cuando se disponga del ZIP.

**Se cierra cuando** Complementary renderiza las escenas verificadas en las tres dimensiones en ambos backends, con capturas de día, noche y clima. Las pruebas detectan recursos faltantes y comprueban la oclusión del agua y el vidrio, los objetos, la mano y las sombras. El resultado de Cursed Fog se registra cuando se pruebe su ZIP.

## Etapa 3. Hacer robustos los cambios de pack y de mundo

- Gestionar juntos los programas, las mallas y el historial que pertenecen al pack activo. Preparar y validar los programas nuevos antes de sustituir los recursos en uso.
- Aplicar los ajustes del usuario, restablecerlos y guardar los valores por pack. Ante un error de compilación, conservar el último estado funcional y mostrar qué programa falló.
- Probar cambios de tamaño, de dimensión, de pack y de mundo con el JAR. Verificar que se liberan los recursos del mundo anterior y que la selección se restaura tras reiniciar.

**Se cierra cuando** la secuencia de activar, editar, provocar un fallo controlado, cambiar de pack, redimensionar, cerrar el mundo y reiniciar funciona en OpenGL y Vulkan.

## Etapa 4. Reducir el costo del terreno con Embeddium como referencia

- Sustituir las subidas de la malla combinada al cambiar la lista visible por reutilización de mallas GPU por sección. Actualizar las secciones reconstruidas y liberar las que se descarguen. Comprobar que un trabajo cancelado o perteneciente al mundo anterior no publica geometría.
- Reducir las reconstrucciones y la ordenación de transparencias cuando la vista y los datos de entrada permiten reutilizar el resultado. Mantener el orden que exige el pack.
- Comparar los contadores y tiempos con la Etapa 1 en la misma escena y hardware.

**Se cierra cuando** una cámara inmóvil reutiliza la geometría, editar un bloque sube solo las secciones afectadas y recorrer el mundo deja de provocar subidas completas de la malla visible. Las capturas y las pruebas de profundidad confirman que el cambio conserva la escena.

## Etapa 5. Ajustar los perfiles y medir el rendimiento real

- Ajustar los perfiles de entrada, media y alta con controles que afectan al costo de GPU, como resolución interna, distancia y resolución de sombras, y efectos que permita cambiar el pack.
- Medir en una GPU representativa de cada gama. Registrar media, 1% bajo, percentil 95 del tiempo de frame y memoria. Separar las mediciones de estabilidad de las de rendimiento y ejecutar cada backend por su cuenta para medir FPS.
- Evaluar la meta de entrada con una escena, resolución y distancia publicadas. Registrar también cuánto cuesta cada reducción visual.

**Se cierra cuando** hay resultados repetibles por perfil y hardware. Para declarar lograda la meta de entrada, el equipo elegido debe sostener 30 FPS durante el recorrido de referencia y el tiempo de frame debe mostrar la variación observada. Una medición en la RTX 5060 Ti sirve para su configuración y su gama.

## Etapa 6. Verificar y entregar el JAR jugable

- Ejecutar las pruebas automatizadas y repetir los 30 minutos por backend en las tres dimensiones con el JAR que se entregará.
- Probar instalación limpia, selección y desactivación, volver a abrir mundo y reiniciar Minecraft. Guardar capturas, logs, configuración de rendimiento y SHA-256 del JAR.
- Subir a Git los cambios de cada etapa con sus pruebas y dejar accesible el JAR de la versión verificada.

**Se cierra cuando** el JAR empaquetado completa el recorrido en ambos backends y quedan documentados los resultados, los perfiles probados y las limitaciones observadas.

## Primera tarea de implementación

Medir cuántas veces se ejecuta updatePreviewMesh y cuántos bytes sube el buffer de terreno en los tres escenarios de la Etapa 1. Registrar esa base mientras se completa la prueba larga del JAR actual. Usar los datos para comprobar el efecto de reutilizar mallas por sección en la Etapa 4.
