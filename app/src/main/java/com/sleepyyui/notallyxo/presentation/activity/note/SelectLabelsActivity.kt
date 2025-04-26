package com.sleepyyui.notallyxo.presentation.activity.note

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.data.model.Label
import com.sleepyyui.notallyxo.databinding.ActivityLabelBinding
import com.sleepyyui.notallyxo.databinding.DialogInputBinding
import com.sleepyyui.notallyxo.presentation.activity.LockedActivity
import com.sleepyyui.notallyxo.presentation.add
import com.sleepyyui.notallyxo.presentation.setCancelButton
import com.sleepyyui.notallyxo.presentation.showAndFocus
import com.sleepyyui.notallyxo.presentation.showToast
import com.sleepyyui.notallyxo.presentation.view.main.label.SelectableLabelAdapter

class SelectLabelsActivity : LockedActivity<ActivityLabelBinding>() {

    private lateinit var selectedLabels: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLabelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedList = savedInstanceState?.getStringArrayList(EXTRA_SELECTED_LABELS)
        val passedList = requireNotNull(intent.getStringArrayListExtra(EXTRA_SELECTED_LABELS))
        selectedLabels = savedList ?: passedList

        val result = Intent()
        result.putExtra(EXTRA_SELECTED_LABELS, selectedLabels)
        setResult(RESULT_OK, result)

        setupToolbar()
        setupRecyclerView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(EXTRA_SELECTED_LABELS, selectedLabels)
    }

    private fun setupToolbar() {
        binding.Toolbar.apply {
            setNavigationOnClickListener { finish() }
            menu.add(R.string.add_label, R.drawable.add) { addLabel() }
        }
    }

    private fun addLabel() {
        val binding = DialogInputBinding.inflate(layoutInflater)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_label)
            .setView(binding.root)
            .setCancelButton()
            .setPositiveButton(R.string.save) { dialog, _ ->
                val value = binding.EditText.text.toString().trim()
                if (value.isNotEmpty()) {
                    val label = Label(value)
                    baseModel.insertLabel(label) { success ->
                        if (success) {
                            dialog.dismiss()
                        } else showToast(R.string.label_exists)
                    }
                }
            }
            .showAndFocus(binding.EditText, allowFullSize = true)
    }

    private fun setupRecyclerView() {
        val labelAdapter = SelectableLabelAdapter(selectedLabels)
        labelAdapter.onChecked = { position, checked ->
            if (position != -1) {
                val label = labelAdapter.currentList[position]
                if (checked) {
                    if (!selectedLabels.contains(label)) {
                        selectedLabels.add(label)
                    }
                } else selectedLabels.remove(label)
            }
        }

        binding.MainListView.apply {
            setHasFixedSize(true)
            adapter = labelAdapter
            addItemDecoration(
                DividerItemDecoration(this@SelectLabelsActivity, RecyclerView.VERTICAL)
            )
        }

        baseModel.labels.observe(this) { labels ->
            labelAdapter.submitList(labels)
            if (labels.isEmpty()) {
                binding.EmptyState.visibility = View.VISIBLE
            } else binding.EmptyState.visibility = View.INVISIBLE
        }
    }

    companion object {
        const val EXTRA_SELECTED_LABELS = "notallyxo.intent.extra.SELECTED_LABELS"
    }
}
