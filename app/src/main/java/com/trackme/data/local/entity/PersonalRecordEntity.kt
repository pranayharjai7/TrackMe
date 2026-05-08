package com.trackme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trackme.domain.model.PersonalRecord

@Entity(tableName = "personal_records")
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
