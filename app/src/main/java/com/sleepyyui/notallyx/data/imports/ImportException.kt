package com.sleepyyui.notallyx.data.imports

class ImportException(val textResId: Int, cause: Throwable) : RuntimeException(cause)
