package com.sleepyyui.notallyx.presentation.view.main.reminder

import androidx.recyclerview.widget.RecyclerView
import com.sleepyyui.notallyx.R
import com.sleepyyui.notallyx.data.model.Reminder
import com.sleepyyui.notallyx.data.model.toText
import com.sleepyyui.notallyx.databinding.RecyclerReminderBinding

class ReminderVH(
    private val binding: RecyclerReminderBinding,
    private val listener: ReminderListener,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(value: Reminder) {
        binding.apply {
            DateTime.text = value.dateTime.toText()
            Repetition.text =
                value.repetition?.toText(itemView.context)
                    ?: itemView.context.getText(R.string.reminder_no_repetition)
            EditButton.setOnClickListener { listener.edit(value) }
            DeleteButton.setOnClickListener { listener.delete(value) }
        }
    }
}
