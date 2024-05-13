package com.graydyn.nutritionlib

import android.util.Log
import com.google.mlkit.vision.text.Text
import com.graydyn.nutritionlib.model.Macros

/**
 * The TextRecognizer returns a series of blocks af text
 * In order to determine which value we need to determine which text blocks appear
 * beside each other horizontally
 */
class TextBlocksInterpreter {
    companion object {
        private val TAG = "TextBlocksInterpreter"
        fun read(blocks: List<Text.TextBlock>, oldMacros: Macros): Macros {
            var macros = oldMacros;

            //first shuck the blocks into a big list of lines
            val lines = mutableListOf<Text.Line>()
            for (block in blocks) {
                for (line in block.lines) {
                    lines.add(line)
                }
            }

            //make sure that left most blocks end up in the left side of final string
            //lines.sortBy { it.boundingBox?.left }

            //then we figure out where our row positions are
            val rowYs = ArrayList<Int>()
            var lastRowHeight = 0
            for (line in lines) {
                val centerPoint = (line.boundingBox!!.bottom + line.boundingBox!!.top) / 2
                if (rowYs.size == 0) {
                    rowYs.add(centerPoint)
                } else {
                    if (centerPoint - rowYs.last() > lastRowHeight ) {
                        rowYs.add(centerPoint)
                    }
                }
                lastRowHeight = line.boundingBox!!.bottom - line.boundingBox!!.top
            }

            //now append each line to its closest row
            val rowValues = ArrayList(MutableList(rowYs.size) { "" })
            for (line in lines) {
                var bestMatch = -1
                var bestMatchIndex = 0
                for (i in 0..rowYs.size - 1) {
                    if ((Math.abs(line.boundingBox!!.top - rowYs[i]) < bestMatch) || (bestMatch == -1)){
                        bestMatch = Math.abs(line.boundingBox!!.top - rowYs[i])
                        bestMatchIndex = i
                    }
                }
                rowValues[bestMatchIndex] = rowValues[bestMatchIndex] + " " + line.text
            }
            macros = readTextLines(rowValues, macros)

            return macros;
        }

        private fun readTextLines(lines: ArrayList<String>, macros: Macros): Macros {
            for (line in lines) {
                val foundItems = ArrayList<String>()

                //remove numbers with the % symbol, as these are percent daily values, not what we are looking for
                val re = Regex("[0-9]+%")
                val lineNoPercent = re.replace(line, "")

                //TODO fuzzy match would be better since OCR aint perfect, but its tricky to search for a fuzzy word in a text line
                //TODO although there are only a few words we look up, maybe just make a dict of common mistakes?
                if ((lineNoPercent.contains("Calories")) || (lineNoPercent.contains("calories"))){
                    foundItems.add("calories")
                }
                if ((lineNoPercent.contains("Protein")) || (lineNoPercent.contains("protein"))){
                    foundItems.add("protein")
                }
                if ((lineNoPercent.contains("Fat")) || (lineNoPercent.contains("fat"))){
                    foundItems.add("fat")
                }
                if ((lineNoPercent.contains("Carbohydrate")) || (lineNoPercent.contains("carbohydrate"))){
                    foundItems.add("carbs")
                }

                if (foundItems.size == 1) {  //one macro per line of text
                    val number = lineNoPercent.filter { it.isDigit() }
                    if (number != "") {
                        when (foundItems[0]) {
                            "protein" -> macros.protein = number.toInt()
                            "fat" -> macros.fat = number.toInt()
                            "carbs" -> macros.carbs = number.toInt()
                            "calories" -> macros.calories = number.toInt()
                        }
                    }
                }
                else{
                    //TODO its unusual, but some labels do have more than one macro per line, could add support for that
                }
            }

            return macros
        }
    }
}