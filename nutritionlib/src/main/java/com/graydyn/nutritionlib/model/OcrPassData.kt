package com.graydyn.nutritionlib.model

import android.graphics.Rect

data class RawOcrLine(val text: String, val boundingBox: Rect?)

data class MacroDetection(val macro: String, val value: Float, val fromLine: String)

data class OcrPassData(
    val rawLines: List<RawOcrLine>,       // all lines sorted top-to-bottom before row grouping
    val rowGroups: List<List<String>>,    // each spatial row as a list of element texts
    val rowTexts: List<String>,           // joined row strings fed to macro detection
    val detections: List<MacroDetection>, // macros newly found this frame (empty if none)
    val accumulatedMacros: Macros         // snapshot of Macros state after this frame
)
