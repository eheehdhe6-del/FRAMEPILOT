package com.example.data

import com.example.domain.model.AnimationFrame
import com.example.domain.model.BackgroundSpec
import com.example.domain.model.CURRENT_SCHEMA_VERSION
import com.example.domain.model.FramePilotProject
import com.example.domain.model.Layer
import com.example.domain.model.LayerCategory
import com.example.domain.model.Scene
import com.example.engine.TimelineEngine
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object ProjectSerializer {

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(FramePilotProject::class.java).indent("  ")
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun toJson(project: FramePilotProject): String {
        val normalized = normalizeProject(project)
        return adapter.toJson(normalized)
    }

    fun fromJson(json: String): FramePilotProject {
        val root = mapAdapter.fromJson(json)
            ?: throw IllegalArgumentException("Unable to parse FramePilot project JSON")
        val version = (root["schemaVersion"] as? Number)?.toInt() ?: 1
        val parsed = if (version < CURRENT_SCHEMA_VERSION && !root.containsKey("scenes")) {
            migrateV1Map(root)
        } else {
            adapter.fromJson(json)
                ?: throw IllegalArgumentException("Unable to parse FramePilot project JSON")
        }
        return normalizeProject(parsed)
    }

    fun normalizeProject(project: FramePilotProject): FramePilotProject {
        val normalizedScenes = project.scenes
            .sortedBy { it.orderIndex }
            .mapIndexed { sIdx, scene ->
                val normalizedLayers = scene.layers
                    .sortedBy { it.orderIndex }
                    .mapIndexed { lIdx, layer -> layer.copy(orderIndex = lIdx) }
                val normalizedFrames = if (scene.frames.isEmpty()) {
                    listOf(AnimationFrame(orderIndex = 0, exposureFrames = 1))
                } else {
                    TimelineEngine.normalizeOrder(scene.frames)
                }
                scene.copy(
                    orderIndex = sIdx,
                    layers = normalizedLayers,
                    frames = normalizedFrames
                )
            }
        return project.copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            scenes = normalizedScenes
        )
    }

    /**
     * Best-effort migration from v1 single-scene JSON payloads where layers/frames
     * lived directly at the root object.
     */
    private fun migrateV1Map(root: Map<String, Any?>): FramePilotProject {
        val id = (root["id"] as? String)?.ifBlank { null } ?: java.util.UUID.randomUUID().toString()
        val name = (root["name"] as? String) ?: "Imported Project"
        val width = (root["canvasWidth"] as? Number)?.toInt() ?: 1080
        val height = (root["canvasHeight"] as? Number)?.toInt() ?: 1920
        val fps = (root["fps"] as? Number)?.toInt() ?: 24
        val now = java.time.Instant.now().toString()
        val defaultLayer = Layer(name = "Drawing Layer", category = LayerCategory.CHARACTER, orderIndex = 0)
        val defaultScene = Scene(
            orderIndex = 0,
            name = "Scene 1",
            background = BackgroundSpec(),
            layers = listOf(defaultLayer),
            frames = listOf(AnimationFrame(orderIndex = 0, exposureFrames = 1))
        )
        return FramePilotProject(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            id = id,
            name = name,
            createdAtIso = (root["createdAtIso"] as? String) ?: now,
            updatedAtIso = now,
            canvasWidth = width,
            canvasHeight = height,
            aspectRatioLabel = (root["aspectRatioLabel"] as? String) ?: "9:16",
            fps = fps,
            scenes = listOf(defaultScene)
        )
    }
}
