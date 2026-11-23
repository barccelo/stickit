# Stickit

Aplicación Android nativa para preparar GIFs que WhatsApp puede ofrecer como GIF o sticker al pegarlos.

## Primera versión
- Importar imágenes, GIFs y vídeos desde Galería o desde «Compartir».
- Crear GIFs con dos fotogramas iguales a partir de una imagen, o convertir un vídeo corto.
- Guardar GIFs en una biblioteca privada con nombre, paquete y etiquetas múltiples.
- Buscar en la biblioteca; tocar para copiar como contenido `image/gif` al portapapeles.
- Abrir un selector flotante sobre otras aplicaciones (requiere permiso explícito).

**Importante:** WhatsApp decide si muestra «GIF o sticker» al pegar. Debe comprobarse en el Samsung S25 Ultra; no existe garantía oficial de ese comportamiento.

## Obtener actualizaciones con Obtainium

Fuente: `https://github.com/barccelo/stickit`.

Obtainium detectará solamente APK publicados como archivos de **GitHub Releases**. El paquete de las versiones finales es siempre `com.barccelo.stickit`. Deben incrementarse `versionCode` y `versionName` para cada release. Todas las versiones deben firmarse con **el mismo keystore privado**; perder la clave obliga a reinstalar y puede perder los datos.

Para proteger futuras actualizaciones, el workflow **no publica releases sin firma**. Se necesitan estos cuatro secretos en Settings → Secrets and variables → Actions:
- `STICKIT_KEYSTORE_BASE64`: archivo JKS codificado en base64.
- `STICKIT_STORE_PASSWORD`
- `STICKIT_KEY_ALIAS`
- `STICKIT_KEY_PASSWORD`

Las compilaciones de la rama `main` generan APK de desarrollo con paquete independiente `com.barccelo.stickit.debug` y firma de prueba. **No instalar el debug como sustituto del APK de Obtainium.**

La primera release estable se publica colocando un tag `v0.1.0` después de configurar los secretos. La compilación debe pasar antes de publicarse. No cargar claves privadas al repositorio.

## Tecnología
Kotlin, Jetpack Compose, SQLite local, Android ClipboardManager con `content://`, selector flotante del sistema y conversor GIF de base Kotlin. Sin conexión a servidores ni registro de usuario.

## Estado
Prototipo inicial, pendiente de CI y pruebas de integración real con WhatsApp.
