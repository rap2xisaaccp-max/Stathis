package citu.edu.stathis.mobile.features.exercise.adaptive

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class InMemoryEvidenceQueue(
    private val maxRetries: Int = 5
) : EvidenceQueue {
    private val items = ConcurrentLinkedQueue<QueuedEvidence>()
    private val deadLetter = AtomicInteger(0)

    val deadLetterCount: Int get() = deadLetter.get()
    val pendingCount: Int get() = items.size

    override fun enqueue(event: FormEvidenceEvent, jpeg: ByteArray) {
        if (event.interventionId.isBlank()) return
        if (!JpegCompressor.isAcceptableSize(jpeg)) return
        if (items.any { it.event.interventionId == event.interventionId }) return
        items.add(QueuedEvidence(event, jpeg))
    }

    override fun pending(): List<QueuedEvidence> = items.toList()

    override fun acknowledge(interventionId: String) {
        val iterator = items.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().event.interventionId == interventionId) {
                iterator.remove()
            }
        }
    }

    override fun requeueAfterFailure(failed: List<QueuedEvidence>): Int {
        var accepted = 0
        failed.forEach { item ->
            val next = item.copy(attempts = item.attempts + 1)
            if (next.attempts > maxRetries) {
                deadLetter.incrementAndGet()
            } else {
                items.add(next)
                accepted++
            }
        }
        return accepted
    }

    override fun isEmpty(): Boolean = items.isEmpty()

    override fun clear() {
        items.clear()
    }
}
