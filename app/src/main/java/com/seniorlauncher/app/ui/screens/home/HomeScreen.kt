package com.seniorlauncher.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.unit.Dp
import com.seniorlauncher.app.AllowedAppOption
import com.seniorlauncher.app.MINI_APP_CAMERA_ID
import com.seniorlauncher.app.MINI_APP_FLASHLIGHT_ID
import com.seniorlauncher.app.MINI_APP_GALLERY_ID
import com.seniorlauncher.app.MINI_APP_PHONE_ID
import com.seniorlauncher.app.MINI_APP_SMS_ID
import com.seniorlauncher.app.R
import com.seniorlauncher.app.service.AppsService
import com.seniorlauncher.app.ui.theme.MiniAppCameraColor
import com.seniorlauncher.app.ui.theme.MiniAppFlashlightColor
import com.seniorlauncher.app.ui.theme.MiniAppGalleryColor
import com.seniorlauncher.app.ui.theme.MiniAppPhoneColor
import com.seniorlauncher.app.ui.theme.MiniAppSmsColor
import com.seniorlauncher.app.service.NotificationBadgeStore

@Composable
fun HomeScreen(
    userName: String,
    greeting: String,
    showSosButton: Boolean,
    apps: List<AllowedAppOption>,
    isAppsReady: Boolean,
    onSettingsLongPress: () -> Unit,
    onSosClick: () -> Unit,
    onAppClick: (AllowedAppOption) -> Unit,
    unreadSmsCount: Int = 0,
    totalMissedCalls: Int = 0
) {
    BackHandler(enabled = true) { }

    var showSosConfirmDialog by remember { mutableStateOf(false) }
    val systemNotificationCounts by NotificationBadgeStore.counts.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = greeting, color = Color.White, fontSize = 16.sp)
                        Text(
                            text = userName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { onSettingsLongPress() })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Mantén pulsado para configuración",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }

        if (!isAppsReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 0.dp)
            ) {
                if (showSosButton) {
                    Button(
                        onClick = { showSosConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "EMERGENCIA",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (apps.isEmpty()) {
                    Text(
                        text = "No hay apps permitidas. Actívalas desde Configuración.",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = apps, key = { it.id }) { app ->
                            val badgeCount = when (app.id) {
                                MINI_APP_SMS_ID -> unreadSmsCount
                                MINI_APP_PHONE_ID -> totalMissedCalls
                                else -> app.packageName
                                    ?.let { pkg -> systemNotificationCounts[pkg] ?: 0 }
                                    ?: 0
                            }
                            AppGridItem(
                                app = app,
                                badgeCount = badgeCount,
                                onClick = { onAppClick(app) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSosConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSosConfirmDialog = false },
            title = { Text("Llamada de EMERGENCIA") },
            text = { Text("¿Quieres llamar ahora al número de emergencia?") },
            confirmButton = {
                Button(
                    onClick = { showSosConfirmDialog = false; onSosClick() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    )
                ) { Text("Llamar") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSosConfirmDialog = false }
                ) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun AppGridItem(
    app: AllowedAppOption,
    badgeCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(116.dp)) {
                    AppIcon(app = app, size = 116.dp)
                    if (badgeCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-8).dp)
                                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                color = MaterialTheme.colorScheme.onError,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = app.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
@Composable
internal fun AppIcon(app: AllowedAppOption, size: Dp) {
    when {
        app.isMiniApp -> {
            val vector = when (app.id) {
                MINI_APP_PHONE_ID -> Icons.Default.Call
                MINI_APP_CAMERA_ID -> Icons.Default.CameraAlt
                MINI_APP_GALLERY_ID -> Icons.Default.PhotoLibrary
                MINI_APP_SMS_ID -> Icons.Default.Message
                MINI_APP_FLASHLIGHT_ID -> Icons.Default.FlashlightOn
                else -> Icons.Default.Settings
            }
            val containerColor = when (app.id) {
                MINI_APP_PHONE_ID -> MiniAppPhoneColor
                MINI_APP_CAMERA_ID -> MiniAppCameraColor
                MINI_APP_GALLERY_ID -> MiniAppGalleryColor
                MINI_APP_SMS_ID -> MiniAppSmsColor
                MINI_APP_FLASHLIGHT_ID -> MiniAppFlashlightColor
                else -> MiniAppPhoneColor
            }
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(
                (containerColor.red * 255).toInt(),
                (containerColor.green * 255).toInt(),
                (containerColor.blue * 255).toInt(),
                hsv
            )
            val lightContainerColor = Color(
                android.graphics.Color.HSVToColor(
                    (containerColor.alpha * 255).toInt(),
                    floatArrayOf(
                        hsv[0],
                        (hsv[1] * 1.0f).coerceIn(0f, 1f),
                        (hsv[2] + 0.42f).coerceIn(0f, 1f)
                    )
                )
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(lightContainerColor, containerColor)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.48f)

                )
            }
        }
        !app.packageName.isNullOrBlank() -> {
            val context = LocalContext.current
            val density = LocalDensity.current
            val iconSizePx = with(density) { size.roundToPx() }
            Box(
                modifier = Modifier
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
                    .size(size)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                AndroidView(
                    factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                    update = { imageView ->
                    val drawable = runCatching {
                        context.packageManager.getApplicationIcon(app.packageName)
                    }.getOrNull()
                    imageView.setImageDrawable(
                        AppsService.toRoundedSquareAdaptiveDrawable(
                            resources = context.resources,
                            drawable = drawable,
                            sizePx = iconSizePx
                        )
                    )
                },
                modifier = Modifier.size(size)
            )
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
                    .size(size)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}
