package com.seniorlauncher.app.ui.screens.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoCameraBack
import androidx.compose.material.icons.filled.PhotoCameraFront
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.seniorlauncher.app.ui.components.AppBottomPrimaryButton
import com.seniorlauncher.app.ui.components.AppBottomSecondaryButton
import com.seniorlauncher.app.ui.components.AppSubScreen
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
enum class InternalCameraMode(
    val label: String,
    val isVideo: Boolean,
    val lensFacing: Int,
    val actionLabel: String
) {
    PHOTO("Foto", false, CameraSelector.LENS_FACING_BACK, "Hacer foto"),
    PHOTO_SELFIE("Foto selfie", false, CameraSelector.LENS_FACING_FRONT, "Hacer foto"),
    VIDEO("Video", true, CameraSelector.LENS_FACING_BACK, "Grabar video"),
    VIDEO_SELFIE("Video selfie", true, CameraSelector.LENS_FACING_FRONT, "Grabar video")
}

@Composable
fun InternalCameraModePickerScreen(
    onBack: () -> Unit,
    onModeSelected: (InternalCameraMode) -> Unit
) {
    AppSubScreen(
        title = "Selecciona modo de cámara",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { onModeSelected(InternalCameraMode.PHOTO) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PhotoCameraBack,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Foto",
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Button(
                        onClick = { onModeSelected(InternalCameraMode.PHOTO_SELFIE) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PhotoCameraFront,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Foto\nselfie",
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { onModeSelected(InternalCameraMode.VIDEO) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.VideoCameraBack,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Video",
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Button(
                        onClick = { onModeSelected(InternalCameraMode.VIDEO_SELFIE) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.VideoCameraFront,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Video\nselfie",
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
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
fun InternalCameraCaptureScreen(
    mode: InternalCameraMode,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val navLifecycleOwner = LocalLifecycleOwner.current
    val cameraLifecycleOwner = remember(context) { context as? androidx.lifecycle.LifecycleOwner }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true || hasCameraPermission
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true || hasAudioPermission
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var canUseFlash by remember { mutableStateOf(false) }
    var showCaptureFlash by remember { mutableStateOf(false) }
    var isSavingPhoto by remember { mutableStateOf(false) }
    val latestIsSavingPhoto by rememberUpdatedState(isSavingPhoto)
    val isDisposed = remember { AtomicBoolean(false) }
    val shutterSound = remember {
        MediaActionSound().apply {
            runCatching { load(MediaActionSound.SHUTTER_CLICK) }
        }
    }

    LaunchedEffect(showCaptureFlash) {
        if (showCaptureFlash) {
            delay(120)
            showCaptureFlash = false
        }
    }

    BackHandler(enabled = true) {
        recording?.stop()
        recording = null
        onBack()
    }

    DisposableEffect(mode, hasCameraPermission) {
        if (!hasCameraPermission) {
            onDispose {
                recording?.stop()
                recording = null
            }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val listener = Runnable {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val bindingOwner = cameraLifecycleOwner ?: navLifecycleOwner
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(mode.lensFacing)
                        .build()

                    cameraProvider.unbindAll()
                    if (mode.isVideo) {
                        val recorder = Recorder.Builder()
                            .setQualitySelector(
                                QualitySelector.from(
                                    Quality.HD,
                                    androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                                )
                            )
                            .build()
                        val capture = VideoCapture.withOutput(recorder)
                        videoCapture = capture
                        imageCapture = null
                        val camera = cameraProvider.bindToLifecycle(bindingOwner, selector, preview, capture)
                        canUseFlash = camera.cameraInfo.hasFlashUnit()
                    } else {
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                            .build()
                        imageCapture = capture
                        videoCapture = null
                        val camera = cameraProvider.bindToLifecycle(bindingOwner, selector, preview, capture)
                        canUseFlash = camera.cameraInfo.hasFlashUnit()
                    }
                }.onFailure {
                    Toast.makeText(context, "No se pudo iniciar la cámara", Toast.LENGTH_SHORT).show()
                }
            }
            cameraProviderFuture.addListener(listener, mainExecutor)

            onDispose {
                isDisposed.set(true)
                recording?.stop()
                recording = null
                canUseFlash = false
                runCatching { shutterSound.release() }
                if (!latestIsSavingPhoto) {
                    runCatching {
                        cameraProviderFuture.get().unbindAll()
                    }
                }
            }
        }
    }

    fun takePhoto() {
        if (isSavingPhoto) return
        val capture = imageCapture ?: return
        isSavingPhoto = true
        runCatching { shutterSound.play(MediaActionSound.SHUTTER_CLICK) }
        showCaptureFlash = true
        runCatching {
            capture.flashMode = if (
                canUseFlash && mode.lensFacing == CameraSelector.LENS_FACING_BACK
            ) {
                ImageCapture.FLASH_MODE_AUTO
            } else {
                ImageCapture.FLASH_MODE_OFF
            }
        }
        val fileName = "ELD_IMG_${System.currentTimeMillis()}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SeniorLauncher")
            }
        }
        val output = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        capture.takePicture(
            output,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isSavingPhoto = false
                    if (isDisposed.get()) {
                        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
                    }
                    Toast.makeText(context, "Foto guardada", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    isSavingPhoto = false
                    if (isDisposed.get()) {
                        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
                    }
                    if (exception.imageCaptureError != ImageCapture.ERROR_CAMERA_CLOSED) {
                        Toast.makeText(context, "Error al guardar foto", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    fun toggleVideoRecording() {
        val capture = videoCapture ?: return
        val activeRecording = recording
        if (activeRecording != null) {
            activeRecording.stop()
            recording = null
            return
        }

        val fileName = "ELD_VID_${System.currentTimeMillis()}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SeniorLauncher")
            }
        }
        val output = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(values)
            .build()

        var pending = capture.output.prepareRecording(context, output)
        if (hasAudioPermission) {
            pending = pending.withAudioEnabled()
        }

        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    Toast.makeText(context, "Grabando...", Toast.LENGTH_SHORT).show()
                }

                is VideoRecordEvent.Finalize -> {
                    recording = null
                    if (event.hasError()) {
                        Toast.makeText(context, "Error al grabar video", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Video guardado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    AppSubScreen(
        title = mode.label,
        content = {
            if (!hasCameraPermission) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Se necesitan permisos de cámara para continuar",
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (showCaptureFlash) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 0.85f }
                                .background(Color.White)
                        )
                    }
                }
            }
        },
        bottomBar = {
            AppBottomPrimaryButton(
                text = if (!hasCameraPermission) {
                    "Conceder permisos de cámara"
                } else if (mode.isVideo && recording != null) {
                    "Detener"
                } else if (isSavingPhoto) {
                    "Guardando..."
                } else {
                    mode.actionLabel
                },
                icon = Icons.Default.CameraAlt,
                onClick = {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                        return@AppBottomPrimaryButton
                    }
                    if (isSavingPhoto) return@AppBottomPrimaryButton
                    if (mode.isVideo) {
                        toggleVideoRecording()
                    } else {
                        takePhoto()
                    }
                }
            )
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = {
                    recording?.stop()
                    recording = null
                    onBack()
                }
            )
        }
    )
}
