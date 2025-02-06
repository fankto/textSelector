package com.example.textselector

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.textselector.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var searchView: SearchView? = null
    private var searchMenuItem: MenuItem? = null
    private lateinit var db: TextSelectorDatabase
    private var wasSearchExpanded = false
    private var savedSearchQuery: String? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The system loads the correct layout automatically.
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("wasSearchExpanded", searchMenuItem?.isActionViewExpanded ?: false)
        outState.putString("savedSearchQuery", searchView?.query?.toString())
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("TextSelectorPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("isDarkMode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDarkMode

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            wasSearchExpanded = savedInstanceState.getBoolean("wasSearchExpanded", false)
            savedSearchQuery = savedInstanceState.getString("savedSearchQuery")
        }

        // Initialize the Room database.
        db = TextSelectorDatabase.getDatabase(this)

        binding.saveFab.setOnTouchListener(object : View.OnTouchListener {
            var dX = 0f
            var dY = 0f
            var downRawX = 0f
            var downRawY = 0f
            val CLICK_DRAG_TOLERANCE = 10  // in pixels
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        dX = v.x - event.rawX
                        dY = v.y - event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        v.animate().x(event.rawX + dX).y(event.rawY + dY).setDuration(0).start()
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        val upRawX = event.rawX
                        val upRawY = event.rawY
                        if (abs(upRawX - downRawX) < CLICK_DRAG_TOLERANCE &&
                            abs(upRawY - downRawY) < CLICK_DRAG_TOLERANCE
                        ) {
                            v.performClick()
                        }
                        return true
                    }

                    else -> return false
                }
            }
        })

        binding.pinnedEditText.selectionChangeListener = { start, end ->
            binding.saveFab.visibility = if (end - start > 0) View.VISIBLE else View.GONE
        }

        binding.pinnedEditText.onSearchCleared = {
            binding.searchNavigation.visibility = View.GONE
            binding.txtSearchCount.text = ""
            if (binding.pinnedEditText.isPinActive()) {
                binding.bottomBanner.visibility = View.VISIBLE
                binding.tvBannerInfo.text = getString(R.string.pin_active)
            } else {
                binding.bottomBanner.visibility = View.GONE
                binding.tvBannerInfo.text = ""
            }
            searchView?.setQuery("", false)
            searchView?.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.pinnedEditText.windowToken, 0)
            searchMenuItem?.collapseActionView()
        }

        if (binding.pinnedEditText.text.isNullOrEmpty()) {
            binding.pinnedEditText.setText("")
        }

        setupToolbar()
        setupTextArea()
        setupSaveButton()
        setupSearchNavigation()

        // Handle SEND intent if present.
        if (intent?.action == "android.intent.action.SEND" && intent.type == "text/plain") {
            intent.getStringExtra("android.intent.extra.TEXT")?.let {
                binding.pinnedEditText.setText(it)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            // Get the inset from the keyboard (IME)
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            view.setPadding(
                navInsets.left,
                view.paddingTop,
                navInsets.right,
                navInsets.bottom
            )
            // Translate your FAB and search navigation upward by the keyboard’s height.
            binding.saveFab.translationY = -imeInsets.bottom.toFloat()
            binding.searchNavigation.translationY = -imeInsets.bottom.toFloat()
            // Always return the insets so that child views can also use them.
            insets
        }

        binding.pinnedEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.pinnedEditText.nextSearchResult()
                updateSearchNavigation()
                true
            } else {
                false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        searchMenuItem = menu.findItem(R.id.action_search)
        searchView = searchMenuItem?.actionView as? SearchView
        searchView?.apply {
            setIconifiedByDefault(true)
            queryHint = getString(R.string.search_term)
            setOnSearchClickListener {
                requestFocus()
                requestFocusFromTouch()
            }
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = true
                override fun onQueryTextChange(newText: String?): Boolean {
                    binding.pinnedEditText.updateSearch(newText.orEmpty())
                    val resultCount = binding.pinnedEditText.getSearchResultsCount()
                    binding.searchNavigation.visibility =
                        if (resultCount > 0) View.VISIBLE else View.GONE
                    if (resultCount > 0) updateSearchNavigation()
                    return true
                }
            })
        }

        if (wasSearchExpanded) {
            searchMenuItem?.expandActionView()
            searchView?.setQuery(savedSearchQuery, false)
        }

        searchMenuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searchView?.requestFocusFromTouch()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                binding.pinnedEditText.clearSearchHighlights(invokeCallback = false)
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

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        updatePinBanner()
    }

    private fun updatePinBanner() {
        if (binding.pinnedEditText.isPinActive()) {
            binding.bottomBanner.visibility = View.VISIBLE
            binding.tvBannerInfo.text = getString(R.string.pin_active)
        } else {
            binding.bottomBanner.visibility = View.GONE
            binding.tvBannerInfo.text = ""
        }
    }

    // Toggle dark/light mode and store the new preference.
    private fun toggleTheme() {
        val prefs = getSharedPreferences("TextSelectorPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("isDarkMode", false)
        val newMode =
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        prefs.edit().putBoolean("isDarkMode", !isDarkMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
        // Recreate the activity so the theme change takes effect immediately.
        recreate()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val toolbarTitle = binding.toolbar.findViewById<TextView>(R.id.toolbarTitle)

        val aboutMessage = """
        <div style="font-family: 'Segoe UI', Roboto, -apple-system, sans-serif; max-width: 600px; margin: 2em auto; padding: 2em; background: #ffffff; border-radius: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.1);">
            <h1 style="color: #666666; margin: 0 0 1em 0; font-weight: normal;">Tobias Fankhauser</h1>
            
            <div style="display: flex; flex-direction: column; gap: 1em;">
                <a href="https://github.com/TobiFank" style="color: #f0a500; text-decoration: none;">GitHub</a>
                <a href="https://www.linkedin.com/in/tobias-fankhauser" style="color: #f0a500; text-decoration: none;">LinkedIn</a>
                <a href="https://buymeacoffee.com/TobiFank" style="color: #f0a500; text-decoration: none;">Buy me a coffee</a>
            </div>
    
            <p style="color: #666666; margin-top: 2em;">Thank you for exploring my work! I welcome your feedback and bug reports to help make this app even better. Have a great day!</p>
        </div>
        """.trimIndent()

        toolbarTitle.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("About")
                .setMessage(Html.fromHtml(aboutMessage, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("OK", null)
                .show()
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_library -> {
                    showSavedSelections()
                    true
                }

                R.id.action_toggle_theme -> {
                    toggleTheme()
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

        val nameInput = bottomSheetView.findViewById<TextInputEditText>(R.id.nameInput)
        val previewText = bottomSheetView.findViewById<TextView>(R.id.previewText)
        val saveButton = bottomSheetView.findViewById<MaterialButton>(R.id.saveButton)
        val cancelButton = bottomSheetView.findViewById<MaterialButton>(R.id.cancelButton)

        val defaultName = selectedText.take(50).replace("\n", " ")
            .split(" ").take(5).joinToString(" ")
        nameInput.setText(defaultName)
        previewText.text = selectedText

        saveButton.setOnClickListener {
            val name = nameInput.text.toString().takeIf { it.isNotBlank() } ?: defaultName
            val selection = SavedSelection(name = name, text = selectedText)
            saveSelection(selection)
            bottomSheetDialog.dismiss()
            showSuccessSnackbar("Selection saved")
        }
        cancelButton.setOnClickListener { bottomSheetDialog.dismiss() }

        bottomSheetDialog.show()

        // Force the bottom sheet to expand fully
        val bottomSheet =
            bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
        }
    }


    private fun showSavedSelections() {
        lifecycleScope.launch {
            val selections = withContext(Dispatchers.IO) {
                db.savedSelectionDao().getAll()
            }
            val dialogView = layoutInflater.inflate(R.layout.dialog_saved_selections, null)
            val recyclerView =
                dialogView.findViewById<RecyclerView>(R.id.savedSelectionsRecyclerView)
            recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
            val dialog = MaterialAlertDialogBuilder(this@MainActivity)
                .setView(dialogView)
                .create()
            var adapter: SavedSelectionsAdapter? = null
            adapter = SavedSelectionsAdapter(
                selections = selections,
                onItemClick = { selection ->
                    binding.pinnedEditText.clearSelectionPins()
                    binding.pinnedEditText.clearSearchHighlights()
                    binding.pinnedEditText.setText(selection.text)
                    dialog.dismiss()
                },
                onDeleteClick = { selection ->
                    showDeleteConfirmationDialog(selection) {
                        lifecycleScope.launch {
                            val updatedSelections = withContext(Dispatchers.IO) {
                                db.savedSelectionDao().getAll()
                            }
                            adapter?.updateSelections(updatedSelections)
                        }
                    }
                },
                onEditClick = { selection ->
                    showEditDialog(selection) {
                        lifecycleScope.launch {
                            val updatedSelections = withContext(Dispatchers.IO) {
                                db.savedSelectionDao().getAll()
                            }
                            adapter?.updateSelections(updatedSelections)
                        }
                    }
                }
            )
            recyclerView.adapter = adapter
            dialog.show()
        }
    }

    private fun setupSearchNavigation() {
        val btnPrev = binding.searchNavigation.findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = binding.searchNavigation.findViewById<ImageButton>(R.id.btnNext)
        binding.searchNavigation.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            binding.pinnedEditText.previousSearchResult()
            updateSearchNavigation()
        }
        binding.searchNavigation.findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            binding.pinnedEditText.nextSearchResult()
            updateSearchNavigation()
        }
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
        val resultCount = binding.pinnedEditText.getSearchResultsCount()
        if (resultCount > 0) {
            val current = binding.pinnedEditText.getCurrentSearchIndex()
            binding.txtSearchCount.text = "$current/$resultCount"
        } else {
            binding.txtSearchCount.text = ""
        }
    }

    private fun showDeleteConfirmationDialog(selection: SavedSelection, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Selection")
            .setMessage("Are you sure you want to delete '${selection.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.savedSelectionDao().delete(selection)
                    }
                    onDeleted()  // Now the deletion is complete
                    showSuccessSnackbar("Selection deleted")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showEditDialog(selection: SavedSelection, onEdited: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet_save, null)
        dialogView.findViewById<MaterialButton>(R.id.cancelButton).visibility = View.GONE
        dialogView.findViewById<MaterialButton>(R.id.saveButton).visibility = View.GONE
        val nameInput = dialogView.findViewById<EditText>(R.id.nameInput)
        nameInput.setText(selection.name)
        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Selection")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                lifecycleScope.launch {
                    val updatedSelection = selection.copy(
                        name = nameInput.text.toString().takeIf { it.isNotBlank() }
                            ?: selection.name
                    )
                    withContext(Dispatchers.IO) {
                        db.savedSelectionDao().update(updatedSelection)
                    }
                    onEdited()  // Now the update is complete
                    showSuccessSnackbar("Selection updated")
                }
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
        lifecycleScope.launch(Dispatchers.IO) {
            db.savedSelectionDao().insert(selection)
        }
    }

    private fun updateSelection(selection: SavedSelection) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.savedSelectionDao().update(selection)
        }
    }

    private fun deleteSelection(selection: SavedSelection) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.savedSelectionDao().delete(selection)
        }
    }
}
