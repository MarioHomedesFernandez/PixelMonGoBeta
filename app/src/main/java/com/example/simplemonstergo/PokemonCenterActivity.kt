package com.example.simplemonstergo

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PokemonCenterActivity : AppCompatActivity() {

    private lateinit var rvPC: RecyclerView
    private lateinit var pcAdapter: CenterPokemonAdapter
    private var currentSortMode = "Pokedex"
    
    private lateinit var layoutMain: LinearLayout
    private lateinit var layoutPC: View
    private lateinit var layoutTeams: View
    
    private var recyclerViewState: Parcelable? = null

    // Modo edición de equipo
    private var isSequentialMode = false
    private var targetTeam: PokemonLocation? = null
    private var currentSlotToFill = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pokemon_center)

        layoutMain = findViewById(R.id.layoutMainMenu)
        layoutPC = findViewById(R.id.layoutPCView)
        layoutTeams = findViewById(R.id.layoutTeamsView)

        rvPC = findViewById(R.id.rvPC)
        rvPC.layoutManager = GridLayoutManager(this, 3)

        // Navegación
        findViewById<Button>(R.id.btnGoToPC).setOnClickListener { showPC() }
        findViewById<Button>(R.id.btnGoToTeams).setOnClickListener { showTeams() }
        findViewById<View>(R.id.btnBackToMenuPC).setOnClickListener { showMenu() }
        findViewById<View>(R.id.btnBackToMenuTeams).setOnClickListener { showMenu() }

        // Ordenación
        findViewById<Button>(R.id.btnSortPokedexCenter).setOnClickListener { changeSort("Pokedex") }
        findViewById<Button>(R.id.btnSortLevelCenter).setOnClickListener { changeSort("Level") }
        findViewById<Button>(R.id.btnSortStatsCenter).setOnClickListener { changeSort("Stats") }

        // Transferencia
        val btnBulkTransfer = findViewById<Button>(R.id.btnBulkTransferCenter)
        btnBulkTransfer.setOnClickListener {
            SoundManager.playClick(this)
            val selected = pcAdapter.getSelectedUuids()
            PokemonStorage.removeMultiplePokemon(this, selected)
            pcAdapter.isSelectionMode = false
            btnBulkTransfer.visibility = View.GONE
            loadPokemon()
        }

        // Edición de equipos rápida
        findViewById<Button>(R.id.btnEditTeam1).setOnClickListener { startSequentialEdit(PokemonLocation.TEAM_1) }
        findViewById<Button>(R.id.btnEditTeam2).setOnClickListener { startSequentialEdit(PokemonLocation.TEAM_2) }

        // Sanar a todos al entrar al Centro Pokémon
        PokemonStorage.healAllPokemon(this)
        Toast.makeText(this, "¡Todos tus Pokémon han sido curados!", Toast.LENGTH_SHORT).show()

        loadPokemon()
    }

    private fun showMenu() {
        SoundManager.playClick(this)
        isSequentialMode = false
        layoutMain.visibility = View.VISIBLE
        layoutPC.visibility = View.GONE
        layoutTeams.visibility = View.GONE
        findViewById<TextView>(R.id.tvCenterTitle).text = "Centro Pokémon"
    }

    private fun showPC() {
        SoundManager.playClick(this)
        layoutMain.visibility = View.GONE
        layoutPC.visibility = View.VISIBLE
        layoutTeams.visibility = View.GONE
        findViewById<TextView>(R.id.tvCenterTitle).text = if (isSequentialMode) "Selecciona 6 Pokémon" else "Almacenamiento PC"
        loadPokemon()
    }

    private fun showTeams() {
        SoundManager.playClick(this)
        layoutMain.visibility = View.GONE
        layoutPC.visibility = View.GONE
        layoutTeams.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvCenterTitle).text = "Tus Equipos"
        refreshTeamsVisuals()
    }

    private fun changeSort(mode: String) {
        SoundManager.playClick(this)
        currentSortMode = mode
        loadPokemon()
    }

    private fun startSequentialEdit(team: PokemonLocation) {
        SoundManager.playClick(this)
        // Primero vaciamos el equipo actual para rellenarlo de cero
        val all = PokemonStorage.getCapturedPokemon(this)
        all.filter { it.location == team }.forEach {
            it.location = PokemonLocation.PC
            it.teamSlot = null
        }
        PokemonStorage.savePokemonList(this, all)
        
        isSequentialMode = true
        targetTeam = team
        currentSlotToFill = 0
        showPC()
        Toast.makeText(this, "Toca 6 Pokémon para el equipo", Toast.LENGTH_LONG).show()
    }

    private fun loadPokemon() {
        // Guardar estado del scroll antes de recargar
        recyclerViewState = rvPC.layoutManager?.onSaveInstanceState()

        var allPokemon = PokemonStorage.getCapturedPokemon(this)
        
        allPokemon = when (currentSortMode) {
            "Pokedex" -> allPokemon.sortedBy { it.speciesId }
            "Level" -> allPokemon.sortedByDescending { it.level }
            "Stats" -> allPokemon.sortedByDescending { it.getTotalStats() }
            else -> allPokemon
        }

        val pcList = allPokemon.filter { it.location == PokemonLocation.PC }
        val btnBulkTransfer = findViewById<Button>(R.id.btnBulkTransferCenter)

        pcAdapter = CenterPokemonAdapter(
            pcList,
            onSelectionChanged = { count ->
                if (!isSequentialMode) {
                    if (count > 0) {
                        btnBulkTransfer.visibility = View.VISIBLE
                        btnBulkTransfer.text = "Transferir ($count)"
                    } else {
                        btnBulkTransfer.visibility = View.GONE
                    }
                }
            },
            onClick = { pokemon ->
                SoundManager.playClick(this)
                if (isSequentialMode) {
                    handleSequentialSelection(pokemon)
                } else {
                    val intent = Intent(this, PokemonDetailActivity::class.java)
                    intent.putExtra("POKEMON_UUID", pokemon.uuid)
                    startActivity(intent)
                }
            }
        )
        rvPC.adapter = pcAdapter
        
        // Restaurar estado del scroll
        rvPC.layoutManager?.onRestoreInstanceState(recyclerViewState)
    }

    private fun handleSequentialSelection(pokemon: PokemonInstance) {
        PokemonStorage.assignToTeam(this, pokemon.uuid, targetTeam!!, currentSlotToFill)
        currentSlotToFill++
        
        if (currentSlotToFill >= 6) {
            Toast.makeText(this, "¡Equipo completo!", Toast.LENGTH_SHORT).show()
            showTeams()
        } else {
            Toast.makeText(this, "Añadido. Quedan ${6 - currentSlotToFill}", Toast.LENGTH_SHORT).show()
            loadPokemon() // Refrescar para quitar el ya asignado de la lista
        }
    }

    private fun refreshTeamsVisuals() {
        val all = PokemonStorage.getCapturedPokemon(this)
        updateTeamGrid(1, all.filter { it.location == PokemonLocation.TEAM_1 })
        updateTeamGrid(2, all.filter { it.location == PokemonLocation.TEAM_2 })
    }

    private fun updateTeamGrid(teamNum: Int, teamPokemon: List<PokemonInstance>) {
        for (slot in 0..5) {
            val resId = resources.getIdentifier("slotCenter${teamNum}_$slot", "id", packageName)
            val slotView = findViewById<View>(resId) ?: continue
            val pokemon = teamPokemon.find { it.teamSlot == slot }
            
            val iv = slotView.findViewById<android.widget.ImageView>(R.id.ivSlotPokemon)
            val tv = slotView.findViewById<TextView>(R.id.tvSlotName)
            
            if (pokemon != null) {
                val base = PokemonData.getBaseStats(pokemon.speciesId)
                tv.text = base.name
                com.bumptech.glide.Glide.with(this).load("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${pokemon.speciesId}.png").into(iv)
                slotView.setOnClickListener {
                    PokemonStorage.removeFromTeam(this, pokemon.uuid)
                    refreshTeamsVisuals()
                }
            } else {
                tv.text = "Vacío"
                iv.setImageResource(android.R.drawable.ic_input_add)
                slotView.setOnClickListener {
                    // Clic normal en slot vacío: permite asignar uno individualmente si se quiere
                    isSequentialMode = true
                    targetTeam = if (teamNum == 1) PokemonLocation.TEAM_1 else PokemonLocation.TEAM_2
                    currentSlotToFill = slot
                    showPC()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.musicaoutsetisland)
        // Si estábamos viendo el PC o Equipos, mantenemos la vista
        if (layoutPC.visibility == View.VISIBLE) loadPokemon()
        else if (layoutTeams.visibility == View.VISIBLE) refreshTeamsVisuals()
    }

    override fun onBackPressed() {
        if (layoutPC.visibility == View.VISIBLE || layoutTeams.visibility == View.VISIBLE) {
            showMenu()
        } else {
            super.onBackPressed()
        }
    }
}
