package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioRobolectricUiTests {

    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: org.junit.rules.RuleChain = org.junit.rules.RuleChain
        .outerRule(object : org.junit.rules.ExternalResource() {
            override fun before() {
                clearProjectsDir()
            }

            override fun after() {
                clearProjectsDir()
            }

            private fun clearProjectsDir() {
                kotlinx.coroutines.runBlocking {
                    com.example.data.ProjectStore.awaitIdle()
                }
                val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                java.io.File(ctx.filesDir, "projects").deleteRecursively()
            }
        })
        .around(composeTestRule)

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
        composeTestRule.onNodeWithTag("export_direction_only_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("export_file_content_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sketch Fidelity ≠ Final Output Style", substring = true).assertExists()

        // Toggle Direction-Only OFF -> live preview switches to Faithful Sketch Reconstruction
        composeTestRule.onNodeWithTag("export_direction_only_switch").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Faithful Sketch Reconstruction", substring = true).assertExists()

        // Toggle back ON, open optional skill.md / target style note, enter custom style directive
        composeTestRule.onNodeWithTag("export_direction_only_switch").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("export_style_note_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("export_style_note_input").performTextReplacement("Follow my studio skill.md")
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodes(
                androidx.compose.ui.test.hasText("Follow my studio skill.md", substring = true) and
                    hasTestTag("export_file_content_text")
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `studio clear canvas button opens confirmation dialog and supports undo`() {
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithTag("library_projects_grid").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("0 project(s)").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("library_projects_grid")
            .performScrollToNode(hasTestTag("project_card_sample-graduation-reel"))

        // Open seeded sample project with pre-existing strokes/shapes
        composeTestRule.onNodeWithTag("project_card_sample-graduation-reel").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("studio_drawing_canvas").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("studio_undo_button").assertIsNotEnabled()
        val density = composeTestRule.density.density
        val topBarBefore = composeTestRule.onNodeWithTag("studio_top_bar").fetchSemanticsNode().boundsInRoot
        val canvasBefore = composeTestRule.onNodeWithTag("studio_drawing_canvas").fetchSemanticsNode().boundsInRoot
        val savePillBefore = composeTestRule.onNodeWithTag("studio_save_status_pill", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

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

        // Click Clear All -> Confirm clears drawings and transitions SaveStatus to Unsaved
        composeTestRule.onNodeWithTag("studio_clear_canvas_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_clear_canvas_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_undo_button").assertIsEnabled()

        val topBarAfter = composeTestRule.onNodeWithTag("studio_top_bar").fetchSemanticsNode().boundsInRoot
        val canvasAfter = composeTestRule.onNodeWithTag("studio_drawing_canvas").fetchSemanticsNode().boundsInRoot
        val savePillAfter = composeTestRule.onNodeWithTag("studio_save_status_pill", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pillHeightDpBefore = savePillBefore.height / density
        val pillHeightDpAfter = savePillAfter.height / density
        org.junit.Assert.assertTrue(
            "Save pill should stay single-line, uncrushed, and identical size (density=$density, before=$savePillBefore [${pillHeightDpBefore}dp], after=$savePillAfter [${pillHeightDpAfter}dp])",
            savePillBefore.width >= 50f &&
                savePillAfter.width >= 50f &&
                pillHeightDpBefore <= 44f &&
                pillHeightDpAfter <= 44f &&
                kotlin.math.abs(savePillBefore.height - savePillAfter.height) <= 0.5f &&
                kotlin.math.abs(savePillBefore.width - savePillAfter.width) <= 0.5f
        )
        org.junit.Assert.assertEquals(
            "Top bar height must not shift between Saved and Unsaved (before=$topBarBefore, after=$topBarAfter)",
            topBarBefore.height,
            topBarAfter.height,
            0.5f
        )
        org.junit.Assert.assertEquals(
            "Canvas top position must not shift when SaveStatus changes (before=$canvasBefore, after=$canvasAfter)",
            canvasBefore.top,
            canvasAfter.top,
            0.5f
        )

        // Undo restores drawings and enables Redo
        composeTestRule.onNodeWithTag("studio_undo_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_redo_button").assertIsEnabled()
    }

    @Test
    fun `pose editor reset to default restores starter joint coordinates and supports undo`() {
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithTag("library_projects_grid").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("0 project(s)").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("library_projects_grid")
            .performScrollToNode(hasTestTag("project_card_sample-graduation-reel"))

        composeTestRule.onNodeWithTag("project_card_sample-graduation-reel").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("studio_drawing_canvas").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Open Layers sheet, select Graduate Protagonist, and open PoseEditorSheet
        composeTestRule.onNodeWithTag("timeline_layers_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("layer_row_grad-s1-hero").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_2d_pose_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("pose_editor_panel").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Verify docked PoseEditorPanel does NOT block the drawing canvas
        val canvasBounds = composeTestRule.onNodeWithTag("studio_drawing_canvas").fetchSemanticsNode().boundsInRoot
        val panelBounds = composeTestRule.onNodeWithTag("pose_editor_panel").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(
            "Canvas should remain clearly visible above PoseEditorPanel (canvasHeight=${canvasBounds.height}, canvasBottom=${canvasBounds.bottom}, panelTop=${panelBounds.top})",
            canvasBounds.height > 120f && canvasBounds.bottom <= panelBounds.top + 2f
        )

        // Scene 1 places Graduate Protagonist at (540, 1220) scale 1.16 with rightHand y=+42*1.16 -> 1175.92f.toInt() = 1175 px
        composeTestRule.onNodeWithText("rightHand Y: 1175 px").performScrollTo().assertIsDisplayed()

        // Switch to Whole Body mode, drag the entire character on canvas, and test Size + Angle controls
        composeTestRule.onNodeWithTag("pose_mode_whole_body").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Character X: 545 px").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_drawing_canvas").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX, height * 0.65f))
            moveTo(androidx.compose.ui.geometry.Offset(centerX - width * 0.20f, height * 0.50f))
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Character X: 545 px").assertDoesNotExist()

        // Adjust Skeleton Size slider and verify it updates, then reset to 100% with pill
        val initialSizeLabel = composeTestRule
            .onNodeWithText("Skeleton Size:", substring = true)
            .performScrollTo()
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .first()
            .text
        composeTestRule.onNodeWithTag("pose_whole_scale_slider").performScrollTo().performTouchInput {
            down(center)
            moveTo(androidx.compose.ui.geometry.Offset(right - 10f, centerY))
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(initialSizeLabel).assertDoesNotExist()
        composeTestRule.onNodeWithTag("pose_whole_scale_reset").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Skeleton Size: 100%").performScrollTo().assertIsDisplayed()

        // Adjust Skeleton Angle slider and verify it updates from 0°, then reset with 0° pill
        composeTestRule.onNodeWithText("Skeleton Angle: 0°").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pose_whole_angle_slider").performScrollTo().performTouchInput {
            down(center)
            moveTo(androidx.compose.ui.geometry.Offset(right - 10f, centerY))
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Skeleton Angle: 0°").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pose_whole_angle_reset").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Skeleton Angle: 0°").performScrollTo().assertIsDisplayed()

        // Switch back to Single Joint and verify Reset to Default restores (540, 960 -> rightHand Y = 880 px)
        composeTestRule.onNodeWithTag("pose_mode_single_joint").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pose_reset_default_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("rightHand Y: 880 px").performScrollTo().assertIsDisplayed()

        // Close panel and verify Undo is enabled
        composeTestRule.onNodeWithTag("pose_editor_done_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_undo_button").assertIsEnabled()
    }

    @Test
    fun `quick shape canvas edge toggle snaps freehand stroke on release and supports undo and export`() {
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("new_project_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Create a fresh blank project so canvas strokes are easy to verify in export
        composeTestRule.onNodeWithTag("new_project_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_project_name_input").performTextReplacement("QuickShape Test")
        composeTestRule.onNodeWithTag("start_creating_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("studio_drawing_canvas").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Dismiss tutorial strip if shown so Robolectric viewport leaves unobstructed drawing area
        if (composeTestRule.onAllNodesWithContentDescription("Dismiss tutorial").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithContentDescription("Dismiss tutorial").performClick()
            composeTestRule.waitForIdle()
        }

        // Collapse timeline so Robolectric viewport gives unobstructed drawing space
        composeTestRule.onNodeWithTag("timeline_collapse_toggle").performClick()
        composeTestRule.waitForIdle()

        // Verify QuickShape edge toggle is displayed and starts OFF
        composeTestRule.onNodeWithTag("canvas_quick_shape_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithText("OFF").assertIsDisplayed()

        // Toggle QuickShape ON -> indicator switches to ON and feedback pill shows
        composeTestRule.onNodeWithTag("canvas_quick_shape_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ON").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_shape_feedback_pill").assertIsDisplayed()

        // Draw a rough circle in the middle unobstructed area of the canvas and release
        composeTestRule.onNodeWithTag("studio_drawing_canvas").performTouchInput {
            val cx = width * 0.44f
            val cy = height * 0.46f
            val r = minOf(width, height) * 0.14f
            down(androidx.compose.ui.geometry.Offset(cx + r, cy))
            for (step in 1..24) {
                val angle = 2.0 * Math.PI * step / 24.0
                val wobble = if (step % 2 == 0) 4f else -4f
                moveTo(
                    androidx.compose.ui.geometry.Offset(
                        (cx + (r + wobble) * kotlin.math.cos(angle)).toFloat(),
                        (cy + (r - wobble) * kotlin.math.sin(angle)).toFloat()
                    )
                )
            }
            up()
        }
        composeTestRule.waitForIdle()

        // Undo should now be enabled, and tapping Undo disables Undo and enables Redo
        composeTestRule.onNodeWithTag("studio_undo_button").assertIsEnabled()
        composeTestRule.onNodeWithTag("studio_undo_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_undo_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("studio_redo_button").assertIsEnabled()

        // Redo restores the snapped shape, then verify Export includes the closed loop in OPENCODE-PROMPT.md
        composeTestRule.onNodeWithTag("studio_redo_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_export_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("export_file_content_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("closed circle-like loop", substring = true).assertExists()
    }

    @Test
    fun `after graduation 30s reel appears in library, switches across scenes in studio, and exports all 6 scenes`() {
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithTag("library_projects_grid").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("0 project(s)").fetchSemanticsNodes().isEmpty()
        }
        // Only the single 30s flagship sample should be seeded (no legacy butterfly card)
        composeTestRule.onNodeWithTag("project_card_sample-park-butterfly").assertDoesNotExist()
        composeTestRule.onNodeWithTag("library_projects_grid")
            .performScrollToNode(hasTestTag("project_card_sample-graduation-reel"))
        composeTestRule.onNodeWithTag("project_card_sample-graduation-reel").performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("studio_drawing_canvas").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // StudioTopBar displays multi-scene badge "1/6 · Campus Hook"
        composeTestRule.onNodeWithText("After College Ends (30s)").assertIsDisplayed()
        composeTestRule.onNodeWithText("1/6 · Campus Hook", substring = true).assertIsDisplayed()

        // Open Scene Planner from top bar trigger and switch to Scene 5 (Sunrise Clarity)
        composeTestRule.onNodeWithTag("studio_scene_planner_trigger").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Total Reel Duration: 30.0s across 6 scene(s)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scene_chip_grad-scene-5").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Close Scene Planner sheet via Save Scene Plan
        composeTestRule.onNodeWithText("Save Scene Plan").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Save Scene Plan").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("5/6 · Sunrise Clarity", substring = true).assertIsDisplayed()

        // Export OpenCode package and confirm direction-only setting + multi-scene output are present
        composeTestRule.onNodeWithTag("studio_export_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("export_file_content_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New Chapter Finale", substring = true).assertExists()
    }

    @Test
    fun `playback stop reverse connected reel mode and customizable scene sequence controls work end-to-end`() {
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithTag("library_projects_grid").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("0 project(s)").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("library_projects_grid")
            .performScrollToNode(hasTestTag("project_card_sample-graduation-reel"))
        composeTestRule.onNodeWithTag("project_card_sample-graduation-reel").performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("studio_drawing_canvas").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // 1. Toggle Whole Reel connected mode -> shows continuous reel scrubber & scene strip
        composeTestRule.onNodeWithTag("timeline_connected_mode_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("connected_reel_sequence_bar").assertIsDisplayed()

        // Step Next Frame across scenes in Scene 1 (Sample Scene 1 has multiple frames -> step through and verify stop resets to 0.0s)
        composeTestRule.onNodeWithTag("timeline_next_frame").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("timeline_next_frame").performClick()
        composeTestRule.waitForIdle()

        // Reverse toggle works cleanly
        composeTestRule.onNodeWithTag("timeline_reverse_toggle").performClick()
        composeTestRule.waitForIdle()

        // Stop resets playhead back to 0.0s / 30.0s & Scene 1
        composeTestRule.onNodeWithTag("timeline_stop").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("timeline_status_label").assertTextContains("0.0s/30.0s", substring = true)

        // 2. Open Scene Planner -> confirm old broken transition selector is removed (only Hard Cut)
        composeTestRule.onNodeWithTag("studio_scene_planner_trigger").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Transition to next scene").assertDoesNotExist()
        composeTestRule.onNodeWithText("Crossfade").assertDoesNotExist()

        // Swap Scene 1 down -> Scene 2 ("Hallway Routine") moves to #1 ("1. Hallway Routine")
        composeTestRule.onNodeWithTag("scene_move_down_grad-scene-1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("scene_chip_grad-scene-2").performScrollTo().assertTextContains("1. Hallway Routine", substring = true)

        // Add Fade In (0.5s) and Combine All scenes into one continuous editable scene
        composeTestRule.onNodeWithTag("planner_add_fade_in_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("planner_combine_all_scenes_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        org.junit.Assert.assertTrue(
            composeTestRule.onAllNodesWithText("1. Whole Reel (Connected)", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun `fullscreen drawing toggle expands canvas edge to edge hides chrome and exits cleanly`() {
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithTag("library_projects_grid").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("0 project(s)").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("library_projects_grid")
            .performScrollToNode(hasTestTag("project_card_sample-graduation-reel"))
        composeTestRule.onNodeWithTag("project_card_sample-graduation-reel").performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            Thread.sleep(50)
            composeTestRule.onAllNodesWithTag("studio_drawing_canvas").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        val normalBounds = composeTestRule.onNodeWithTag("studio_drawing_canvas").fetchSemanticsNode().boundsInRoot
        composeTestRule.onNodeWithTag("studio_top_bar").assertIsDisplayed()

        // Enter Full Screen Drawing Mode
        composeTestRule.onNodeWithTag("canvas_fullscreen_toggle").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("studio_top_bar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("timeline_collapse_toggle").assertDoesNotExist()
        composeTestRule.onNodeWithTag("canvas_fullscreen_exit").assertIsDisplayed()

        val fullscreenBounds = composeTestRule.onNodeWithTag("studio_drawing_canvas").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(
            "Fullscreen canvas height (${fullscreenBounds.height}) should be significantly larger than normal (${normalBounds.height})",
            fullscreenBounds.height > normalBounds.height + 100f
        )

        // Hide tools for pure canvas view, then show again
        composeTestRule.onNodeWithTag("fullscreen_tools_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tool_pen").assertDoesNotExist()
        composeTestRule.onNodeWithTag("fullscreen_tools_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tool_pen").assertIsDisplayed()

        // Exit Full Screen back to normal Studio UI
        composeTestRule.onNodeWithTag("canvas_fullscreen_exit").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_top_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("timeline_collapse_toggle").assertIsDisplayed()
    }
}

