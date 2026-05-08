package com.trackme.domain.model

data class Exercise(
    val id: String,
    val name: String,
    val category: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipment: String,
    val instructions: List<String>,
    val gifUrl: String,
    val youtubeQuery: String,
)
