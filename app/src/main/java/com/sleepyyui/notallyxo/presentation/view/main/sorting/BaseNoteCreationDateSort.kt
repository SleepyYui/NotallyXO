package com.sleepyyui.notallyxo.presentation.view.main.sorting

import androidx.recyclerview.widget.RecyclerView
import com.sleepyyui.notallyxo.data.model.BaseNote
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.SortDirection

class BaseNoteCreationDateSort(adapter: RecyclerView.Adapter<*>?, sortDirection: SortDirection) :
    ItemSort(adapter, sortDirection) {

    override fun compare(note1: BaseNote, note2: BaseNote, sortDirection: SortDirection): Int {
        val sort = note1.compareCreated(note2)
        return if (sortDirection == SortDirection.ASC) sort else -1 * sort
    }
}

fun BaseNote.compareCreated(other: BaseNote) = timestamp.compareTo(other.timestamp)
