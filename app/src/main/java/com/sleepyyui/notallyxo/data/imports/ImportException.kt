package com.sleepyyui.notallyxo.data.imports

class ImportException(val textResId: Int, cause: Throwable) : RuntimeException(cause)
