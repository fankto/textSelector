package com.example.textselector.ui

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.textselector.R
import com.example.textselector.data.SavedSelection
import com.example.textselector.databinding.ActivityMainBinding
import com.example.textselector.ui.viewmodel.MainViewModel
import com.example.textselector.ui.viewmodel.MainViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(this) }
    private var searchView: SearchView? = null
    private var searchMenuItem: MenuItem? = null
    private var wasSearchExpanded = false
    private var savedSearchQuery: String? = null

    fun SearchView.queryTextChanges(): Flow<String> = callbackFlow {
        val listener = object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?) : Boolean {
                trySend(newText.orEmpty())
                return true
            }
        }
        setOnQueryTextListener(listener)
        awaitClose { setOnQueryTextListener(null) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("TextSelectorPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("isDarkMode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pinnedEditText.showSoftInputOnFocus = false

        savedInstanceState?.let {
            wasSearchExpanded = it.getBoolean("wasSearchExpanded", false)
            savedSearchQuery = it.getString("savedSearchQuery")
        }

        setupToolbar()
        setupTextArea()
        setupSaveButton()
        setupSearchNavigation()
        observeViewModel()

        // Handle SEND intent
        if (intent?.action == "android.intent.action.SEND" && intent.type == "text/plain") {
            intent.getStringExtra("android.intent.extra.TEXT")?.let {
                binding.pinnedEditText.setText(it)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(navInsets.left, view.paddingTop, navInsets.right, navInsets.bottom)
            // Use safe call in case saveFab is missing in some layouts
            binding.saveFab?.translationY = -imeInsets.bottom.toFloat()
            binding.searchNavigation.translationY = -imeInsets.bottom.toFloat()
            insets
        }

        binding.pinnedEditText.selectionChangeListener = { start, end ->
            binding.saveFab?.visibility = if (end - start > 0) View.VISIBLE else View.GONE
        }
        binding.pinnedEditText.onPinChanged = { updatePinBanner() }
        binding.pinnedEditText.onSearchCleared = { updateSearchNavigation(clear = true) }

        binding.pinnedEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.pinnedEditText.nextSearchResult()
                binding.nestedScrollView?.post {
                    centerSearchResult()
                } ?: binding.pinnedEditText.post {
                    binding.pinnedEditText.centerOffsetInView(binding.pinnedEditText.selectionStart)
                }
                updateSearchNavigation()
                true
            } else false
        }
    }

    private fun observeViewModel() {
        viewModel.savedSelections.observe(this, Observer {
            // Update UI if needed.
        })
    }

    private fun centerSearchResult() {
        val scrollView = binding.nestedScrollView ?: return
        val editText = binding.pinnedEditText
        val layout = editText.layout ?: return

        val offset = editText.selectionStart
        val line = layout.getLineForOffset(offset)
        // Get the vertical center of the found line
        val lineCenter = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2

        val editTextTop = editText.top
        // Instead of scrollView.height/2, use 40% of scrollView height (adjust as needed)
        val targetScrollY = editTextTop + lineCenter - (scrollView.height * 0.4).toInt()

        scrollView.smoothScrollTo(0, targetScrollY.coerceAtLeast(0))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("wasSearchExpanded", searchMenuItem?.isActionViewExpanded ?: false)
        outState.putString("savedSearchQuery", searchView?.query?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        searchMenuItem = menu.findItem(R.id.action_search)
        searchView = searchMenuItem?.actionView as? SearchView
        searchView?.let { sv ->
            sv.queryHint = getString(R.string.search_term)
            lifecycleScope.launch {
                sv.queryTextChanges()
                    .debounce(300)
                    .collectLatest { query ->
                        binding.pinnedEditText.updateSearch(query)
                        updateSearchNavigation()
                        binding.nestedScrollView?.post {
                            centerSearchResult()
                        } ?: binding.pinnedEditText.post {
                            binding.pinnedEditText.centerOffsetInView(binding.pinnedEditText.selectionStart)
                        }
                    }
            }
        }
        if (wasSearchExpanded) {
            searchMenuItem?.expandActionView()
            searchView?.setQuery(savedSearchQuery, false)
        }
        searchMenuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searchView?.requestFocus()
                return true
            }
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                binding.pinnedEditText.clearSearchHighlights()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_library -> {
                showSavedSelections()
                return true
            }
            R.id.action_toggle_theme -> {
                toggleTheme()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.findViewById<TextView>(R.id.toolbarTitle).apply {
            text = getString(R.string.app_name)
            setOnClickListener { showAboutDialog() }
        }
    }

    private fun setupTextArea() {
        if (binding.pinnedEditText.text.isNullOrEmpty())
            binding.pinnedEditText.setText("")
    }

    private fun setupSaveButton() {
        // Use safe calls so that if saveFab is absent (e.g. in landscape) it won’t crash.
        binding.saveFab?.visibility = View.GONE
        binding.saveFab?.setOnClickListener {
            animateSaveButton { showSaveBottomSheet() }
        }
    }

    private fun animateSaveButton(onAnimationEnd: () -> Unit) {
        binding.saveFab?.animate()?.apply {
            scaleX(0.8f).scaleY(0.8f).setDuration(100)
            withEndAction {
                binding.saveFab?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)
                    ?.withEndAction(onAnimationEnd)
                    ?.start()
            }
            start()
        }
    }

    private fun setupSearchNavigation() {
        binding.searchNavigation.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            binding.pinnedEditText.previousSearchResult()
            // Wait a moment for the selection to update, then center the result:
            binding.pinnedEditText.post {
                centerSearchResult()
                updateSearchNavigation()
            }
        }
        binding.searchNavigation.findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            binding.pinnedEditText.nextSearchResult()
            binding.pinnedEditText.post {
                centerSearchResult()
                updateSearchNavigation()
            }
        }
    }

    private fun updateSearchNavigation(clear: Boolean = false) {
        val count = binding.pinnedEditText.getSearchResultsCount?.invoke() ?: 0
        if (count > 0) {
            binding.searchNavigation.visibility = View.VISIBLE
            val current = binding.pinnedEditText.getCurrentSearchIndex?.invoke() ?: 0
            binding.txtSearchCount?.text = "$current / $count"
        } else {
            binding.searchNavigation.visibility = View.GONE
            binding.txtSearchCount?.text = ""
        }
    }

    private fun showSaveBottomSheet() {
        val selectionText = binding.pinnedEditText.text?.substring(
            binding.pinnedEditText.selectionStart,
            binding.pinnedEditText.selectionEnd
        ) ?: return

        val bottomSheetDialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_save, null)
        bottomSheetDialog.setContentView(sheetView)

        val nameInput = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.nameInput)
        val previewText = sheetView.findViewById<TextView>(R.id.previewText)
        val saveButton = sheetView.findViewById<TextView>(R.id.saveButton)
        val cancelButton = sheetView.findViewById<TextView>(R.id.cancelButton)

        val defaultName = selectionText.take(50).replace("\n", " ").split(" ").take(5).joinToString(" ")
        nameInput.setText(defaultName)
        previewText.text = selectionText

        saveButton.setOnClickListener {
            val name = nameInput.text.toString().ifBlank { defaultName }
            val selection = SavedSelection(name = name, text = selectionText)
            viewModel.saveSelection(selection)
            bottomSheetDialog.dismiss()
            showSnackbar(getString(R.string.selection_saved))
        }
        cancelButton.setOnClickListener { bottomSheetDialog.dismiss() }
        bottomSheetDialog.show()
    }

    private fun showSavedSelections() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_saved_selections, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.savedSelectionsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        // Initialize adapter with empty list.
        val adapter = SavedSelectionsAdapter(emptyList(),
            onItemClick = { selection ->
                binding.pinnedEditText.clearSelectionPins()
                binding.pinnedEditText.clearSearchHighlights()
                binding.pinnedEditText.setText(selection.text)
                alertDialog.dismiss()
            },
            onDeleteClick = { selection -> showDeleteConfirmation(selection) },
            onEditClick = { selection -> showEditDialog(selection) }
        )
        recyclerView.adapter = adapter

        // Create and attach an observer.
        val selectionsObserver = Observer<List<SavedSelection>> { newSelections ->
            if (newSelections.isNullOrEmpty()) {
                alertDialog.dismiss()
                showSnackbar(getString(R.string.no_selections))
            } else {
                adapter.updateSelections(newSelections)
            }
        }
        viewModel.savedSelections.observe(this, selectionsObserver)

        // Remove observer when dialog is dismissed.
        alertDialog.setOnDismissListener {
            viewModel.savedSelections.removeObserver(selectionsObserver)
        }

        alertDialog.show()
    }

    private fun showDeleteConfirmation(selection: SavedSelection) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_selection))
            .setMessage(getString(R.string.delete_confirmation, selection.name))
            .setPositiveButton(getString(R.string.delete)) { dialog, which ->
                viewModel.deleteSelection(selection)
                showSnackbar(getString(R.string.selection_deleted))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showEditDialog(selection: SavedSelection) {
        val dialogView: View = layoutInflater.inflate(R.layout.dialog_edit_selection, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.nameInput)
        nameInput.setText(selection.name)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.edit_selection))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { dialog: DialogInterface, which: Int ->
                val updatedSelection = selection.copy(
                    name = nameInput.text.toString().ifBlank { selection.name }
                )
                viewModel.updateSelection(updatedSelection)
                showSnackbar(getString(R.string.selection_updated))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toggleTheme() {
        val prefs = getSharedPreferences("TextSelectorPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("isDarkMode", false)
        prefs.edit().putBoolean("isDarkMode", !isDarkMode).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (!isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        recreate()
    }

    private fun updatePinBanner() {
        binding.bottomBanner.visibility =
            if (binding.pinnedEditText.selectionStart != binding.pinnedEditText.selectionEnd)
                View.VISIBLE else View.GONE
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showAboutDialog() {
        val aboutHtml = """
        <h1>textSelector</h1>        
        <h3>About This App</h3>
        <p>This app was created to bring desktop-like text selection functionality to Android. 
        Having missed the convenience of shift-selection from desktop computers, I developed 
        this solution using double-tap selection and search capabilities to make text selection 
        easier and more precise on mobile devices.</p>
        
        <h3>Features</h3>
        <ul>
            <li>Double-tap text selection</li>
            <li>Quick search functionality</li>
            <li>Tripple-tap to dismiss selection</li>
            <li>Precise start and end point selection</li>
        </ul>
        
        <h3>Version</h3>
        <p>1.0.0</p>
        
        <h3>Contact & Links</h3>
        <p>Developer: Tobias Fankhauser</p>
        <p>Visit my <a href="https://github.com/TobiFank">GitHub</a> for source code and updates.</p>
        <p>Connect with me on <a href="https://www.linkedin.com/in/tobias-fankhauser">LinkedIn</a>.</p>
        
        <h3>Feedback</h3>
        <p>Found a bug or have a suggestion? Please report it on GitHub or contact me through LinkedIn.</p>
        
        <p>Thank you for using textSelector!</p>
    """.trimIndent()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.about))
            .setMessage(Html.fromHtml(aboutHtml, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(getString(R.string.ok), null)
            .create()

        // Enable link clicking
        dialog.show()
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            movementMethod = LinkMovementMethod.getInstance()
            // Improve text appearance
            textSize = 14f
            setLineSpacing(0f, 1.2f)  // Add some line spacing for better readability
        }
    }
}
