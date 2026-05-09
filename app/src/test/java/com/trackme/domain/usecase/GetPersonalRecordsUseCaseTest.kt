package com.trackme.domain.usecase

import app.cash.turbine.test
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.repository.WorkoutRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetPersonalRecordsUseCaseTest {

    private val repo = mockk<WorkoutRepository>()
    private val useCase = GetPersonalRecordsUseCase(repo)

    @Test
    fun `returns personal records for user`() = runTest {
        val pr = PersonalRecord("pr1", "user1", "bench-press", 100f, 8, 1000L)
        every { repo.getPersonalRecords("user1") } returns flowOf(listOf(pr))

        useCase("user1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(100f, result[0].maxWeightKg)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
