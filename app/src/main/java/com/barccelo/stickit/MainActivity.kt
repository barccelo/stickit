package com.barccelo.stickit

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors

private val StickitIndigo = Color(0xFF4855B2)
private val StickitInk = Color(0xFF1D2233)

/**
 * Stickit: all content stays on-device. Opening an imported pack and saving a
 * ZIP backup are user-initiated Storage Access Framework operations.
 */
class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_QUICK_IMPORT = "com.barccelo.stickit.QUICK_IMPORT"
    }
    private lateinit var store: StickerStore
    private val worker = Executors.newSingleThreadExecutor()

    private var entries by mutableStateOf<List<Sticker>>(emptyList())
    private var packNames by mutableStateOf<List<String>>(emptyList())
    private var themeMode by mutableIntStateOf(AppearanceSettings.SYSTEM)
    private var pendingPackLink by mutableStateOf<String?>(null)
    private var overlayActive by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var previewVersion by mutableIntStateOf(0)
    private var shortcutEnabled by mutableStateOf(false)
    private var shortcutPhrases by mutableStateOf<List<String>>(listOf("STK"))
    private var emojiSuggestions by mutableStateOf(true)
    private var pickerHeight by mutableIntStateOf(340)

    private var pendingImport by mutableStateOf<List<Uri>>(emptyList())
    private var pendingZip by mutableStateOf<Uri?>(null)
    private var editing by mutableStateOf<Sticker?>(null)
    private var artwork by mutableStateOf<Sticker?>(null)

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) pendingImport = uris.take(100)
    }
    private val zipPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingZip = uri
    }
    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runTask {
            val report = StickitPackImport.folder(this, store, uri)
            "Carpeta importada: ${report.added} stickers, ${report.skipped} omitidos."
        }
    }
    private val backupWriter = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runTask {
            val count = StickitBackup.export(this, store, uri)
            "Respaldo guardado: ${count} stickers."
        }
    }
    private val backupReader = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runTask {
            val count = StickitBackup.restore(this, store, uri)
            "Respaldo restaurado: $count stickers nuevos, sin borrar los existentes."
        }
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = StickerStore(this)
        themeMode = AppearanceSettings.mode(this)
        refreshShortcutSettings()
        refresh()
        processShareIntent(intent)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = themeMode == AppearanceSettings.DARK ||
                (themeMode == AppearanceSettings.SYSTEM && systemDark)
            val colors = if (dark) darkColorScheme(
                primary = Color(0xFFAEBBFF), onPrimary = Color(0xFF182350),
                secondary = Color(0xFFAEBBFF), background = Color(0xFF10121B),
                surface = Color(0xFF1B1E29), onSurface = Color(0xFFF3F3FA),
                surfaceVariant = Color(0xFF292D3B),
                onSurfaceVariant = Color(0xFFB4BBD0),
                outlineVariant = Color(0xFF3C4254),
                secondaryContainer = Color(0xFF303758),
                tertiary = Color(0xFFE8BE75)
            ) else lightColorScheme(
                primary = StickitIndigo, onPrimary = Color.White,
                secondary = StickitIndigo, background = Color(0xFFFAFAFC),
                surface = Color.White, onSurface = StickitInk,
                surfaceVariant = Color(0xFFF1F2F8),
                onSurfaceVariant = Color(0xFF80889A),
                outlineVariant = Color(0xFFE8EAF2),
                secondaryContainer = Color(0xFFEDEFFC),
                tertiary = Color(0xFFCE9A39)
            )
            MaterialTheme(colorScheme = colors) {
                StickitHome(
                    stickers = entries,
                    packs = packNames,
                    onCreatePack = { name ->
                        runTask { store.createPack(name); "Paquete creado." }
                    },
                    onRenamePack = { old, name ->
                        runTask { store.renamePack(old, name); "Paquete renombrado." }
                    },
                    onMovePack = { name, delta ->
                        runTask { store.movePack(name, delta); "Orden de paquetes actualizado." }
                    },
                    onDeletePack = { name, deleteContents ->
                        runTask {
                            store.deletePack(name, deleteContents)
                            "Paquete eliminado."
                        }
                    },
                    fileOf = { store.stickerFile(it) },
                    previewVersion = previewVersion,
                    busy = busy, overlayEnabled = overlayActive,
                    themeMode = themeMode,
                    onChangeTheme = { mode ->
                        themeMode = mode
                        AppearanceSettings.save(this, mode)
                    },
                    shortcutEnabled = shortcutEnabled,
                    shortcutPhrases = shortcutPhrases,
                    emojiSuggestions = emojiSuggestions,
                    pickerHeight = pickerHeight,
                    onEnableShortcuts = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onAddShortcut = { text ->
                        ShortcutSettings.savePhrases(this, shortcutPhrases + text)
                        refreshShortcutSettings()
                    },
                    onRemoveShortcut = { text ->
                        ShortcutSettings.savePhrases(this, shortcutPhrases.filterNot { it == text })
                        refreshShortcutSettings()
                    },
                    onToggleSuggestions = { enabled ->
                        ShortcutSettings.setSuggestionsEnabled(this, enabled)
                        refreshShortcutSettings()
                    },
                    onChangePickerHeight = { height ->
                        ShortcutSettings.setPanelHeightDp(this, height)
                        refreshShortcutSettings()
                    },
                    onShowPicker = { showPickerPreview() },
                    onToggleOverlay = { if (it) enableOverlay() else disableOverlay() },
                    onPick = { mediaPicker.launch(arrayOf("image/*", "video/*")) },
                    onPaste = { fromClipboard() },
                    onZip = { zipPicker.launch(arrayOf("application/zip",
                        "application/x-zip-compressed", "application/octet-stream")) },
                    onFolder = { directoryPicker.launch(null) },
                    onExportBackup = {
                        backupWriter.launch("Stickit-respaldo-${System.currentTimeMillis()}.zip")
                    },
                    onImportBackup = {
                        backupReader.launch(arrayOf("application/zip",
                            "application/x-zip-compressed", "application/octet-stream"))
                    },
                    onCopy = { copySticker(it) },
                    onEdit = { editing = it },
                    onFavorite = { store.toggleFavorite(it); refresh() },
                    onBulkEdit = { ids, pack, tags, favorite ->
                        runTask {
                            store.editMany(ids, pack, tags, favorite)
                            "${ids.size} stickers actualizados."
                        }
                    },
                    onBulkDelete = { ids ->
                        runTask {
                            store.removeMany(ids)
                            "${ids.size} stickers eliminados."
                        }
                    }
                )

                val sharedLink = pendingPackLink
                if (sharedLink != null) {
                    AlertDialog(
                        onDismissRequest = { pendingPackLink = null },
                        title = { Text("Paquete compartido por WhatsApp") },
                        text = {
                            Text("WhatsApp ha compartido un enlace al paquete, no sus archivos. " +
                                "Stickit no puede descargar directamente un paquete privado " +
                                "desde ese enlace. Puedes abrirlo en WhatsApp o importar el ZIP " +
                                "exportado del paquete desde Stickit.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val url = sharedLink
                                pendingPackLink = null
                                try {
                                    val uri = Uri.parse(url)
                                    val host = uri.host.orEmpty().lowercase()
                                    if (uri.scheme == "https" &&
                                        (host == "wa.me" || host == "whatsapp.com" ||
                                            host.endsWith(".whatsapp.com"))) {
                                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    } else notify("El enlace no es de WhatsApp")
                                } catch (_: Exception) {
                                    notify("No se pudo abrir el enlace")
                                }
                            }) { Text("Abrir en WhatsApp") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingPackLink = null }) {
                                Text("Cerrar")
                            }
                        }
                    )
                }

                if (pendingImport.isNotEmpty()) {
                    val sources = pendingImport
                    val label = if (sources.size == 1) nameOf(sources.first()) else "Sticker"
                    ImportStickersDialog(
                        count = sources.size, suggestedName = label,
                        onDismiss = { pendingImport = emptyList() },
                        onImport = { name, tags, pack, emojis ->
                            pendingImport = emptyList()
                            runTask {
                                var success = 0; var failed = 0
                                sources.forEachIndexed { index, uri ->
                                    var generated: File? = null
                                    try {
                                        generated = GifConverter.import(this, uri)
                                        store.add(generated, if (sources.size == 1) name
                                            else nameOf(uri), tags, pack, emojis)
                                        success++
                                    } catch (e: Exception) {
                                        generated?.delete()
                                        failed++
                                    }
                                }
                                if (success == 0) error("Ningún sticker se pudo importar")
                                "$success guardados" + if (failed > 0) " · $failed omitidos" else ""
                            }
                        }
                    )
                }

                if (pendingZip != null) {
                    val uri = pendingZip!!
                    PackNameDialog(
                        onDismiss = { pendingZip = null },
                        onConfirm = { pack ->
                            pendingZip = null
                            runTask {
                                val result = StickitPackImport.zip(this, store, uri, pack)
                                "${result.added} stickers importados · ${result.skipped} omitidos."
                            }
                        }
                    )
                }

                val current = editing
                if (current != null) {
                    val gifFile = store.stickerFile(current)
                    val base = gifFile.nameWithoutExtension
                    val hasSource = listOf("source.gif", "source.webp").any {
                        File(gifFile.parentFile, "$base.$it").isFile
                    }
                    StickerMetadataDialog(
                        sticker = current,
                        canReprocess = File(gifFile.parentFile, "$base.source.gif").isFile,
                        canUndo = StickerArtworkEditor.canUndo(gifFile),
                        onDismiss = { editing = null },
                        onSave = { name, tags, pack, emojis ->
                            store.update(current, name, tags, pack, emojis)
                            editing = null
                            refresh()
                        },
                        onArtwork = { editing = null; artwork = current },
                        onReprocess = {
                            editing = null
                            runTask {
                                GifConverter.reprocessStoredGif(gifFile)
                                "GIF reprocesado. Puedes copiarlo nuevamente."
                            }
                        },
                        onUndo = {
                            editing = null
                            runTask {
                                StickerArtworkEditor.undo(gifFile)
                                "Edición de imagen deshecha."
                            }
                        },
                        onDelete = {
                            editing = null
                            runTask {
                                store.remove(current)
                                "Sticker eliminado."
                            }
                        }
                    )
                }

                val art = artwork
                if (art != null) {
                    val gif = store.stickerFile(art)
                    StickerArtworkDialog(
                        file = gif,
                        hasUndo = StickerArtworkEditor.canUndo(gif),
                        onDismiss = { artwork = null },
                        onSave = { edits ->
                            artwork = null
                            runTask {
                                StickerArtworkEditor.apply(gif, edits)
                                "Imagen editada. El GIF está listo para compartir."
                            }
                        },
                        onUndo = {
                            artwork = null
                            runTask {
                                StickerArtworkEditor.undo(gif)
                                "Versión anterior restaurada."
                            }
                        }
                    )
                }
            }
        }
        if (intent?.action == ACTION_QUICK_IMPORT) {
            window.decorView.post {
                mediaPicker.launch(arrayOf("image/*", "video/*"))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayActive = getSharedPreferences("stickit_ui", Context.MODE_PRIVATE)
            .getBoolean("overlay_enabled", false)
        refreshShortcutSettings()
        themeMode = AppearanceSettings.mode(this)
        if (::store.isInitialized) refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_QUICK_IMPORT) {
            window.decorView.post { mediaPicker.launch(arrayOf("image/*", "video/*")) }
        } else processShareIntent(intent)
    }

    private fun processShareIntent(intent: Intent?) {
        if (intent == null) return
        val received = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) received.add(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (list != null) received.addAll(list)
            }
        }
        if (received.isEmpty() && intent.action in
            listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            intent.clipData?.let { clips ->
                for (i in 0 until clips.itemCount) clips.getItemAt(i).uri?.let { received.add(it) }
            }
        }
        if (received.isNotEmpty()) {
            val unique = received.distinct().take(100)
            val packages = unique.filter(::isZipContent)
            if (packages.size == 1 && unique.size == 1) {
                pendingZip = packages.first()
            } else {
                pendingImport = unique.filterNot(::isZipContent)
                if (packages.isNotEmpty()) {
                    notify("Selecciona un ZIP de paquete a la vez para importarlo.")
                }
            }
            return
        }
        val message = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val link = Regex("https://(?:[a-zA-Z0-9-]+\\.)*(?:whatsapp\\.com|wa\\.me)/[^\\s]+",
            RegexOption.IGNORE_CASE).find(message)?.value
        if (link != null) pendingPackLink = link
        else if (intent.action == Intent.ACTION_SEND && message.isNotBlank()) {
            notify("WhatsApp compartió texto, no archivos de stickers.")
        }
    }

    private fun isZipContent(uri: Uri): Boolean {
        try {
            contentResolver.openInputStream(uri).use { input ->
                val bytes = ByteArray(4)
                if (input != null && input.read(bytes) == 4) {
                    return bytes[0] == 'P'.code.toByte() &&
                        bytes[1] == 'K'.code.toByte() &&
                        bytes[2] == 3.toByte() &&
                        bytes[3] == 4.toByte()
                }
            }
        } catch (_: Exception) {}
        return contentResolver.getType(uri).orEmpty().contains("zip", ignoreCase = true)
    }

    private fun nameOf(uri: Uri): String {
        var name: String? = null
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        } catch (_: Exception) {}
        return (name ?: uri.lastPathSegment ?: "Nuevo sticker")
            .substringAfterLast('/').substringBeforeLast('.')
            .take(90).ifBlank { "Nuevo sticker" }
    }

    private fun fromClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val data = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            val uri = data?.uri ?: data?.intent?.data
            if (uri != null) pendingImport = listOf(uri)
            else notify("No hay una imagen accesible en el portapapeles. Utiliza Compartir.")
        } catch (e: Exception) {
            notify("No se pudo abrir el portapapeles: ${e.message}")
        }
    }

    private fun refresh() {
        entries = store.all()
        packNames = store.packNames()
    }

    private fun refreshShortcutSettings() {
        shortcutEnabled = StickerShortcutService.isEnabled(this)
        shortcutPhrases = ShortcutSettings.phrases(this)
        emojiSuggestions = ShortcutSettings.suggestionsEnabled(this)
        pickerHeight = ShortcutSettings.panelHeightDp(this)
    }

    private fun showPickerPreview() {
        val service = StickerShortcutService.active()
        if (service != null) {
            service.openPicker()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            notify("Para abrir el selector, activa Acceso rápido o los atajos de accesibilidad.")
            return
        }
        val intent = Intent(this, OverlayService::class.java)
            .setAction(OverlayService.ACTION_SHOW)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun runTask(task: () -> String) {
        if (busy) { notify("Hay una operación en curso"); return }
        busy = true
        worker.execute {
            try {
                val message = task()
                runOnUiThread {
                    busy = false
                    previewVersion++
                    refresh()
                    refreshShortcutSettings()
                    themeMode = AppearanceSettings.mode(this)
                    notify(message)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    busy = false
                    previewVersion++
                    refresh()
                    notify("No se pudo completar: ${e.message ?: "Error inesperado"}")
                }
            }
        }
    }

    private fun copySticker(sticker: Sticker) {
        try {
            ClipboardBridge.copyGif(this, store.stickerFile(sticker))
            store.markUsed(sticker)
            refresh()
            notify("GIF copiado. Pégalo en WhatsApp como sticker.")
        } catch (e: Exception) {
            notify("No se pudo copiar: ${e.message}")
        }
    }

    private fun notify(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            notify("Permite la ventana flotante y regresa a Stickit.")
        } else {
            if (Build.VERSION.SDK_INT >= 33)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
            getSharedPreferences("stickit_ui", Context.MODE_PRIVATE)
                .edit().putBoolean("overlay_enabled", true).apply()
            overlayActive = true
        }
    }

    private fun disableOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        getSharedPreferences("stickit_ui", Context.MODE_PRIVATE)
            .edit().putBoolean("overlay_enabled", false).apply()
        overlayActive = false
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
