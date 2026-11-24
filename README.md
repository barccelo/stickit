# Stickit

Aplicación Android nativa de biblioteca personal de stickers para Samsung S25 Ultra.

## Funcionalidades

- **Creación rápida:** imágenes, vídeos, GIF y WebP estáticos o animados.
- **Importar desde WhatsApp:** en un sticker, pulsar **Compartir → Stickit** para importar un WebP suelto. También recibe varios archivos a través de «Compartir».
- **Paquetes descargados/exportados:** importación de archivos ZIP con WebP/GIF/PNG o carpetas seleccionadas desde el explorador de Android; se pueden renombrar y organizar los paquetes.
- **Biblioteca inteligente:** etiquetas múltiples, búsqueda por nombres, etiquetas y paquetes; favoritos y recientes.
- **Selección múltiple:** pulsación prolongada sobre cualquier sticker; editar etiquetas, mover de paquete, marcar favoritos o eliminar varios.
- **Edición visual:** añadir texto, ampliar/recortar, girar, reflejar, y deshacer la última edición de imagen. Se aplica a los fotogramas del GIF.
- **Selector flotante:** activar/desactivar en Ajustes; se puede ocultar manteniendo pulsada la burbuja o desde su propia notificación.
- **Respaldo local:** **Ajustes → Guardar respaldo** crea un ZIP con imágenes, GIF y originales retenidos, etiquetas, paquetes, favoritos e historial. **Restaurar respaldo** agrega los stickers faltantes y no borra la biblioteca actual.
- **Uso en WhatsApp:** tocar para copiar como `image/gif` y pegar en una conversación. WhatsApp decide si ofrece enviarlo como GIF o sticker.

### Limitaciones de WhatsApp

El acceso a **todos los paquetes instalados dentro de WhatsApp** no está disponible para aplicaciones ajenas debido al almacenamiento privado de Android. Stickit únicamente importa contenidos que el usuario comparte directamente, carpetas a las que concede acceso o archivos ZIP exportados/descargados. No lee ni modifica la base de datos privada de WhatsApp.

El formato de portapapeles GIF está sujeto al comportamiento de WhatsApp; la vista previa y la opción «enviar como sticker» pueden variar entre sus versiones. Por compatibilidad, la conversión de animaciones utiliza temporalmente un fragmento máximo de 2,4 segundos y aproximadamente 25 fotogramas por segundo. Se conserva una copia del archivo GIF o WebP original cuando la importación lo permite.

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
