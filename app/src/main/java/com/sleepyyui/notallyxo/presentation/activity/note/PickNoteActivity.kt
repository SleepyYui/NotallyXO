package com.sleepyyui.notallyxo.presentation.activity.note

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.data.NotallyXODatabase
import com.sleepyyui.notallyxo.data.model.BaseNote
import com.sleepyyui.notallyxo.data.model.Header
import com.sleepyyui.notallyxo.databinding.ActivityPickNoteBinding
import com.sleepyyui.notallyxo.presentation.activity.LockedActivity
import com.sleepyyui.notallyxo.presentation.view.main.BaseNoteAdapter
import com.sleepyyui.notallyxo.presentation.view.main.BaseNoteVHPreferences
import com.sleepyyui.notallyxo.presentation.view.misc.ItemListener
import com.sleepyyui.notallyxo.presentation.viewmodel.BaseNoteModel
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.NotallyXOPreferences
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.NotesView
import com.sleepyyui.notallyxo.utils.getExternalImagesDirectory
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class PickNoteActivity : LockedActivity<ActivityPickNoteBinding>(), ItemListener {

    protected lateinit var adapter: BaseNoteAdapter

    private val excludedNoteId by lazy { intent.getLongExtra(EXTRA_EXCLUDE_NOTE_ID, -1L) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val result = Intent()
        setResult(RESULT_CANCELED, result)

        val preferences = NotallyXOPreferences.getInstance(application)

        adapter =
            with(preferences) {
                BaseNoteAdapter(
                    Collections.emptySet(),
                    dateFormat.value,
                    notesSorting.value,
                    BaseNoteVHPreferences(
                        textSize.value,
                        maxItems.value,
                        maxLines.value,
                        maxTitle.value,
                        labelTagsHiddenInOverview.value,
                    ),
                    application.getExternalImagesDirectory(),
                    this@PickNoteActivity,
                )
            }

        binding.MainListView.apply {
            adapter = this@PickNoteActivity.adapter
            setHasFixedSize(true)
            layoutManager =
                if (preferences.notesView.value == NotesView.GRID) {
                    StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
                } else LinearLayoutManager(this@PickNoteActivity)
        }

        val database = NotallyXODatabase.getDatabase(application)

        val pinned = Header(getString(R.string.pinned))
        val others = Header(getString(R.string.others))
        val archived = Header(getString(R.string.archived))

        database.observe(this) {
            lifecycleScope.launch {
                val notes =
                    withContext(Dispatchers.IO) {
                        val raw =
                            it.getBaseNoteDao().getAllNotes().filter { it.id != excludedNoteId }
                        BaseNoteModel.transform(raw, pinned, others, archived)
                    }
                adapter.submitList(notes)
                binding.EmptyView.visibility =
                    if (notes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    override fun onClick(position: Int) {
        if (position != -1) {
            val note = (adapter.getItem(position) as BaseNote)
            val success = Intent()
            success.putExtra(EXTRA_PICKED_NOTE_ID, note.id)
            success.putExtra(EXTRA_PICKED_NOTE_TITLE, note.title)
            success.putExtra(EXTRA_PICKED_NOTE_TYPE, note.type.name)
            setResult(RESULT_OK, success)
            finish()
        }
    }

    override fun onLongClick(position: Int) {}

    companion object {
        const val EXTRA_EXCLUDE_NOTE_ID = "notallyxo.intent.extra.EXCLUDE_NOTE_ID"

        const val EXTRA_PICKED_NOTE_ID = "notallyxo.intent.extra.PICKED_NOTE_ID"
        const val EXTRA_PICKED_NOTE_TITLE = "notallyxo.intent.extra.PICKED_NOTE_TITLE"
        const val EXTRA_PICKED_NOTE_TYPE = "notallyxo.intent.extra.PICKED_NOTE_TYPE"
    }
}
