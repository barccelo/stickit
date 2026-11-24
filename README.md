# Stickit

Aplicación Android nativa de biblioteca personal de stickers para Samsung S25 Ultra.

## Funcionalidades

- **Creación rápida:** imágenes, vídeos, GIF y WebP estáticos o animados.
- **Importar desde WhatsApp:** en un sticker, pulsar **Compartir → Stickit** para importar un WebP suelto. También recibe varios archivos a través de «Compartir».
- **Paquetes descargados/exportados:** importación de archivos ZIP con WebP/GIF/PNG o carpetas seleccionadas desde el explorador de Android; se pueden renombrar y organizar los paquetes.
- **Biblioteca inteligente:** etiquetas múltiples, búsqueda por nombres, etiquetas y paquetes; favoritos y recientes.
- **Administración de paquetes:** crear, renombrar y ordenar paquetes, eliminar solo el paquete (moviendo los stickers a General) o eliminar también sus stickers.
- **Selección múltiple:** pulsación prolongada sobre cualquier sticker; editar etiquetas, mover de paquete, marcar favoritos o eliminar varios.
- **Edición visual:** añadir texto, ampliar/recortar, girar, reflejar, y deshacer la última edición de imagen. Se aplica a los fotogramas del GIF.
- **Selector flotante:** activar/desactivar en Ajustes; se puede ocultar manteniendo pulsada la burbuja o desde su propia notificación. Al entrar en aplicaciones recientes, el servicio de accesibilidad opcional lo minimiza; el asa superior controla la altura y el botón de crear abre la importación rápida.
- **Tema oscuro:** automático según el sistema, claro u oscuro, incluido el selector flotante.
- **Respaldo local:** **Ajustes → Guardar respaldo** crea un ZIP con imágenes, GIF y originales retenidos, etiquetas, paquetes, favoritos e historial. **Restaurar respaldo** agrega los stickers faltantes y no borra la biblioteca actual.
- **Uso en WhatsApp:** tocar para copiar como `image/gif` y pegar en una conversación. WhatsApp decide si ofrece enviarlo como GIF o sticker.

### Limitaciones de WhatsApp

Al pulsar **Compartir paquete** dentro de WhatsApp, la aplicación suele enviar un enlace al paquete, **no un ZIP descargable**. Stickit identifica el enlace y permite abrirlo en WhatsApp, pero no puede extraer sus stickers por una API pública de WhatsApp. Si Compartir entrega un ZIP real, Stickit lo detecta y lo importa directamente.

El acceso a **todos los paquetes instalados dentro de WhatsApp** no está disponible para aplicaciones ajenas debido al almacenamiento privado de Android. Stickit únicamente importa contenidos que el usuario comparte directamente, carpetas a las que concede acceso o archivos ZIP exportados/descargados. No lee ni modifica la base de datos privada de WhatsApp.

El formato de portapapeles GIF está sujeto al comportamiento de WhatsApp; la vista previa y la opción «enviar como sticker» pueden variar entre sus versiones. Por compatibilidad, la conversión de animaciones utiliza temporalmente un fragmento máximo de 2,4 segundos y aproximadamente 25 fotogramas por segundo. Se conserva una copia del archivo GIF o WebP original cuando la importación lo permite.

## Selector al escribir y sugerencias

- **Atajos:** abre **Ajustes → Atajos y sugerencias**. Puedes crear varios (por defecto `STK`). En una conversación de WhatsApp, escribir uno de esos códigos al final de una palabra abrirá Stickit.
- **Permiso explícito:** en ese mismo apartado, selecciona **Habilitar atajos en Android**. Luego activa el servicio de accesibilidad **Stickit** en los ajustes del teléfono. No se activa sin tu autorización.
- **Alcance restringido:** Stickit solo observa los cambios de texto de campos editables de WhatsApp y WhatsApp Business; ignora campos de contraseña. El análisis se hace en el dispositivo; no almacena los mensajes, palabras escritas ni pulsaciones.
- **Coincidencias:** escribir las primeras **tres o más letras** de un nombre o etiqueta muestra una barra con stickers relacionados. También aparecen los stickers asociados a un emoji cuando lo escribes.
- **Asociaciones:** abre **Editar sticker** para añadir múltiples etiquetas y emojis como cápsulas removibles. La barra de sugerencias utiliza los emojis asociados, no los inventa automáticamente. Al escoger una sugerencia por emoji, Stickit intenta borrar solo ese emoji si el campo de WhatsApp sigue enfocado y el cursor está al final.
- **Selector ajustable:** el panel tiene el ancho del teclado y una altura configurable en Ajustes (230–540 dp); también se amplía o reduce arrastrando su asa superior. Las sugerencias se pueden mover desde el asa lateral y recuerdan su posición.
- **Para usar un sticker:** toca su imagen; Stickit copia el GIF y cierra el panel. Después pégalo en WhatsApp. No envía mensajes automáticamente y no borra los atajos que hayas escrito. En sugerencias por emoji intenta eliminar únicamente el emoji disparador.
- **Sin accesibilidad:** puedes seguir usando el selector flotante manual y la aplicación normalmente. El atajo y las sugerencias automáticas no funcionan sin el servicio opcional.

