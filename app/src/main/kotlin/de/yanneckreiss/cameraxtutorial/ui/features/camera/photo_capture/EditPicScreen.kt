package de.yanneckreiss.cameraxtutorial.ui.features.camera.photo_capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun EditPicScreen(
    lastCapturedPhoto: Bitmap,
    modifiedPicture: (Bitmap) -> Unit
) {

    var brightness by remember { mutableFloatStateOf(1f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    val aspectRatio = lastCapturedPhoto.width.toFloat() / lastCapturedPhoto.height.toFloat()

    // Keep track of the image history
    var imageQueue by remember { mutableStateOf(listOf(lastCapturedPhoto)) }

    var updatedPhoto by remember { mutableStateOf<Bitmap?>(null) }

    // Automatically update image queue whenever adjustments are made
    LaunchedEffect(brightness, contrast, saturation, rotation) {
        updatedPhoto = adjustImage(lastCapturedPhoto, brightness, contrast, saturation).let {
            rotateAndCropBitmap(it, rotation, aspectRatio)
        }
        // Add the updated photo to the queue
        imageQueue = (imageQueue + updatedPhoto!!).toMutableList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Use Box to layer images
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Loop through all images in the queue and layer them
            imageQueue.forEachIndexed { index, bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Edited photo $index",
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    contentScale = androidx.compose.ui.layout.ContentScale.None
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Brightness Slider
        Text(text = "Brightness: ${((brightness - 1f) * 100).roundToInt()}%")
        Slider(
            value = brightness,
            onValueChange = { brightness = it },
            valueRange = 0.5f..2f,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Contrast Slider
        Text(text = "Contrast: ${((contrast - 1f) * 100).roundToInt()}%")
        Slider(
            value = contrast,
            onValueChange = { contrast = it },
            valueRange = 0.5f..2f,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Saturation Slider
        Text(text = "Saturation: ${((saturation - 1f) * 100).roundToInt()}%")
        Slider(
            value = saturation,
            onValueChange = { saturation = it },
            valueRange = 0f..2f,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Rotation Buttons
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { rotation -= 10f }) {
                Text("Rotate Left")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { rotation += 10f }) {
                Text("Rotate Right")
            }
        }

        Button(onClick = {
            if(updatedPhoto != null){
                modifiedPicture(updatedPhoto!!)
            }

        }) {
            Text("Save")
        }
    }
}

// Function to adjust brightness, contrast, and saturation
fun adjustImage(bitmap: Bitmap, brightness: Float, contrast: Float, saturation: Float): Bitmap {
    val colorMatrix = ColorMatrix().apply {
        // Apply brightness
        val brightnessMatrix = ColorMatrix().apply {
            setScale(brightness, brightness, brightness, 1f)
        }
        postConcat(brightnessMatrix)

        // Apply contrast
        val translate = (-0.5f * contrast + 0.5f) * 255
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        postConcat(contrastMatrix)

        // Apply saturation
        val saturationMatrix = ColorMatrix().apply {
            setSaturation(saturation)
        }
        postConcat(saturationMatrix)
    }
    return applyColorMatrix(bitmap, colorMatrix)
}

// Helper function to apply a ColorMatrix to a Bitmap
private fun applyColorMatrix(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
    val canvas = Canvas(result)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(colorMatrix)
    }
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return result
}

fun rotateAndCropBitmap(bitmap: Bitmap, rotation: Float, aspectRatio: Float): Bitmap {
    // Create a matrix to apply the rotation
    val matrix = Matrix().apply {
        postRotate(rotation)
    }

    // Rotate the bitmap using the matrix
    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

    // Get the rotated width and height
    val rotatedWidth = rotatedBitmap.width
    val rotatedHeight = rotatedBitmap.height

    // Now calculate the dimensions to crop the image based on aspect ratio
    val newHeight = (rotatedWidth / aspectRatio).toInt()
    val newWidth = rotatedWidth

    // Ensure that the crop height doesn't exceed the rotated bitmap's height
    val cropHeight = newHeight.coerceAtMost(rotatedHeight)

    // Calculate the Y-position to center the crop
    val cropY = ((rotatedHeight - cropHeight) / 2).coerceAtLeast(0)

    // Ensure the X-position is valid
    val cropX = 0

    // Crop the bitmap within the new dimensions
    val finalBitmap = Bitmap.createBitmap(rotatedBitmap, cropX, cropY, newWidth, cropHeight)

    // If the final bitmap size is smaller than the original, adjust accordingly
    return finalBitmap
}