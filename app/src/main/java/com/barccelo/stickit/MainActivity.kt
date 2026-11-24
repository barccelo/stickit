package com.barccelo.stickit

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors

private val Ink = Color(0xFF1B2131)
private val Mist = Color(0xFFF1F2F7)
private val CanvasColor = Color(0xFFFAFAFC)
private val Accent = Color(0xFF4753AE)
private val BorderColor = Color(0xFFE7E8EF)
private val Muted = Color(0xFF7B8292)

class MainActivity : ComponentActivity() {
    private lateinit var store: StickerStore
    private val worker = Executors.newSingleThreadExecutor()
    private var pendingImport by mutableStateOf<Uri?>(null)
    private var editing by mutableStateOf<Sticker?>(null)
    private var entries by mutableStateOf<List<Sticker>>(emptyList())
    private var busy by mutableStateOf(false)
    private var previewVersion by mutableIntStateOf(0)
    private var overlayActive by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = StickerStore(this)
        refresh()
        processShareIntent(intent)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent,
                    onPrimary = Color.White,
                    background = CanvasColor,
                    surface = Color.White,
                    onSurface = Ink,
                    surfaceVariant = Mist
                )
            ) {
                Screen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayActive = getSharedPreferences("stickit_ui", Context.MODE_PRIVATE)
            .getBoolean("overlay_enabled", false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processShareIntent(intent)
    }

    private fun processShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            if (uri != null) pendingImport = uri
        }
    }

    private fun refresh() { entries = store.all() }

    private fun fromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val item = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val uri = item?.uri ?: item?.intent?.data
        if (uri != null) {
            pendingImport = uri
        } else {
            notify("No hay una imagen o GIF accesible en el portapapeles. Usa Compartir.")
        }
    }

    private fun notify(message: String) {
        notice = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun importFile(uri: Uri, name: String, tags: String, pack: String) {
        pendingImport = null
        busy = true
        worker.execute {
            try {
                val file = GifConverter.import(this, uri)
                try { store.add(file, name, tags, pack) }
                catch (error: Exception) { file.delete(); throw error }
                runOnUiThread { busy = false; refresh(); notify("GIF guardado en Stickit") }
            } catch (error: Exception) {
                runOnUiThread { busy = false; notify("Error al convertir: ${error.message}") }
            }
        }
    }

    private fun reprocessGif(sticker: Sticker) {
        editing = null
        busy = true
        worker.execute {
            try {
                GifConverter.reprocessStoredGif(store.stickerFile(sticker))
                runOnUiThread {
                    busy = false
                    previewVersion++
                    refresh()
                    notify("GIF actualizado. Cópialo nuevamente y pruébalo en WhatsApp.")
                }
            } catch (error: Exception) {
                runOnUiThread {
                    busy = false
                    notify("No se pudo reprocesar: ${error.message}")
                }
            }
        }
    }

    private fun copy(sticker: Sticker) {
        try {
            ClipboardBridge.copyGif(this, store.stickerFile(sticker))
            store.markUsed(sticker)
            refresh()
            notify("GIF copiado. Abre WhatsApp y pégalo.")
        } catch (error: Exception) {
            notify("No se pudo copiar: ${error.message}")
        }
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
            notify("Permite mostrar Stickit sobre otras aplicaciones y regresa aquí.")
        } else {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
            getSharedPreferences("stickit_ui", Context.MODE_PRIVATE)
                .edit().putBoolean("overlay_enabled", true).apply()
            overlayActive = true
            notify("Selector flotante activado")
        }
    }

    private fun disableOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        getSharedPreferences("stickit_ui", Context.MODE_PRIVATE)
            .edit().putBoolean("overlay_enabled", false).apply()
        overlayActive = false
        notify("Selector flotante oculto")
    }

    @Composable
    private fun Screen() {
        var query by remember { mutableStateOf("") }
        var filter by remember { mutableStateOf("Todos") }
        val packages = remember(entries) { entries.map { it.pack }.distinct().sorted() }
        val filters = listOf("Todos", "Recientes", "Favoritos") + packages.map { "Paquete: $it" }
        val visible = remember(entries, query, filter) {
            entries.filter { it.matches(query) }.filter {
                when {
                    filter == "Favoritos" -> it.favorite
                    filter.startsWith("Paquete: ") -> it.pack == filter.removePrefix("Paquete: ")
                    else -> true
                }
            }.let {
                if (filter == "Recientes") it.sortedByDescending { item -> item.lastUsed } else it
            }
        }

        Column(Modifier.fillMaxSize().background(CanvasColor)) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("stickit", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp, color = Ink)
                        Text("Tu biblioteca de stickers", color = Muted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFEEF0FA)) {
                        Icon(Icons.Outlined.Layers, contentDescription = null,
                            modifier = Modifier.padding(14.dp).size(24.dp), tint = Accent)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { picker.launch(arrayOf("image/*", "video/*")) },
                        enabled = !busy,
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Importar", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { fromClipboard() }, enabled = !busy,
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null,
                            modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pegar imagen")
                    }
                }
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(13.dp),
                            color = if (overlayActive) Color(0xFFECEEFE) else Mist) {
                            Icon(Icons.Outlined.Layers, contentDescription = null,
                                tint = if (overlayActive) Accent else Muted,
                                modifier = Modifier.padding(10.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Selector flotante", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium, color = Ink)
                            Text(if (overlayActive) "Visible sobre otras apps" else "Oculto",
                                style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        Switch(
                            checked = overlayActive, onCheckedChange = { enabled ->
                                if (enabled) enableOverlay() else disableOverlay()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Buscar stickers y etiquetas", color = Muted) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Muted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(17.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFFB8BEE9),
                        unfocusedBorderColor = BorderColor
                    )
                )
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mis stickers", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.weight(1f))
                    Text("${entries.size} guardados", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { value ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { filter = value },
                        label = { Text(value) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFEAECF8)) {
                            Icon(Icons.Outlined.Layers, contentDescription = null,
                                modifier = Modifier.padding(21.dp).size(32.dp), tint = Accent)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(if (entries.isEmpty()) "Tu colección empieza aquí" else "Sin resultados",
                            fontWeight = FontWeight.SemiBold, color = Ink,
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(5.dp))
                        Text(if (entries.isEmpty())
                            "Importa una imagen, un GIF o un vídeo."
                            else "Prueba con otra etiqueta o palabra.",
                            color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { "${it.id}:$previewVersion" }) { sticker ->
                        StickerCard(sticker,
                            file = store.stickerFile(sticker),
                            onCopy = { copy(sticker) },
                            onEdit = { editing = sticker },
                            onFavorite = { store.toggleFavorite(sticker); refresh() }
                        )
                    }
                }
            }
        }

        val incoming = pendingImport
        if (incoming != null) {
            var name by remember(incoming) { mutableStateOf("Nuevo sticker") }
            var tags by remember(incoming) { mutableStateOf("") }
            var pack by remember(incoming) { mutableStateOf("General") }
            StickerEditor(
                title = "Crear sticker", name = name, tags = tags, pack = pack,
                onName = { name = it }, onTags = { tags = it }, onPack = { pack = it },
                onDismiss = { pendingImport = null },
                onSave = { importFile(incoming, name, tags, pack) }
            )
        }

        val current = editing
        if (current != null) {
            var name by remember(current.id) { mutableStateOf(current.name) }
            var tags by remember(current.id) { mutableStateOf(current.tags) }
            var pack by remember(current.id) { mutableStateOf(current.pack) }
            var confirmDelete by remember(current.id) { mutableStateOf(false) }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Eliminar sticker") },
                    text = { Text("Esta acción eliminará el GIF guardado.") },
                    confirmButton = { TextButton(onClick = {
                        store.remove(current); editing = null; refresh()
                    }) { Text("Eliminar") } },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
                )
            } else {
                StickerEditor(
                    title = "Editar sticker", name = name, tags = tags, pack = pack,
                    onName = { name = it }, onTags = { tags = it }, onPack = { pack = it },
                    onDismiss = { editing = null },
                    onSave = { store.update(current, name, tags, pack); editing = null; refresh() },
                    onDelete = { confirmDelete = true },
                    onReprocess = if (File(store.stickerFile(current).parentFile,
                        "${File(current.fileName).nameWithoutExtension}.source.gif").isFile)
                        ({ reprocessGif(current) }) else null
                )
            }
        }
    }
}

