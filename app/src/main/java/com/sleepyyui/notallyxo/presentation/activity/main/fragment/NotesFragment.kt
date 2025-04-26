package com.sleepyyui.notallyxo.presentation.activity.main.fragment

import android.os.Bundle
import android.view.View
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.data.model.Folder

class NotesFragment : NotallyFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.folder.value = Folder.NOTES
    }

    override fun getObservable() = model.baseNotes!!

    override fun getBackground() = R.drawable.notebook
}
