package com.sleepyyui.notallyxo.presentation.activity.main.fragment.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import com.sleepyyui.notallyxo.databinding.PreferenceSwitchBinding

/**
 * Creates a switch preference with the given title and subtitle.
 *
 * This is specifically designed for toggleable preferences like cloud sync settings.
 *
 * @param title The title of the preference
 * @param subtitle The subtitle with descriptive text
 * @param isChecked Whether the switch is checked
 * @param isEnabled Whether the preference is enabled (can be interacted with)
 * @param onCheckedChange Callback when the switch is toggled
 * @return The inflated and configured PreferenceSwitchBinding
 */
fun inflatePreferenceSwitch(
    inflater: LayoutInflater,
    parent: ViewGroup?,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    isEnabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
): PreferenceSwitchBinding {
    val binding = PreferenceSwitchBinding.inflate(inflater, parent, false)

    // Configure the switch preference
    binding.Title.text = title
    binding.Value.text = subtitle
    binding.Value.visibility =
        if (subtitle.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    binding.PreferenceSwitch.isChecked = isChecked
    binding.root.isEnabled = isEnabled
    binding.PreferenceSwitch.isEnabled = isEnabled

    // Set alpha for disabled state
    binding.root.alpha = if (isEnabled) 1.0f else 0.5f

    // Set click listener on the root to toggle the switch
    binding.root.setOnClickListener {
        if (isEnabled) {
            val newState = !binding.PreferenceSwitch.isChecked
            binding.PreferenceSwitch.isChecked = newState
            onCheckedChange(newState)
        }
    }

    return binding
}

/** Extension property to get the switch component from a PreferenceSwitchBinding */
val PreferenceSwitchBinding.switch: SwitchCompat
    get() = PreferenceSwitch
