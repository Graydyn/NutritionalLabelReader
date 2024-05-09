package com.graydyn.nutritionlib.model

import java.io.Serializable

data class Macros(var calories: Int, var fat: Int, var protein: Int, var carbs: Int) : Serializable{

    constructor(): this(-1,-1,-1,-1) {

    }
    fun isComplete(): Boolean{
        if ((calories != -1) && (fat != -1) && (protein != -1) && carbs != -1) return true
        return false
    }
}
