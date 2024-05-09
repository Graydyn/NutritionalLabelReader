package com.graydyn.nutrionlabelreader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.graydyn.nutrionlabelreader.ui.theme.NutrionLabelReaderTheme
import com.graydyn.nutritionlib.NutritionReaderActivity

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val getLabelLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(TAG, result.data.toString())
            }
        }

        enableEdgeToEdge()
        setContent {
            NutrionLabelReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Button(
                        onClick = {
                            //startActivity(Intent(this, NutritionReaderActivity::class.java))
                            val intent = Intent(this, NutritionReaderActivity::class.java)
                            getLabelLauncher.launch(intent)

                        },
                        modifier = Modifier.padding(innerPadding),
                        content = {Text("Read Label")},
                    )
                }
            }
        }
    }
}
