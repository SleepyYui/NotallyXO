package com.sleepyyui.notallyxo.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import androidx.recyclerview.widget.SortedListAdapterCallback
import com.sleepyyui.notallyxo.data.model.BaseNote
import com.sleepyyui.notallyxo.data.model.Header
import com.sleepyyui.notallyxo.data.model.Item
import com.sleepyyui.notallyxo.databinding.RecyclerBaseNoteBinding
import com.sleepyyui.notallyxo.databinding.RecyclerHeaderBinding
import com.sleepyyui.notallyxo.presentation.view.main.sorting.BaseNoteColorSort
import com.sleepyyui.notallyxo.presentation.view.main.sorting.BaseNoteCreationDateSort
import com.sleepyyui.notallyxo.presentation.view.main.sorting.BaseNoteModifiedDateSort
import com.sleepyyui.notallyxo.presentation.view.main.sorting.BaseNoteTitleSort
import com.sleepyyui.notallyxo.presentation.view.misc.ItemListener
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.DateFormat
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.NotesSort
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.NotesSortBy
import java.io.File

class BaseNoteAdapter(
    private val selectedIds: Set<Long>,
    private val dateFormat: DateFormat,
    private var notesSort: NotesSort,
    private val preferences: BaseNoteVHPreferences,
    private val imageRoot: File?,
    private val listener: ItemListener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var list = SortedList(Item::class.java, notesSort.createCallback())

    override fun getItemViewType(position: Int): Int {
        return when (list[position]) {
            is Header -> 0
            is BaseNote -> 1
        }
    }

    override fun getItemCount(): Int {
        return list.size()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = list[position]) {
            is Header -> (holder as HeaderVH).bind(item)
            is BaseNote ->
                (holder as BaseNoteVH).bind(
                    item,
                    imageRoot,
                    selectedIds.contains(item.id),
                    notesSort.sortedBy,
                )
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else handleCheck(holder, position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> {
                val binding = RecyclerHeaderBinding.inflate(inflater, parent, false)
                HeaderVH(binding)
            }
            else -> {
                val binding = RecyclerBaseNoteBinding.inflate(inflater, parent, false)
                BaseNoteVH(binding, dateFormat, preferences, listener)
            }
        }
    }

    fun setNotesSort(notesSort: NotesSort) {
        this.notesSort = notesSort
        replaceSortCallback(notesSort.createCallback())
    }

    fun getItem(position: Int): Item? {
        return list[position]
    }

    val currentList: List<Item>
        get() = list.toList()

    fun submitList(items: List<Item>) {
        list.replaceAll(items)
    }

    private fun NotesSort.createCallback() =
        when (sortedBy) {
            NotesSortBy.TITLE -> BaseNoteTitleSort(this@BaseNoteAdapter, sortDirection)
            NotesSortBy.MODIFIED_DATE ->
                BaseNoteModifiedDateSort(this@BaseNoteAdapter, sortDirection)
            NotesSortBy.CREATION_DATE ->
                BaseNoteCreationDateSort(this@BaseNoteAdapter, sortDirection)
            NotesSortBy.COLOR -> BaseNoteColorSort(this@BaseNoteAdapter, sortDirection)
        }

    private fun replaceSortCallback(sortCallback: SortedListAdapterCallback<Item>) {
        val mutableList = mutableListOf<Item>()
        for (i in 0 until list.size()) {
            mutableList.add(list[i])
        }
        list.clear()
        list = SortedList(Item::class.java, sortCallback)
        list.addAll(mutableList)
    }

    private fun handleCheck(holder: RecyclerView.ViewHolder, position: Int) {
        val baseNote = list[position] as BaseNote
        (holder as BaseNoteVH).updateCheck(selectedIds.contains(baseNote.id), baseNote.color)
    }

    private fun <T> SortedList<T>.toList(): List<T> {
        val mutableList = mutableListOf<T>()
        for (i in 0 until this.size()) {
            mutableList.add(this[i])
        }
        return mutableList.toList()
    }
}
