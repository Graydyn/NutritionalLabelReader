package com.graydyn.nutritionlib.model

import java.io.Serializable

data class Macros(
    var calories: Float,
    var fat: Float,
    var protein: Float,
    var carbs: Float,
    var gramsPerServing: Int
) : Serializable {

    constructor() : this(-1f, -1f, -1f, -1f, -1)

    fun isComplete(proteinOnly: Boolean = false): Boolean {
        if (proteinOnly) return calories != -1f && protein != -1f
        return calories != -1f && fat != -1f && protein != -1f && carbs != -1f
    }
}
