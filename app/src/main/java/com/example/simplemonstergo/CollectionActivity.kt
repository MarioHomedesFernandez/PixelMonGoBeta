package com.example.simplemonstergo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class CollectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teams)
        refreshTeams()
    }

    private fun refreshTeams() {
        val allPokemon = PokemonStorage.getCapturedPokemon(this)
        
        setupTeam(1, allPokemon.filter { it.location == PokemonLocation.TEAM_1 })
        setupTeam(2, allPokemon.filter { it.location == PokemonLocation.TEAM_2 })
    }

    private fun setupTeam(teamNum: Int, teamPokemon: List<PokemonInstance>) {
        for (slot in 0..5) {
            val resId = resources.getIdentifier("slot${teamNum}_$slot", "id", packageName)
            val slotView = findViewById<View>(resId) ?: continue
            val pokemon = teamPokemon.find { it.teamSlot == slot }
            
            val iv = slotView.findViewById<ImageView>(R.id.ivSlotPokemon)
            val tv = slotView.findViewById<TextView>(R.id.tvSlotName)
            
            if (pokemon != null) {
                val base = PokemonData.getBaseStats(pokemon.speciesId)
                tv.text = base.name
                val url = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${pokemon.speciesId}.png"
                Glide.with(this).load(url).into(iv)
                
                slotView.setOnClickListener {
                    SoundManager.playClick(this)
                    // En "Mis Equipos", al clicar vamos a detalles para mejorar stats
                    val intent = Intent(this, PokemonDetailActivity::class.java)
                    intent.putExtra("POKEMON_UUID", pokemon.uuid)
                    startActivity(intent)
                }
            } else {
                tv.text = "Vacío"
                iv.setImageResource(android.R.drawable.ic_input_add)
                slotView.setOnClickListener(null) // No hace nada en esta pantalla
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.musicaoutsetisland)
        refreshTeams()
    }
}
