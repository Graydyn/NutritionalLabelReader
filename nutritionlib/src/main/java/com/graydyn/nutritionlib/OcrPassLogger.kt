package com.graydyn.nutritionlib

import android.content.Context
import android.util.Log
import com.graydyn.nutritionlib.model.OcrPassData
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

class OcrPassLogger(context: Context) {
    private val TAG = "OcrPassLogger"
    private val writer: BufferedWriter

    init {
        val file = File(context.getExternalFilesDir(null), "ocr_passes.ndjson")
        writer = BufferedWriter(FileWriter(file, false)) // false = overwrite each session
        Log.i(TAG, "OCR pass log: ${file.absolutePath}")
    }

    fun log(passData: OcrPassData) {
        try {
            writer.write(serialize(passData).toString())
            writer.newLine()
            writer.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write OCR pass", e)
        }
    }

    fun close() {
        try { writer.close() } catch (e: Exception) { /* ignore on shutdown */ }
    }

    private fun serialize(data: OcrPassData): JSONObject {
        val root = JSONObject()

        val rawLinesArr = JSONArray()
        for (line in data.rawLines) {
            val lineObj = JSONObject().put("text", line.text)
            val bb = line.boundingBox
            lineObj.put("boundingBox", if (bb != null)
                JSONObject().put("left", bb.left).put("top", bb.top)
                    .put("right", bb.right).put("bottom", bb.bottom)
            else JSONObject.NULL)
            rawLinesArr.put(lineObj)
        }
        root.put("rawLines", rawLinesArr)

        val rowGroupsArr = JSONArray()
        for (group in data.rowGroups) {
            val arr = JSONArray()
            group.forEach { arr.put(it) }
            rowGroupsArr.put(arr)
        }
        root.put("rowGroups", rowGroupsArr)

        val rowTextsArr = JSONArray()
        data.rowTexts.forEach { rowTextsArr.put(it) }
        root.put("rowTexts", rowTextsArr)

        val detectionsArr = JSONArray()
        for (det in data.detections) {
            detectionsArr.put(
                JSONObject()
                    .put("macro", det.macro)
                    .put("value", det.value)
                    .put("fromLine", det.fromLine)
            )
        }
        root.put("detections", detectionsArr)

        root.put("accumulatedMacros", JSONObject()
            .put("calories", data.accumulatedMacros.calories)
            .put("fat", data.accumulatedMacros.fat)
            .put("protein", data.accumulatedMacros.protein)
            .put("carbs", data.accumulatedMacros.carbs)
            .put("gramsPerServing", data.accumulatedMacros.gramsPerServing))

        return root
    }
}
