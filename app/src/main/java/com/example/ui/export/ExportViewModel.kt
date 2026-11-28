package com.example.ui.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ExportOptions
import com.example.data.ExportedPackage
import com.example.data.PackageExporter
import com.example.data.ProjectStore
import com.example.domain.model.FramePilotProject
import com.example.engine.SpatialMapGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExportUiState(
    val isLoading: Boolean = true,
    val project: FramePilotProject? = null,
    val exportedPackage: ExportedPackage? = null,
    val exportOptions: ExportOptions = ExportOptions(),
    val warningCount: Int = 0,
    val loadError: String? = null
)

class ExportViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private var rebuildJob: Job? = null
    private var persistJob: Job? = null

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            runCatching {
                val loaded = store.loadProject(projectId)
                    ?: error("Project '$projectId' not found on disk")
                val options = ExportOptions(
                    sketchAsDirectionOnly = loaded.exportSketchAsDirectionOnly,
                    externalStyleGuideNote = loaded.exportStyleGuideNote
                )
                val pkgAndWarnings = withContext(Dispatchers.Default) {
                    PackageExporter.generatePackage(loaded, options) to SpatialMapGenerator.collectWarnings(loaded).size
                }
                Triple(loaded, options, pkgAndWarnings)
            }.onSuccess { (loaded, options, pkgAndWarnings) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        project = loaded,
                        exportedPackage = pkgAndWarnings.first,
                        exportOptions = options,
                        warningCount = pkgAndWarnings.second,
                        loadError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = err.message ?: err::class.java.simpleName
                    )
                }
            }
        }
    }

    fun setSketchAsDirectionOnly(enabled: Boolean) {
        val current = _uiState.value
        val project = current.project ?: return
        val newOptions = current.exportOptions.copy(sketchAsDirectionOnly = enabled)
        applyOptions(project, newOptions, debounceSaveMs = 0L)
    }

    fun setExternalStyleGuideNote(note: String) {
        val current = _uiState.value
        val project = current.project ?: return
        val newOptions = current.exportOptions.copy(externalStyleGuideNote = note)
        applyOptions(project, newOptions, debounceSaveMs = 350L)
    }

    private fun applyOptions(
        project: FramePilotProject,
        newOptions: ExportOptions,
        debounceSaveMs: Long
    ) {
        val updatedProject = project.copy(
            exportSketchAsDirectionOnly = newOptions.sketchAsDirectionOnly,
            exportStyleGuideNote = newOptions.externalStyleGuideNote
        )
        _uiState.update {
            it.copy(
                project = updatedProject,
                exportOptions = newOptions
            )
        }
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            val pkg = withContext(Dispatchers.Default) {
                PackageExporter.generatePackage(updatedProject, newOptions)
            }
            _uiState.update { it.copy(exportedPackage = pkg) }
        }
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            if (debounceSaveMs > 0L) delay(debounceSaveMs)
            runCatching { store.saveProject(updatedProject) }
        }
    }
}
