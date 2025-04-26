package com.sleepyyui.notallyxo.presentation.activity.main.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.data.model.Folder
import com.sleepyyui.notallyxo.data.model.Item

class UnlabeledFragment : NotallyFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.folder.value = Folder.NOTES
    }

    override fun getBackground() = R.drawable.label_off

    override fun getObservable(): LiveData<List<Item>> {
        return model.getNotesWithoutLabel()
    }
}
