package com.sleepyyui.notallyx.utils.changehistory

interface Change {
    fun redo()

    fun undo()
}
