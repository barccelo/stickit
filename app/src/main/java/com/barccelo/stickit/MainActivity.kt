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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors

private val Ink = Color(0xFF17191D)
private val Mist = Color(0xFFF4F5F7)
private val Accent = Color(0xFF495BCE)

class MainActivity : ComponentActivity() {
    private lateinit var store: StickerStore
    private val worker = Executors.newSingleThreadExecutor()
    private var pendingImport by mutableStateOf<Uri?>(null)
    private var editing by mutableStateOf<Sticker?>(null)
    private var entries by mutableStateOf<List<Sticker>>(emptyList())
    private var busy by mutableStateOf(false)
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
                    background = Color.White,
                    surface = Color.White,
                    onSurface = Ink,
                    surfaceVariant = Mist
                )
            ) {
                Screen()
            }
        }
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
            notify("Selector flotante activado")
        }
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

        Column(Modifier.fillMaxSize().background(Color.White)) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 14.dp)) {
                Text("Stickit", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text("Tus stickers, donde los necesitas", color = Color.Gray)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("image/*", "video/*")) }, enabled = !busy) {
                        Text("Importar")
                    }
                    OutlinedButton(onClick = { fromClipboard() }, enabled = !busy) {
                        Text("Portapapeles")
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { enableOverlay() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) { Text("Activar selector flotante") }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Buscar etiquetas, texto o paquetes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
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
                    Text(
                        if (entries.isEmpty()) "Importa una imagen, un GIF o un vídeo para comenzar."
                        else "No hay stickers que coincidan con esta búsqueda.",
                        color = Color.Gray
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { sticker ->
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
                    onDelete = { confirmDelete = true }
                )
            }
        }
    }
}

@Composable
private fun StickerEditor(
    title: String, name: String, tags: String, pack: String,
    onName: (String) -> Unit, onTags: (String) -> Unit, onPack: (String) -> Unit,
    onDismiss: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)? = null
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
        colors = CardDefaults.cardColors(containerColor = Mist),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onCopy, onLongClick = onEdit)
    ) {
        Box(Modifier.fillMaxWidth()) {
            GifPreview(file, Modifier.fillMaxWidth().height(100.dp).padding(10.dp))
            TextButton(onClick = onFavorite,
                modifier = Modifier.align(Alignment.TopEnd).height(36.dp)) {
                Text(if (sticker.favorite) "★" else "☆", color = Accent)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 12.dp)) {
            Text(sticker.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            Text(sticker.tags.ifBlank { sticker.pack }, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                color = Color.Gray)
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
