package com.graydyn.tracker.ui.components

fun perServingToPer100g(perServing: Float?, gramsPerServing: Float): Float? =
    perServing?.let { it / gramsPerServing * 100f }

fun perServingToPerItem(perServing: Float?, itemsPerServing: Float): Float? =
    perServing?.let { it / itemsPerServing }

/** Renders an amount the way a user would have typed it: drops a trailing ".0". */
fun formatAmount(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString()
    else value.toString()
