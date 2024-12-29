package de.yanneckreiss.cameraxtutorial.ui.features.camera.photo_capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.moyuru.cropify.Cropify
import io.moyuru.cropify.rememberCropifyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun EditPicScreen2(
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

    // Apply adjustments and save to queue
    var updatedPhoto by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(brightness, contrast, saturation, rotation) {
        withContext(Dispatchers.IO) {
            val adjustedBitmap = adjustImage2(lastCapturedPhoto, brightness, contrast, saturation)
            val transformedBitmap = rotateAndCropBitmap2(adjustedBitmap, rotation)
            updatedPhoto = transformedBitmap
            imageQueue = (imageQueue + transformedBitmap).toMutableList() // Save to queue
        }
    }

    val cropifyState = rememberCropifyState()

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
                    contentScale = ContentScale.None
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
            cropifyState.crop()

        }) {
            Text("Save")
        }
    }
}

// Function to adjust brightness, contrast, and saturation
private fun adjustImage2(bitmap: Bitmap, brightness: Float, contrast: Float, saturation: Float): Bitmap {
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
    return applyColorMatrix2(bitmap, colorMatrix)
}

// Helper function to apply a ColorMatrix to a Bitmap
private fun applyColorMatrix2(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
    val canvas = Canvas(result)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(colorMatrix)
    }
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return result
}

private fun rotateAndCropBitmap2(bitmap: Bitmap, rotation: Float): Bitmap {
    // Create a matrix to apply the rotation
    val matrix = Matrix().apply {
        postRotate(rotation)
    }

    // Rotate the bitmap using the matrix
    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

    // Get the rotated width and height
    val rotatedWidth = rotatedBitmap.width
    val rotatedHeight = rotatedBitmap.height

    // Calculate the bounds of the original bitmap
    val originalWidth = bitmap.width.toFloat()
    val originalHeight = bitmap.height.toFloat()

    // Calculate the top-left and bottom-right corners of the rotated bitmap
    val rotatedCorners = listOf(
        Offset(0f, 0f), // Top-Left
        Offset(rotatedWidth.toFloat(), 0f), // Top-Right
        Offset(rotatedWidth.toFloat(), rotatedHeight.toFloat()), // Bottom-Right
        Offset(0f, rotatedHeight.toFloat())  // Bottom-Left
    )
    val center = Offset(rotatedWidth/2f, rotatedHeight/2f)
    // Transform the corners according to the rotation
    val transformedCorners = rotatedCorners.map {
        val x =  it.x  - center.x
        val y =  it.y - center.y
        val rotatedX = x * kotlin.math.cos(Math.toRadians(rotation.toDouble())).toFloat() - y * kotlin.math.sin(Math.toRadians(rotation.toDouble())).toFloat()
        val rotatedY = x * kotlin.math.sin(Math.toRadians(rotation.toDouble())).toFloat() + y * kotlin.math.cos(Math.toRadians(rotation.toDouble())).toFloat()
        Offset(rotatedX + center.x, rotatedY + center.y)

    }

    // Calculate the max and min x and y values of the rotated bitmap
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    transformedCorners.forEach { point ->
        minX = minOf(minX, point.x)
        minY = minOf(minY, point.y)
        maxX = maxOf(maxX, point.x)
        maxY = maxOf(maxY, point.y)
    }
    // Calculate the crop dimensions
    val cropX =  -minX.coerceAtLeast(0f)
    val cropY = -minY.coerceAtLeast(0f)
    val cropWidth = (maxX-minX).coerceAtMost(originalWidth).toInt()
    val cropHeight = (maxY-minY).coerceAtMost(originalHeight).toInt()

    val finalBitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(finalBitmap)
    val paint = Paint()
    canvas.drawBitmap(rotatedBitmap, -cropX, -cropY, paint)

    return finalBitmap
}

fun ImageBitmap.toBitmap(): Bitmap {
    return this.asAndroidBitmap() // Converts ImageBitmap to Android Bitmap
}

fun Bitmap.toImageBitmap(): ImageBitmap {
    return this.asImageBitmap()
}