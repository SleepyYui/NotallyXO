package com.sleepyyui.notallyxo.presentation.activity.main.fragment

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.data.model.Folder
import com.sleepyyui.notallyxo.data.model.Item

class DisplayLabelFragment : NotallyFragment() {

    private lateinit var label: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.folder.value = Folder.NOTES
    }

    override fun getBackground() = R.drawable.label

    override fun getObservable(): LiveData<List<Item>> {
        label = requireNotNull(requireArguments().getString(EXTRA_DISPLAYED_LABEL))
        return model.getNotesByLabel(label)
    }

    override fun prepareNewNoteIntent(intent: Intent): Intent {
        return intent.putExtra(EXTRA_DISPLAYED_LABEL, label)
    }

    companion object {
        const val EXTRA_DISPLAYED_LABEL = "notallyxo.intent.extra.DISPLAYED_LABEL"
    }
}
