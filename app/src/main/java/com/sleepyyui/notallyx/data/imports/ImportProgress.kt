package com.sleepyyui.notallyx.data.imports

import com.sleepyyui.notallyx.presentation.view.misc.Progress

open class ImportProgress(
    current: Int = 0,
    total: Int = 0,
    inProgress: Boolean = true,
    indeterminate: Boolean = false,
    val stage: ImportStage = ImportStage.IMPORT_NOTES,
) : Progress(current, total, inProgress, indeterminate)

enum class ImportStage {
    IMPORT_NOTES,
    EXTRACT_FILES,
    IMPORT_FILES,
}
