package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trackme.domain.model.Exercise
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["name"], name = "idx_exercises_name"),
        Index(value = ["category"], name = "idx_exercises_category"),
    ],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val primaryMuscles: String,      // JSON array stored as String
    val secondaryMuscles: String,    // JSON array stored as String
    val equipment: String,
    val instructions: String,        // JSON array stored as String
    val gifUrl: String,
    val youtubeQuery: String,
) {
    fun toDomain(): Exercise = Exercise(
        id = id, name = name, category = category,
        primaryMuscles = primaryMuscles.parseJsonStringList(),
        secondaryMuscles = secondaryMuscles.parseJsonStringList(),
        equipment = equipment,
        instructions = instructions.parseJsonStringList(),
        gifUrl = gifUrl,
        youtubeQuery = youtubeQuery,
    )
}

private val entityJson = Json

fun String.parseJsonStringList(): List<String> =
    entityJson.decodeFromString(ListSerializer(String.serializer()), this)

fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id, name = name, category = category,
    primaryMuscles = primaryMuscles.toJsonString(),
    secondaryMuscles = secondaryMuscles.toJsonString(),
    equipment = equipment,
    instructions = instructions.toJsonString(),
    gifUrl = gifUrl,
    youtubeQuery = youtubeQuery,
)

fun List<String>.toJsonString(): String =
    entityJson.encodeToString(ListSerializer(String.serializer()), this)
