package com.seniorlauncher.app.ui.screens.flashlight

import android.hardware.camera2.CameraCharacteristics
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.hardware.camera2.CameraManager
import com.seniorlauncher.app.R
import com.seniorlauncher.app.ui.components.AppBottomPrimaryButton
import com.seniorlauncher.app.ui.components.AppBottomSecondaryButton
import com.seniorlauncher.app.ui.components.AppSubScreen

@Composable
fun FlashlightScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isOn by remember { mutableStateOf(false) }

    fun toggleTorch(target: Boolean) {
        val success = setTorchEnabled(context, target)
        if (success) isOn = target
        else Toast.makeText(context, "No se pudo cambiar la linterna", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(Unit) {
        onDispose { setTorchEnabled(context, false) }
    }

    AppSubScreen(
        title = "Linterna",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flashlight),
                    contentDescription = null,
                    tint = if (isOn) Color(0xFFFFB300) else Color.Gray,
                    modifier = Modifier.size(180.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (isOn) "Encendida" else "Apagada",
                    color = Color.Black,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        bottomBar = {
            AppBottomPrimaryButton(
                text = if (isOn) "Apagar" else "Encender",
                icon = Icons.Default.CameraAlt,
                onClick = { toggleTorch(!isOn) }
            )
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = {
                    toggleTorch(false)
                    onBack()
                }
            )
        }
    )
}

private fun setTorchEnabled(context: android.content.Context, enabled: Boolean): Boolean {
    return runCatching {
        val cameraManager = context.getSystemService(CameraManager::class.java) ?: return false
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            runCatching {
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK
            }.getOrDefault(false)
        } ?: return false
        cameraManager.setTorchMode(cameraId, enabled)
        true
    }.getOrDefault(false)
}
