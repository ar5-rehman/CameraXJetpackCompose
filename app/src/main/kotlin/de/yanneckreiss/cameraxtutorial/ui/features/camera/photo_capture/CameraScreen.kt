package de.yanneckreiss.cameraxtutorial.ui.features.camera.photo_capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.yanneckreiss.cameraxtutorial.ui.features.camera.CameraxManager
import de.yanneckreiss.cameraxtutorial.ui.features.camera.core.ReaderType
import de.yanneckreiss.cameraxtutorial.ui.features.camera.core.state.FlashStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = koinViewModel()
) {
    val cameraState: CameraState by viewModel.state.collectAsStateWithLifecycle()

    CameraContent(
        onPhotoCaptured = viewModel::storePhotoInGallery,
        lastCapturedPhoto = cameraState.capturedImage
    )
}

@Composable
private fun CameraContent(
    onPhotoCaptured: (Bitmap) -> Unit,
    lastCapturedPhoto: Bitmap? = null
) {
    val context: Context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val scope: CoroutineScope = rememberCoroutineScope()
    val cameraController = remember  { LifecycleCameraController(context) }

    var isEditScreen by rememberSaveable { mutableStateOf(false) }
    var flashStatus by rememberSaveable { mutableStateOf(FlashStatus.DISABLED) }
    var qrCodeResult by rememberSaveable { mutableStateOf<String?>(null) }
    var brightnessValue by rememberSaveable { mutableFloatStateOf(0f) }
    var cameraPreview: PreviewView? by remember { mutableStateOf(null) }

    val cameraManager = remember(cameraPreview) {
        cameraPreview?.let {
            CameraxManager.getInstance(
                cameraController = cameraController,
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = it,
            ).apply {
                if(cameraPreview != null){
                    startCamera()
                    setReaderFormats(
                        ReaderType.FORMAT_QR_CODE.value,
                        ReaderType.FORMAT_ALL_FORMATS.value,
                        ReaderType.FORMAT_EAN_13.value,
                        ReaderType.FORMAT_UPC_E.value,
                        ReaderType.FORMAT_UPC_A.value,
                        ReaderType.FORMAT_AZTEC.value
                    )
                    startReading()
                    setQrReadSuccessListener { result ->
                        qrCodeResult = result
                    }
                    setFlashStatusChangedListener { newFlashStatus ->
                        flashStatus = newFlashStatus
                    }
                    setPhotoCaptureResultListener { bitmap ->
                        onPhotoCaptured(bitmap)
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(text = "Take photo") },
                onClick = {
                    scope.launch {
                        cameraManager?.capturePhoto()
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "Camera capture icon"
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // PreviewView for CameraX
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                factory = { context ->
                    PreviewView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        cameraController.setPinchToZoomEnabled(true)

                        // Ensure scaleType is compatible with landscape/portrait
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        cameraPreview = this
                        this.controller = cameraController
                        cameraController.bindToLifecycle(lifecycleOwner)
                    }
                },
                update = {
                    cameraManager?.let {
                        cameraController.enableTorch(flashStatus == FlashStatus.ENABLED)
                        cameraController.cameraControl?.setExposureCompensationIndex(brightnessValue.toInt())
                    }
                }
            )
            // Flash mode toggle button
            Button(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                onClick = {
                    cameraManager?.changeFlashStatus()
                }
            ) {
                Text("Flash: ${if (flashStatus == FlashStatus.ENABLED) {
                    "On"
                } else {
                    "Off"
                }}")
            }
            // Brightness slider
            Slider(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .align(Alignment.TopCenter),
                value = brightnessValue,
                onValueChange = {
                    brightnessValue = it
                    cameraController.cameraControl?.setExposureCompensationIndex(it.toInt())
                },
                valueRange = -2f..2f
            )

            if (qrCodeResult != null) {
                Text(text = "QR Code Result: $qrCodeResult", modifier = Modifier.align(Alignment.Center))
            }

            if (lastCapturedPhoto != null) {
                LastPhotoPreview(
                    modifier = Modifier.align(alignment = BottomStart),
                    lastCapturedPhoto = lastCapturedPhoto,
                    onPicClicked = {
                        isEditScreen = true
                    }
                )
            }
        }
    }

    if(isEditScreen){
        if (lastCapturedPhoto != null) {
            EditPicScreen2(
                lastCapturedPhoto = lastCapturedPhoto,
                modifiedPicture = {
                    onPhotoCaptured(it)
                }
            )
        }
    }
}

@Composable
private fun LastPhotoPreview(
    modifier: Modifier = Modifier,
    lastCapturedPhoto: Bitmap,
    onPicClicked: () -> Unit
) {
    val capturedPhoto: ImageBitmap = remember(lastCapturedPhoto.hashCode()) { lastCapturedPhoto.asImageBitmap() }

    Card(
        modifier = modifier
            .size(128.dp)
            .padding(16.dp)
            .clickable {
                onPicClicked()
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Image(
            bitmap = capturedPhoto,
            contentDescription = "Last captured photo",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}