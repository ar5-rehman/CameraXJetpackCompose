package ab.rehmann.camerax.ui.features.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import ab.rehmann.camerax.ui.features.camera.core.Constants
import ab.rehmann.camerax.ui.features.camera.core.Constants.CAMERA_RESOLUTION_HEIGHT
import ab.rehmann.camerax.ui.features.camera.core.Constants.CAMERA_RESOLUTION_WIDTH
import ab.rehmann.camerax.ui.features.camera.core.Constants.MLKIT_READER_MANAGER_TAG
import ab.rehmann.camerax.ui.features.camera.core.Constants.NO_BARCODE_RESULT
import ab.rehmann.camerax.ui.features.camera.core.state.FlashStatus
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.common.Barcode.BarcodeFormat
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraxManager(
    context: Context,
    cameraController: LifecycleCameraController,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    cameraSelectorType: Int? = CameraSelector.LENS_FACING_BACK,
    accuracyLevel: Int = Constants.BARCODE_ACCURACY_DEFAULT_COUNT
) {
    private var mContext = context
    private var mPreviewView = previewView
    private var mLifecycleOwner = lifecycleOwner
    private var mCameraController = cameraController
    private var accuracyCounter = 0
    private var tempBarcodeResult = NO_BARCODE_RESULT
    private var continueToRead = true
    private var mAccuracyLevel = accuracyLevel
    private lateinit var barcodeScannerOptions: BarcodeScannerOptions
    private var flashStatus = FlashStatus.DISABLED

    private var cameraResolutionWidth = CAMERA_RESOLUTION_WIDTH
    private var cameraResolutionHeight = CAMERA_RESOLUTION_HEIGHT

    private var mCameraSelectorType = cameraSelectorType
    private var cameraSelector: CameraSelector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var cameraExecutor: ExecutorService
    private val imageCaptureBuilder: ImageCapture

    //region Listeners
    private var qrReadSuccessListener: ((String) -> Unit)? = null
    fun setQrReadSuccessListener(listener: (String) -> Unit) {
        qrReadSuccessListener = listener
    }

    private var flashStatusChangedListener: ((FlashStatus) -> Unit)? = null
    fun setFlashStatusChangedListener(listener: (FlashStatus) -> Unit) {
        flashStatusChangedListener = listener
    }

    private var photoCaptureResultListener: ((Bitmap) -> Unit)? = null
    fun setPhotoCaptureResultListener(listener: (Bitmap) -> Unit) {
        photoCaptureResultListener = listener
    }

    private fun sendQrReaderSuccess(result: String) {
        qrReadSuccessListener?.let { qrReaderSuccess ->
            qrReaderSuccess(result)
        }
    }

    private fun sendFlashStatusChanged(flashStatus: FlashStatus) {
        flashStatusChangedListener?.let { flashStatusChanged ->
            flashStatusChanged(flashStatus)
        }
    }

    private fun sendPhotoCaptureResult(captureResult: Bitmap) {
        photoCaptureResultListener?.let { capturedPhotoBitmap ->
            capturedPhotoBitmap(captureResult)
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: CameraxManager? = null

        fun getInstance(
            context: Context,
            cameraController: LifecycleCameraController,
            lifecycleOwner: LifecycleOwner,
            previewView: PreviewView,
            cameraSelectorType: Int? = CameraSelector.LENS_FACING_BACK,
            accuracyLevel: Int = Constants.BARCODE_ACCURACY_DEFAULT_COUNT
        ) = INSTANCE
            ?: synchronized(this) {
                INSTANCE ?: CameraxManager(
                    context,
                    cameraController,
                    lifecycleOwner,
                    previewView,
                    cameraSelectorType,
                    accuracyLevel
                ).also {
                    INSTANCE = it
                }
            }
    }

    init {
        cameraExecutor = Executors.newSingleThreadExecutor()
        imageCaptureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    fun startCamera() {
        if (cameraExecutor.isShutdown || cameraExecutor.isTerminated) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
        addCameraProviderFeatureListener()
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        stopReading()
    }

    fun startReading() {
        continueToRead = true
    }

    fun stopReading() {
        continueToRead = false
    }

    fun destroyReferences() {
        stopReading()
        stopCamera()
        cameraExecutor.shutdown()
        barcodeScanner?.close()
        INSTANCE = null
    }


    fun changeCameraType() {
        when (mCameraSelectorType) {
            CameraSelector.LENS_FACING_BACK -> {
                mCameraSelectorType = CameraSelector.LENS_FACING_FRONT
            }

            CameraSelector.LENS_FACING_FRONT -> {
                mCameraSelectorType = CameraSelector.LENS_FACING_BACK
            }
        }
        addCameraProviderFeatureListener()
    }

    fun setReaderFormats(@BarcodeFormat vararg moreFormats: Int) {
        val firstBarcodeFormat = moreFormats[0]
        if (firstBarcodeFormat == Barcode.FORMAT_CODABAR && moreFormats.size == 1) {
            setReadingAccuracyLevel(1)
        }
        moreFormats.drop(1)
        barcodeScannerOptions = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                firstBarcodeFormat,
                *moreFormats
            ).build()
    }

    fun setReadingAccuracyLevel(accuracyLevel: Int) {
        if (accuracyLevel < 1) {
            mAccuracyLevel = 1
            return
        }
        if (accuracyLevel > 3) {
            mAccuracyLevel = 3
            return
        }
        mAccuracyLevel = accuracyLevel
    }

    fun setCameraResolution(width: Int, height: Int) {
        cameraResolutionWidth = width
        cameraResolutionHeight = height
    }

    fun changeFlashStatus() {
        flashStatus = when (flashStatus) {
            FlashStatus.ENABLED -> {
                mCameraController.enableTorch(false)
                FlashStatus.DISABLED
            }
            FlashStatus.DISABLED -> {
                mCameraController.enableTorch(true)
                FlashStatus.ENABLED
            }
        }
        sendFlashStatusChanged(flashStatus)
    }



    @SuppressLint("UnsafeOptInUsageError")
    fun processImageProxy(imageProxy: ImageProxy) {
        if (imageProxy.image != null) {
            val image: Image? = imageProxy.image
            barcodeScanner = BarcodeScanning.getClient(barcodeScannerOptions)

            if (image == null) return

            val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner?.process(inputImage)
                ?.addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val barcode = barcodes[0]
                        val barcodeResult = barcode.rawValue
                            ?: return@addOnSuccessListener
                        if (continueToRead) {
                            setAccurateBarcodeResult(barcodeResult)
                        }
                    }
                }?.addOnFailureListener {
                    Log.e(MLKIT_READER_MANAGER_TAG, it.stackTrace.toString())
                }
                ?.addOnCompleteListener {
                    imageProxy.image!!.close()
                    imageProxy.close()
                }
        }
    }

    private fun setAccurateBarcodeResult(
        barcodeResult: String
    ) {
        if (accuracyCounter >= mAccuracyLevel) {
            Log.w(
                MLKIT_READER_MANAGER_TAG,
                "consecutive reading SUCCESS...Barcode result: ${barcodeResult}]"
            )
            accuracyCounter = 0
            tempBarcodeResult = NO_BARCODE_RESULT
            continueToRead = false
            startReading()
            sendQrReaderSuccess(barcodeResult)
        } else {
            if (barcodeResult == tempBarcodeResult) {
                Log.w(
                    MLKIT_READER_MANAGER_TAG,
                    "times matched: ${accuracyCounter + 1}",
                )
                accuracyCounter++
            } else {
                Log.w(
                    MLKIT_READER_MANAGER_TAG,
                    """
                    different data received, temp data updated ...
                    Old Temp: $tempBarcodeResult
                    """.trimIndent() + "\n"
                            + "New Temp: " + barcodeResult
                )
                tempBarcodeResult = barcodeResult
                accuracyCounter = 0
            }
        }
    }

    private fun addCameraProviderFeatureListener() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(mContext)
        cameraProviderFuture?.addListener(Runnable {
            try {
                cameraProvider =
                    cameraProviderFuture?.get()
                cameraProvider?.let {
                    bindImageAnalysis(it)
                }
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(mContext))
    }

    private fun bindImageAnalysis(cameraProvider: ProcessCameraProvider) {
        val imageAnalysisUseCase = ImageAnalysis.Builder()
            .setTargetResolution(
                Size(
                    cameraResolutionWidth,
                    cameraResolutionHeight
                )
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysisUseCase.setAnalyzer(
            cameraExecutor
        ) { imageProxy: ImageProxy? ->
            imageProxy?.let {
                processImageProxy(
                    it
                )
            }
        }
        val orientationEventListener: OrientationEventListener =
            object : OrientationEventListener(mContext) {
                override fun onOrientationChanged(orientation: Int) {
                    val rotation: Int = when (orientation) {
                        in 45..134 -> Surface.ROTATION_270
                        in 135..224 -> Surface.ROTATION_180
                        in 225..314 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }
                    imageCaptureBuilder.targetRotation = rotation
                }
            }
        orientationEventListener.enable()
        val preview = Preview.Builder().build()
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(mCameraSelectorType!!)
            .build()
        preview.surfaceProvider = mPreviewView.surfaceProvider
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(mLifecycleOwner, cameraSelector!!,
            preview, imageAnalysisUseCase, imageCaptureBuilder
        )
    }

    suspend fun capturePhoto() {
        val photo = mPreviewView.bitmap
       // val fileUri = Uri.fromFile(imageCaptureBuilder.takePhoto(cameraExecutor))
        withContext(Dispatchers.IO){
            if (photo != null) {
                sendPhotoCaptureResult(
                    photo
                )
            }
        }

    }
}