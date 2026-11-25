package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.ui.components.TouchDrawingCanvas
import com.example.ui.components.TouchDrawingState
import com.example.ui.components.TouchDrawingUndoRedoBar
import com.example.ui.components.TouchStroke
import com.example.ui.theme.FramePilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TouchDrawingCanvasTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `stack based undo redo reverts latest strokes clear and clears redo on new draw`() {
        val drawingState = TouchDrawingState()
        val completed = mutableListOf<TouchStroke>()

        composeTestRule.setContent {
            FramePilotTheme {
                Column {
                    TouchDrawingUndoRedoBar(state = drawingState)
                    TouchDrawingCanvas(
                        modifier = Modifier.size(300.dp),
                        state = drawingState,
                        brushColor = Color(0xFFFF6B5B),
                        brushWidth = 10f,
                        onStrokeCompleted = { completed.add(it) }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Initially undo/redo disabled
        composeTestRule.onNodeWithTag("touch_canvas_undo_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("touch_canvas_redo_button").assertIsNotEnabled()

        // Draw stroke 1
        composeTestRule.onNodeWithTag("touch_drawing_canvas").performTouchInput {
            down(Offset(20f, 20f))
            moveTo(Offset(100f, 60f))
            up()
        }
        // Draw stroke 2
        composeTestRule.onNodeWithTag("touch_drawing_canvas").performTouchInput {
            down(Offset(40f, 120f))
            moveTo(Offset(180f, 180f))
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(2, drawingState.strokes.size)
        assertEquals(2, completed.size)
        composeTestRule.onNodeWithTag("touch_canvas_stroke_count").assertTextEquals("2")
        composeTestRule.onNodeWithTag("touch_canvas_undo_button").assertIsEnabled()

        // Undo latest stroke (stroke 2) via UI button
        composeTestRule.onNodeWithTag("touch_canvas_undo_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, drawingState.strokes.size)
        assertEquals(completed[0].id, drawingState.strokes.first().id)
        composeTestRule.onNodeWithTag("touch_canvas_stroke_count").assertTextEquals("1")
        composeTestRule.onNodeWithTag("touch_canvas_redo_button").assertIsEnabled()

        // Redo stroke 2 via UI button
        composeTestRule.onNodeWithTag("touch_canvas_redo_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, drawingState.strokes.size)
        assertEquals(completed[1].id, drawingState.strokes.last().id)

        // Undo once, then draw new stroke 3 -> redo stack must clear!
        composeTestRule.onNodeWithTag("touch_canvas_undo_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("touch_drawing_canvas").performTouchInput {
            down(Offset(60f, 200f))
            moveTo(Offset(220f, 220f))
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(2, drawingState.strokes.size)
        assertFalse(drawingState.canRedo)
        composeTestRule.onNodeWithTag("touch_canvas_redo_button").assertIsNotEnabled()

        // Tap Clear button -> confirmation dialog appears; Cancel keeps strokes intact
        composeTestRule.onNodeWithTag("touch_canvas_clear_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("touch_canvas_cancel_clear_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, drawingState.strokes.size)

        // Tap Clear button -> Confirm clears all strokes and keeps undo available
        composeTestRule.onNodeWithTag("touch_canvas_clear_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("touch_canvas_confirm_clear_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, drawingState.strokes.size)
        assertTrue(drawingState.canUndo)

        // Undo Clear restores both strokes
        composeTestRule.onNodeWithTag("touch_canvas_undo_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, drawingState.strokes.size)
    }
}
