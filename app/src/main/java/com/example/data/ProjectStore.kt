package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.FramePilotProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

private val Context.studioDataStore by preferencesDataStore(name = "framepilot_prefs")

data class ProjectSummary(
    val id: String,
    val name: String,
    val aspectRatioLabel: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val fps: Int,
    val sceneCount: Int,
    val totalFrames: Int,
    val totalDurationMs: Long,
    val updatedAtIso: String,
    val previewProject: FramePilotProject
)

class ProjectStore(private val context: Context) {
    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    companion object {
        private val ioMutex = Mutex()
        private val pendingIoOps = AtomicInteger(0)
        private const val SAMPLE_MARKER_FILE = ".sample_v4_migrated"

        suspend fun awaitIdle() {
            while (pendingIoOps.get() > 0) {
                delay(5)
            }
            ioMutex.withLock { }
        }
    }

    private suspend inline fun <T> runIoLocked(crossinline block: suspend () -> T): T {
        pendingIoOps.incrementAndGet()
        try {
            return withContext(Dispatchers.IO) {
                ioMutex.withLock {
                    coroutineContext.ensureActive()
                    block()
                }
            }
        } finally {
            pendingIoOps.decrementAndGet()
        }
    }

    private val tutorialCompletedKey = booleanPreferencesKey("tutorial_completed")
    private val advancedModeKey = booleanPreferencesKey("advanced_mode_default")

    val tutorialCompletedFlow: Flow<Boolean> = context.studioDataStore.data.map { prefs ->
        prefs[tutorialCompletedKey] ?: false
    }

    val advancedModeFlow: Flow<Boolean> = context.studioDataStore.data.map { prefs ->
        prefs[advancedModeKey] ?: false
    }

    suspend fun setTutorialCompleted(completed: Boolean) {
        context.studioDataStore.edit { it[tutorialCompletedKey] = completed }
    }

    suspend fun setAdvancedModeDefault(advanced: Boolean) {
        context.studioDataStore.edit { it[advancedModeKey] = advanced }
    }

    suspend fun ensureSeededAndList(): List<ProjectSummary> = runIoLocked {
        val marker = File(projectsDir, SAMPLE_MARKER_FILE)
        if (!marker.exists()) {
            File(projectsDir, "sample-park-butterfly").takeIf { it.exists() }?.deleteRecursively()
            writeAtomic(SampleProjects.createAfterGraduationReelSample())
            marker.writeText("2", Charsets.UTF_8)
        }
        readAllSummariesUnlocked()
    }

    suspend fun listProjects(): List<ProjectSummary> = runIoLocked {
        readAllSummariesUnlocked()
    }

    private fun readAllSummariesUnlocked(): List<ProjectSummary> {
        val dirs = projectsDir.listFiles { f -> f.isDirectory }?.toList().orEmpty()
        return dirs.mapNotNull { dir ->
            val file = File(dir, "project.json")
            if (!file.exists()) return@mapNotNull null
            runCatching {
                val project = ProjectSerializer.fromJson(file.readText(Charsets.UTF_8))
                ProjectSummary(
                    id = project.id,
                    name = project.name,
                    aspectRatioLabel = project.aspectRatioLabel,
                    canvasWidth = project.canvasWidth,
                    canvasHeight = project.canvasHeight,
                    fps = project.fps,
                    sceneCount = project.scenes.size,
                    totalFrames = project.scenes.sumOf { it.frames.size },
                    totalDurationMs = project.totalDurationMs,
                    updatedAtIso = project.updatedAtIso,
                    previewProject = project
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAtIso }
    }

    suspend fun loadProject(projectId: String): FramePilotProject? = runIoLocked {
        val file = File(File(projectsDir, projectId), "project.json")
        if (!file.exists()) return@runIoLocked null
        runCatching { ProjectSerializer.fromJson(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    suspend fun saveProject(project: FramePilotProject): FramePilotProject = runIoLocked {
        val stamped = project.copy(updatedAtIso = java.time.Instant.now().toString())
        writeAtomic(stamped)
        stamped
    }

    suspend fun duplicateProject(projectId: String): FramePilotProject? = runIoLocked {
        val file = File(File(projectsDir, projectId), "project.json")
        if (!file.exists()) return@runIoLocked null
        val original = ProjectSerializer.fromJson(file.readText(Charsets.UTF_8))
        val now = java.time.Instant.now().toString()
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)",
            createdAtIso = now,
            updatedAtIso = now
        )
        writeAtomic(copy)
        copy
    }

    suspend fun renameProject(projectId: String, newName: String): Boolean = runIoLocked {
        val file = File(File(projectsDir, projectId), "project.json")
        if (!file.exists()) return@runIoLocked false
        val proj = ProjectSerializer.fromJson(file.readText(Charsets.UTF_8))
        val updated = proj.copy(
            name = newName.trim().ifBlank { proj.name },
            updatedAtIso = java.time.Instant.now().toString()
        )
        writeAtomic(updated)
        true
    }

    suspend fun deleteProject(projectId: String): Boolean = runIoLocked {
        val dir = File(projectsDir, projectId)
        if (dir.exists()) dir.deleteRecursively() else false
    }

    private fun writeAtomic(project: FramePilotProject) {
        val dir = File(projectsDir, project.id).apply { mkdirs() }
        val target = File(dir, "project.json")
        val tmp = File(dir, "project.json.tmp")
        val json = ProjectSerializer.toJson(project)
        tmp.writeText(json, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(json, Charsets.UTF_8)
            tmp.delete()
        }
    }
}
