package com.shangmentiyu.sportscoach.ui.sportcategory

import androidx.compose.runtime.Stable

@Stable
data class SportsCategory(
    val id: String,
    val name: String,
    val items: List<SportCategoryItem>
)
