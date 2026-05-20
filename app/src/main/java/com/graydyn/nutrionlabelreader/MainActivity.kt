package com.graydyn.nutrionlabelreader

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.graydyn.nutrionlabelreader.ui.theme.NutrionLabelReaderTheme
import com.graydyn.nutritionlib.NutritionReaderActivity
import com.graydyn.nutritionlib.model.Macros

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var macros by mutableStateOf<Macros?>(null)

        val getLabelLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                macros = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getSerializableExtra("ActivityResult", Macros::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getSerializableExtra("ActivityResult") as? Macros
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            NutrionLabelReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(this@MainActivity, NutritionReaderActivity::class.java)
                                getLabelLauncher.launch(intent)
                            },
                            content = { Text("Read Label") }
                        )

                        macros?.let { m ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("OCR Results", fontSize = 20.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Calories: ${if (m.calories != -1f) formatMacro(m.calories) else "not found"}")
                            Text("Fat: ${if (m.fat != -1f) "${formatMacro(m.fat)}g" else "not found"}")
                            Text("Carbs: ${if (m.carbs != -1f) "${formatMacro(m.carbs)}g" else "not found"}")
                            Text("Protein: ${if (m.protein != -1f) "${formatMacro(m.protein)}g" else "not found"}")
                        }
                    }
                }
            }
        }
    }
}

private fun formatMacro(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString()
    else "%.1f".format(value)
