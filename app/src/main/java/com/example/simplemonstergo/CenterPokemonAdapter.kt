package com.example.simplemonstergo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CenterPokemonAdapter(
    private var pokemonList: List<PokemonInstance>,
    private val onSelectionChanged: (Int) -> Unit,
    private val onClick: (PokemonInstance) -> Unit
) : RecyclerView.Adapter<CenterPokemonAdapter.CenterViewHolder>() {

    private val selectedUuids = mutableSetOf<String>()
    var isSelectionMode = false

    class CenterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPokemon: ImageView = view.findViewById(R.id.ivPokemon)
        val tvId: TextView = view.findViewById(R.id.tvPokemonId)
        val tvLevel: TextView = view.findViewById(R.id.tvLevel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CenterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pokemon, parent, false)
        return CenterViewHolder(view)
    }

    override fun onBindViewHolder(holder: CenterViewHolder, position: Int) {
        val p = pokemonList[position]
        val base = PokemonData.getBaseStats(p.speciesId)

        holder.tvId.text = p.nickname ?: base.name
        holder.tvLevel.text = "Lv. ${p.level}"
        
        // Efecto visual de selección (tinte)
        holder.itemView.alpha = if (selectedUuids.contains(p.uuid)) 0.5f else 1.0f

        // IMPORTANTE: Sprite estático para el PC como pidió el usuario
        val url = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.speciesId}.png"
        
        // Tamaño uniforme para el catálogo (sin escalas relativas)
        holder.ivPokemon.scaleX = 1.0f
        holder.ivPokemon.scaleY = 1.0f

        Glide.with(holder.itemView.context)
            .load(url)
            .placeholder(android.R.drawable.progress_indeterminate_horizontal)
            .into(holder.ivPokemon)

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(p.uuid)
            } else {
                onClick(p)
            }
        }

        holder.itemView.setOnLongClickListener {
            isSelectionMode = true
            toggleSelection(p.uuid)
            true
        }
    }

    private fun toggleSelection(uuid: String) {
        if (selectedUuids.contains(uuid)) selectedUuids.remove(uuid)
        else selectedUuids.add(uuid)
        onSelectionChanged(selectedUuids.size)
        notifyDataSetChanged()
    }

    override fun getItemCount() = pokemonList.size

    fun getSelectedUuids() = selectedUuids.toList()

    fun updateList(newList: List<PokemonInstance>) {
        pokemonList = newList
        selectedUuids.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }
}
