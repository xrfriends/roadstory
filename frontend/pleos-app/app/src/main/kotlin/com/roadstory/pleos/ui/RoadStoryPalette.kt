package com.roadstory.pleos.ui

import android.graphics.Color

object RoadStoryPalette {
    // Figma Make `pleos-tokens.css` dark theme, transcribed to Android colors.
    val background = Color.rgb(29, 30, 33)       // basic-50 / background-primary
    val surface = Color.rgb(19, 20, 23)          // basic-00 / background-secondary
    val raisedSurface = Color.rgb(49, 50, 54)    // basic-100 / surface-high
    val accentSurface = Color.rgb(68, 70, 78)    // basic-200 / surface-accent
    val line = Color.argb(26, 255, 255, 255)      // alpha-dark-100
    val primary = Color.rgb(224, 225, 230)        // filled button enabled
    val primaryPressed = Color.rgb(199, 201, 206)
    val primaryDark = Color.rgb(19, 20, 23)
    val text = Color.WHITE
    val secondaryText = Color.argb(214, 255, 255, 255)
    val tertiaryText = Color.argb(163, 255, 255, 255)
    val active = Color.rgb(0, 224, 116)
    val warm = Color.rgb(68, 70, 78)
    val cool = Color.rgb(49, 50, 54)
    val focus = Color.rgb(0, 130, 255)
}
