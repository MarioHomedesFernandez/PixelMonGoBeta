package com.example.simplemonstergo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class PokemonDetailActivity : AppCompatActivity() {

    private lateinit var pokemonUuid: String
    private var instance: PokemonInstance? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pokemon_detail)

        pokemonUuid = intent.getStringExtra("POKEMON_UUID") ?: ""
        loadPokemon()
    }

    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.musicaoutsetisland)
    }

    private fun loadPokemon() {
        val list = PokemonStorage.getCapturedPokemon(this)
        instance = list.find { it.uuid == pokemonUuid }

        if (instance == null) {
            finish()
            return
        }

        val p = instance!!
        val b = PokemonData.getBaseStats(p.speciesId)

        findViewById<TextView>(R.id.tvDetailName).text = b.name
        findViewById<TextView>(R.id.tvDetailLevel).text = "Nivel ${p.level}"
        findViewById<TextView>(R.id.tvDetailTypes).text = b.types.joinToString(" / ")

        // Efecto Ranger
        /*val tvRangerEffect = TextView(this).apply {
            text = "Efecto Incursión: ${PokemonData.getRangerEffectDescription(p.speciesId)}"
            setTextColor(android.graphics.Color.parseColor("#2980B9"))
            setPadding(0, 12, 0, 12)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        findViewById<android.widget.LinearLayout>(R.id.layoutMoves).addView(tvRangerEffect, 0)*/

        // Stats calculadas con IVs individuales
        findViewById<TextView>(R.id.tvStatHp).text = PokemonData.calculateHP(b.hp, p.level, p.ivHp).toString()
        findViewById<TextView>(R.id.tvStatAtk).text = PokemonData.calculateStat(b.attack, p.level, p.ivAtk).toString()
        findViewById<TextView>(R.id.tvStatDef).text = PokemonData.calculateStat(b.defense, p.level, p.ivDef).toString()
        findViewById<TextView>(R.id.tvStatSpAtk).text = PokemonData.calculateStat(b.spAttack, p.level, p.ivSpAtk).toString()
        findViewById<TextView>(R.id.tvStatSpDef).text = PokemonData.calculateStat(b.spDefense, p.level, p.ivSpDef).toString()
        findViewById<TextView>(R.id.tvStatSpeed).text = PokemonData.calculateStat(b.speed, p.level, p.ivSpeed).toString()

        val candies = PokemonStorage.getCandies(this, b.familyId)
        findViewById<TextView>(R.id.tvCandies).text = "Caramelos ${b.name}: $candies"

        // Mostrar movimientos
        val layoutMoves = findViewById<android.widget.LinearLayout>(R.id.layoutMoves)
        layoutMoves.removeAllViews()

        // Efecto Ranger (Visible en la sección de movimientos)
        val tvEffect = TextView(this).apply {
            text = "EFECTO INCURSIÓN:\n${PokemonData.getRangerEffectDescription(p.speciesId)}"
            setTextColor(android.graphics.Color.parseColor("#2980B9"))
            setPadding(0, 0, 0, 32)
            setTypeface(null, android.graphics.Typeface.BOLD)
            textSize = 15f
        }
        layoutMoves.addView(tvEffect)

        p.getSafeMoves().forEach { move ->
            val tv = TextView(this)
            tv.text = "${move.name} (${move.type}) - Potencia: ${if (move.power > 0) move.power else "-"}"
            tv.setPadding(0, 4, 0, 4)
            layoutMoves.addView(tv)
        }

        val ivDetail = findViewById<ImageView>(R.id.ivDetailPokemon)
        val name = PokemonData.getPokemonNameForSprite(p.speciesId)
        val url = "https://play.pokemonshowdown.com/sprites/gen5ani/$name.gif"
        
        // Aplicar escala relativa solo en los detalles
        val scale = PokemonData.getPokemonScaleFactor(p.speciesId)
        ivDetail.scaleX = scale
        ivDetail.scaleY = scale

        Glide.with(this).asGif().load(url).into(ivDetail)

        // Botón Subir Nivel
        val btnLevelUp = findViewById<Button>(R.id.btnLevelUp)
        btnLevelUp.setOnClickListener {
            SoundManager.playClick(this)
            if (p.level >= 100) {
                Toast.makeText(this, "Nivel máximo alcanzado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (candies > 0) {
                PokemonStorage.addCandies(this, b.familyId, -1)
                val currentList = PokemonStorage.getCapturedPokemon(this).toMutableList()
                val idx = currentList.indexOfFirst { it.uuid == pokemonUuid }
                if (idx != -1) {
                    currentList[idx].level++
                    PokemonStorage.savePokemonList(this, currentList)
                    loadPokemon()
                    Toast.makeText(this, "¡Nivel subido!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "No tienes caramelos", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón Evolucionar
        val btnEvolve = findViewById<Button>(R.id.btnEvolve)
        if (b.evolutionId != null) {
            btnEvolve.isEnabled = true
            btnEvolve.text = "Evolucionar (${b.evolutionCandies} caramelos)"
            btnEvolve.setOnClickListener {
                SoundManager.playClick(this)
                if (candies >= b.evolutionCandies) {
                    PokemonStorage.addCandies(this, b.familyId, -b.evolutionCandies)
                    val currentList = PokemonStorage.getCapturedPokemon(this).toMutableList()
                    val idx = currentList.indexOfFirst { it.uuid == pokemonUuid }
                    if (idx != -1) {
                        var nextSpeciesId = b.evolutionId!!
                        
                        // Lógica especial para Eevee (133): Evolución aleatoria
                        if (p.speciesId == 133) {
                            nextSpeciesId = listOf(134, 135, 136).random() // Vaporeon, Jolteon, Flareon
                        }
                        
                        currentList[idx] = currentList[idx].copy(speciesId = nextSpeciesId)
                        // Resetear movimientos para la nueva especie
                        currentList[idx].moves = null
                        currentList[idx].getSafeMoves()

                        PokemonStorage.savePokemonList(this, currentList)
                        
                        // Lanzar animación de evolución
                        val intent = Intent(this, EvolutionActivity::class.java).apply {
                            putExtra("OLD_ID", p.speciesId)
                            putExtra("NEW_ID", nextSpeciesId)
                            putExtra("POKEMON_NAME", b.name)
                        }
                        startActivity(intent)
                        
                        loadPokemon()
                    }
                } else {
                    Toast.makeText(this, "No tienes suficientes caramelos", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            btnEvolve.isEnabled = false
            btnEvolve.text = "No puede evolucionar más"
        }

        // Botón RA
        findViewById<Button>(R.id.btnAR).setOnClickListener {
            SoundManager.playClick(this)
            val intent = android.content.Intent(this, PokemonARActivity::class.java)
            intent.putExtra("SPECIES_ID", p.speciesId)
            startActivity(intent)
        }

        // Botón Transferir
        findViewById<Button>(R.id.btnTransfer).setOnClickListener {
            SoundManager.playClick(this)
            PokemonStorage.removePokemon(this, p.uuid)
            Toast.makeText(this, "Transferido al profesor. +1 caramelo.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

