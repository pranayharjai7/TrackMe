package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trackme.domain.model.Exercise

@Entity(tableName = "exercises")
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

fun String.parseJsonStringList(): List<String> =
    trimStart('[').trimEnd(']')
        .split(",")
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }

fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id, name = name, category = category,
    primaryMuscles = primaryMuscles.toJsonString(),
    secondaryMuscles = secondaryMuscles.toJsonString(),
    equipment = equipment,
    instructions = instructions.toJsonString(),
    gifUrl = gifUrl,
    youtubeQuery = youtubeQuery,
)

fun List<String>.toJsonString(): String = "[${joinToString(",") { "\"$it\"" }}]"
