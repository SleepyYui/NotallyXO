package com.sleepyyui.notallyx.utils.audio

import android.app.Service
import android.os.Binder

class LocalBinder<T : Service>(private val service: T) : Binder() {

    fun getService(): T = service
}
