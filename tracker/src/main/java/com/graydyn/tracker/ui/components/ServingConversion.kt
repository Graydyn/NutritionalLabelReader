package com.graydyn.tracker.ui.components

fun perServingToPer100g(perServing: Float?, gramsPerServing: Float): Float? =
    perServing?.let { it / gramsPerServing * 100f }

fun perServingToPerItem(perServing: Float?, itemsPerServing: Float): Float? =
    perServing?.let { it / itemsPerServing }
