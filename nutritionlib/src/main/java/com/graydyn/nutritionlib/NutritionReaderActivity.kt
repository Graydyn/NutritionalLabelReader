package com.graydyn.nutritionlib

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.graydyn.nutritionlib.databinding.ActivityNutritionReaderBinding
import com.graydyn.nutritionlib.model.Macros
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NutritionReaderActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private val TAG = "NutritionReaderActivity"
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private lateinit var viewBinding: ActivityNutritionReaderBinding
    private var macros = Macros()
    private lateinit var ocrPassLogger: OcrPassLogger
    private val messageHandler = Handler(Looper.getMainLooper())
    private var proteinOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityNutritionReaderBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        proteinOnly = intent.getBooleanExtra(EXTRA_PROTEIN_ONLY, false)
        if (proteinOnly) {
            viewBinding.statusFat.visibility = View.GONE
            viewBinding.statusCarbs.visibility = View.GONE
        }

        if (OCR_LOGGING_ENABLED) ocrPassLogger = OcrPassLogger(this)
        updateProgressUI(Macros())

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    private fun returnResult(macros: Macros){
        val intent = Intent()
        intent.putExtra("ActivityResult", macros)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
               .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, TextAnalyzer ())
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateProgressUI(macros: Macros) {
        runOnUiThread {
            fun bind(view: TextView, label: String, value: Int) {
                if (value == -1) {
                    view.text = "○  $label"
                    view.setTextColor(Color.parseColor("#80FFFFFF"))
                } else {
                    view.text = "✓  $label: $value"
                    view.setTextColor(Color.parseColor("#FF4CAF50"))
                }
            }
            bind(viewBinding.statusCalories, "Calories", macros.calories)
            bind(viewBinding.statusFat,      "Fat",      macros.fat)
            bind(viewBinding.statusCarbs,    "Carbs",    macros.carbs)
            bind(viewBinding.statusProtein,  "Protein",  macros.protein)
        }
    }

    private fun showValidationMessage(text: String) {
        runOnUiThread {
            viewBinding.statusMessage.text = text
            viewBinding.statusMessage.visibility = View.VISIBLE
            messageHandler.removeCallbacksAndMessages(null)
            messageHandler.postDelayed({
                viewBinding.statusMessage.visibility = View.GONE
            }, 2000)
        }
    }

    // Protein: 4 cal/g, carbs: 4 cal/g, fat: 9 cal/g.
    // Accepts up to 20% deviation to account for rounding and fiber differences.
    private fun isCalorieConsistent(macros: Macros): Boolean {
        val expected = macros.fat * 9 + macros.carbs * 4 + macros.protein * 4
        val allowance = macros.calories * 0.20
        val passes = Math.abs(macros.calories - expected) <= allowance
        if (!passes) {
            Log.d(TAG, "Calorie check failed: detected=${macros.calories}, " +
                    "calculated=$expected (fat=${macros.fat}*9 + carbs=${macros.carbs}*4 + protein=${macros.protein}*4)")
        }
        return passes
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        messageHandler.removeCallbacksAndMessages(null)
        if (::ocrPassLogger.isInitialized) ocrPassLogger.close()
    }

    companion object {
        const val EXTRA_PROTEIN_ONLY = "protein_only"

        private const val TAG = "CameraXApp"
        private const val OCR_LOGGING_ENABLED = false
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private inner class TextAnalyzer : ImageAnalysis.Analyzer {
        private val recognizer: TextRecognizer  = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val blocks: List<Text.TextBlock> = visionText.getTextBlocks()
                        //with each analysed image, we add to our macros object
                        //that way we dont need to capture every macro in one frame
                        //it makes it easier to deal with things like glare
                        val (newMacros, passData) = TextBlocksInterpreter.read(blocks, macros)
                        macros = newMacros
                        if (OCR_LOGGING_ENABLED) ocrPassLogger.log(passData)
                        updateProgressUI(macros)
                        Log.d(TAG, macros.toString())
                        if (macros.isComplete(proteinOnly)) {
                            if (proteinOnly || isCalorieConsistent(macros)) {
                                returnResult(macros)
                            } else {
                                showValidationMessage("Validation failed, rescanning...")
                                macros = Macros()
                            }
                        }
                        imageProxy.close()
                        mediaImage.close()
                    }

                    .addOnFailureListener { e ->
                        Log.e(TAG, e.message.toString())
                        imageProxy.close()
                        mediaImage.close()
                    }
            }
        }
    }
}


