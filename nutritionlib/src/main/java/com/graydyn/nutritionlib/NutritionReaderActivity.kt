package com.graydyn.nutritionlib

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.willowtreeapps.fuzzywuzzy.diffutils.FuzzySearch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NutritionReaderActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private val TAG = "NutritionReaderActivity"
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private lateinit var viewBinding: ActivityNutritionReaderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityNutritionReaderBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
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
                var macros = Macros()
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val blocks: List<Text.TextBlock> = visionText.getTextBlocks()
                        for (block in blocks){
                            macros = readTextBlock(block, macros)
                        }

                        if (macros.isComplete()){
                            returnResult(macros)
                        }
                        imageProxy.close()
                        mediaImage.close()
                    }

                    .addOnFailureListener { e ->
                        Log.e(TAG,e.message.toString())
                    }
            }
        }

        fun readTextBlock(textBlock: Text.TextBlock, macros: Macros) : Macros{
            for (line in textBlock.lines) {
                //usually each macro is on its own line, but sometimes they're all on one line so we need to accomodate that
                val foundItems = ArrayList<String>()
                for (element in line.elements) {
                    if (FuzzySearch.ratio("Calories", element.text) > 90){
                        foundItems.add("calories")
                        Log.d(TAG,"Found something like calories " + element.text)
                    }
                    if (FuzzySearch.ratio("Protein", element.text) > 90){
                        foundItems.add("protein")
                        Log.d(TAG,"Found something like protein " + element.text)
                    }
                    if (FuzzySearch.ratio("Fat", element.text) > 90){
                        foundItems.add("fat")
                        Log.d(TAG,"Found something like fat " + element.text)
                    }
                    if ((FuzzySearch.ratio("Carb", element.text) > 90) || (FuzzySearch.ratio("Carbohydrate", element.text) > 90)){
                        foundItems.add("carbs")
                        Log.d(TAG,"Found something like carbs " + element.text)
                    }
                }
                if (foundItems.size == 1){  //one macro per line of text
                    for (element in line.elements) {
                        val number =element.text.toString().filter { it.isDigit() }
                        if (number != "") {
                            Log.d(TAG, number)
                            macros.protein = number.toInt()
                        }
                    }

                }

            }
            return macros;
        }
    }
}


