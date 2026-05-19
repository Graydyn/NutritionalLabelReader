package com.graydyn.nutritionlib.model

import java.io.Serializable

data class Macros(var calories: Int, var fat: Int, var protein: Int, var carbs: Int) : Serializable {

    constructor() : this(-1, -1, -1, -1)

    fun isComplete(proteinOnly: Boolean = false): Boolean {
        if (proteinOnly) return calories != -1 && protein != -1
        return calories != -1 && fat != -1 && protein != -1 && carbs != -1
    }
}
