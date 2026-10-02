package com.example.simplemonstergo

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PokemonAdapter(private var pokemonList: List<PokemonInstance>) :
    RecyclerView.Adapter<PokemonAdapter.PokemonViewHolder>() {

    class PokemonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPokemon: ImageView = view.findViewById(R.id.ivPokemon)
        val tvId: TextView = view.findViewById(R.id.tvPokemonId)
        val tvLevel: TextView = view.findViewById(R.id.tvLevel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PokemonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pokemon, parent, false)
        return PokemonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PokemonViewHolder, position: Int) {
        val instance = pokemonList[position]
        val base = PokemonData.getBaseStats(instance.speciesId)
        
        holder.tvId.text = instance.nickname ?: base.name
        holder.tvLevel.text = "Lv. ${instance.level} (IV: ${instance.getTotalStats()})"

        val urlSprite = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${instance.speciesId}.png"
        
        // Tamaño uniforme para el catálogo (sin escalas relativas)
        holder.ivPokemon.scaleX = 1.0f
        holder.ivPokemon.scaleY = 1.0f
        
        Glide.with(holder.itemView.context)
            .load(urlSprite)
            .placeholder(android.R.drawable.progress_indeterminate_horizontal)
            .into(holder.ivPokemon)

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, PokemonDetailActivity::class.java)
            intent.putExtra("POKEMON_UUID", instance.uuid)
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount() = pokemonList.size

    fun updateList(newList: List<PokemonInstance>) {
        pokemonList = newList
        notifyDataSetChanged()
    }
}
