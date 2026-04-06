package com.seniorlauncher.app.ui.screens.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLayoutDirection
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import com.seniorlauncher.app.ui.components.AppBottomPrimaryButton
import com.seniorlauncher.app.ui.components.AppBottomSecondaryButton
import com.seniorlauncher.app.ui.components.AppSubScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri

data class GalleryMediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val dateTakenMillis: Long
)

private val GALLERY_TILE_HEIGHT = 190.dp

@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onItemSelected: (GalleryMediaItem) -> Unit
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(hasGalleryPermission(context))
    }
    var isLoading by remember { mutableStateOf(true) }
    var mediaItems by remember { mutableStateOf<List<GalleryMediaItem>>(emptyList()) }
    var galleryVersion by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasGalleryPermission(context)
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissions)
        }

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
						if (!Environment.isExternalStorageManager()) {
								val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
										data = Uri.parse("package:${context.packageName}")
								}
								context.startActivity(intent)
						}
				}
    }

    LaunchedEffect(hasPermission, galleryVersion) {
        if (!hasPermission) {
            isLoading = false
            mediaItems = emptyList()
            return@LaunchedEffect
        }
        isLoading = true
        mediaItems = withContext(Dispatchers.IO) { loadGalleryTimeline(context) }
        isLoading = false
    }

    DisposableEffect(hasPermission) {
        if (!hasPermission) {
            onDispose { }
        } else {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    galleryVersion += 1
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    galleryVersion += 1
                }
            }

            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )

            onDispose {
                context.contentResolver.unregisterContentObserver(observer)
            }
        }
    }

    AppSubScreen(
        title = "Galería",
        content = {
            when {
                !hasPermission -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Permiso de galería requerido",
                            color = Color.Gray,
                            fontSize = 20.sp
                        )
                    }
                }

                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                mediaItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No hay fotos ni vídeos",
                            color = Color.Gray,
                            fontSize = 20.sp
                        )
                    }
                }

                else -> {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            reverseLayout = true,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)
                        ) {
                            items(mediaItems, key = { "${it.id}_${it.isVideo}" }) { item ->
                                GalleryGridItem(
                                    item = item,
                                    onClick = { onItemSelected(item) }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = onBack
            )
        }
    )
}

@Composable
private fun GalleryGridItem(
    item: GalleryMediaItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tileSizePx = with(density) { GALLERY_TILE_HEIGHT.roundToPx() }
    val model = remember(item.uri, item.isVideo, tileSizePx) {
        val builder = ImageRequest.Builder(context)
            .data(item.uri)
            .size(tileSizePx, tileSizePx)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .allowHardware(true)

        if (item.isVideo) {
            builder
                .decoderFactory(VideoFrameDecoder.Factory())
                .videoFrameMillis(1_000)
        }

        builder.build()
    }
    val dateText = remember(item.dateTakenMillis) { formatTimelineDate(item.dateTakenMillis) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GALLERY_TILE_HEIGHT)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEFEFEF))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low
        )

        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        Color.Black.copy(alpha = 0.55f),
                        CircleShape
                    )
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }

        Text(
            text = dateText,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 2.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun GalleryDetailScreen(
    item: GalleryMediaItem,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    AppSubScreen(
        title = if (item.isVideo) "Video" else "Foto",
        content = {
            if (item.isVideo) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(item.uri)
                            setOnPreparedListener { mediaPlayer ->
                                mediaPlayer.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        },
        bottomBar = {
            AppBottomPrimaryButton(
                text = if (isDeleting) "Borrando..." else "Borrar",
                icon = Icons.Default.Delete,
                onClick = {
                    if (!isDeleting) showDeleteDialog = true
                }
            )
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = onBack
            )
        }
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) showDeleteDialog = false
            },
            title = { Text("¿Borrar elemento?") },
            text = { Text("Esta acción eliminará este archivo de la galería.") },
            dismissButton = {
                Button(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting,
                    modifier = Modifier.height(64.dp)
                ) {
                    Text("Cancelar")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDeleting) return@Button
                        isDeleting = true
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                deleteGalleryMediaItem(context, item.uri)
                            }
                            isDeleting = false
                            showDeleteDialog = false
                            if (deleted) {
                                onDeleted()
                            }
                        }
                    },
                    enabled = !isDeleting,
                    modifier = Modifier.height(64.dp)
                ) {
                    Text(if (isDeleting) "Borrando..." else "Borrar")
                }
            }
        )
    }
}

private fun deleteGalleryMediaItem(context: Context, uri: Uri): Boolean {
    return runCatching {
        context.contentResolver.delete(uri, null, null) > 0
    }.getOrDefault(false)
}

private fun hasGalleryPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val img = context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED
        val vid = context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) ==
            PackageManager.PERMISSION_GRANTED
        img && vid
    } else {
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
}

private fun loadGalleryTimeline(context: Context): List<GalleryMediaItem> {
    val result = mutableListOf<GalleryMediaItem>()

    fun readCollection(collectionUri: Uri, isVideo: Boolean) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            collectionUri,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateTakenIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val dateAddedIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val dateTaken = cursor.getLong(dateTakenIdx)
                val dateAdded = cursor.getLong(dateAddedIdx) * 1000L
                val whenMillis = if (dateTaken > 0L) dateTaken else dateAdded
                val itemUri = ContentUris.withAppendedId(collectionUri, id)
                result.add(
                    GalleryMediaItem(
                        id = id,
                        uri = itemUri,
                        isVideo = isVideo,
                        dateTakenMillis = whenMillis
                    )
                )
            }
        }
    }

    readCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false)
    readCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)

    return result.sortedByDescending { it.dateTakenMillis }
}

private fun formatTimelineDate(millis: Long): String {
    return runCatching {
        SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(millis))
    }.getOrDefault("")
}
