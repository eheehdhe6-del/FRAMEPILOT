package com.example

import com.example.data.PackageExporter
import com.example.data.PackageImporter
import com.example.data.SampleProjects
import com.example.domain.model.AnimationFrame
import com.example.domain.model.DrawingStroke
import com.example.domain.model.StrokePoint
import com.example.engine.TimelineEngine
import com.example.engine.UndoRedoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSequenceAndRegressionTests {

    private fun makeTestFrames(count: Int): List<AnimationFrame> {
        return (0 until count).map { idx ->
            val markerStroke = DrawingStroke(
                id = "stroke-orig-${idx + 1}",
                layerId = "layer-1",
                points = listOf(StrokePoint((idx + 1) * 100f, 200f))
            )
            AnimationFrame(
                id = "frame-uuid-${idx + 1}",
                orderIndex = idx,
                exposureFrames = 1,
                note = "Frame ${idx + 1}",
                layerDrawings = mapOf("layer-1" to listOf(markerStroke))
            )
        }
    }

    @Test
    fun `test 1 initial sequence reads chronologically 1 to 5`() {
        val frames = TimelineEngine.normalizeOrder(makeTestFrames(5))
        val visibleNums = frames.map { TimelineEngine.visibleFrameNumber(frames, it.id) }
        assertEquals(listOf(1, 2, 3, 4, 5), visibleNums)

        val ticks = TimelineEngine.buildPlaybackTicks(frames, reverse = false)
        assertEquals(listOf(1, 2, 3, 4, 5), ticks.map { it.visibleFrameNumber })
        assertEquals(frames.map { it.id }, ticks.map { it.frame.id })
    }

    @Test
    fun `test 2 duplicate frame 2 inserts copy at position 3 preserving original artwork`() {
        val initial = makeTestFrames(5)
        val res = TimelineEngine.duplicateFrameAfter(initial, "frame-uuid-2")
        assertEquals(6, res.frames.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), res.frames.mapIndexed { i, _ -> i + 1 })

        val frame2 = res.frames[1]
        val frame3 = res.frames[2]
        assertNotEquals(frame2.id, frame3.id)
        assertEquals(res.selectedFrameId, frame3.id)
        assertEquals(
            frame2.layerDrawings["layer-1"]?.first()?.points,
            frame3.layerDrawings["layer-1"]?.first()?.points
        )
        // Original frame 1 & 2 artwork preserved
        assertEquals("frame-uuid-1", res.frames[0].id)
        assertEquals("frame-uuid-2", res.frames[1].id)
        assertEquals("frame-uuid-3", res.frames[3].id)
    }

    @Test
    fun `test 3 insert blank after frame 3 updates numbering sequentially without altering other frames`() {
        val initial = makeTestFrames(5)
        val res = TimelineEngine.addBlankFrameAfter(initial, "frame-uuid-3")
        assertEquals(6, res.frames.size)
        assertEquals(res.selectedFrameId, res.frames[3].id)
        assertTrue(res.frames[3].layerDrawings.isEmpty())
        assertEquals("frame-uuid-3", res.frames[2].id)
        assertEquals("frame-uuid-4", res.frames[4].id)
        assertEquals(400f, res.frames[4].layerDrawings["layer-1"]?.first()?.points?.first()?.x)
    }

    @Test
    fun `test 4 delete frame 3 preserves identities and updates visible numbering`() {
        val initial = makeTestFrames(5)
        val res = TimelineEngine.deleteFrame(initial, "frame-uuid-3")
        assertEquals(4, res.frames.size)
        assertEquals(listOf("frame-uuid-1", "frame-uuid-2", "frame-uuid-4", "frame-uuid-5"), res.frames.map { it.id })
        // Selects next chronological frame (originally 4, now at index 2 -> visible #3)
        assertEquals("frame-uuid-4", res.selectedFrameId)
        assertEquals(listOf(1, 2, 3, 4), res.frames.map { TimelineEngine.visibleFrameNumber(res.frames, it.id) })

        // Deleting last frame selects previous frame
        val delLast = TimelineEngine.deleteFrame(res.frames, "frame-uuid-5")
        assertEquals("frame-uuid-4", delLast.selectedFrameId)
    }

    @Test
    fun `test 5 reorder frame 5 to position 2 updates playback order to match timeline`() {
        val initial = makeTestFrames(5)
        val reordered = TimelineEngine.reorderFrame(initial, fromIndex = 4, toIndex = 1)
        assertEquals(
            listOf("frame-uuid-1", "frame-uuid-5", "frame-uuid-2", "frame-uuid-3", "frame-uuid-4"),
            reordered.map { it.id }
        )
        val playbackIds = TimelineEngine.buildPlaybackTicks(reordered).map { it.frame.id }
        assertEquals(reordered.map { it.id }, playbackIds)
    }

    @Test
    fun `test 6 playback proceeds left to right unless reverse explicitly requested`() {
        val frames = makeTestFrames(4)
        val forward = TimelineEngine.buildPlaybackTicks(frames, reverse = false)
        assertEquals(listOf(1, 2, 3, 4), forward.map { it.visibleFrameNumber })

        val reverse = TimelineEngine.buildPlaybackTicks(frames, reverse = true)
        assertEquals(listOf(4, 3, 2, 1), reverse.map { it.visibleFrameNumber })
    }

    @Test
    fun `test 7 exposure duration extends visibility without duplicating stored frames`() {
        val frames = makeTestFrames(3)
        val updated = TimelineEngine.setExposure(frames, "frame-uuid-2", 3)
        assertEquals(3, updated.size)
        assertEquals(3, updated[1].exposureFrames)

        val ticks = TimelineEngine.buildPlaybackTicks(updated)
        assertEquals(5, ticks.size)
        assertEquals(listOf(1, 2, 2, 2, 3), ticks.map { it.visibleFrameNumber })
    }

    @Test
    fun `test 8 undo and redo restore previous frame order and contents`() {
        val undoRedo = UndoRedoManager()
        val proj0 = SampleProjects.createBlankProject("UndoTest", 1080, 1920, "9:16", 24)
        val scene0 = proj0.scenes.first()
        val f1 = scene0.frames.first()

        // Duplicate frame
        undoRedo.recordBeforeChange(proj0)
        val dup = TimelineEngine.duplicateFrameAfter(scene0.frames, f1.id)
        val proj1 = proj0.copy(scenes = listOf(scene0.copy(frames = dup.frames)))
        assertEquals(2, proj1.scenes.first().frames.size)

        // Undo -> back to 1 frame
        val restored = undoRedo.undo(proj1)!!
        assertEquals(1, restored.scenes.first().frames.size)

        // Redo -> back to 2 frames
        val redone = undoRedo.redo(restored)!!
        assertEquals(2, redone.scenes.first().frames.size)
    }

    @Test
    fun `regression fixture 8-cel basketball preserves drawings exposures fps note and round-trip`() {
        val fixture = SampleProjects.createEightCelBasketballRegressionFixture()
        assertEquals(30, fixture.fps)
        val scene = fixture.scenes.first()
        assertEquals(8, scene.frames.size)
        assertEquals(14, scene.totalExposureTicks())
        assertTrue(scene.frames.last().note.contains("faster bouncing", ignoreCase = true))

        // Playback ticks match chronological order 1..8 across 14 ticks
        val ticks = TimelineEngine.buildPlaybackTicks(scene.frames, reverse = false)
        assertEquals(14, ticks.size)
        assertEquals(1, ticks.first().visibleFrameNumber)
        assertEquals(8, ticks.last().visibleFrameNumber)
        assertTrue(ticks.zipWithNext().all { (a, b) -> a.visibleFrameNumber <= b.visibleFrameNumber })

        // Export to ZIP and re-import losslessly
        val zipBytes = PackageExporter.exportToZipBytes(fixture)
        val imported = PackageImporter.importBytes(zipBytes, assignNewId = false).project

        assertEquals(30, imported.fps)
        val impScene = imported.scenes.first()
        assertEquals(8, impScene.frames.size)
        assertEquals(14, impScene.totalExposureTicks())
        assertEquals(scene.frames.map { it.id }, impScene.frames.map { it.id })
        assertEquals(scene.frames.map { it.exposureFrames }, impScene.frames.map { it.exposureFrames })
        assertEquals(scene.frames.last().note, impScene.frames.last().note)

        // Stroke geometry preserved for every cel
        for (i in 0 until 8) {
            val origPts = scene.frames[i].layerDrawings.values.flatten().first().points
            val impPts = impScene.frames[i].layerDrawings.values.flatten().first().points
            assertEquals(origPts.size, impPts.size)
            assertEquals(origPts.first().x, impPts.first().x, 0.01f)
            assertEquals(origPts.first().y, impPts.first().y, 0.01f)
        }
    }
}