@Composable
private fun StickerEditor(
    title: String, name: String, tags: String, pack: String,
    onName: (String) -> Unit, onTags: (String) -> Unit, onPack: (String) -> Unit,
    onDismiss: () -> Unit, onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onReprocess: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, onName, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(tags, onTags,
                    label = { Text("Etiquetas (separadas por comas)") },
                    placeholder = { Text("bravo, amarillo, aviso") })
                OutlinedTextField(pack, onPack, label = { Text("Paquete") }, singleLine = true)
                Text("Al guardar se prepara un GIF para copiar a WhatsApp.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (onReprocess != null) TextButton(onClick = onReprocess) {
                    Text("Reprocesar GIF para WhatsApp")
                }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Eliminar sticker") }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCard(
    sticker: Sticker, file: File,
    onCopy: () -> Unit, onEdit: () -> Unit, onFavorite: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onCopy, onLongClick = onEdit)
    ) {
        Box(Modifier.fillMaxWidth()) {
            GifPreview(file, Modifier.fillMaxWidth().height(100.dp).padding(10.dp))
            IconButton(onClick = onFavorite,
                modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                Icon(if (sticker.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorito", tint = if (sticker.favorite) Accent else Muted,
                    modifier = Modifier.size(19.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 12.dp)) {
            Text(sticker.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            Text(sticker.tags.ifBlank { sticker.pack }, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                color = Muted)
        }
    }
}

@Composable
fun GifPreview(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                try {
                    val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
                    setImageDrawable(drawable)
                    (drawable as? AnimatedImageDrawable)?.start()
                } catch (_: Exception) {
                    setImageDrawable(null)
                }
            }
        }
    )
}
