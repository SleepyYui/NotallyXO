package com.sleepyyui.notallyxo.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViewsService
import com.sleepyyui.notallyxo.NotallyXOApplication
import com.sleepyyui.notallyxo.presentation.activity.note.EditActivity.Companion.EXTRA_SELECTED_BASE_NOTE

class WidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val id = intent.getLongExtra(EXTRA_SELECTED_BASE_NOTE, 0)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        return WidgetFactory(application as NotallyXOApplication, id, widgetId)
    }
}
