package com.sleepyyui.notallyx.presentation.activity.main.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import com.sleepyyui.notallyx.R
import com.sleepyyui.notallyx.data.model.Folder
import com.sleepyyui.notallyx.data.model.Item

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
