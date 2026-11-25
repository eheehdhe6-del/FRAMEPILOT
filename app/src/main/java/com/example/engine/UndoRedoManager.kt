package com.example.engine

import com.example.domain.model.FramePilotProject

class UndoRedoManager(private val maxHistory: Int = 60) {
    private val undoStack = ArrayDeque<FramePilotProject>()
    private val redoStack = ArrayDeque<FramePilotProject>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    fun recordBeforeChange(previousState: FramePilotProject) {
        if (undoStack.lastOrNull() == previousState) return
        undoStack.addLast(previousState)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(currentState: FramePilotProject): FramePilotProject? {
        if (undoStack.isEmpty()) return null
        val prev = undoStack.removeLast()
        redoStack.addLast(currentState)
        return prev
    }

    fun redo(currentState: FramePilotProject): FramePilotProject? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        undoStack.addLast(currentState)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
