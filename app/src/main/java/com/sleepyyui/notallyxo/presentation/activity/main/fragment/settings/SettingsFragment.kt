package com.sleepyyui.notallyxo.presentation.activity.main.fragment.settings

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.ACTION_OPEN_DOCUMENT_TREE
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
import com.sleepyyui.notallyxo.NotallyXOApplication
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.data.imports.FOLDER_OR_FILE_MIMETYPE
import com.sleepyyui.notallyxo.data.imports.ImportSource
import com.sleepyyui.notallyxo.data.imports.txt.APPLICATION_TEXT_MIME_TYPES
import com.sleepyyui.notallyxo.data.model.SyncStatus
import com.sleepyyui.notallyxo.data.model.toText
import com.sleepyyui.notallyxo.databinding.DialogTextInputBinding
import com.sleepyyui.notallyxo.databinding.FragmentSettingsBinding
import com.sleepyyui.notallyxo.presentation.activity.conflict.ConflictResolutionActivity
import com.sleepyyui.notallyxo.presentation.activity.main.MainActivity
import com.sleepyyui.notallyxo.presentation.setCancelButton
import com.sleepyyui.notallyxo.presentation.setEnabledSecureFlag
import com.sleepyyui.notallyxo.presentation.setupImportProgressDialog
import com.sleepyyui.notallyxo.presentation.setupProgressDialog
import com.sleepyyui.notallyxo.presentation.showAndFocus
import com.sleepyyui.notallyxo.presentation.showDialog
import com.sleepyyui.notallyxo.presentation.showToast
import com.sleepyyui.notallyxo.presentation.view.misc.TextWithIconAdapter
import com.sleepyyui.notallyxo.presentation.viewmodel.BaseNoteModel
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.Constants.PASSWORD_EMPTY
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.LongPreference
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.NotallyXOPreferences
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.NotallyXOPreferences.Companion.EMPTY_PATH
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.PeriodicBackup
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.PeriodicBackup.Companion.BACKUP_MAX_MIN
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.PeriodicBackup.Companion.BACKUP_PERIOD_DAYS_MIN
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.PeriodicBackupsPreference
import com.sleepyyui.notallyxo.utils.MIME_TYPE_JSON
import com.sleepyyui.notallyxo.utils.MIME_TYPE_ZIP
import com.sleepyyui.notallyxo.utils.backup.exportPreferences
import com.sleepyyui.notallyxo.utils.catchNoBrowserInstalled
import com.sleepyyui.notallyxo.utils.getExtraBooleanFromBundleOrIntent
import com.sleepyyui.notallyxo.utils.getLastExceptionLog
import com.sleepyyui.notallyxo.utils.getLogFile
import com.sleepyyui.notallyxo.utils.getUriForFile
import com.sleepyyui.notallyxo.utils.reportBug
import com.sleepyyui.notallyxo.utils.security.CloudEncryptionService
import com.sleepyyui.notallyxo.utils.security.showBiometricOrPinPrompt
import com.sleepyyui.notallyxo.utils.sync.CloudSyncService
import com.sleepyyui.notallyxo.utils.sync.ConflictManager
import com.sleepyyui.notallyxo.utils.sync.SyncSettingsManager
import com.sleepyyui.notallyxo.utils.sync.SyncStatusIndicator
import com.sleepyyui.notallyxo.utils.wrapWithChooser
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private val model: BaseNoteModel by activityViewModels()

    private var _binding: FragmentSettingsBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var importBackupActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var importOtherActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportBackupActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var chooseBackupFolderActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var setupLockActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var disableLockActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportSettingsActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var importSettingsActivityResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var selectedImportSource: ImportSource
    private lateinit var syncStatusIndicator: SyncStatusIndicator

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        syncStatusIndicator = SyncStatusIndicator.getInstance(requireContext())

        model.preferences.apply {
            setupAppearance(binding)
            setupContentDensity(binding)
            setupBackup(binding)
            setupAutoBackups(binding)
            setupSecurity(binding)
            setupSettings(binding)
        }
        setupAbout(binding)
        setupCloudSync(binding)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActivityResultLaunchers()

        val showImportBackupsFolder =
            getExtraBooleanFromBundleOrIntent(
                savedInstanceState,
                EXTRA_SHOW_IMPORT_BACKUPS_FOLDER,
                false,
            )
        showImportBackupsFolder.let {
            if (it) {
                model.refreshBackupsFolder(
                    requireContext(),
                    askForUriPermissions = ::askForUriPermissions,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (model.showRefreshBackupsFolderAfterThemeChange) {
            outState.putBoolean(EXTRA_SHOW_IMPORT_BACKUPS_FOLDER, true)
        }
    }

    private fun setupActivityResultLaunchers() {
        importBackupActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { importBackup(it) }
                }
            }
        importOtherActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        model.importFromOtherApp(uri, selectedImportSource)
                    }
                }
            }
        exportBackupActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> model.exportBackup(uri) }
                }
            }
        chooseBackupFolderActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        model.setupBackupsFolder(uri)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            activity?.let {
                                val permission = Manifest.permission.POST_NOTIFICATIONS
                                if (
                                    it.checkSelfPermission(permission) !=
                                        PackageManager.PERMISSION_GRANTED
                                ) {
                                    MaterialAlertDialogBuilder(it)
                                        .setMessage(
                                            R.string.please_grant_notally_notification_auto_backup
                                        )
                                        .setNegativeButton(R.string.skip, null)
                                        .setPositiveButton(R.string.continue_) { _, _ ->
                                            it.requestPermissions(arrayOf(permission), 0)
                                        }
                                        .show()
                                }
                            }
                        }
                    }
                }
            }
        setupLockActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                showEnableBiometricLock()
            }
        disableLockActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                showDisableBiometricLock()
            }
        exportSettingsActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        if (requireContext().exportPreferences(model.preferences, uri)) {
                            showToast(R.string.export_settings_success)
                        } else {
                            showToast(R.string.export_settings_failure)
                        }
                    }
                }
            }
        importSettingsActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        model.importPreferences(
                            requireContext(),
                            uri,
                            ::askForUriPermissions,
                            { showToast(R.string.import_settings_success) },
                        ) {
                            showToast(R.string.import_settings_failure)
                        }
                    }
                }
            }
    }

    private fun importBackup(uri: Uri) {
        when (requireContext().contentResolver.getType(uri)) {
            "text/xml" -> {
                model.importXmlBackup(uri)
            }

            MIME_TYPE_ZIP -> {
                val layout = DialogTextInputBinding.inflate(layoutInflater, null, false)
                val password = model.preferences.backupPassword.value
                layout.InputText.apply {
                    if (password != PASSWORD_EMPTY) {
                        setText(password)
                    }
                    transformationMethod = PasswordTransformationMethod.getInstance()
                }
                layout.InputTextLayout.endIconMode = END_ICON_PASSWORD_TOGGLE
                layout.Message.apply {
                    setText(R.string.import_backup_password_hint)
                    visibility = View.VISIBLE
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.backup_password)
                    .setView(layout.root)
                    .setPositiveButton(R.string.import_backup) { dialog, _ ->
                        dialog.cancel()
                        val usedPassword = layout.InputText.text.toString()
                        model.importZipBackup(uri, usedPassword)
                    }
                    .setCancelButton()
                    .show()
            }
        }
    }

    private fun NotallyXOPreferences.setupAppearance(binding: FragmentSettingsBinding) {
        notesView.observe(viewLifecycleOwner) { value ->
            binding.View.setup(notesView, value, requireContext()) { newValue ->
                model.savePreference(notesView, newValue)
            }
        }

        theme.merge(useDynamicColors).observe(viewLifecycleOwner) {
            (themeValue, useDynamicColorsValue) ->
            binding.Theme.setup(
                theme,
                themeValue,
                useDynamicColorsValue,
                requireContext(),
                layoutInflater,
            ) { newThemeValue, newUseDynamicColorsValue ->
                model.savePreference(theme, newThemeValue)
                model.savePreference(useDynamicColors, newUseDynamicColorsValue)
                val packageManager = requireContext().packageManager
                val intent = packageManager.getLaunchIntentForPackage(requireContext().packageName)
                val componentName = intent!!.component
                val mainIntent =
                    Intent.makeRestartActivityTask(componentName).apply {
                        putExtra(MainActivity.EXTRA_FRAGMENT_TO_OPEN, R.id.Settings)
                    }
                mainIntent.setPackage(requireContext().packageName)
                requireContext().startActivity(mainIntent)
                Runtime.getRuntime().exit(0)
            }
        }

        dateFormat.merge(applyDateFormatInNoteView).observe(viewLifecycleOwner) {
            (dateFormatValue, applyDateFormatInEditNoteValue) ->
            binding.DateFormat.setup(
                dateFormat,
                dateFormatValue,
                applyDateFormatInEditNoteValue,
                requireContext(),
                layoutInflater,
            ) { newDateFormatValue, newApplyDateFormatInEditNote ->
                model.savePreference(dateFormat, newDateFormatValue)
                model.savePreference(applyDateFormatInNoteView, newApplyDateFormatInEditNote)
            }
        }

        textSize.observe(viewLifecycleOwner) { value ->
            binding.TextSize.setup(textSize, value, requireContext()) { newValue ->
                model.savePreference(textSize, newValue)
            }
        }

        notesSorting.observe(viewLifecycleOwner) { notesSort ->
            binding.NotesSortOrder.setup(
                notesSorting,
                notesSort,
                requireContext(),
                layoutInflater,
                model,
            )
        }

        listItemSorting.observe(viewLifecycleOwner) { value ->
            binding.CheckedListItemSorting.setup(listItemSorting, value, requireContext()) {
                newValue ->
                model.savePreference(listItemSorting, newValue)
            }
        }

        binding.MaxLabels.setup(maxLabels, requireContext()) { newValue ->
            model.savePreference(maxLabels, newValue)
        }

        startView.merge(model.labels).observe(viewLifecycleOwner) { (startViewValue, labelsValue) ->
            binding.StartView.setupStartView(
                startView,
                startViewValue,
                labelsValue,
                requireContext(),
                layoutInflater,
            ) { newValue ->
                model.savePreference(startView, newValue)
            }
        }
    }

    private fun NotallyXOPreferences.setupContentDensity(binding: FragmentSettingsBinding) {
        binding.apply {
            MaxTitle.setup(maxTitle, requireContext()) { newValue ->
                model.savePreference(maxTitle, newValue)
            }
            MaxItems.setup(maxItems, requireContext()) { newValue ->
                model.savePreference(maxItems, newValue)
            }

            MaxLines.setup(maxLines, requireContext()) { newValue ->
                model.savePreference(maxLines, newValue)
            }
            MaxLabels.setup(maxLabels, requireContext()) { newValue ->
                model.savePreference(maxLabels, newValue)
            }
            labelTagsHiddenInOverview.observe(viewLifecycleOwner) { value ->
                binding.LabelsHiddenInOverview.setup(
                    labelTagsHiddenInOverview,
                    value,
                    requireContext(),
                    layoutInflater,
                    R.string.labels_hidden_in_overview,
                ) { enabled ->
                    model.savePreference(labelTagsHiddenInOverview, enabled)
                }
            }
        }
    }

    private fun NotallyXOPreferences.setupBackup(binding: FragmentSettingsBinding) {
        binding.apply {
            ImportBackup.setOnClickListener {
                val intent =
                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .apply {
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(MIME_TYPE_ZIP, "text/xml"))
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        .wrapWithChooser(requireContext())
                importBackupActivityResultLauncher.launch(intent)
            }
            ImportOther.setOnClickListener { importFromOtherApp() }
            ExportBackup.setOnClickListener {
                val intent =
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .apply {
                            type = MIME_TYPE_ZIP
                            addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, "NotallyX Backup")
                        }
                        .wrapWithChooser(requireContext())
                exportBackupActivityResultLauncher.launch(intent)
            }
        }
        model.exportProgress.setupProgressDialog(this@SettingsFragment, R.string.exporting_backup)
        model.importProgress.setupImportProgressDialog(
            this@SettingsFragment,
            R.string.importing_backup,
        )
    }

    private fun NotallyXOPreferences.setupAutoBackups(binding: FragmentSettingsBinding) {
        backupsFolder.observe(viewLifecycleOwner) { value ->
            binding.BackupsFolder.setupBackupsFolder(
                value,
                requireContext(),
                ::displayChooseBackupFolderDialog,
            ) {
                model.disableBackups()
            }
        }
        backupOnSave.merge(backupsFolder).observe(viewLifecycleOwner) { (onSave, backupFolder) ->
            binding.BackupOnSave.setup(
                backupOnSave,
                onSave,
                requireContext(),
                layoutInflater,
                messageResId = R.string.auto_backup_on_save,
                enabled = backupFolder != EMPTY_PATH,
                disabledTextResId = R.string.auto_backups_folder_set,
            ) { enabled ->
                model.savePreference(backupOnSave, enabled)
            }
        }
        periodicBackups.merge(backupsFolder).observe(viewLifecycleOwner) {
            (periodicBackup, backupFolder) ->
            setupPeriodicBackup(
                binding,
                periodicBackup,
                backupFolder,
                periodicBackups,
                periodicBackupLastExecution,
            )
        }
    }

    private fun importFromOtherApp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_other_app)
            .setAdapter(
                TextWithIconAdapter(
                    requireContext(),
                    ImportSource.entries.toMutableList(),
                    { item -> getString(item.displayNameResId) },
                    ImportSource::iconResId,
                )
            ) { _, which ->
                selectedImportSource = ImportSource.entries[which]
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(selectedImportSource.helpTextResId)
                    .setPositiveButton(R.string.import_action) { dialog, _ ->
                        dialog.cancel()
                        when (selectedImportSource.mimeType) {
                            FOLDER_OR_FILE_MIMETYPE ->
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(selectedImportSource.displayNameResId)
                                    .setItems(
                                        arrayOf(
                                            getString(R.string.folder),
                                            getString(R.string.single_file),
                                        )
                                    ) { _, which ->
                                        when (which) {
                                            0 ->
                                                importOtherActivityResultLauncher.launch(
                                                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                                        .apply {
                                                            addCategory(Intent.CATEGORY_DEFAULT)
                                                        }
                                                        .wrapWithChooser(requireContext())
                                                )
                                            1 ->
                                                importOtherActivityResultLauncher.launch(
                                                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                                                        .apply {
                                                            type = "text/*"
                                                            addCategory(Intent.CATEGORY_OPENABLE)
                                                            putExtra(
                                                                Intent.EXTRA_MIME_TYPES,
                                                                arrayOf("text/*") +
                                                                    APPLICATION_TEXT_MIME_TYPES,
                                                            )
                                                        }
                                                        .wrapWithChooser(requireContext())
                                                )
                                        }
                                    }
                                    .setCancelButton()
                                    .show()
                            else ->
                                importOtherActivityResultLauncher.launch(
                                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                                        .apply {
                                            type = "application/*"
                                            putExtra(
                                                Intent.EXTRA_MIME_TYPES,
                                                arrayOf(selectedImportSource.mimeType),
                                            )
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                        }
                                        .wrapWithChooser(requireContext())
                                )
                        }
                    }
                    .also {
                        selectedImportSource.documentationUrl?.let<String, Unit> { docUrl ->
                            it.setNegativeButton(R.string.help) { _, _ ->
                                val intent =
                                    Intent(Intent.ACTION_VIEW)
                                        .apply { data = Uri.parse(docUrl) }
                                        .wrapWithChooser(requireContext())
                                startActivity(intent)
                            }
                        }
                    }
                    .setNeutralButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
                    .showAndFocus(allowFullSize = true)
            }
            .setCancelButton()
            .show()
    }

    private fun setupPeriodicBackup(
        binding: FragmentSettingsBinding,
        value: PeriodicBackup,
        backupFolder: String,
        preference: PeriodicBackupsPreference,
        lastExecutionPreference: LongPreference,
    ) {
        val periodicBackupsEnabled = value.periodInDays > 0 && backupFolder != EMPTY_PATH
        binding.PeriodicBackups.setupPeriodicBackup(
            periodicBackupsEnabled,
            requireContext(),
            layoutInflater,
            enabled = backupFolder != EMPTY_PATH,
        ) { enabled ->
            if (enabled) {
                val periodInDays =
                    preference.value.periodInDays.let {
                        if (it >= BACKUP_PERIOD_DAYS_MIN) it else BACKUP_PERIOD_DAYS_MIN
                    }
                val maxBackups =
                    preference.value.maxBackups.let {
                        if (it >= BACKUP_MAX_MIN) it else BACKUP_MAX_MIN
                    }
                model.savePreference(
                    preference,
                    preference.value.copy(periodInDays = periodInDays, maxBackups = maxBackups),
                )
            } else {
                model.savePreference(preference, preference.value.copy(periodInDays = 0))
            }
        }
        lastExecutionPreference.observe(viewLifecycleOwner) { time ->
            binding.PeriodicBackupLastExecution.apply {
                if (time != -1L) {
                    isVisible = true
                    text =
                        "${requireContext().getString(R.string.auto_backup_last)}: ${Date(time).toText()}"
                } else isVisible = false
            }
        }
        binding.PeriodicBackupsPeriodInDays.setup(
            value.periodInDays,
            R.string.backup_period_days,
            PeriodicBackup.BACKUP_PERIOD_DAYS_MIN,
            PeriodicBackup.BACKUP_PERIOD_DAYS_MAX,
            requireContext(),
            enabled = periodicBackupsEnabled,
        ) { newValue ->
            model.savePreference(preference, preference.value.copy(periodInDays = newValue))
        }
        binding.PeriodicBackupsMax.setup(
            value.maxBackups,
            R.string.max_backups,
            PeriodicBackup.BACKUP_MAX_MIN,
            PeriodicBackup.BACKUP_MAX_MAX,
            requireContext(),
            enabled = periodicBackupsEnabled,
        ) { newValue: Int ->
            model.savePreference(preference, preference.value.copy(maxBackups = newValue))
        }
    }

    private fun NotallyXOPreferences.setupSecurity(binding: FragmentSettingsBinding) {
        biometricLock.observe(viewLifecycleOwner) { value ->
            binding.BiometricLock.setup(
                biometricLock,
                value,
                requireContext(),
                model,
                ::showEnableBiometricLock,
                ::showDisableBiometricLock,
                ::showBiometricsNotSetupDialog,
            )
        }

        backupPassword.observe(viewLifecycleOwner) { value ->
            binding.BackupPassword.setupBackupPassword(
                backupPassword,
                value,
                requireContext(),
                layoutInflater,
            ) { newValue ->
                model.savePreference(backupPassword, newValue)
            }
        }

        secureFlag.observe(viewLifecycleOwner) { value ->
            binding.SecureFlag.setup(secureFlag, value, requireContext(), layoutInflater) { newValue
                ->
                model.savePreference(secureFlag, newValue)
                activity?.setEnabledSecureFlag(newValue)
            }
        }
    }

    private fun NotallyXOPreferences.setupSettings(binding: FragmentSettingsBinding) {
        binding.apply {
            ImportSettings.setOnClickListener {
                showDialog(R.string.import_settings_message, R.string.import_action) { _, _ ->
                    val intent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .apply {
                                type = MIME_TYPE_JSON
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "NotallyX_Settings.json")
                            }
                            .wrapWithChooser(requireContext())
                    importSettingsActivityResultLauncher.launch(intent)
                }
            }
            ExportSettings.setOnClickListener {
                showDialog(R.string.export_settings_message, R.string.export) { _, _ ->
                    val intent =
                        Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .apply {
                                type = MIME_TYPE_JSON
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "NotallyX_Settings.json")
                            }
                            .wrapWithChooser(requireContext())
                    exportSettingsActivityResultLauncher.launch(intent)
                }
            }
            ResetSettings.setOnClickListener {
                showDialog(R.string.reset_settings_message, R.string.reset_settings) { _, _ ->
                    model.resetPreferences { _ -> showToast(R.string.reset_settings_success) }
                }
            }
            dataInPublicFolder.observe(viewLifecycleOwner) { value ->
                binding.DataInPublicFolder.setup(
                    dataInPublicFolder,
                    value,
                    requireContext(),
                    layoutInflater,
                    R.string.data_in_public_message,
                ) { enabled ->
                    if (enabled) {
                        model.enableDataInPublic()
                    } else {
                        model.disableDataInPublic()
                    }
                }
            }
            AutoSaveAfterIdle.setupAutoSaveIdleTime(autoSaveAfterIdleTime, requireContext()) {
                newValue ->
                model.savePreference(autoSaveAfterIdleTime, newValue)
            }

            ClearData.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.clear_data_message)
                    .setPositiveButton(R.string.delete_all) { _, _ -> model.deleteAll() }
                    .setCancelButton()
                    .show()
            }
        }
        model.deletionProgress.setupProgressDialog(this@SettingsFragment, R.string.deleting_files)
    }

    private fun setupAbout(binding: FragmentSettingsBinding) {
        binding.apply {
            SendFeedback.setOnClickListener {
                val options =
                    arrayOf(
                        getString(R.string.report_bug),
                        getString(R.string.make_feature_request),
                        getString(R.string.send_feedback),
                    )
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.send_feedback)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> {
                                val app = requireContext().applicationContext as Application
                                val logs = app.getLastExceptionLog()
                                reportBug(logs)
                            }

                            1 ->
                                requireContext().catchNoBrowserInstalled {
                                    startActivity(
                                        Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(
                                                    "https://github.com/SleepyYui/NotallyX/issues/new?labels=enhancement&template=feature_request.md"
                                                ),
                                            )
                                            .wrapWithChooser(requireContext())
                                    )
                                }
                            2 -> {
                                val intent =
                                    Intent(Intent.ACTION_SEND)
                                        .apply {
                                            selector =
                                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                                            putExtra(
                                                Intent.EXTRA_EMAIL,
                                                arrayOf("notallyxo@yahoo.com"),
                                            )
                                            putExtra(Intent.EXTRA_SUBJECT, "NotallyX [Feedback]")
                                            val app =
                                                requireContext().applicationContext as Application
                                            val log = app.getLogFile()
                                            if (log.exists()) {
                                                val uri = app.getUriForFile(log)
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                            }
                                        }
                                        .wrapWithChooser(requireContext())
                                try {
                                    startActivity(intent)
                                } catch (exception: ActivityNotFoundException) {
                                    showToast(R.string.install_an_email)
                                }
                            }
                        }
                    }
                    .setCancelButton()
                    .show()
            }
            Rate.setOnClickListener {
                openLink("https://play.google.com/store/apps/details?id=com.sleepyyui.notallyxo")
            }
            SourceCode.setOnClickListener { openLink("https://github.com/SleepyYui/NotallyXO") }
            Libraries.setOnClickListener {
                val libraries =
                    arrayOf(
                        "Glide",
                        "Pretty Time",
                        "SwipeDrawer",
                        "Work Manager",
                        "Subsampling Scale ImageView",
                        "Material Components for Android",
                        "SQLCipher",
                        "Zip4J",
                        "AndroidFastScroll",
                        "ColorPickerView",
                    )
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.libraries)
                    .setItems(libraries) { _, which ->
                        when (which) {
                            0 -> openLink("https://github.com/bumptech/glide")
                            1 -> openLink("https://github.com/ocpsoft/prettytime")
                            2 -> openLink("https://leaqi.github.io/SwipeDrawer_en")
                            3 ->
                                openLink(
                                    "https://developer.android.com/jetpack/androidx/releases/work"
                                )
                            4 ->
                                openLink(
                                    "https://github.com/davemorrissey/subsampling-scale-image-view"
                                )
                            5 ->
                                openLink(
                                    "https://github.com/material-components/material-components-android"
                                )
                            6 -> openLink("https://github.com/sqlcipher/sqlcipher")
                            7 -> openLink("https://github.com/srikanth-lingala/zip4j")
                            8 -> openLink("https://github.com/zhanghai/AndroidFastScroll")
                            9 -> openLink("https://github.com/skydoves/ColorPickerView")
                        }
                    }
                    .setCancelButton()
                    .show()
            }
            Donate.setOnClickListener { openLink("https://ko-fi.com/sleepyyui") }

            try {
                val pInfo =
                    requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
                val version = pInfo.versionName
                VersionText.text = "v$version"
            } catch (_: PackageManager.NameNotFoundException) {}
        }
    }

    private fun setupCloudSync(binding: FragmentSettingsBinding) {
        val syncSettingsManager = SyncSettingsManager.getInstance(requireContext())
        val syncStatusIndicator = SyncStatusIndicator.getInstance(requireContext())
        val conflictManager = ConflictManager.getInstance(requireContext())

        fun updateDependentPreferenceStates(isEnabled: Boolean) {
            _binding?.let { b ->
                b.CloudServerConfig.setup(
                    title = getString(R.string.cloud_server_config),
                    subtitle = getString(R.string.cloud_server_config_message),
                    isEnabled = isEnabled,
                )
                b.CloudEncryptionPassword.setup(
                    title = getString(R.string.cloud_encryption_password),
                    subtitle = getString(R.string.cloud_encryption_password_message),
                    isEnabled = isEnabled,
                )
                b.CloudSyncOptions.setup(
                    title = getString(R.string.cloud_sync_options),
                    subtitle = getString(R.string.cloud_sync_options_message),
                    isEnabled = isEnabled,
                )
                val isConfigured = syncSettingsManager.areSettingsConfigured()
                b.CloudSyncNow.isEnabled = isEnabled && isConfigured
                b.CloudSyncNow.alpha = if (b.CloudSyncNow.isEnabled) 1.0f else 0.5f

                // Show or hide the resolve conflicts button based on whether there are any
                // conflicts
                updateConflictButton()

                if (!isEnabled) {
                    b.CloudSyncStatus.visibility = View.GONE
                } else if (!isConfigured) {
                    b.CloudSyncStatus.text = getString(R.string.cloud_not_configured)
                    b.CloudSyncStatus.visibility = View.VISIBLE
                } else {
                    syncStatusIndicator.updateStatusText(b.CloudSyncStatus)
                }
            }
        }

        val cloudSyncSwitch =
            inflatePreferenceSwitch(
                layoutInflater,
                null,
                getString(R.string.cloud_sync_enabled),
                getString(R.string.cloud_sync_enabled_message),
                syncSettingsManager.isSyncEnabled,
                true,
            ) { isChecked ->
                syncSettingsManager.isSyncEnabled = isChecked
                updateDependentPreferenceStates(isChecked)

                if (!isChecked) {
                    syncStatusIndicator.updateStatus(SyncStatus.NOT_CONFIGURED)
                } else if (!syncSettingsManager.areSettingsConfigured()) {
                    syncStatusIndicator.updateStatus(SyncStatus.NOT_CONFIGURED)
                } else {
                    syncStatusIndicator.updateStatus(SyncStatus.IDLE)
                }
            }

        binding.CloudSyncEnabledContainer.removeAllViews()
        binding.CloudSyncEnabledContainer.addView(cloudSyncSwitch.root)

        updateDependentPreferenceStates(syncSettingsManager.isSyncEnabled)

        binding.CloudServerConfig.root.setOnClickListener {
            if (syncSettingsManager.isSyncEnabled) showServerConfigDialog(syncSettingsManager)
        }
        binding.CloudEncryptionPassword.root.setOnClickListener {
            if (syncSettingsManager.isSyncEnabled) showEncryptionPasswordDialog(syncSettingsManager)
        }
        binding.CloudSyncOptions.root.setOnClickListener {
            if (syncSettingsManager.isSyncEnabled) showSyncOptionsDialog(syncSettingsManager)
        }

        binding.CloudSyncNow.setOnClickListener {
            if (binding.CloudSyncNow.isEnabled) {
                syncStatusIndicator.updateStatus(SyncStatus.SYNCING)
                syncStatusIndicator.updateStatusText(binding.CloudSyncStatus)

                binding.CloudSyncNow.isEnabled = false
                binding.CloudSyncNow.alpha = 0.5f

                // Actually perform sync using CloudSyncService
                val cloudSyncService = CloudSyncService.getInstance(requireContext())

                // Launch in a coroutine scope to perform the sync operation
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val result = cloudSyncService.syncNotes()

                        // Update UI on main thread
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                // Only update to success if the operation actually succeeded
                                syncStatusIndicator.updateStatus(
                                    SyncStatus.SYNCED,
                                    isTemporary = true,
                                )
                            }
                            // Status indicator already updated by the service if it failed

                            binding.CloudSyncNow.isEnabled = true
                            binding.CloudSyncNow.alpha = 1.0f
                            syncStatusIndicator.updateStatusText(binding.CloudSyncStatus)

                            // Update conflict button visibility in case conflicts were detected
                            updateConflictButton()
                        }
                    } catch (e: Exception) {
                        // Handle unexpected errors
                        withContext(Dispatchers.Main) {
                            syncStatusIndicator.updateStatus(
                                SyncStatus.FAILED,
                                e.message ?: getString(R.string.cloud_connection_error),
                            )
                            binding.CloudSyncNow.isEnabled = true
                            binding.CloudSyncNow.alpha = 1.0f
                            syncStatusIndicator.updateStatusText(binding.CloudSyncStatus)
                        }
                    }
                }
            }
        }

        // Add the Resolve Conflicts button
        binding.CloudResolveConflicts.setOnClickListener {
            startActivity(ConflictResolutionActivity.createIntent(requireContext()))
        }

        // Initialize the button state
        updateConflictButton()

        if (syncSettingsManager.isSyncEnabled) {
            syncStatusIndicator.updateStatusText(binding.CloudSyncStatus)
        } else {
            binding.CloudSyncStatus.visibility = View.GONE
        }

        // Listen for status changes
        syncStatusIndicator.syncStatus.observe(viewLifecycleOwner) { status ->
            _binding?.let { b ->
                if (syncSettingsManager.isSyncEnabled) {
                    syncStatusIndicator.updateStatusText(b.CloudSyncStatus)
                }

                val canSync =
                    status != SyncStatus.SYNCING &&
                        syncSettingsManager.isSyncEnabled &&
                        syncSettingsManager.areSettingsConfigured()

                b.CloudSyncNow.isEnabled = canSync
                b.CloudSyncNow.alpha = if (canSync) 1.0f else 0.5f

                // Update conflict button when status changes (conflicts might be detected)
                updateConflictButton()
            }
        }

        // Listen for conflict changes
        conflictManager.pendingConflicts.observe(viewLifecycleOwner) { conflicts ->
            updateConflictButton()
        }
    }

    /**
     * Updates the visibility and state of the conflict resolution button based on whether there are
     * any pending conflicts.
     */
    private fun updateConflictButton() {
        _binding?.let { b ->
            val conflictManager = ConflictManager.getInstance(requireContext())
            val hasConflicts = conflictManager.hasConflicts()
            val conflictCount = conflictManager.getConflictCount()

            if (hasConflicts) {
                b.CloudResolveConflicts.visibility = View.VISIBLE
                b.CloudResolveConflicts.text =
                    resources.getQuantityString(
                        R.plurals.cloud_sync_conflicts_detected,
                        conflictCount,
                        conflictCount,
                    )
            } else {
                b.CloudResolveConflicts.visibility = View.GONE
            }
        }
    }

    private fun showServerConfigDialog(syncSettingsManager: SyncSettingsManager) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_server_config, null)

        val serverUrlInput = dialogView.findViewById<TextInputEditText>(R.id.serverUrlInput)
        val serverPortInput = dialogView.findViewById<TextInputEditText>(R.id.serverPortInput)
        val authTokenInput = dialogView.findViewById<TextInputEditText>(R.id.authTokenInput)

        serverUrlInput.setText(syncSettingsManager.serverUrl)
        serverPortInput.setText(syncSettingsManager.serverPort.toString())
        authTokenInput.setText(syncSettingsManager.authToken)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cloud_server_config)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val serverUrl = serverUrlInput.text.toString().trim()
                val serverPortText = serverPortInput.text.toString().trim()
                val serverPort = serverPortText.toIntOrNull() ?: 8080
                val authToken = authTokenInput.text.toString().trim()

                syncSettingsManager.serverUrl = serverUrl
                syncSettingsManager.serverPort = serverPort
                syncSettingsManager.authToken = authToken

                val configComplete = syncSettingsManager.areSettingsConfigured()
                if (configComplete) {
                    syncStatusIndicator.updateStatus(SyncStatus.IDLE)
                } else {
                    syncStatusIndicator.updateStatus(SyncStatus.NOT_CONFIGURED)
                }

                _binding?.let { b ->
                    b.CloudSyncNow.isEnabled = configComplete
                    b.CloudSyncNow.alpha = if (configComplete) 1.0f else 0.5f
                    syncStatusIndicator.updateStatusText(b.CloudSyncStatus)
                }

                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showEncryptionPasswordDialog(syncSettingsManager: SyncSettingsManager) {
        val layout = DialogTextInputBinding.inflate(layoutInflater, null, false)

        layout.InputText.apply {
            transformationMethod = PasswordTransformationMethod.getInstance()
            hint = getString(R.string.cloud_encryption_password)
        }

        layout.InputTextLayout.endIconMode = END_ICON_PASSWORD_TOGGLE
        layout.Message.apply {
            setText(R.string.cloud_encryption_password_message)
            visibility = View.VISIBLE
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cloud_encryption_password)
            .setView(layout.root)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val password = layout.InputText.text.toString()

                if (password.isNotEmpty()) {
                    try {
                        val cloudEncryptionService = CloudEncryptionService()
                        val salt = cloudEncryptionService.generateSalt()
                        syncSettingsManager.encryptionSalt =
                            android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)

                        if (syncSettingsManager.userId.isEmpty()) {
                            syncSettingsManager.userId = cloudEncryptionService.createUserId()
                        }

                        Toast.makeText(
                                requireContext(),
                                "Encryption password set",
                                Toast.LENGTH_SHORT,
                            )
                            .show()

                        val configComplete = syncSettingsManager.areSettingsConfigured()
                        if (configComplete) {
                            syncStatusIndicator.updateStatus(SyncStatus.IDLE)
                        } else {
                            syncStatusIndicator.updateStatus(SyncStatus.NOT_CONFIGURED)
                        }

                        _binding?.let { b ->
                            b.CloudSyncNow.isEnabled = configComplete
                            b.CloudSyncNow.alpha = if (configComplete) 1.0f else 0.5f
                            syncStatusIndicator.updateStatusText(b.CloudSyncStatus)
                        }
                    } catch (e: Exception) {
                        syncStatusIndicator.updateStatus(
                            SyncStatus.FAILED,
                            "Failed to set encryption: ${e.message}",
                        )
                        Toast.makeText(
                                requireContext(),
                                "Error setting encryption",
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                } else {
                    syncSettingsManager.encryptionSalt = ""
                    Toast.makeText(
                            requireContext(),
                            "Encryption password cleared",
                            Toast.LENGTH_SHORT,
                        )
                        .show()

                    syncStatusIndicator.updateStatus(SyncStatus.NOT_CONFIGURED)
                    _binding?.let { b ->
                        b.CloudSyncNow.isEnabled = false
                        b.CloudSyncNow.alpha = 0.5f
                        syncStatusIndicator.updateStatusText(b.CloudSyncStatus)
                    }
                }

                dialog.dismiss()
            }
            .setNeutralButton(R.string.clear) { dialog, _ ->
                syncSettingsManager.encryptionSalt = ""
                Toast.makeText(requireContext(), "Encryption password cleared", Toast.LENGTH_SHORT)
                    .show()

                syncStatusIndicator.updateStatus(SyncStatus.NOT_CONFIGURED)
                _binding?.let { b ->
                    b.CloudSyncNow.isEnabled = false
                    b.CloudSyncNow.alpha = 0.5f
                    syncStatusIndicator.updateStatusText(b.CloudSyncStatus)
                }

                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showSyncOptionsDialog(syncSettingsManager: SyncSettingsManager) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sync_options, null)

        val autoSyncSwitch = dialogView.findViewById<SwitchCompat>(R.id.autoSyncSwitch)
        val wifiOnlySwitch = dialogView.findViewById<SwitchCompat>(R.id.wifiOnlySwitch)

        autoSyncSwitch.isChecked = syncSettingsManager.isAutoSyncEnabled
        wifiOnlySwitch.isChecked = syncSettingsManager.isWifiOnlySync
        wifiOnlySwitch.isEnabled = autoSyncSwitch.isChecked

        autoSyncSwitch.setOnCheckedChangeListener { _, isChecked ->
            wifiOnlySwitch.isEnabled = isChecked
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cloud_sync_options)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                syncSettingsManager.isAutoSyncEnabled = autoSyncSwitch.isChecked
                syncSettingsManager.isWifiOnlySync = wifiOnlySwitch.isChecked
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun displayChooseBackupFolderDialog() {
        showDialog(R.string.auto_backups_folder_hint, R.string.choose_folder) { _, _ ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).wrapWithChooser(requireContext())
            chooseBackupFolderActivityResultLauncher.launch(intent)
        }
    }

    private fun showEnableBiometricLock() {
        showBiometricOrPinPrompt(
            false,
            setupLockActivityResultLauncher,
            R.string.enable_lock_title,
            R.string.enable_lock_description,
            onSuccess = { cipher ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    model.enableBiometricLock(cipher)
                }
                val app = (activity?.application as NotallyXOApplication)
                app.locked.value = false
                showToast(R.string.biometrics_setup_success)
            },
        ) {
            showBiometricsNotSetupDialog()
        }
    }

    private fun showDisableBiometricLock() {
        showBiometricOrPinPrompt(
            true,
            disableLockActivityResultLauncher,
            R.string.disable_lock_title,
            R.string.disable_lock_description,
            model.preferences.iv.value!!,
            onSuccess = { cipher ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    model.disableBiometricLock(cipher)
                }
                showToast(R.string.biometrics_disable_success)
            },
        ) {}
    }

    private fun showBiometricsNotSetupDialog() {
        showDialog(R.string.biometrics_not_setup, R.string.tap_to_set_up) { _, _ ->
            val intent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Intent(Settings.ACTION_FINGERPRINT_ENROLL)
                } else {
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                }
            setupLockActivityResultLauncher.launch(intent)
        }
    }

    private fun openLink(link: String) {
        val uri = Uri.parse(link)
        val intent = Intent(Intent.ACTION_VIEW, uri).wrapWithChooser(requireContext())
        startActivity(intent)
    }

    private fun askForUriPermissions(uri: Uri) {
        chooseBackupFolderActivityResultLauncher.launch(
            Intent(ACTION_OPEN_DOCUMENT_TREE).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                }
            }
        )
    }

    companion object {
        const val EXTRA_SHOW_IMPORT_BACKUPS_FOLDER =
            "notallyxo.intent.extra.SHOW_IMPORT_BACKUPS_FOLDER"
    }
}
