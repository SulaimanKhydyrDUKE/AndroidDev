// FaceDetector

package com.example.faceid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.faceid.ui.theme.FaceIDTheme
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private val isCameraPermissionGranted = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isCameraPermissionGranted.value = true
        }
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                isCameraPermissionGranted.value = true
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    companion object {
        const val TAG = "FaceIDApp"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestCameraPermission()

        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV initialized successfully")
        } else {
            Log.e(TAG, "OpenCV initialization failed")
        }

        setContent {
            FaceIDTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isCameraPermissionGranted.value) {
                        CameraWithHeadPose(modifier = Modifier.padding(innerPadding))
                    } else {
                        Text(text = "Camera permission is required to use this app")
                    }
                }
            }
        }
    }
}

// Head pose angles data class
data class HeadPoseAngles(
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f
)

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraWithHeadPose(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var headPoseAngles by remember { mutableStateOf(HeadPoseAngles()) }

    val faceLandmarker = remember {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .build()

            val options = FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setOutputFaceBlendshapes(false)
                .setOutputFacialTransformationMatrixes(false)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .build()

            FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "Error creating FaceLandmarker: Please make sure 'face_landmarker.task' is in the assets folder.", e)
            null
        }
    }

    val faceModel3D = remember {
        MatOfPoint3f(
            Point3(285.0, 528.0, 200.0),
            Point3(285.0, 371.0, 152.0),
            Point3(197.0, 574.0, 128.0),
            Point3(173.0, 425.0, 108.0),
            Point3(360.0, 574.0, 128.0),
            Point3(391.0, 425.0, 108.0)
        )
    }

    fun rotationMatrixToAngles(rotationMatrix: Mat): FloatArray {
        val m = rotationMatrix
        val x = atan2(m.get(2, 1)[0], m.get(2, 2)[0])
        val y = atan2(-m.get(2, 0)[0], sqrt(m.get(0, 0)[0] * m.get(0, 0)[0] + m.get(1, 0)[0] * m.get(1, 0)[0]))
        val z = atan2(m.get(1, 0)[0], m.get(0, 0)[0])

        return floatArrayOf(
            (x * 180 / PI).toFloat(),
            (y * 180 / PI).toFloat(),
            (z * 180 / PI).toFloat()
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                val previewView = PreviewView(viewContext)
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build() // Use default YUV format

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        if (faceLandmarker == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mpImage = imageProxy.toMPImage()
                        val result = faceLandmarker.detect(mpImage)

                        if (result.faceLandmarks().isNotEmpty()) {
                            val faceLandmarks = result.faceLandmarks()[0]
                            val landmarkIndices = intArrayOf(1, 9, 57, 130, 287, 359)
                            val imagePoints = mutableListOf<Point>()

                            val imageWidth = imageProxy.width.toFloat()
                            val imageHeight = imageProxy.height.toFloat()

                            for (idx in landmarkIndices) {
                                if (idx < faceLandmarks.size) {
                                    val landmark = faceLandmarks[idx]
                                    imagePoints.add(Point(landmark.x() * imageWidth.toDouble(), landmark.y() * imageHeight.toDouble()))
                                }
                            }

                            if (imagePoints.size == 6) {
                                val imagePointsMat = MatOfPoint2f(*imagePoints.toTypedArray())
                                val focalLength = imageWidth.toDouble()

                                // Correctly initialize the camera matrix, element by element
                                val cameraMatrix = Mat(3, 3, CvType.CV_64FC1)
                                cameraMatrix.put(0, 0, focalLength)
                                cameraMatrix.put(0, 1, 0.0)
                                cameraMatrix.put(0, 2, imageWidth / 2.0)
                                cameraMatrix.put(1, 0, 0.0)
                                cameraMatrix.put(1, 1, focalLength)
                                cameraMatrix.put(1, 2, imageHeight / 2.0)
                                cameraMatrix.put(2, 0, 0.0)
                                cameraMatrix.put(2, 1, 0.0)
                                cameraMatrix.put(2, 2, 1.0)

                                val distCoeffs = MatOfDouble(0.0, 0.0, 0.0, 0.0)
                                val rotationVec = Mat()
                                val translationVec = Mat()

                                val success = Calib3d.solvePnP(
                                    faceModel3D,
                                    imagePointsMat,
                                    cameraMatrix,
                                    distCoeffs,
                                    rotationVec,
                                    translationVec
                                )

                                if (success) {
                                    val rotationMatrix = Mat()
                                    Calib3d.Rodrigues(rotationVec, rotationMatrix)
                                    val angles = rotationMatrixToAngles(rotationMatrix)

                                    headPoseAngles = HeadPoseAngles(
                                        pitch = angles[0],
                                        yaw = angles[1],
                                        roll = angles[2]
                                    )

                                    rotationMatrix.release()
                                }

                                // Release all Mats
                                imagePointsMat.release()
                                cameraMatrix.release()
                                distCoeffs.release()
                                rotationVec.release()
                                translationVec.release()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(MainActivity.TAG, "Error processing image: ${e.message}", e)
                    } finally {
                        imageProxy.close()
                    }
                }

                // --- ROBUST CAMERA BINDING WITH FALLBACK ---
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e(MainActivity.TAG, "Front camera binding failed, falling back to back camera", e)
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e2: Exception) {
                        Log.e(MainActivity.TAG, "FATAL: Camera binding failed for both front and back cameras", e2)
                    }
                }

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay to display head pose angles
        Column(
            modifier = Modifier
                .padding(16.dp)
                .background(Color.Green.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            Text(
                text = "Pitch: ${headPoseAngles.pitch.toInt()}°",
                color = Color.White,
                fontSize = 20.sp
            )
            Text(
                text = "YaRRRw: ${headPoseAngles.yaw.toInt()}°",
                color = Color.White,
                fontSize = 20.sp
            )
            Text(
                text = "Roll: ${headPoseAngles.roll.toInt()}°",
                color = Color.White,
                fontSize = 20.sp
            )
        }
    }
}

@ExperimentalGetImage
fun ImageProxy.toMPImage(): MPImage {
    val bitmap = this.toBitmap() // Use the built-in toBitmap() for simplicity and safety
    return BitmapImageBuilder(bitmap).build()
}

@ComposePreview(showBackground = true)
@Composable
fun GreetingPreview() {
    FaceIDTheme {
        //CameraWithHeadPose() // This would crash the preview
    }
}
