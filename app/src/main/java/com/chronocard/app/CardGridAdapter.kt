package com.chronocard.app

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CardGridAdapter(
    private val keys: List<String>,
    private val onSlotClicked: (cardKey: String) -> Unit
) : RecyclerView.Adapter<CardGridAdapter.VH>() {

    // cardKey -> uri string, updated externally after a pick completes
    private val thumbUris = HashMap<String, String>()

    fun setThumb(cardKey: String, path: String) {
        thumbUris[cardKey] = path
        val idx = keys.indexOf(cardKey)
        if (idx >= 0) notifyItemChanged(idx)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.ivThumb)
        val label: TextView = view.findViewById(R.id.tvLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card_slot, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val key = keys[position]
        holder.label.text = key
        val path = thumbUris[key]
        if (path != null) {
            holder.img.setImageURI(Uri.fromFile(java.io.File(path)))
        } else {
            holder.img.setImageDrawable(null)
        }
        holder.itemView.setOnClickListener { onSlotClicked(key) }
    }

    override fun getItemCount() = keys.size
}
