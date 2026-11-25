package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioRobolectricUiTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `library creates new project enters studio edits frames and opens export`() {
        composeTestRule.waitForIdle()

        // Wait for seeded sample project card or New Project button
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("new_project_button").fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Tap New Project on Library screen
        composeTestRule.onNodeWithTag("new_project_button").performClick()
        composeTestRule.waitForIdle()

        // 2. Enter name, scroll to Start Creating inside ModalBottomSheet, and click
        composeTestRule.onNodeWithTag("new_project_name_input").performTextReplacement("Robolectric Reel")
        composeTestRule.onNodeWithTag("fps_chip_30").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("start_creating_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("studio_drawing_canvas").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // 3. Switch to Draw Frames mode and test Add Blank + Duplicate + Delete
        composeTestRule.onNodeWithTag("mode_tab_draw_frames").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("frame_add_blank").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("frame_card_2").assertIsDisplayed()

        composeTestRule.onNodeWithTag("frame_duplicate").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("frame_card_3").assertIsDisplayed()

        composeTestRule.onNodeWithTag("frame_delete").performClick()
        composeTestRule.waitForIdle()

        // 4. Switch to Motion Studio and open Add Motion sheet
        composeTestRule.onNodeWithTag("mode_tab_motion_studio").performClick()
        composeTestRule.waitForIdle()

        // 5. Open Export screen and verify OpenCode prompt preview is displayed
        composeTestRule.onNodeWithTag("studio_export_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("export_file_content_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("OpenCode Package Export").assertIsDisplayed()
        composeTestRule.onNodeWithTag("export_file_content_text").assertIsDisplayed()
    }

    @Test
    fun `studio clear canvas button opens confirmation dialog and supports undo`() {
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("project_card_sample-park-butterfly").fetchSemanticsNodes().isNotEmpty()
        }

        // Open seeded sample project with pre-existing strokes/shapes
        composeTestRule.onNodeWithTag("project_card_sample-park-butterfly").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("studio_drawing_canvas").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("studio_undo_button").assertIsNotEnabled()

        // Click Clear All button -> shows confirmation dialog
        composeTestRule.onNodeWithTag("studio_clear_canvas_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Clear all drawings?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_clear_canvas_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_clear_canvas_button").assertIsDisplayed()

        // Cancel dismisses dialog without mutating canvas or push to undo stack
        composeTestRule.onNodeWithTag("cancel_clear_canvas_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_undo_button").assertIsNotEnabled()

        // Click Clear All -> Confirm clears drawings and pushes undoable state
        composeTestRule.onNodeWithTag("studio_clear_canvas_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_clear_canvas_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_undo_button").assertIsEnabled()

        // Undo restores drawings and enables Redo
        composeTestRule.onNodeWithTag("studio_undo_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_redo_button").assertIsEnabled()
    }

    @Test
    fun `pose editor reset to default restores starter joint coordinates and supports undo`() {
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("project_card_sample-park-butterfly").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("project_card_sample-park-butterfly").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("studio_drawing_canvas").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Open Layers sheet, select Stickman Hero, and open PoseEditorSheet
        composeTestRule.onNodeWithTag("timeline_layers_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("layer_row_layer-char-stickman").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_2d_pose_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Park Butterfly sample places Stickman Hero at (540, 1140), so rightHand Y starts at 1060 px
        composeTestRule.onNodeWithText("rightHand Y: 1060 px").assertIsDisplayed()

        // Reset to Default (centered at 540, 960 -> rightHand Y = 880 px)
        composeTestRule.onNodeWithTag("pose_reset_default_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("rightHand Y: 880 px").assertIsDisplayed()

        // Close sheet and verify Undo is enabled
        composeTestRule.onNodeWithTag("pose_editor_done_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_undo_button").assertIsEnabled()
    }
}
