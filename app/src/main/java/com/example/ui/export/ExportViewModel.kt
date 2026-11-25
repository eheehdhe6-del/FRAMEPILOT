package com.example.ui.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ExportedPackage
import com.example.data.PackageExporter
import com.example.data.ProjectStore
import com.example.domain.model.FramePilotProject
import com.example.engine.SpatialMapGenerator
import kotlinx.coroutines.Dispatchers
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
    val warningCount: Int = 0,
    val loadError: String? = null
)

class ExportViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            runCatching {
                val loaded = store.loadProject(projectId)
                    ?: error("Project '$projectId' not found on disk")
                val pkgAndWarnings = withContext(Dispatchers.Default) {
                    PackageExporter.generatePackage(loaded) to SpatialMapGenerator.collectWarnings(loaded).size
                }
                loaded to pkgAndWarnings
            }.onSuccess { (loaded, pkgAndWarnings) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        project = loaded,
                        exportedPackage = pkgAndWarnings.first,
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
}
