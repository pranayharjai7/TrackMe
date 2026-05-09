package com.trackme.domain.usecase

import com.trackme.domain.repository.WorkoutRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LogSetUseCaseTest {

    private val repo = mockk<WorkoutRepository>(relaxed = true)
    private val useCase = LogSetUseCase(repo)

    @Test
    fun `logSet saves set and updates personal record`() = runTest {
        val set = useCase("session1", "user1", "bench-press", 1, 100f, 8)

        coVerify(exactly = 1) { repo.logSet(any()) }
        coVerify(exactly = 1) { repo.updatePersonalRecord("user1", "bench-press", 100f, 8, any()) }
        assertEquals(100f, set.weightKg)
        assertEquals(8, set.reps)
        assertEquals(true, set.completed)
    }
}
