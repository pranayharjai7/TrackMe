package com.trackme.wearbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandReplayPlannerTest {
    @Test
    fun enqueueUniqueRejectsDuplicateCommandIds() {
        val command = command("one", 10L)

        val queue = CommandReplayPlanner.enqueueUnique(emptyList(), command, now = 11L)
        val duplicate = CommandReplayPlanner.enqueueUnique(queue, command, now = 12L)

        assertEquals(1, duplicate.size)
        assertEquals("one", duplicate.single().command.commandId)
    }

    @Test
    fun replayBatchIsChronologicalAndRemovesDeliveredCommands() {
        val first = QueuedCommand(command("first", 20L), enqueuedAt = 2L)
        val second = QueuedCommand(command("second", 10L), enqueuedAt = 1L)

        val batch = CommandReplayPlanner.nextReplayBatch(listOf(first, second))
        val remaining = CommandReplayPlanner.markDelivered(batch, setOf("second"))

        assertEquals(listOf("second", "first"), batch.map { it.command.commandId })
        assertEquals(listOf("first"), remaining.map { it.command.commandId })
    }

    @Test
    fun watchActionReplayIsIdempotentChronologicalAndRemovesDeliveredActions() {
        val later = action(id = "later", eventIndex = 2L, timestamp = 20L)
        val earlier = action(id = "earlier", eventIndex = 1L, timestamp = 30L)

        val queued = WatchActionReplayPlanner.enqueueUnique(
            WatchActionReplayPlanner.enqueueUnique(emptyList(), later),
            earlier,
        )
        val duplicate = WatchActionReplayPlanner.enqueueUnique(queued, earlier)
        val replay = WatchActionReplayPlanner.nextReplayBatch(duplicate)
        val remaining = WatchActionReplayPlanner.markDelivered(replay, setOf("earlier"))

        assertEquals(2, duplicate.size)
        assertEquals(listOf("earlier", "later"), replay.map { it.actionId })
        assertEquals(listOf("later"), remaining.map { it.actionId })
    }

    private fun command(id: String, createdAt: Long) = WatchCommandPayload(
        commandId = id,
        type = WatchCommandType.LOG_SET,
        createdAt = createdAt,
        sessionId = "session",
        exerciseId = "exercise",
        log = SetLogPayload(weightKg = 50f, reps = 5),
    )

    private fun action(id: String, eventIndex: Long, timestamp: Long) = WatchActionPayload(
        actionId = id,
        actionType = WatchActionType.COMPLETE_SET,
        timestamp = timestamp,
        sessionId = "session",
        exerciseId = "exercise",
        setNumber = 1,
        eventIndex = eventIndex,
        log = SetLogPayload(weightKg = 50f, reps = 5),
    )
}
