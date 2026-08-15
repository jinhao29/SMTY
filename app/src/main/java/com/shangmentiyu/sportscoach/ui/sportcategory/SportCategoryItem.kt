package com.shangmentiyu.sportscoach.ui.sportcategory

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector

@Stable
data class SportCategoryItem(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val description: String
)
