package com.graydyn.nutritionlib

import android.util.Log
import com.google.mlkit.vision.text.Text
import com.graydyn.nutritionlib.model.Macros
import com.willowtreeapps.fuzzywuzzy.diffutils.FuzzySearch
import kotlin.math.log

/**
 * The TextRecognizer returns a series of blocks af text
 * In order to determine which value we need to determine which text blocks appear
 * beside each other horizontally
 */
class TextBlocksInterpreter {
    companion object {
        private val TAG = "TextBlocksInterpreter"
        fun read(blocks: List<Text.TextBlock>, oldMacros: Macros) : Macros {
            var macros = oldMacros;

            //first shuck the blocks into a big list of lines
            val lines = mutableListOf<Text.Line>()
            for (block in blocks) {
                for (line in block.lines){
                    lines.add(line)
                }
            }
            //sort the text lines from top to bottom and shuck the strings
            //lines.sortBy { it.boundingBox?.top }
            val shuckedLines = mutableListOf<String>()
            var currentY = 0
            var previousBlockHeight = 0;
            for (line in lines){
                if (shuckedLines.size == 0 || (line.boundingBox!!.top - currentY < previousBlockHeight)){
                    shuckedLines.add(line.text)
                }
                else{
                    shuckedLines.set(shuckedLines.lastIndex, shuckedLines.last() + " " + line.text)
                }
                previousBlockHeight = line.boundingBox!!.bottom - line.boundingBox!!.top
                currentY = line.boundingBox!!.top
            }
            for (line in shuckedLines){
                Log.d(TAG, line)
            }


            return macros;
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