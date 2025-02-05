package com.example.textselector

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.textselector.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import com.google.android.material.snackbar.Snackbar
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val PREFS_KEY = "saved_texts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTextArea()
        setupSaveButton()

        // If started with a SEND intent, load the text
        if (intent?.action == "android.intent.action.SEND" && intent.type == "text/plain") {
            intent.getStringExtra("android.intent.extra.TEXT")?.let {
                binding.pinnedEditText.setText(it)
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_library -> {
                    showSavedSelections()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTextArea() {
        binding.pinnedEditText.doAfterTextChanged { text ->
            binding.saveFab.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun setupSaveButton() {
        binding.saveFab.apply {
            visibility = View.GONE
            setOnClickListener {
                animateSaveButton {
                    showSaveBottomSheet()
                }
            }
        }
    }

    private fun animateSaveButton(onAnimationEnd: () -> Unit) {
        binding.saveFab.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(100)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.saveFab.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction(onAnimationEnd)
                    .start()
            }
            .start()
    }

    private fun showSaveBottomSheet() {
        val selectedText = binding.pinnedEditText.text?.substring(
            binding.pinnedEditText.selectionStart,
            binding.pinnedEditText.selectionEnd
        ) ?: return

        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_save, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        // Setup views
        val nameInput = bottomSheetView.findViewById<View>(R.id.nameInput)
        val previewText = bottomSheetView.findViewById<View>(R.id.previewText)
        val saveButton = bottomSheetView.findViewById<View>(R.id.saveButton)
        val cancelButton = bottomSheetView.findViewById<View>(R.id.cancelButton)

        // Generate default name from first few words
        val defaultName = selectedText.take(50).replace("\n", " ")
            .split(" ").take(5).joinToString(" ")
        nameInput.text = defaultName

        // Show preview
        previewText.text = selectedText

        saveButton.setOnClickListener {
            val name = nameInput.text.toString().takeIf { it.isNotBlank() } ?: defaultName
            saveSelection(SavedSelection(
                name = name,
                text = selectedText
            ))
            bottomSheetDialog.dismiss()
            showSuccessSnackbar("Selection saved")
        }

        cancelButton.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun showSavedSelections() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(R.layout.dialog_saved_selections)
            .create()

        dialog.show()

        // Setup RecyclerView
        val recyclerView = dialog.findViewById<RecyclerView>(R.id.savedSelectionsRecyclerView)
        recyclerView?.layoutManager = LinearLayoutManager(this)

        val selections = loadSavedSelectionsFromPrefs().sortedByDescending { it.timestamp }
        recyclerView?.adapter = SavedSelectionsAdapter(
            selections = selections,
            onItemClick = { selection ->
                binding.pinnedEditText.setText(selection.text)
                dialog.dismiss()
            },
            onDeleteClick = { selection ->
                showDeleteConfirmationDialog(selection) {
                    recyclerView?.adapter = SavedSelectionsAdapter(
                        selections = loadSavedSelectionsFromPrefs().sortedByDescending { it.timestamp },
                        onItemClick = { s -> binding.pinnedEditText.setText(s.text); dialog.dismiss() },
                        onDeleteClick = { s -> showDeleteConfirmationDialog(s) {} },
                        onEditClick = { s -> showEditDialog(s) {} }
                    )
                }
            },
            onEditClick = { selection ->
                showEditDialog(selection) {
                    recyclerView?.adapter = SavedSelectionsAdapter(
                        selections = loadSavedSelectionsFromPrefs().sortedByDescending { it.timestamp },
                        onItemClick = { s -> binding.pinnedEditText.setText(s.text); dialog.dismiss() },
                        onDeleteClick = { s -> showDeleteConfirmationDialog(s) {} },
                        onEditClick = { s -> showEditDialog(s) {} }
                    )
                }
            }
        )
    }

    private fun showDeleteConfirmationDialog(selection: SavedSelection, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Selection")
            .setMessage("Are you sure you want to delete '${selection.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSelection(selection)
                onDeleted()
                showSuccessSnackbar("Selection deleted")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(selection: SavedSelection, onEdited: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet_save, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.nameInput)
        nameInput.setText(selection.name)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Selection")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val updatedSelection = selection.copy(
                    name = nameInput.text.toString().takeIf { it.isNotBlank() } ?: selection.name
                )
                updateSelection(updatedSelection)
                onEdited()
                showSuccessSnackbar("Selection updated")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSuccessSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(getColor(R.color.gold_primary))
            .setTextColor(getColor(R.color.light_text_primary))
            .show()
    }

    private fun saveSelection(selection: SavedSelection) {
        val selections = loadSavedSelectionsFromPrefs().toMutableList()
        selections.add(selection)
        saveSeletionsToPrefs(selections)
    }

    private fun updateSelection(selection: SavedSelection) {
        val selections = loadSavedSelectionsFromPrefs().toMutableList()
        val index = selections.indexOfFirst { it.id == selection.id }
        if (index != -1) {
            selections[index] = selection
            saveSeletionsToPrefs(selections)
        }
    }

    private fun deleteSelection(selection: SavedSelection) {
        val selections = loadSavedSelectionsFromPrefs().toMutableList()
        selections.removeAll { it.id == selection.id }
        saveSeletionsToPrefs(selections)
    }

    private fun loadSavedSelectionsFromPrefs(): List<SavedSelection> {
        val prefs = getSharedPreferences("TextSelectorPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray(prefs.getString(PREFS_KEY, "[]"))
        return List(jsonArray.length()) { index ->
            val obj = jsonArray.getJSONObject(index)
            SavedSelection(
                id = obj.optLong("id", System.currentTimeMillis()),
                name = obj.getString("name"),
                text = obj.getString("text"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }

    private fun saveSeletionsToPrefs(selections: List<SavedSelection>) {
        val jsonArray = JSONArray()
        selections.forEach { selection ->
            jsonArray.put(JSONObject().apply {
                put("id", selection.id)
                put("name", selection.name)
                put("text", selection.text)
                put("timestamp", selection.timestamp)
            })
        }
        getSharedPreferences("TextSelectorPrefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY, jsonArray.toString())
            .apply()
    }
}