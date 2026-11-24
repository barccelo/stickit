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
