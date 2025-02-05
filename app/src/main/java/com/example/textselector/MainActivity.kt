// app/src/main/java/com/example/textselector/MainActivity.kt
package com.example.textselector

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.textselector.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.material.snackbar.Snackbar
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

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
        setupSearchNavigation()

        // If started with a SEND intent, load the text
        if (intent?.action == "android.intent.action.SEND" && intent.type == "text/plain") {
            intent.getStringExtra("android.intent.extra.TEXT")?.let {
                binding.pinnedEditText.setText(it)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // Set up the SearchView.
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    binding.pinnedEditText.highlightSearch(it)
                    updateSearchNavigation() // update the counter text
                    binding.searchNavigation.visibility = View.VISIBLE
                }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    binding.pinnedEditText.clearHighlights()
                    binding.searchNavigation.visibility = View.GONE
                }
                return true
            }
        })
        return true
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

        // Get views with proper types
        val nameInput = bottomSheetView.findViewById<TextInputEditText>(R.id.nameInput)
        val previewText = bottomSheetView.findViewById<TextView>(R.id.previewText)
        val saveButton = bottomSheetView.findViewById<MaterialButton>(R.id.saveButton)
        val cancelButton = bottomSheetView.findViewById<MaterialButton>(R.id.cancelButton)

        // Generate a default name and set it using setText()
        val defaultName = selectedText.take(50).replace("\n", " ")
            .split(" ").take(5).joinToString(" ")
        nameInput.setText(defaultName)

        // Show preview text
        previewText.text = selectedText

        saveButton.setOnClickListener {
            val name = nameInput.text.toString().takeIf { it.isNotBlank() } ?: defaultName
            saveSelection(SavedSelection(name = name, text = selectedText))
            bottomSheetDialog.dismiss()
            showSuccessSnackbar("Selection saved")
        }

        cancelButton.setOnClickListener { bottomSheetDialog.dismiss() }

        bottomSheetDialog.show()
    }

    private fun showSavedSelections() {
        try {
            // Inflate the dialog layout containing the RecyclerView.
            val dialogView = layoutInflater.inflate(R.layout.dialog_saved_selections, null)
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.savedSelectionsRecyclerView)
            recyclerView.layoutManager = LinearLayoutManager(this)
            val selections = loadSavedSelectionsFromPrefs().sortedByDescending { it.timestamp }

            val dialog = MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create()

            // Declare adapter as nullable so it can be captured in lambdas.
            var adapter: SavedSelectionsAdapter? = null
            adapter = SavedSelectionsAdapter(
                selections = selections,
                onItemClick = { selection ->
                    // Clear any pins or search state before loading the saved text.
                    binding.pinnedEditText.clearSelectionPins()
                    binding.pinnedEditText.clearHighlights()
                    binding.pinnedEditText.setText(selection.text)
                    dialog.dismiss()
                },
                onDeleteClick = { selection ->
                    showDeleteConfirmationDialog(selection) {
                        adapter?.updateSelections(loadSavedSelectionsFromPrefs().sortedByDescending { it.timestamp })
                    }
                },
                onEditClick = { selection ->
                    showEditDialog(selection) {
                        adapter?.updateSelections(loadSavedSelectionsFromPrefs().sortedByDescending { it.timestamp })
                    }
                }
            )
            recyclerView.adapter = adapter
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(binding.root, "Error loading selections", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupSearchNavigation() {
        // Access the search navigation container from the binding
        val btnPrev = binding.searchNavigation.findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = binding.searchNavigation.findViewById<ImageButton>(R.id.btnNext)

        btnPrev.setOnClickListener {
            binding.pinnedEditText.previousSearchResult()
            updateSearchNavigation()
        }
        btnNext.setOnClickListener {
            binding.pinnedEditText.nextSearchResult()
            updateSearchNavigation()
        }
    }

    private fun updateSearchNavigation() {
        val count = binding.pinnedEditText.getSearchResultsCount()
        val current = binding.pinnedEditText.getCurrentSearchIndex()
        val txtSearchCount = binding.searchNavigation.findViewById<TextView>(R.id.txtSearchCount)
        txtSearchCount.text = "$current/$count"
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
        saveSelectionsToPrefs(selections)
    }

    private fun updateSelection(selection: SavedSelection) {
        val selections = loadSavedSelectionsFromPrefs().toMutableList()
        val index = selections.indexOfFirst { it.id == selection.id }
        if (index != -1) {
            selections[index] = selection
            saveSelectionsToPrefs(selections)
        }
    }

    private fun deleteSelection(selection: SavedSelection) {
        val selections = loadSavedSelectionsFromPrefs().toMutableList()
        selections.removeAll { it.id == selection.id }
        saveSelectionsToPrefs(selections)
    }

    private fun loadSavedSelectionsFromPrefs(): List<SavedSelection> {
        val prefs = getSharedPreferences("TextSelectorPrefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString(PREFS_KEY, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            List(jsonArray.length()) { index ->
                val obj = jsonArray.getJSONObject(index)
                SavedSelection(
                    id = obj.optLong("id", System.currentTimeMillis()),
                    name = obj.getString("name"),
                    text = obj.getString("text"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveSelectionsToPrefs(selections: List<SavedSelection>) {
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
