package com.example.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PackageImporter
import com.example.data.ProjectStore
import com.example.data.ProjectSummary
import com.example.data.SampleProjects
import com.example.domain.model.AspectRatioPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val projects: List<ProjectSummary> = emptyList(),
    val isLoading: Boolean = true,
    val statusMessage: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val list = store.ensureSeededAndList()
            _uiState.update { it.copy(projects = list, isLoading = false) }
        }
    }

    fun createProject(
        name: String,
        preset: AspectRatioPreset,
        customWidth: Int,
        customHeight: Int,
        fps: Int,
        startTutorial: Boolean,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val w = if (preset == AspectRatioPreset.CUSTOM) customWidth.coerceIn(256, 4096) else preset.width
            val h = if (preset == AspectRatioPreset.CUSTOM) customHeight.coerceIn(256, 4096) else preset.height
            val proj = SampleProjects.createBlankProject(
                name = name,
                width = w,
                height = h,
                aspectLabel = if (preset == AspectRatioPreset.CUSTOM) "${w}x${h}" else preset.shortCode,
                fps = fps
            ).copy(tutorialCompleted = !startTutorial)
            store.saveProject(proj)
            store.setTutorialCompleted(!startTutorial)
            refresh()
            onCreated(proj.id)
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            store.duplicateProject(projectId)
            refresh()
            _uiState.update { it.copy(statusMessage = "Project duplicated") }
        }
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            store.renameProject(projectId, newName)
            refresh()
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            store.deleteProject(projectId)
            refresh()
            _uiState.update { it.copy(statusMessage = "Project deleted") }
        }
    }

    fun loadSampleGraduationReel(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val sample = SampleProjects.createAfterGraduationReelSample()
            store.saveProject(sample)
            refresh()
            onReady(sample.id)
        }
    }

    fun importPackageBytes(bytes: ByteArray, onImported: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val res = PackageImporter.importBytes(bytes, assignNewId = true)
                store.saveProject(res.project)
                refresh()
                _uiState.update {
                    it.copy(statusMessage = "Imported ${res.project.name} (${res.sourceFormat})")
                }
                onImported(res.project.id)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusMessage = "Import failed: ${err.message ?: "invalid file"}")
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
