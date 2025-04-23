package com.sleepyyui.notallyx.presentation.activity.main.fragment

import android.os.Bundle
import android.view.View
import com.sleepyyui.notallyx.R
import com.sleepyyui.notallyx.data.model.Folder

class ArchivedFragment : NotallyFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.folder.value = Folder.ARCHIVED
    }

    override fun getBackground() = R.drawable.archive

    override fun getObservable() = model.archivedNotes!!
}
