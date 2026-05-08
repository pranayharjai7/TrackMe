package com.trackme.data.exercise

import android.content.Context
import com.trackme.data.local.entity.ExerciseEntity
import com.trackme.data.local.entity.toJsonString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class RawExercise(
    val id: String,
    val name: String,
    val category: String,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
)

@Singleton
class ExerciseAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadExercises(): List<ExerciseEntity> {
        val raw = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val exercises = json.decodeFromString<List<RawExercise>>(raw)
        return exercises.map { it.toEntity() }
    }

    private fun RawExercise.toEntity(): ExerciseEntity {
        val gifUrl = images.firstOrNull()?.let {
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/$it"
        } ?: ""
        return ExerciseEntity(
            id = id,
            name = name,
            category = category,
            primaryMuscles = primaryMuscles.toJsonString(),
            secondaryMuscles = secondaryMuscles.toJsonString(),
            equipment = equipment ?: "none",
            instructions = instructions.toJsonString(),
            gifUrl = gifUrl,
            youtubeQuery = "$name tutorial",
        )
    }
}
