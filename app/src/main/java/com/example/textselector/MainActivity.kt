// app/src/main/java/com/example/textselector/MainActivity.kt
package com.example.textselector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.textselector.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // Key to store texts in SharedPreferences as a JSON array.
    private val PREFS_KEY = "saved_texts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If started with a SEND intent, load the text.
        if (intent?.action == "android.intent.action.SEND" && intent.type == "text/plain") {
            intent.getStringExtra("android.intent.extra.TEXT")?.let {
                binding.pinnedEditText.setText(it)
            }
        } else {
            // Otherwise, load a sample text
            binding.pinnedEditText.setText(getLongText())
        }

        // Paste button: get text from clipboard.
        binding.pasteButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pasteText = clipData.getItemAt(0).text.toString()
                binding.pinnedEditText.setText(pasteText)
            }
        }

        // Search: scroll to first occurrence of the search term.
        binding.searchButton.setOnClickListener {
            val searchTerm = binding.searchEditText.text.toString()
            if (searchTerm.isEmpty()) return@setOnClickListener

            val text = binding.pinnedEditText.text.toString()
            // Start search from after the current selection.
            val startIndex = binding.pinnedEditText.selectionEnd
            var index = text.indexOf(searchTerm, startIndex)
            // If not found after current selection, wrap-around to search from the beginning.
            if (index == -1 && startIndex > 0) {
                index = text.indexOf(searchTerm, 0)
            }
            if (index >= 0) {
                binding.pinnedEditText.setSelection(index, index + searchTerm.length)
                binding.pinnedEditText.requestFocus()
            }
        }


        // Save: persist the current text and pin positions.
        binding.saveButton.setOnClickListener {
            saveCurrentText()
        }

        // Load: show saved texts in a dialog.
        binding.loadButton.setOnClickListener {
            loadSavedTexts()
        }
    }

    private fun getLongText(): String {
        val lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
        return lorem.repeat(300)
    }

    /** Save the current text (and pin positions) to SharedPreferences.  */
    private fun saveCurrentText() {
        val prefs = getSharedPreferences("TextSelectorPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray(prefs.getString(PREFS_KEY, "[]"))
        val obj = JSONObject().apply {
            put("text", binding.pinnedEditText.text.toString())
            put("pinnedStart", binding.pinnedEditText.pinnedStart ?: JSONObject.NULL)
            put("pinnedEnd", binding.pinnedEditText.pinnedEnd ?: JSONObject.NULL)
        }
        jsonArray.put(obj)
        prefs.edit().putString(PREFS_KEY, jsonArray.toString()).apply()
    }

    /** Load saved texts and let the user choose one to load.  */
    private fun loadSavedTexts() {
        val prefs = getSharedPreferences("TextSelectorPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray(prefs.getString(PREFS_KEY, "[]"))
        if (jsonArray.length() == 0) return

        // Create a simple list (using the first 40 characters as a preview).
        val listItems = mutableListOf<String>()
        val texts = mutableListOf<JSONObject>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val preview = obj.getString("text").take(40) + "..."
            listItems.add(preview)
            texts.add(obj)
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listItems)
        AlertDialog.Builder(this)
            .setTitle("Load Saved Text")
            .setAdapter(adapter) { dialog, which ->
                val obj = texts[which]
                binding.pinnedEditText.setText(obj.getString("text"))
                binding.pinnedEditText.pinnedStart =
                    if (obj.isNull("pinnedStart")) null else obj.getInt("pinnedStart")
                binding.pinnedEditText.pinnedEnd =
                    if (obj.isNull("pinnedEnd")) null else obj.getInt("pinnedEnd")
                // If both pins exist, update selection.
                if (binding.pinnedEditText.pinnedStart != null && binding.pinnedEditText.pinnedEnd != null) {
                    val start = minOf(
                        binding.pinnedEditText.pinnedStart!!,
                        binding.pinnedEditText.pinnedEnd!!
                    )
                    val end = maxOf(
                        binding.pinnedEditText.pinnedStart!!,
                        binding.pinnedEditText.pinnedEnd!!
                    )
                    binding.pinnedEditText.setSelection(start, end)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
