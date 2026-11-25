package com.example.data

import com.example.domain.model.FramePilotProject
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ImportResult(
    val project: FramePilotProject,
    val sourceFormat: String,
    val warningCount: Int
)

object PackageImporter {

    /**
     * Imports either a FramePilot ZIP package (containing project.json) or a raw project.json payload.
     * When [preserveProjectId] is false (default for user imports into library), assigns a fresh project ID
     * only if requested via [assignNewId].
     */
    fun importBytes(
        bytes: ByteArray,
        assignNewId: Boolean = false
    ): ImportResult {
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        val (jsonText, format) = if (isZip) {
            extractProjectJsonFromZip(bytes) to "ZIP Package"
        } else {
            bytes.toString(Charsets.UTF_8) to "JSON Project"
        }

        val parsed = ProjectSerializer.fromJson(jsonText)
        val finalProject = if (assignNewId) {
            val now = java.time.Instant.now().toString()
            parsed.copy(
                id = UUID.randomUUID().toString(),
                updatedAtIso = now
            )
        } else {
            parsed
        }

        val warnings = com.example.engine.SpatialMapGenerator.collectWarnings(finalProject)
        return ImportResult(
            project = finalProject,
            sourceFormat = format,
            warningCount = warnings.size
        )
    }

    private fun extractProjectJsonFromZip(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                if (!entry.isDirectory && (name == "project.json" || name.endsWith("/project.json"))) {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        throw IllegalArgumentException("ZIP package does not contain a project.json file")
    }
}
