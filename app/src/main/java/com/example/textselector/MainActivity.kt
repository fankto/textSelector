// app/src/main/java/com/example/textselector/MainActivity.kt
package com.example.textselector

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.textselector.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.material.snackbar.Snackbar
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
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
        Log.d("MainActivity", "onCreate started")
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        Log.d("MainActivity", "Binding inflated")
        setContentView(binding.root)

        // If the text area is empty, set some default text
        if (binding.pinnedEditText.text.isNullOrEmpty()) {
            Log.d("MainActivity", "Setting default text")
            binding.pinnedEditText.setText(
                "The quick brown fox jumps over the lazy dog.\n" +
                        "This is a test string for searching. Fox, dog, and quick."
            )
        }

        Log.d("MainActivity", "About to setup toolbar")
        setupToolbar()
        Log.d("MainActivity", "About to setup text area")
        setupTextArea()
        Log.d("MainActivity", "About to setup save button")
        setupSaveButton()
        Log.d("MainActivity", "About to setup search navigation")
        setupSearchNavigation()
        Log.d("MainActivity", "onCreate complete")

        // Handle SEND intent
        if (intent?.action == "android.intent.action.SEND" && intent.type == "text/plain") {
            Log.d("MainActivity", "Handling SEND intent")
            intent.getStringExtra("android.intent.extra.TEXT")?.let {
                binding.pinnedEditText.setText(it)
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d("MainActivity", "onCreateOptionsMenu started")
        try {
            menuInflater.inflate(R.menu.menu_main, menu)
            Log.d("MainActivity", "Menu inflated")

            val searchItem = menu.findItem(R.id.action_search)
            if (searchItem == null) {
                Log.e("MainActivity", "Search item not found in menu!")
                return true
            }
            Log.d("MainActivity", "Found search item: ${searchItem.title}")

            val actionView = searchItem.actionView
            if (actionView == null) {
                Log.e("MainActivity", "Search item has no action view!")
                return true
            }
            Log.d("MainActivity", "Action view type: ${actionView.javaClass.name}")

            val searchView = actionView as? SearchView
            if (searchView == null) {
                Log.e("MainActivity", "Action view is not a SearchView!")
                return true
            }
            Log.d("MainActivity", "Successfully cast to SearchView")

            searchView.apply {
                Log.d("MainActivity", "Configuring SearchView")
                isIconified = false
                queryHint = getString(R.string.search_term)

                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        Log.d("MainActivity", "Search submitted: $query")
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        Log.d("MainActivity", "Text changed: $newText")
                        val queryText = newText.orEmpty()

                        try {
                            Log.d("MainActivity", "Calling updateSearch with: $queryText")
                            binding.pinnedEditText.updateSearch(queryText)
                            val resultCount = binding.pinnedEditText.getSearchResultsCount()
                            Log.d("MainActivity", "Got $resultCount results")

                            binding.searchNavigation.visibility = if (resultCount > 0) View.VISIBLE else View.GONE
                            binding.bottomBanner.visibility = if (resultCount > 0) View.VISIBLE else View.GONE

                            if (resultCount > 0) {
                                updateSearchNavigation()
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Search error", e)
                        }

                        return true
                    }
                })
                Log.d("MainActivity", "Search listener setup complete")
            }

            Log.d("MainActivity", "onCreateOptionsMenu completed successfully")
            return true
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreateOptionsMenu", e)
            return false
        }
    }

    private fun setupToolbar() {
        Log.d("MainActivity", "Setting up toolbar")
        setSupportActionBar(binding.toolbar)  // Add this line if not already present
        Log.d("MainActivity", "Set support action bar")

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            Log.d("MainActivity", "Menu item clicked: ${menuItem.itemId}")
            when (menuItem.itemId) {
                R.id.action_library -> {
                    Log.d("MainActivity", "Library action selected")
                    showSavedSelections()
                    true
                }
                else -> {
                    Log.d("MainActivity", "Unknown menu item: ${menuItem.itemId}")
                    false
                }
            }
        }
        Log.d("MainActivity", "Toolbar setup complete")
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
            val recyclerView =
                dialogView.findViewById<RecyclerView>(R.id.savedSelectionsRecyclerView)
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
                    binding.pinnedEditText.clearSearchHighlights()
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
        val bannerText = if (count > 0) {
            "PIN ACTIVE – $current / $count"
        } else {
            "PIN ACTIVE"
        }
        binding.tvBannerInfo.text = bannerText
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
        // Hide the bottom-sheet buttons so that only the dialog's buttons show.
        dialogView.findViewById<MaterialButton>(R.id.cancelButton).visibility = View.GONE
        dialogView.findViewById<MaterialButton>(R.id.saveButton).visibility = View.GONE

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
