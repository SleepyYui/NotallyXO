package com.sleepyyui.notallyx.presentation.activity.main.fragment

import android.os.Bundle
import android.view.View
import com.sleepyyui.notallyx.R
import com.sleepyyui.notallyx.data.model.Folder

class NotesFragment : NotallyFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.folder.value = Folder.NOTES
    }

    override fun getObservable() = model.baseNotes!!

    override fun getBackground() = R.drawable.notebook
}
