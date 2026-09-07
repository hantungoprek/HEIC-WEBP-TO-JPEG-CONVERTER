package com.pixconverter

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Teal = Color(0xFF49C4A9)
private val DarkSurface = Color(0xFF414340)

data class SelectedImage(val uri: Uri, val name: String)

data class HistoryItem(
    val inputName: String,
    val outputName: String,
    val timestamp: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PixConverterApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixConverterApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var darkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", true)) }
    var selected by remember { mutableStateOf<List<SelectedImage>>(emptyList()) }
    var history by remember { mutableStateOf(HistoryStore.load(context)) }
    var converting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val list = uris.map { uri ->
                val name = DisplayNameResolver.getName(context.contentResolver, uri)
                SelectedImage(uri, name ?: "image")
            }
            selected = list
            resultText = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            picker.launch(arrayOf("image/heic", "image/heif", "image/webp", "image/*"))
        } else {
            Toast.makeText(context, "Storage permission diperlukan di Android 8/8.1.", Toast.LENGTH_LONG).show()
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) {
            androidx.compose.material3.darkColorScheme(
                primary = Teal,
                secondary = Teal,
                surface = DarkSurface,
                background = Color(0xFF202120)
            )
        } else {
            androidx.compose.material3.lightColorScheme(
                primary = Color(0xFF008B72),
                secondary = Color(0xFF008B72)
            )
        }
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(top = 36.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(android.R.drawable.ic_menu_gallery),
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                "HEIC WEBP to JPEG",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        NavigationDrawerItem(
                            label = { Text("Dark Theme") },
                            selected = darkTheme,
                            onClick = {
                                darkTheme = !darkTheme
                                prefs.edit().putBoolean("dark_theme", darkTheme).apply()
                            },
                            icon = {
                                Icon(
                                    painterResource(android.R.drawable.ic_menu_manage),
                                    contentDescription = null
                                )
                            },
                            badge = {
                                Switch(
                                    checked = darkTheme,
                                    onCheckedChange = {
                                        darkTheme = it
                                        prefs.edit().putBoolean("dark_theme", it).apply()
                                    }
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "v1.0.2",
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("HEIC WEBP to JPEG", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                }
            ) { padding ->
                MainContent(
                    modifier = Modifier.padding(padding),
                    selected = selected,
                    converting = converting,
                    progress = progress,
                    resultText = resultText,
                    historyCount = history.size,
                    onAdd = {
                        if (Build.VERSION.SDK_INT <= 28 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            picker.launch(arrayOf("image/heic", "image/heif", "image/webp", "image/*"))
                        }
                    },
                    onRemove = { image ->
                        selected = selected.filterNot { it.uri == image.uri }
                    },
                    onClear = { selected = emptyList() },
                    onConvert = {
                        if (selected.isEmpty() || converting) return@MainContent
                        converting = true
                        progress = 0f
                        resultText = null
                        scope.launch {
                            val result = Converter.convertAll(context, selected) { done, total ->
                                progress = done.toFloat() / total.toFloat()
                            }
                            history = HistoryStore.load(context)
                            converting = false
                            progress = 1f
                            resultText = "${result.success} berhasil • ${result.failed} gagal"
                            if (result.success > 0) selected = emptyList()
                        }
                    },
                    history = history,
                    onClearHistory = {
                        HistoryStore.clear(context)
                        history = emptyList()
                    }
                )
            }
        }
    }
}

@Composable
private fun MainContent(
    modifier: Modifier,
    selected: List<SelectedImage>,
    converting: Boolean,
    progress: Float,
    resultText: String?,
    historyCount: Int,
    onAdd: () -> Unit,
    onRemove: (SelectedImage) -> Unit,
    onClear: () -> Unit,
    onConvert: () -> Unit,
    history: List<HistoryItem>,
    onClearHistory: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (selected.isEmpty()) 300.dp else 220.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected.isEmpty()) {
                    Button(
                        onClick = onAdd,
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(58.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("ADD IMAGE")
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${selected.size} image${if (selected.size == 1) "" else "s"} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        selected.take(4).forEach {
                            Text(
                                it.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (selected.size > 4) {
                            Text("+ ${selected.size - 4} more…")
                        }
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onAdd,
                        modifier = Modifier.weight(1f)
                    ) { Text("ADD MORE") }
                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.weight(1f)
                    ) { Text("CLEAR") }
                }
            }
            item {
                Button(
                    onClick = onConvert,
                    enabled = !converting,
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text(if (converting) "CONVERTING…" else "CONVERT TO JPEG")
                }
            }
        }

        if (converting) {
            item {
                Column {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Teal,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${(progress * 100).toInt()}%")
                }
            }
        }

        if (resultText != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(android.R.drawable.checkbox_on_background),
                        contentDescription = null,
                        tint = Teal
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(resultText)
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(android.R.drawable.ic_menu_recent_history),
                    contentDescription = null,
                    tint = Teal
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "HISTORY ($historyCount)",
                    color = Teal,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (history.isNotEmpty()) {
            items(history.take(20)) { item ->
                HistoryRow(item)
            }
            item {
                Text(
                    "Clear history",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { onClearHistory() }.padding(8.dp)
                )
            }
        } else {
            item {
                Text(
                    "Belum ada konversi.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem) {
    val date = remember(item.timestamp) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.inputName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "→ ${item.outputName}",
                color = Teal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(date, style = MaterialTheme.typography.bodySmall)
        }
    }
}

object DisplayNameResolver {
    fun getName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        return uri.lastPathSegment
    }
}

