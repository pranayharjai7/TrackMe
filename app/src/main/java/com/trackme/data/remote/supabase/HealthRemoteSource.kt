package com.trackme.data.remote.supabase

import com.trackme.data.local.entity.HealthSnapshotEntity
import com.trackme.data.remote.dto.HealthSnapshotDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote data source for Supabase health snapshot writes.
 *
 * Architecture Layer: Data remote source
 *
 * Responsibilities:
 * - Upsert Health Connect daily snapshots into the backend table.
 * - Keep DTO serialization details outside repositories and workers.
 */
@Singleton
class HealthRemoteSource @Inject constructor(private val supabase: SupabaseClient) {
    suspend fun upsertSnapshot(entity: HealthSnapshotEntity) {
        supabase.postgrest["health_snapshots"].upsert(
            HealthSnapshotDto(
                entity.id, entity.userId, entity.date,
                entity.weightKg, entity.heightCm, entity.bmi,
                entity.steps, entity.activeCaloriesBurned, entity.updatedAt,
            )
        )
    }
}
