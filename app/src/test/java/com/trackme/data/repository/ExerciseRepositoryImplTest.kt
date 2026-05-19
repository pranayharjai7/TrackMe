package com.trackme.data.repository

import app.cash.turbine.test
import com.trackme.data.exercise.ExerciseAssetLoader
import com.trackme.data.local.dao.ExerciseDao
import com.trackme.data.local.entity.ExerciseEntity
import com.trackme.data.local.entity.toJsonString
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseRepositoryImplTest {

    private val dao = mockk<ExerciseDao>()
    private val assetLoader = mockk<ExerciseAssetLoader>()
    private val repo = ExerciseRepositoryImpl(dao, assetLoader)

    private fun fakeEntity(id: String, name: String) = ExerciseEntity(
        id = id, name = name, category = "Strength",
        primaryMuscles = listOf("chest").toJsonString(),
        secondaryMuscles = listOf("triceps").toJsonString(),
        equipment = "barbell",
        instructions = listOf("Step 1").toJsonString(),
        gifUrl = "", youtubeQuery = "$name tutorial",
    )

    @Test
    fun `seedIfEmpty seeds when count is zero`() = runTest {
        coEvery { dao.count() } returns 0
        coEvery { assetLoader.loadExercises() } returns listOf(fakeEntity("id1", "Bench Press"))
        coEvery { dao.insertAll(any()) } returns Unit

        repo.seedIfEmpty()

        coVerify(exactly = 1) { dao.insertAll(any()) }
    }

    @Test
    fun `seedIfEmpty skips when already seeded`() = runTest {
        coEvery { dao.count() } returns 800

        repo.seedIfEmpty()

        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `search returns mapped domain models`() = runTest {
        every { dao.search("bench", any()) } returns flowOf(
            listOf(fakeEntity("bench-press", "Bench Press"))
        )

        repo.search("bench", 30).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Bench Press", result[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByMuscle returns mapped domain models`() = runTest {
        every { dao.getByMuscle("chest") } returns flowOf(
            listOf(fakeEntity("bench-press", "Bench Press"))
        )

        repo.getByMuscle("chest").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Bench Press", result[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getById returns mapped domain model`() = runTest {
        coEvery { dao.getById("bench-press") } returns fakeEntity("bench-press", "Bench Press")

        val result = repo.getById("bench-press")

        assertEquals("Bench Press", result?.name)
    }
}