data class ConvertResult(val success: Int, val failed: Int)

object Converter {
    suspend fun convertAll(
        context: Context,
        images: List<SelectedImage>,
        onProgress: (Int, Int) -> Unit
    ): ConvertResult = withContext(Dispatchers.IO) {
        var success = 0
        var failed = 0

        images.forEachIndexed { index, image ->
            try {
                convertOne(context, image)
                success++
            } catch (_: Exception) {
                failed++
            }
            withContext(Dispatchers.Main) {
                onProgress(index + 1, images.size)
            }
        }
        ConvertResult(success, failed)
    }

    private fun convertOne(context: Context, image: SelectedImage) {
        val resolver = context.contentResolver
        val bitmap = decode(resolver, image.uri)
        val oriented = applyExifOrientation(resolver, image.uri, bitmap)
        val base = image.name.substringBeforeLast('.', image.name)
            .ifBlank { "converted_image" }
        val fileName = uniqueJpegName(resolver, base)
        saveJpeg(context, fileName, oriented)
        if (oriented !== bitmap) bitmap.recycle()
        oriented.recycle()
        HistoryStore.add(context, HistoryItem(image.name, fileName, System.currentTimeMillis()))
    }

    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open image" }
                BitmapFactory.decodeStream(input)
                    ?: throw IllegalArgumentException("Unsupported or corrupt image")
            }
        }
    }

    private fun applyExifOrientation(
        resolver: ContentResolver,
        uri: Uri,
        bitmap: Bitmap
    ): Bitmap {
        return try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bitmap, 270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flip(bitmap, true, false)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> flip(bitmap, false, true)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        val a = android.graphics.Matrix().apply {
                            setRotate(90f)
                            postScale(-1f, 1f)
                        }
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, a, true)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        val a = android.graphics.Matrix().apply {
                            setRotate(270f)
                            postScale(-1f, 1f)
                        }
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, a, true)
                    }
                    else -> bitmap
                }
            } ?: bitmap
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun flip(bitmap: Bitmap, x: Boolean, y: Boolean): Bitmap {
        val matrix = android.graphics.Matrix().apply { postScale(if (x) -1f else 1f, if (y) -1f else 1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun uniqueJpegName(resolver: ContentResolver, base: String): String {
        val clean = base.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        var candidate = "$clean.jpg"
        var i = 1

        if (Build.VERSION.SDK_INT >= 29) {
            while (resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
                    arrayOf(candidate, Environment.DIRECTORY_PICTURES + "/PixConverter/"),
                    null
                )?.use { it.moveToFirst() } == true) {
                candidate = "$clean ($i).jpg"
                i++
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "PixConverter"
            )
            while (File(dir, candidate).exists()) {
                candidate = "$clean ($i).jpg"
                i++
            }
        }
        return candidate
    }

    private fun saveJpeg(context: Context, fileName: String, bitmap: Bitmap) {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/PixConverter"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw IllegalStateException("Cannot create output file")

            try {
                resolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output)
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                        throw IllegalStateException("JPEG compression failed")
                    }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "PixConverter"
            )
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("Cannot create Pictures/PixConverter")
            }
            val file = File(dir, fileName)
            FileOutputStream(file).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                    throw IllegalStateException("JPEG compression failed")
                }
            }
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
        }
    }
}

object HistoryStore {
    private const val PREF = "history"
    private const val KEY = "items"

    fun load(context: Context): List<HistoryItem> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        HistoryItem(
                            o.getString("input"),
                            o.getString("output"),
                            o.getLong("time")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, item: HistoryItem) {
        val items = (listOf(item) + load(context)).take(100)
        val array = org.json.JSONArray()
        items.forEach {
            array.put(
                org.json.JSONObject().apply {
                    put("input", it.inputName)
                    put("output", it.outputName)
                    put("time", it.timestamp)
                }
            )
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
