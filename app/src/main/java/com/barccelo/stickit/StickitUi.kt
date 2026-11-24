package com.barccelo.stickit

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

private val Ink = Color(0xFF1D2233)
private val Indigo = Color(0xFF4855B2)
private val Secondary = Color(0xFF80889A)
private val Background = Color(0xFFFAFAFC)
private val Light = Color(0xFFF1F2F8)
private val Stroke = Color(0xFFE8EAF2)

@Composable
fun StickerPreview(file: File, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                try {
                    val image = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
                    setImageDrawable(image)
                    (image as? AnimatedImageDrawable)?.start()
                } catch (_: Exception) { setImageDrawable(null) }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTile(
    sticker: Sticker,
    file: File,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFFEDEFFC) else Color.White,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp,
            if (selected) Indigo else Stroke),
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(Modifier.padding(9.dp)) {
            Box(
                Modifier.fillMaxWidth().height(110.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Light),
                contentAlignment = Alignment.Center
            ) {
                StickerPreview(file, Modifier.fillMaxSize().padding(5.dp))
                if (selected) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Seleccionado",
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(23.dp),
                        tint = Indigo)
                } else if (selecting) {
                    Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = "No seleccionado",
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(21.dp),
                        tint = Secondary)
                }
                if (!selecting) {
                    IconButton(onClick = onFavorite,
                        modifier = Modifier.align(Alignment.TopEnd).size(33.dp)) {
                        Icon(if (sticker.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (sticker.favorite) "Quitar favorito" else "Fijar favorito",
                            tint = if (sticker.favorite) Color(0xFFCE9A39) else Secondary,
                            modifier = Modifier.size(19.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sticker.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f), fontSize = 12.sp,
                    lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                if (!selecting) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "Editar sticker",
                            tint = Secondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(listOf(sticker.emojis, sticker.tags.ifBlank { sticker.pack })
                .filter { it.isNotBlank() }.joinToString(" · "), color = Secondary, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun StickitHome(
    stickers: List<Sticker>,
    fileOf: (Sticker) -> File,
    previewVersion: Int,
    busy: Boolean,
    overlayEnabled: Boolean,
    shortcutEnabled: Boolean,
    shortcutPhrases: List<String>,
    emojiSuggestions: Boolean,
    pickerHeight: Int,
    onEnableShortcuts: () -> Unit,
    onAddShortcut: (String) -> Unit,
    onRemoveShortcut: (String) -> Unit,
    onToggleSuggestions: (Boolean) -> Unit,
    onChangePickerHeight: (Int) -> Unit,
    onShowPicker: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onPick: () -> Unit,
    onPaste: () -> Unit,
    onZip: () -> Unit,
    onFolder: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onCopy: (Sticker) -> Unit,
    onEdit: (Sticker) -> Unit,
    onFavorite: (Sticker) -> Unit,
    onBulkEdit: (Set<Long>, String?, String?, Boolean?) -> Unit,
    onBulkDelete: (Set<Long>) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("Todos") }
    var importMenu by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var bulkDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val packs = remember(stickers) { stickers.map { it.pack }.distinct().sorted() }
    val filters = listOf("Todos", "Recientes", "Favoritos") +
        packs.map { "Paquete: $it" }
    val shown = remember(stickers, search, activeFilter) {
        stickers.filter { it.matches(search) }.filter {
            when {
                activeFilter == "Favoritos" -> it.favorite
                activeFilter.startsWith("Paquete: ") -> it.pack ==
                    activeFilter.removePrefix("Paquete: ")
                else -> true
            }
        }.let {
            if (activeFilter == "Recientes") it.sortedByDescending { item -> item.lastUsed }
            else it
        }
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(color = Color.White, shadowElevation = 12.dp) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${selected.size} seleccionados", fontWeight = FontWeight.SemiBold,
                                color = Ink, modifier = Modifier.weight(1f))
                            TextButton(onClick = { selected.clear() }) { Text("Cancelar") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { bulkDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(13.dp)) { Text("Editar lote", fontSize = 12.sp) }
                            OutlinedButton(onClick = { deleteDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(13.dp)) {
                                Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Eliminar", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                NavigationBar(containerColor = Color.White, tonalElevation = 2.dp) {
                    listOf(
                        Triple("Biblioteca", Icons.Outlined.Collections, 0),
                        Triple("Paquetes", Icons.Outlined.Folder, 1),
                        Triple("Ajustes", Icons.Outlined.Tune, 2)
                    ).forEach { (name, icon, index) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index; selected.clear() },
                            icon = { Icon(icon, contentDescription = name) },
                            label = { Text(name, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Indigo, indicatorColor = Light,
                                selectedTextColor = Indigo, unselectedIconColor = Secondary)
                        )
                    }
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("stickit", color = Ink, fontWeight = FontWeight.Bold,
                        letterSpacing = (-1f).sp, style = MaterialTheme.typography.headlineMedium)
                    Text(when (tab) {
                        1 -> "Organiza tus colecciones"
                        2 -> "Personaliza y protege tu biblioteca"
                        else -> "Tus stickers, siempre a mano"
                    }, color = Secondary, fontSize = 12.sp)
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(23.dp),
                    strokeWidth = 2.dp)
                else if (tab == 0) {
                    Box {
                        FilledTonalIconButton(
                            onClick = { importMenu = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Light, contentColor = Indigo)
                        ) { Icon(Icons.Outlined.Add, contentDescription = "Importar") }
                        DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Fotos, GIF y vídeos") },
                                leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null) },
                                onClick = { importMenu = false; onPick() })
                            DropdownMenuItem(
                                text = { Text("Desde portapapeles") },
                                leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                                onClick = { importMenu = false; onPaste() })
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Importar paquete ZIP") },
                                leadingIcon = { Icon(Icons.Outlined.Inventory2, null) },
                                onClick = { importMenu = false; onZip() })
                            DropdownMenuItem(
                                text = { Text("Importar carpeta") },
                                leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                                onClick = { importMenu = false; onFolder() })
                        }
                    }
                }
            }
            when (tab) {
                0 -> {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Surface(shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Stroke),
                            color = Color.White) {
                            OutlinedTextField(value = search, onValueChange = { search = it },
                                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Secondary) },
                                placeholder = { Text("Buscar nombres, caras o etiquetas",
                                    color = Secondary, fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(17.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = Color(0xFFA7AFE9),
                                    unfocusedBorderColor = Color.Transparent))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            filters.forEach { filter ->
                                FilterChip(selected = activeFilter == filter,
                                    onClick = { activeFilter = filter; selected.clear() },
                                    label = { Text(filter, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Light, selectedLabelColor = Indigo),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true, selected = activeFilter == filter,
                                        borderColor = Stroke, selectedBorderColor = Indigo),
                                    shape = RoundedCornerShape(12.dp))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selected.isEmpty()) "Mis stickers" else "Seleccionar stickers",
                                color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                modifier = Modifier.weight(1f))
                            Text("${shown.size} stickers", color = Secondary, fontSize = 12.sp)
                            if (selected.isNotEmpty()) {
                                TextButton(onClick = {
                                    if (selected.size == shown.size) selected.clear()
                                    else { selected.clear(); selected.addAll(shown.map { it.id }) }
                                }) { Text("Todos") }
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(color = Light, shape = RoundedCornerShape(24.dp)) {
                                    Icon(Icons.Outlined.Collections, null, tint = Indigo,
                                        modifier = Modifier.padding(22.dp).size(37.dp))
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(if (stickers.isEmpty()) "Tu colección comienza aquí"
                                    else "Sin coincidencias", color = Ink, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Text(if (stickers.isEmpty()) "Importa imágenes o comparte stickers desde WhatsApp."
                                    else "Prueba con otra búsqueda o etiqueta.",
                                    color = Secondary, textAlign = TextAlign.Center,
                                    fontSize = 13.sp)
                                if (stickers.isEmpty()) {
                                    Spacer(Modifier.height(14.dp))
                                    Button(onClick = onPick, shape = RoundedCornerShape(13.dp)) {
                                        Text("Importar mis primeros stickers")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            items(shown, key = { "${it.id}:$previewVersion" }) { sticker ->
                                val marked = sticker.id in selected
                                StickerTile(sticker, fileOf(sticker), marked, selected.isNotEmpty(),
                                    onClick = {
                                        if (selected.isEmpty()) onCopy(sticker)
                                        else if (marked) selected.remove(sticker.id)
                                        else selected.add(sticker.id)
                                    },
                                    onLongClick = {
                                        if (marked) selected.remove(sticker.id)
                                        else selected.add(sticker.id)
                                    },
                                    onEdit = { onEdit(sticker) },
                                    onFavorite = { onFavorite(sticker) })
                            }
                        }
                    }
                }
                1 -> {
                    if (packs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Los paquetes aparecerán cuando importes stickers", color = Secondary)
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(18.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            lazyItems(packs) { pack ->
                                val list = stickers.filter { it.pack == pack }
                                Surface(onClick = {
                                    activeFilter = "Paquete: $pack"; tab = 0
                                }, color = Color.White, shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, Stroke)) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(62.dp).clip(RoundedCornerShape(14.dp))
                                            .background(Light), contentAlignment = Alignment.Center) {
                                            if (list.isNotEmpty()) StickerPreview(fileOf(list.first()),
                                                Modifier.fillMaxSize().padding(5.dp))
                                        }
                                        Spacer(Modifier.width(13.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(pack, fontWeight = FontWeight.SemiBold, color = Ink)
                                            Text("${list.size} stickers", color = Secondary, fontSize = 12.sp)
                                        }
                                        Icon(Icons.Outlined.ChevronRight, null, tint = Secondary)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            SettingsPanel("Acceso rápido", "Controla cuándo se muestra Stickit") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Layers, null, tint = Indigo)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Selector flotante", color = Ink, fontWeight = FontWeight.Medium)
                                        Text(if (overlayEnabled) "Activo sobre otras aplicaciones" else "Desactivado",
                                            color = Secondary, fontSize = 12.sp)
                                    }
                                    Switch(checked = overlayEnabled, onCheckedChange = onToggleOverlay)
                                }
                            }
                        }
                        item {
                            SettingsPanel("Atajos y sugerencias",
                                "Abre el selector escribiendo un código o encuentra stickers mediante emojis") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Keyboard, null, tint = Indigo)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Detección de atajos", fontWeight = FontWeight.SemiBold,
                                            color = Ink, fontSize = 13.sp)
                                        Text(if (shortcutEnabled) "Habilitada en WhatsApp"
                                            else "Requiere activación manual",
                                            color = Secondary, fontSize = 11.sp)
                                    }
                                    if (shortcutEnabled) Icon(Icons.Filled.CheckCircle,
                                        contentDescription = "Activado", tint = Indigo)
                                }
                                OutlinedButton(onClick = onEnableShortcuts,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(13.dp)) {
                                    Text(if (shortcutEnabled) "Configurar permiso de accesibilidad"
                                        else "Habilitar atajos en Android")
                                }
                                Text("Se revisan únicamente los finales de los mensajes que escribes en WhatsApp y WhatsApp Business. No se guardan mensajes ni contraseñas.",
                                    color = Secondary, fontSize = 11.sp, lineHeight = 16.sp)
                                Text("Mis atajos", color = Ink, fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp)
                                var newShortcut by remember { mutableStateOf("") }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    shortcutPhrases.forEach { phrase ->
                                        InputChip(selected = false,
                                            onClick = { onRemoveShortcut(phrase) },
                                            label = { Text(phrase, fontSize = 12.sp) },
                                            trailingIcon = {
                                                Icon(Icons.Outlined.Close,
                                                    contentDescription = "Quitar atajo $phrase",
                                                    modifier = Modifier.size(15.dp))
                                            },
                                            shape = RoundedCornerShape(50))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = newShortcut,
                                        onValueChange = { newShortcut = it.take(20) },
                                        label = { Text("Nuevo atajo") },
                                        placeholder = { Text("STK, PEG, /stick") },
                                        singleLine = true, modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(13.dp))
                                    FilledIconButton(
                                        enabled = newShortcut.trim().length >= 2 &&
                                            shortcutPhrases.size < 20,
                                        onClick = {
                                            onAddShortcut(newShortcut)
                                            newShortcut = ""
                                        }
                                    ) {
                                        Icon(Icons.Outlined.Add, contentDescription = "Añadir atajo")
                                    }
                                }
                                Text("Los códigos se detectan al final de una palabra; puedes tener varios. El texto escrito permanece en el chat.",
                                    color = Secondary, fontSize = 11.sp, lineHeight = 16.sp)
                                HorizontalDivider(color = Stroke)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Sugerencias por palabras y emojis", color = Ink,
                                            fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text("Coincidencia con nombres, etiquetas y emojis",
                                            color = Secondary, fontSize = 11.sp)
                                    }
                                    Switch(checked = emojiSuggestions, onCheckedChange = onToggleSuggestions)
                                }
                                HorizontalDivider(color = Stroke)
                                Text("Altura del selector · $pickerHeight dp", color = Ink,
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Slider(value = pickerHeight.toFloat(),
                                    onValueChange = { onChangePickerHeight(it.toInt()) },
                                    valueRange = 230f..540f)
                                Text("También podrás cambiar su tamaño arrastrando el borde superior. Ocupa el ancho del teclado y desaparece después de copiar.",
                                    color = Secondary, fontSize = 11.sp, lineHeight = 16.sp)
                                OutlinedButton(onClick = onShowPicker,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(13.dp)) {
                                    Icon(Icons.Outlined.OpenInFull, null,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Abrir selector de prueba")
                                }
                            }
                        }
                        item {
                            SettingsPanel("Mis colecciones", "Importa paquetes que hayas descargado o exportado") {
                                OutlinedButton(onClick = onZip,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                    Icon(Icons.Outlined.Archive, null, modifier = Modifier.size(19.dp))
                                    Spacer(Modifier.width(8.dp)); Text("Importar paquete ZIP")
                                }
                                OutlinedButton(onClick = onFolder,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                    Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(19.dp))
                                    Spacer(Modifier.width(8.dp)); Text("Importar carpeta de stickers")
                                }
                                Text("En WhatsApp: selecciona un sticker, pulsa Compartir y elige Stickit.",
                                    color = Secondary, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                        item {
                            SettingsPanel("Copia de seguridad", "Respaldo local que incluye imágenes, GIF, etiquetas, favoritos y paquetes") {
                                Button(onClick = onExportBackup,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                    Icon(Icons.Outlined.SaveAlt, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp)); Text("Guardar respaldo")
                                }
                                OutlinedButton(onClick = onImportBackup,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                    Icon(Icons.Outlined.Restore, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp)); Text("Restaurar respaldo")
                                }
                                Text("El respaldo se guarda donde tú elijas. Al restaurar, se añaden los que falten sin borrar tu biblioteca.",
                                    color = Secondary, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                        item {
                            Text("Stickit · Biblioteca local, sin cuenta ni sincronización en la nube.",
                                color = Secondary, fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    if (bulkDialog) {
        var tags by remember { mutableStateOf("") }
        var pack by remember { mutableStateOf("") }
        var markFavorite by remember { mutableStateOf(false) }
        var applyFavorite by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { bulkDialog = false },
            title = { Text("Editar ${selected.size} stickers") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = tags, onValueChange = { tags = it },
                        label = { Text("Añadir etiquetas") },
                        placeholder = { Text("feliz, sorpresa, amigo") })
                    OutlinedTextField(value = pack, onValueChange = { pack = it },
                        label = { Text("Mover a paquete (opcional)") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applyFavorite, onCheckedChange = { applyFavorite = it })
                        Text("Cambiar favoritos", fontSize = 13.sp)
                        Spacer(Modifier.width(5.dp))
                        if (applyFavorite) Switch(checked = markFavorite,
                            onCheckedChange = { markFavorite = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onBulkEdit(selected.toSet(), pack.takeIf { it.isNotBlank() },
                        tags.takeIf { it.isNotBlank() },
                        if (applyFavorite) markFavorite else null)
                    selected.clear(); bulkDialog = false
                }) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { bulkDialog = false }) { Text("Cancelar") } })
    }

    if (deleteDialog) {
        AlertDialog(onDismissRequest = { deleteDialog = false },
            title = { Text("¿Eliminar ${selected.size} stickers?") },
            text = { Text("Se eliminarán de Stickit. Los paquetes originales en WhatsApp no se modificarán.") },
            confirmButton = {
                TextButton(onClick = {
                    onBulkDelete(selected.toSet()); selected.clear(); deleteDialog = false
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun SettingsPanel(title: String, subtitle: String,
    children: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(21.dp),
        border = BorderStroke(1.dp, Stroke)) {
        Column(Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Secondary, fontSize = 12.sp, lineHeight = 17.sp)
            HorizontalDivider(color = Stroke)
            children()
        }
    }
}

/**
 * Compact removable capsules. The input is committed as one or more tokens
 * when the user taps +; editing never alters another sticker.
 */
@Composable
fun CapsuleEditor(title: String, tokens: String, onChange: (String) -> Unit,
    placeholder: String) {
    var draft by remember { mutableStateOf("") }
    val chips = tokens.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    fun commit() {
        val additions = draft.split(",").map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 32 }
        if (additions.isNotEmpty()) {
            onChange((chips + additions).distinctBy(String::lowercase).joinToString(", "))
            draft = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                chips.forEach { chip ->
                    InputChip(selected = false,
                        onClick = { onChange(chips.filterNot { it == chip }.joinToString(", ")) },
                        label = { Text(chip, fontSize = 12.sp) },
                        trailingIcon = {
                            Icon(Icons.Outlined.Close, contentDescription = "Quitar $chip",
                                modifier = Modifier.size(14.dp))
                        },
                        shape = RoundedCornerShape(50))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = {
                draft = it.take(90)
                if (draft.endsWith(",")) commit()
            },
                placeholder = { Text(placeholder, fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(13.dp))
            FilledIconButton(onClick = { commit() }, enabled = draft.isNotBlank()) {
                Icon(Icons.Outlined.Add, contentDescription = "Añadir $title")
            }
        }
    }
}

@Composable
fun StickerMetadataDialog(sticker: Sticker, canReprocess: Boolean, canUndo: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    onArtwork: () -> Unit, onReprocess: () -> Unit, onUndo: () -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(sticker.id) { mutableStateOf(sticker.name) }
    var tags by remember(sticker.id) { mutableStateOf(sticker.tags) }
    var emojis by remember(sticker.id) { mutableStateOf(sticker.emojis) }
    var pack by remember(sticker.id) { mutableStateOf(sticker.pack) }
    var deletion by remember { mutableStateOf(false) }
    if (deletion) {
        AlertDialog(onDismissRequest = { deletion = false },
            title = { Text("Eliminar sticker") },
            text = { Text("Se eliminará de Stickit, incluidos sus archivos locales.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deletion = false }) { Text("Cancelar") } })
    } else {
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text("Editar sticker") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        singleLine = true, label = { Text("Nombre") })
                    CapsuleEditor("Etiquetas", tags, { tags = it },
                        "Ej.: bravo, meme, amarillo")
                    CapsuleEditor("Emojis asociados", emojis, { emojis = it },
                        "Ej.: 😂, 😡, ❤️")
                    OutlinedTextField(value = pack, onValueChange = { pack = it },
                        singleLine = true, label = { Text("Paquete") })
                    OutlinedButton(onClick = onArtwork, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AutoFixHigh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Editar imagen y animación")
                    }
                    if (canUndo) {
                        TextButton(onClick = onUndo) { Text("Deshacer edición de imagen") }
                    }
                    if (canReprocess) {
                        TextButton(onClick = onReprocess) { Text("Reprocesar GIF para WhatsApp") }
                    }
                    TextButton(onClick = { deletion = true }) {
                        Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("Eliminar sticker")
                    }
                }
            },
            confirmButton = { Button(onClick = { onSave(name, tags, pack, emojis) }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
    }
}

@Composable
fun ImportStickersDialog(count: Int, suggestedName: String,
    onDismiss: () -> Unit, onImport: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf(suggestedName) }
    var tags by remember { mutableStateOf("") }
    var emojis by remember { mutableStateOf("") }
    var pack by remember { mutableStateOf("General") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Guardar sticker" else "Importar $count stickers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (count == 1) OutlinedTextField(name, { name = it },
                    label = { Text("Nombre") }, singleLine = true)
                CapsuleEditor("Etiquetas", tags, { tags = it }, "Ej.: risa, reacción")
                CapsuleEditor("Emojis", emojis, { emojis = it }, "Ej.: 😂, ❤️")
                OutlinedTextField(pack, { pack = it },
                    label = { Text("Paquete") }, singleLine = true)
                if (count > 1) Text("Se utilizará el nombre de cada archivo.",
                    color = Secondary, fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = { onImport(name, tags, pack, emojis) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun StickerArtworkDialog(file: File, hasUndo: Boolean,
    onDismiss: () -> Unit,
    onSave: (ArtworkEdits) -> Unit,
    onUndo: () -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var zoom by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableIntStateOf(0) }
    var mirror by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Editar sticker") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(210.dp).clip(RoundedCornerShape(18.dp))
                    .background(Light), contentAlignment = Alignment.Center) {
                    StickerPreview(file,
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom * (if (mirror) -1f else 1f)
                            scaleY = zoom
                            rotationZ = rotation.toFloat()
                        })
                    if (caption.isNotBlank()) {
                        Text(caption, color = Color.White, fontWeight = FontWeight.Bold,
                            fontSize = 15.sp, maxLines = 3, textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(8.dp).background(Color(0xB0000000),
                                    RoundedCornerShape(8.dp)).padding(8.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { rotation = (rotation + 90) % 360 },
                        shape = RoundedCornerShape(13.dp)) {
                        Icon(Icons.Outlined.RotateRight, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("Girar")
                    }
                    OutlinedButton(onClick = { mirror = !mirror },
                        shape = RoundedCornerShape(13.dp)) {
                        Icon(Icons.Outlined.Flip, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("Espejo")
                    }
                }
                Text("Recortar / ampliar · ${(zoom * 100).toInt()}%",
                    color = Secondary, fontSize = 12.sp)
                Slider(value = zoom, onValueChange = { zoom = it },
                    valueRange = 1f..2.3f)
                OutlinedTextField(value = caption, onValueChange = { caption = it.take(100) },
                    label = { Text("Añadir texto (opcional)") },
                    maxLines = 2)
                if (hasUndo) TextButton(onClick = onUndo) {
                    Text("Restaurar versión anterior")
                }
                Text("La edición se aplicará a todos los fotogramas.",
                    color = Secondary, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(ArtworkEdits(caption, zoom, rotation, mirror)) }) {
                Text("Guardar cambios")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun PackNameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("Colección importada") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Importar paquete ZIP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Los stickers del archivo se agruparán en el mismo paquete.",
                    color = Secondary, fontSize = 13.sp)
                OutlinedTextField(name, onValueChange = { name = it },
                    label = { Text("Nombre del paquete") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name) }) { Text("Importar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
