package com.example.textselector.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.textselector.R
import com.example.textselector.data.SavedSelection
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class SavedSelectionsAdapter(
    private var selections: List<SavedSelection>,
    private val onItemClick: (SavedSelection) -> Unit,
    private val onDeleteClick: (SavedSelection) -> Unit,
    private val onEditClick: (SavedSelection) -> Unit
) : RecyclerView.Adapter<SavedSelectionsAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.selectionTitle)
        val timestampText: TextView = view.findViewById(R.id.timestamp)
        val previewText: TextView = view.findViewById(R.id.previewText)
        val deleteButton: MaterialButton = view.findViewById(R.id.deleteButton)
        val editButton: MaterialButton = view.findViewById(R.id.editButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_saved_selection, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val selection = selections[position]
        holder.titleText.text = selection.name
        holder.timestampText.text = dateFormat.format(Date(selection.timestamp))
        holder.previewText.text = selection.getPreviewText()
        holder.itemView.setOnClickListener { onItemClick(selection) }
        holder.deleteButton.setOnClickListener { onDeleteClick(selection) }
        holder.editButton.setOnClickListener { onEditClick(selection) }
    }

    override fun getItemCount(): Int = selections.size

    fun updateSelections(newSelections: List<SavedSelection>) {
        selections = newSelections.sortedByDescending { it.timestamp }
        notifyDataSetChanged()
    }
}
