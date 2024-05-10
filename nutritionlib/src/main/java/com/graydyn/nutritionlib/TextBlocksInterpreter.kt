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
            //then we figure out where our row positions are
            val rowYs = ArrayList<Int>()
            var lastRowHeight = 0
            for (line in lines){
                if (rowYs.size == 0){
                    rowYs.add(line.boundingBox!!.top)
                }
                else{
                    if (rowYs.last() + lastRowHeight < line.boundingBox!!.top){
                        rowYs.add(line.boundingBox!!.top)
                    }
                }
                lastRowHeight = line.boundingBox!!.bottom - line.boundingBox!!.top
            }

            //now append each line to its closest row
            val rowValues = ArrayList(MutableList(rowYs.size) { "" })
            for (line in lines) {
                var found = false;
                for (i in 0..rowYs.size-1){
                    if (!found) {
                        if (i == rowYs.size - 1) {
                            rowValues[i] = rowValues[i] + " " + line.text
                        } else {
                            if (Math.abs(line.boundingBox!!.top - rowYs[i]) < Math.abs(line.boundingBox!!.top - rowYs[i + 1])) {
                                rowValues[i] = rowValues[i] + " " + line.text
                                found = true;
                            }
                        }
                    }
                }
            }
            for (rowValue in rowValues){
                Log.d(TAG, rowValue)
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