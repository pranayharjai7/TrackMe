package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trackme.domain.model.PersonalRecord

@Entity(
    tableName = "personal_records",
    indices = [
        Index(value = ["userId", "achievedAt"], name = "idx_personal_records_user_date"),
        Index(value = ["userId", "exerciseId"], name = "idx_personal_records_exercise"),
    ],
)
data class PersonalRecordEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val exerciseId: String,
    val maxWeightKg: Float,
    val maxReps: Int,
    val achievedAt: Long,
) {
    fun toDomain() = PersonalRecord(id, userId, exerciseId, maxWeightKg, maxReps, achievedAt)
}

fun PersonalRecord.toEntity() =
    PersonalRecordEntity(id, userId, exerciseId, maxWeightKg, maxReps, achievedAt)
