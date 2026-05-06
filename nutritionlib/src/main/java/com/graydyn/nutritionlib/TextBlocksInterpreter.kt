package com.graydyn.nutritionlib

import android.util.Log
import com.google.mlkit.vision.text.Text
import com.graydyn.nutritionlib.model.MacroDetection
import com.graydyn.nutritionlib.model.Macros
import com.graydyn.nutritionlib.model.OcrPassData
import com.graydyn.nutritionlib.model.RawOcrLine

/**
 * The TextRecognizer returns a series of blocks of text.
 * Lines are grouped spatially into rows by vertical proximity, then sorted
 * left-to-right within each row before searching for macro values.
 */
class TextBlocksInterpreter {
    companion object {
        private val TAG = "TextBlocksInterpreter"

        fun read(blocks: List<Text.TextBlock>, oldMacros: Macros): Pair<Macros, OcrPassData> {
            val lines = blocks.flatMap { it.lines }

            // Sort top-to-bottom. MLKit doesn't guarantee any ordering.
            val sortedLines = lines.sortedBy { (it.boundingBox!!.top + it.boundingBox!!.bottom) / 2 }

            // Capture raw lines before grouping for debug logging
            val rawLines = sortedLines.map { RawOcrLine(it.text, it.boundingBox) }

            // Group into rows: a line joins the last row whose average center Y
            // is within one line-height of this line's center Y.
            val rows = mutableListOf<MutableList<Text.Line>>()
            for (line in sortedLines) {
                val lineCenter = (line.boundingBox!!.top + line.boundingBox!!.bottom) / 2
                val lineHeight = line.boundingBox!!.bottom - line.boundingBox!!.top

                val matchingRow = rows.lastOrNull { row ->
                    val rowCenter = row.map {
                        (it.boundingBox!!.top + it.boundingBox!!.bottom) / 2
                    }.average()
                    Math.abs(lineCenter - rowCenter) <= lineHeight
                }

                if (matchingRow != null) matchingRow.add(line)
                else rows.add(mutableListOf(line))
            }

            // Sort each row left-to-right; capture as text lists before joining
            val rowGroups = rows.map { row ->
                row.sortedBy { it.boundingBox!!.left }.map { it.text }
            }
            val rowTexts = rowGroups.map { it.joinToString(" ") }

            val (resultMacros, detections) = readTextLines(rowTexts, oldMacros)

            val passData = OcrPassData(
                rawLines = rawLines,
                rowGroups = rowGroups,
                rowTexts = rowTexts,
                detections = detections,
                accumulatedMacros = resultMacros.copy()
            )

            return Pair(resultMacros, passData)
        }

        private fun readTextLines(lines: List<String>, macros: Macros): Pair<Macros, List<MacroDetection>> {
            val detections = mutableListOf<MacroDetection>()
            for (line in lines) {
                // Strip percentage values (daily value %) so they don't interfere with number extraction
                val lineNoPercent = Regex("""\d+\s*%""").replace(line, "")
                val lower = lineNoPercent.lowercase()

                val macro = detectMacro(lower) ?: continue

                // Skip rows that appear to have merged with an adjacent label row.
                // On nutrition labels the order is: calories, fat (+ sub-types: saturated, trans,
                // omega, mono, poly), cholesterol, sodium, carbs, fiber, sugars, protein.
                // If keywords from neighbouring rows appear here, spatial grouping went wrong.
                val contaminated = when (macro) {
                    "carbs"   -> lower.contains("fibre") || lower.contains("fiber") ||
                                 lower.contains("fibres") || lower.contains("omega") ||
                                 lower.contains("saturated")
                    "protein" -> lower.contains("sugar") || lower.contains("sucre")
                    else      -> false
                }
                if (contaminated) {
                    Log.d(TAG, "Skipped contaminated $macro row (merged with adjacent row): \"$line\"")
                    continue
                }

                // Don't overwrite a value already confirmed in a prior frame
                val alreadyFound = when (macro) {
                    "calories" -> macros.calories != -1
                    "fat"      -> macros.fat != -1
                    "protein"  -> macros.protein != -1
                    "carbs"    -> macros.carbs != -1
                    else       -> false
                }
                if (alreadyFound) continue

                // Find the position where the macro keyword ends, then search for the value
                // from there. This prevents numbers from merged rows that appear *before* the
                // keyword (e.g. "Fat / Lipides 6g Calories 110" picks up 110, not 6).
                val keywordEnd = when (macro) {
                    "calories" -> Regex("calori").find(lower)?.range?.last?.plus(1) ?: 0
                    "fat"      -> Regex("fat").find(lower)?.range?.last?.plus(1) ?: 0
                    "carbs"    -> (Regex("carbohydrate").find(lower)
                                    ?: Regex("carbs").find(lower))?.range?.last?.plus(1) ?: 0
                    "protein"  -> Regex("protein").find(lower)?.range?.last?.plus(1) ?: 0
                    else       -> 0
                }
                val match = Regex("""\d+""").find(lineNoPercent, keywordEnd) ?: continue
                val number = match.value.toIntOrNull() ?: continue

                // For gram-based macros, require a unit marker ('g' or '9' as misread 'g')
                // immediately after the number. When 'g' is absorbed into the number ("249"
                // instead of "24 g"), the next character is unrelated text — that signals we
                // picked up the wrong number from a merged row and should skip this line.
                // Calories are dimensionless, so they're exempt from this check.
                if (macro != "calories") {
                    val afterNumber = lineNoPercent.substring(match.range.last + 1).trimStart()
                    val unitChar = afterNumber.firstOrNull()?.lowercaseChar()
                    if (unitChar != null && unitChar != 'g' && unitChar != '9') {
                        Log.d(TAG, "Rejected $macro = $number (no gram unit after value): \"$line\"")
                        continue
                    }
                }

                // Reject values outside plausible per-serving ranges.
                // OCR commonly misreads 'g' as '9', fusing it with the preceding number
                // (e.g. "24 g" → "249"). A 249g carb value in a 40g serving is impossible.
                if (!isPlausible(macro, number)) {
                    Log.d(TAG, "Rejected implausible $macro = $number  (from: \"$line\")")
                    continue
                }

                when (macro) {
                    "calories" -> macros.calories = number
                    "fat"      -> macros.fat = number
                    "protein"  -> macros.protein = number
                    "carbs"    -> macros.carbs = number
                }
                detections.add(MacroDetection(macro = macro, value = number, fromLine = line))
                Log.d(TAG, "Found $macro = $number  (from: \"$line\")")
            }
            return Pair(macros, detections)
        }

        /**
         * Rejects values that are physically impossible for a single serving.
         * The primary OCR error this guards against: 'g' misread as '9' fuses with
         * the preceding number ("24 g" → "249"), inflating the value ~10x.
         */
        private fun isPlausible(macro: String, value: Int): Boolean = when (macro) {
            "calories" -> value in 0..5000
            "fat", "carbs", "protein" -> value in 0..200
            else -> true
        }

        /**
         * Returns the macro key if the line clearly identifies one, or null.
         *
         * Fat is special: nutrition labels have "Total Fat", but also "Saturated Fat",
         * "Trans Fat", "Monounsaturated Fat", etc. We only want the Total Fat row,
         * so we reject any line that qualifies the fat type.
         */
        private fun detectMacro(lower: String): String? {
            if (lower.contains("calorie")) return "calories"
            if (lower.contains("protein")) return "protein"
            if (lower.contains("carbohydrate") || lower.contains("carbs")) return "carbs"
            if (lower.contains("fat") &&
                !lower.contains("saturated") &&
                !lower.contains("trans") &&
                !lower.contains("mono") &&
                !lower.contains("poly")) return "fat"
            return null
        }
    }
}