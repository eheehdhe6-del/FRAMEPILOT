package com.example.engine

import com.example.domain.model.AnimationFrame
import com.example.domain.model.Scene
import java.util.UUID

data class TimelineFrameResult(
    val frames: List<AnimationFrame>,
    val selectedFrameId: String
)

data class PlaybackTick(
    val frame: AnimationFrame,
    val visibleFrameNumber: Int,
    val exposureSubIndex: Int,
    val exposureTotal: Int,
    val globalTickIndex: Int
)

object TimelineEngine {

    /**
     * Always normalizes orderIndex to strict 0..N-1 chronological sequence.
     * Visible frame number is ALWAYS (index + 1), never derived from internal UUIDs.
     */
    fun normalizeOrder(frames: List<AnimationFrame>): List<AnimationFrame> {
        return frames
            .sortedBy { it.orderIndex }
            .mapIndexed { index, frame -> frame.copy(orderIndex = index) }
    }

    fun visibleFrameNumber(frames: List<AnimationFrame>, frameId: String): Int {
        val ordered = normalizeOrder(frames)
        val idx = ordered.indexOfFirst { it.id == frameId }
        return if (idx >= 0) idx + 1 else 1
    }

    fun addBlankFrameAfter(
        frames: List<AnimationFrame>,
        afterFrameId: String?
    ): TimelineFrameResult {
        val ordered = normalizeOrder(frames).toMutableList()
        val insertIndex = if (afterFrameId == null) {
            ordered.size
        } else {
            val found = ordered.indexOfFirst { it.id == afterFrameId }
            if (found >= 0) found + 1 else ordered.size
        }
        val newFrame = AnimationFrame(
            id = UUID.randomUUID().toString(),
            orderIndex = insertIndex,
            exposureFrames = 1,
            note = ""
        )
        ordered.add(insertIndex, newFrame)
        val normalized = ordered.mapIndexed { idx, f -> f.copy(orderIndex = idx) }
        return TimelineFrameResult(normalized, newFrame.id)
    }

    fun duplicateFrameAfter(
        frames: List<AnimationFrame>,
        sourceFrameId: String
    ): TimelineFrameResult {
        val ordered = normalizeOrder(frames).toMutableList()
        val sourceIdx = ordered.indexOfFirst { it.id == sourceFrameId }
        if (sourceIdx < 0) {
            return addBlankFrameAfter(ordered, ordered.lastOrNull()?.id)
        }
        val source = ordered[sourceIdx]
        val duplicatedDrawings = source.layerDrawings.mapValues { (_, strokes) ->
            strokes.map { stroke -> stroke.copy(id = UUID.randomUUID().toString()) }
        }
        val duplicatedShapes = source.layerShapes.mapValues { (_, shapes) ->
            shapes.map { shape -> shape.copy(id = UUID.randomUUID().toString()) }
        }
        val duplicatedPoses = source.layerPoses.mapValues { (_, pose) ->
            pose.copy(id = UUID.randomUUID().toString())
        }
        val copyFrame = AnimationFrame(
            id = UUID.randomUUID().toString(),
            orderIndex = sourceIdx + 1,
            exposureFrames = source.exposureFrames,
            note = source.note,
            layerDrawings = duplicatedDrawings,
            layerShapes = duplicatedShapes,
            layerPoses = duplicatedPoses
        )
        ordered.add(sourceIdx + 1, copyFrame)
        val normalized = ordered.mapIndexed { idx, f -> f.copy(orderIndex = idx) }
        return TimelineFrameResult(normalized, copyFrame.id)
    }

    /**
     * Deletes the target frame (if more than 1 frame exists; if only 1 frame, clears its drawings instead of leaving 0 frames).
     * Selects the next chronological frame, or the previous one if the deleted frame was last.
     */
    fun deleteFrame(
        frames: List<AnimationFrame>,
        frameId: String
    ): TimelineFrameResult {
        val ordered = normalizeOrder(frames).toMutableList()
        if (ordered.isEmpty()) {
            val fresh = AnimationFrame(orderIndex = 0)
            return TimelineFrameResult(listOf(fresh), fresh.id)
        }
        if (ordered.size == 1) {
            val cleared = ordered[0].copy(
                layerDrawings = emptyMap(),
                layerShapes = emptyMap(),
                layerPoses = emptyMap(),
                note = ""
            )
            return TimelineFrameResult(listOf(cleared), cleared.id)
        }
        val idx = ordered.indexOfFirst { it.id == frameId }
        if (idx < 0) {
            return TimelineFrameResult(ordered, ordered.first().id)
        }
        ordered.removeAt(idx)
        val normalized = ordered.mapIndexed { i, f -> f.copy(orderIndex = i) }
        val nextSelectedIdx = idx.coerceAtMost(normalized.lastIndex)
        return TimelineFrameResult(normalized, normalized[nextSelectedIdx].id)
    }

    fun reorderFrame(
        frames: List<AnimationFrame>,
        fromIndex: Int,
        toIndex: Int
    ): List<AnimationFrame> {
        val ordered = normalizeOrder(frames).toMutableList()
        if (fromIndex !in ordered.indices || toIndex !in ordered.indices || fromIndex == toIndex) {
            return ordered
        }
        val item = ordered.removeAt(fromIndex)
        ordered.add(toIndex, item)
        return ordered.mapIndexed { idx, f -> f.copy(orderIndex = idx) }
    }

    fun setExposure(
        frames: List<AnimationFrame>,
        frameId: String,
        exposureFrames: Int
    ): List<AnimationFrame> {
        val clamped = exposureFrames.coerceIn(1, 120)
        return normalizeOrder(frames).map { frame ->
            if (frame.id == frameId) frame.copy(exposureFrames = clamped) else frame
        }
    }

    fun setFrameNote(
        frames: List<AnimationFrame>,
        frameId: String,
        note: String
    ): List<AnimationFrame> {
        return normalizeOrder(frames).map { frame ->
            if (frame.id == frameId) frame.copy(note = note) else frame
        }
    }

    /**
     * Builds the chronological playback tick stream from left to right (or reversed ONLY when reverse == true).
     * Respects exposureFrames without duplicating stored frame data.
     */
    fun buildPlaybackTicks(
        frames: List<AnimationFrame>,
        reverse: Boolean = false
    ): List<PlaybackTick> {
        val ordered = normalizeOrder(frames)
        val numbered = ordered.mapIndexed { idx, frame -> (idx + 1) to frame }
        val sequence = if (reverse) numbered.reversed() else numbered
        val ticks = mutableListOf<PlaybackTick>()
        var globalIdx = 0
        for ((visibleNumber, frame) in sequence) {
            val exposure = frame.exposureFrames.coerceAtLeast(1)
            for (sub in 0 until exposure) {
                ticks.add(
                    PlaybackTick(
                        frame = frame,
                        visibleFrameNumber = visibleNumber,
                        exposureSubIndex = sub,
                        exposureTotal = exposure,
                        globalTickIndex = globalIdx++
                    )
                )
            }
        }
        return ticks
    }

    fun frameAtTimeMs(
        scene: Scene,
        fps: Int,
        timeMs: Long,
        reverse: Boolean = false
    ): PlaybackTick? {
        val ticks = buildPlaybackTicks(scene.frames, reverse)
        if (ticks.isEmpty()) return null
        val safeFps = fps.coerceAtLeast(1)
        val tickDurationMs = 1000.0 / safeFps.toDouble()
        val tickIndex = ((timeMs.coerceAtLeast(0L)) / tickDurationMs).toInt().coerceIn(0, ticks.lastIndex)
        return ticks[tickIndex]
    }
}