En algunos dispositivos Android, la instalación externa de una APK puede exigir habilitar antes **Permitir ajustes restringidos** desde la ficha de información de Stickit. Esto depende de la versión del sistema y de cómo se instaló la aplicación. El acceso es sensible: actívalo solo si comprendes el permiso de lectura de campos de texto que concede Android.

El respaldo ZIP ahora también contiene los atajos y ajustes de presentación, además de stickers, etiquetas, paquetes y favoritos. Los respaldos anteriores siguen siendo compatibles.

## Actualizaciones mediante Obtainium

Repositorio y fuente: `https://github.com/barccelo/stickit`.

[Añadir Stickit a Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%257B%2522id%2522%253A%2522com.barccelo.stickit%2522%252C%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252Fbarccelo%252Fstickit%2522%252C%2522author%2522%253A%2522barccelo%2522%252C%2522name%2522%253A%2522Stickit%2522%257D)

Los APK oficiales se publican en [GitHub Releases](https://github.com/barccelo/stickit/releases) con el mismo identificador permanente `com.barccelo.stickit`, `versionCode` creciente y la **misma firma digital**.

Las compilaciones de desarrollo están separadas (paquete `com.barccelo.stickit.debug`). No instalar APK debug encima de la app firmada para Obtainium.

La firma privada se gestiona mediante el secreto de GitHub Actions `STICKIT_SIGNING_BUNDLE`. **No publicar ni subir las claves al repositorio.**

## Tecnología

Kotlin, Jetpack Compose, SQLite, `ContentResolver`, Android `FileProvider`, GIF89a con paletas adaptativas, decodificación de WebP animado, ZIP para respaldos locales, Storage Access Framework para archivos/carpetas y servicio de selector flotante.

No se requiere cuenta ni servidor para crear, buscar, compartir o respaldar stickers.

## Validaciones

GitHub Actions compila el APK de desarrollo, ejecuta las pruebas del codificador GIF y verifica la firma del APK final antes de publicar una versión. La integración con la versión concreta de WhatsApp del dispositivo necesita pruebas reales en el S25 Ultra.

## Tamaño y compatibilidad

La APK de Obtainium es una compilación de producción con R8 y eliminación de recursos no usados. A partir de 0.4.0 solo incluye librerías nativas de **ARM64**, compatibles con el Galaxy S25 Ultra; otras arquitecturas Android pueden no ser compatibles. Las versiones anteriores sin optimización siguen disponibles en GitHub Releases.

## Creación desde el selector flotante

El botón **⊕** ofrece dos opciones en el propio selector: **Pegar imagen copiada**, leyendo los datos de imagen del portapapeles cuando Stickit pasa a primer plano, o **Elegir imagen, GIF o vídeo**, para utilizar el explorador de Android. Ambas utilizan el formulario habitual de etiquetas, emojis y paquetes. Los enlaces de imagen copiados como texto no equivalen a datos de imagen: para esos sitios utiliza **Compartir con Stickit**.

## Importación WebP y temporización

Stickit ya no recorta silenciosamente los WebP animados a 2,4 segundos. Para conservar el ritmo, interpreta las marcas de tiempo de libwebp como el **fin de cada fotograma**, convierte sus intervalos a centésimas de segundo y agrupa únicamente los menores de 20 ms (la duración mínima estable en GIF). Los WebP de hasta 30 segundos se procesan completos; los mayores devuelven un error explícito en lugar de truncarse. Para recuperar un sticker WebP importado antes de esta corrección, abre **Editar sticker → Corregir animación WebP original**. Se conserva el nombre, las etiquetas y el paquete, pero se restaura la imagen original si había ediciones visuales.

## Corrección de importación WebP — 0.4.3

El decodificador nativo de WebP reutiliza el mismo objeto `Bitmap` al avanzar de fotograma. La versión anterior lo reciclaba indebidamente, impidiendo la importación de algunos stickers animados compartidos desde WhatsApp. La 0.4.3 conserva el fotograma bajo control del decodificador hasta liberar este, mantiene la duración original y proporciona el mensaje concreto si una conversión falla. Los stickers existentes no requieren migración.

## Stickit 0.5.0 — Ordenación, recorte y selección de imágenes

- **Orden persistente de stickers:** la biblioteca y el selector flotante comparten el orden elegido: más nuevos (predeterminado), nombre A–Z, utilizados recientemente, más utilizados o más antiguos. El selector permite cambiarlo desde la primera cápsula de filtros y la biblioteca desde el botón de ordenación.
- **Cierre natural del selector:** Atrás (también mediante el despachador moderno de Android) cierra la ventana; tocar fuera también la descarta. Se conserva el botón X.
- **Recorte real:** selecciona las esquinas o cualquiera de los cuatro lados para cambiar el área de recorte, o arrastra el centro para moverla. El editor muestra una cuadrícula de tercios, permite restablecer, girar, reflejar y añadir texto. La región seleccionada se recorta y se ajusta al cuadrado sin estirarla; se aplica a todos los fotogramas y respeta la cadencia del GIF.
- **Edición al importar:** para un sticker individual pegado, compartido o elegido desde la galería, la opción «Editar y recortar al guardar» viene marcada. Tras guardar se abre directamente el editor; se puede desmarcar para importar sin editar.
- **Aplicación de galería:** al seleccionar contenido se ofrece el diálogo nativo de aplicaciones capaces de proporcionar imágenes y vídeos (según las aplicaciones instaladas y las reglas del fabricante). También se mantiene una opción separada «Examinar archivos» desde el creador flotante.
- **Respaldo local:** preserva la preferencia de ordenación. Las versiones anteriores de los archivos ZIP siguen siendo compatibles.

No se modifica el conversor WebP/GIF que preserva la duración y velocidad de los stickers animados.

## Stickit 0.5.1 — Velocidad de GIF y WebP estabilizada

El conversor WebP ya no produce fotogramas con retardos de 20 ms que determinados reproductores de GIF pueden alargar artificialmente. Remuestrea la animación en intervalos de aproximadamente 40 ms (equivalentes a 25 FPS) o más, utilizando los tiempos acumulados originales para decidir qué fotograma mostrar. Mantiene la duración completa del sticker, con precisión de centésimas de segundo, y un límite máximo de 350 fotogramas de salida. Los fotogramas de origen de baja frecuencia se replican correctamente, evitando errores de importación.

El editor de recorte no vuelve a codificar si se pulsa Guardar sin cambios. Al aplicar una edición real, los GIF antiguos con retardos demasiado cortos también se normalizan a tiempos de reproducción seguros conservando la duración. Para corregir un sticker WebP importado con versiones anteriores, abre **Editar sticker → Corregir animación WebP original** (requiere que Stickit conserve el original). Las pruebas automatizadas comprueban las duraciones, los retardos codificados y la repetición de fotogramas.

## Stickit 0.5.2 — Comparación de reproducción real en WhatsApp

Los GIF pegados en WhatsApp no son stickers nativos WebP. A raíz de las diferencias entre la reproducción local y WhatsApp, se reemplaza la hipótesis de ajustar FPS por una comparación controlada sobre **el mismo archivo WebP original**.

- **Importación WebP nueva:** genera por defecto un GIF completo con un máximo de 60 fotogramas distintos; coalescea fotogramas repetidos y conserva la duración total del original, para reducir la carga del decodificador de GIF en WhatsApp.
- **Editar sticker → Comparar modos de reproducción:** selecciona «Completo · WhatsApp (60 cuadros)», «Completo · mayor fluidez» o «Referencia corta · 2,4 s». Solo el último modo recorta la animación, deliberadamente, para reproducir el caso que funcionaba antes. Los otros dos conservan la duración completa.
- **Datos comprobables:** el editor indica el número de cuadros, la duración y el peso del GIF guardado. Compara la reproducción del mismo sticker en WhatsApp tras cada modo; no se puede verificar desde pruebas locales cómo procesará GIFs el WhatsApp instalado en tu teléfono.
- **Sin perder el original:** Stickit mantiene el `.source.webp` intacto; cada modo reconstruye el GIF de forma atómica. Si no conserva el archivo original (p. ej. respaldos antiguos), la opción no estará disponible.

Las pruebas verifican duración completa, máximo de fotogramas, los tres perfiles, GIFs de fotogramas repetidos y demoras codificadas. No se considera resuelta la diferencia de reproducción en WhatsApp hasta probarla en un dispositivo; si solo la versión corta funciona, será necesario usar el formato nativo de stickers WebP en WhatsApp en vez del portapapeles GIF.

## Stickit 0.5.3 — Nueva conversión completa con ritmo de 2,4 s

La opción **Completo · ritmo de 2,4 s (nuevo)** aplica 40 milisegundos por fotograma hasta el final de la animación, como en la referencia corta comprobada en WhatsApp, pero sin recortarla. Los WebP con frecuencia superior a 25 FPS se remuestrean para evitar ralentizarlos. Se conserva el original, la reconstrucción es atómica y el nuevo perfil es el predeterminado de importación. Se mantienen los tres modos anteriores para comparar.

**Importante:** el resultado pegado está sujeto al procesamiento de GIF de WhatsApp y debe validarse en el teléfono. Si WhatsApp ralentiza incluso esta conversión completa, la siguiente solución es el soporte oficial de stickers WebP animados mediante paquetes, no recortar nuevamente la animación.

## Stickit 0.5.4 — Eliminar aceleración y ofrecer WebP animado nativo

Se revierte el modo experimental que imponía 40 ms a todos los fotogramas y provocaba aceleración. Nuevas importaciones y reconstrucciones de WebP usan la duración real y el modo de mayor fluidez (hasta 350 cuadros); el modo corto de 2,4 s permanece solo como referencia. Los GIF pegados siguen sujetos al reproductor de WhatsApp, que puede alterar animaciones completas.

**Nueva vía nativa:** en un sticker importado desde WebP animado, **Editar sticker → Añadir animados a WhatsApp** prepara una copia de los originales compatibles del mismo paquete (3–30, 512×512, hasta 500 KB y 10 s cada uno) sin modificar el ritmo. Utiliza el ContentProvider y la acción oficiales de WhatsApp para que el usuario confirme la incorporación del paquete en WhatsApp y envíe los stickers desde su selector nativo. No intenta usar el portapapeles GIF para estos envíos. Solo exporta WebP originales válidos; si hay menos de tres, explica por qué no puede formar el paquete. Los cambios hechos exclusivamente en el GIF no modifican el WebP original.

## Stickit 0.5.5 — Progreso, sugerencias, emoji y selector ampliable

- **Importación:** cuadro modal de progreso durante operaciones de biblioteca. Los archivos ZIP, carpetas e importaciones de medios múltiples muestran el número procesado sobre el total; otras operaciones mantienen una barra indeterminada mientras trabajan.
- **Disparadores:** el servicio de accesibilidad borra únicamente el atajo que acaba de invocar el selector, conservando el resto del mensaje. También borra el texto o emoji exacto que activó una sugerencia cuando se escoge el sticker, siempre que el cursor continúe al final de la entrada editable de WhatsApp.
- **Búsqueda:** se admiten etiquetas con varias palabras, coincidencias por cualquiera de sus palabras y por frase, y coincidencias exactas de nombres o etiquetas de una o dos letras (como «Eu»), sin sugerencias masivas por prefijo.
- **Editor:** Enter/Done confirma la etiqueta y mantiene el campo para seguir escribiendo. Selector visual de emojis con selección múltiple y campo de texto manual.
- **Selector flotante:** botón de ampliar/restaurar casi a pantalla completa y gesto hacia arriba en el asa para ampliar (hacia abajo para restaurar), conservando el tamaño compacto anterior. El evento de Inicio/Recientes cierra ambos selectores cuando la accesibilidad está activada; hay un receptor complementario del sistema.
- **Enviar:** mantener pulsado un sticker en el selector abre la hoja de compartir Android (GIF) y la ficha de edición incluye «Enviar GIF a una conversación…». El envío definitivo requiere la confirmación del usuario en la aplicación de destino y no garantiza formato de sticker nativo.
- **Compartir/importar desde terceros:** la actividad pasa a `singleTask` para reutilizar la instancia existente, en lugar de crear múltiples tarjetas en recientes.

## Stickit 0.5.6 — GIF completo con el ritmo de la referencia de 2,4 s

En **Editar sticker → Comparar modos de reproducción → Completo · ritmo de referencia (nuevo)** se aplica a cada ventana consecutiva de 2,4 segundos el **mismo algoritmo de 60 muestras** usado en la versión corta que el usuario comprobó que funcionaba mejor al pegar en WhatsApp. Cada ventana lee las marcas temporales originales del WebP; los fotogramas que cruzan límites de ventana se fusionan sin duplicarlos. La animación completa mantiene la duración original y puede producir más de 350 fotogramas (hasta unos 750 para 30 segundos a 25 FPS). No se impone una duración fija de 40 ms a cada fotograma original, evitando la aceleración de la versión 0.5.3.

La opción corta de 2,4 s y los dos modos completos anteriores se conservan sin modificar para comparar, y el predeterminado permanece como «Completo · mayor fluidez» hasta verificar cuál funciona mejor en WhatsApp. El GIF reconstruido reemplaza exclusivamente la copia derivada; el WebP original se conserva. La prueba automatizada valida la igualdad exacta del primer tramo con el modo corto, la duración total, entradas rápidas y lentas, fronteras entre tramos y los retrasos codificados en GIF. El pegado en WhatsApp requiere prueba real en el dispositivo: WhatsApp podría procesar los GIF largos de manera distinta.
