package com.example.simplemonstergo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

class BagActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BagScreen(onBack = { finish() })
            }
        }
    }
    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.musicaoutsetisland)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var inventory by remember { mutableStateOf(PokemonStorage.getInventory(context)) }
    var pokeballCount by remember { mutableIntStateOf(PokemonStorage.getPokeballs(context)) }
    
    var showHealDialog by remember { mutableStateOf(false) }
    var showReviveDialog by remember { mutableStateOf(false) }

    val itemList = inventory.map { (id, count) -> 
        when(id) {
            "potion" -> Triple(id, "Poción", R.drawable.pocion)
            "revive" -> Triple(id, "Revivir", R.drawable.revivir)
            "trade_link" -> Triple(id, "Intercambiador", R.drawable.intercambio)
            "league_pass" -> Triple(id, "Pase de Liga", R.drawable.gymicon)
            "champion_title" -> Triple(id, "Título de Campeón", android.R.drawable.btn_star_big_on)
            else -> Triple(id, id, android.R.drawable.ic_menu_help)
        }
    }.filter { it.second != "???" && inventory[it.first]!! > 0 }.toMutableList()
    
    // Añadir Pokeballs manualmente si no están en el inventario JSON
    if (pokeballCount > 0) {
        itemList.add(0, Triple("pokeball", "Pokéball", R.drawable.pokeball))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mochila de Objetos", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { SoundManager.playClick(context); onBack() }) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (itemList.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("La mochila está vacía...", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF2F3F4))) {
                items(itemList) { (id, name, res) ->
                    val count = if(id == "pokeball") pokeballCount else inventory[id] ?: 0
                    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = res, contentDescription = null, modifier = Modifier.size(50.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Cantidad: $count", color = Color.Gray)
                            }
                            Button(onClick = {
                                SoundManager.playClick(context)
                                when(id) {
                                    "potion" -> showHealDialog = true
                                    "revive" -> showReviveDialog = true
                                    "league_pass" -> {
                                        PokemonStorage.useItem(context, id)
                                        context.startActivity(Intent(context, LeagueActivity::class.java))
                                        onBack()
                                    }
                                    "champion_title" -> {
                                        PokemonStorage.useItem(context, id)
                                        val legendaries = listOf(144, 145, 146, 150, 151)
                                        val intent = Intent(context, RaidActivity::class.java).apply {
                                            putExtra("POKEMON_ID", legendaries.random())
                                        }
                                        context.startActivity(intent)
                                        onBack()
                                    }
                                    "pokeball" -> Toast.makeText(context, "Las Pokéballs se usan durante la captura.", Toast.LENGTH_SHORT).show()
                                    else -> Toast.makeText(context, "Este objeto se usa en combate.", Toast.LENGTH_SHORT).show()
                                }
                            }, enabled = id != "pokeball") {
                                Text("USAR")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHealDialog) {
        HealPokemonDialog(
            onDismiss = { showHealDialog = false },
            onHeal = { uuid ->
                if (PokemonStorage.healPokemon(context, uuid, 50)) {
                    PokemonStorage.useItem(context, "potion")
                    inventory = PokemonStorage.getInventory(context)
                    Toast.makeText(context, "¡Pokémon curado!", Toast.LENGTH_SHORT).show()
                    if ((inventory["potion"] ?: 0) <= 0) showHealDialog = false
                } else {
                    Toast.makeText(context, "Este Pokémon ya tiene la vida al máximo o está debilitado.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showReviveDialog) {
        RevivePokemonDialog(
            onDismiss = { showReviveDialog = false },
            onRevive = { uuid ->
                val p = PokemonStorage.getCapturedPokemon(context).find { it.uuid == uuid }
                if (p != null && p.getSafeHp() <= 0) {
                    val base = PokemonData.getBaseStats(p.speciesId)
                    val maxHp = PokemonData.calculateHP(base.hp, p.level, p.ivHp)
                    PokemonStorage.updatePokemonHp(context, uuid, maxHp / 2)
                    PokemonStorage.useItem(context, "revive")
                    inventory = PokemonStorage.getInventory(context)
                    Toast.makeText(context, "¡${base.name} ha revivido!", Toast.LENGTH_SHORT).show()
                    if ((inventory["revive"] ?: 0) <= 0) showReviveDialog = false
                } else {
                    Toast.makeText(context, "Este Pokémon no está debilitado.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun RevivePokemonDialog(onDismiss: () -> Unit, onRevive: (String) -> Unit) {
    val context = LocalContext.current
    val captured = remember { PokemonStorage.getCapturedPokemon(context) }
    val fainted = captured.filter { it.getSafeHp() <= 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿A quién quieres revivir?", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (fainted.isEmpty()) {
                    Text("No tienes Pokémon debilitados.")
                } else {
                    fainted.forEach { p ->
                        val base = PokemonData.getBaseStats(p.speciesId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRevive(p.uuid) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.speciesId}.png",
                                contentDescription = null,
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(base.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CERRAR") } }
    )
}

@Composable
fun HealPokemonDialog(onDismiss: () -> Unit, onHeal: (String) -> Unit) {
    val context = LocalContext.current
    val captured = remember { PokemonStorage.getCapturedPokemon(context) }
    // Mostrar solo los que están en equipos para priorizar curación
    val team = captured.filter { it.location != PokemonLocation.PC }.sortedBy { it.location }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿A quién quieres curar?", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (team.isEmpty()) {
                    Text("No tienes Pokémon en tus equipos.")
                } else {
                    team.forEach { p ->
                        val base = PokemonData.getBaseStats(p.speciesId)
                        val maxHp = PokemonData.calculateHP(base.hp, p.level, p.ivHp)
                        val currentHp = p.getSafeHp()
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onHeal(p.uuid) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.speciesId}.png",
                                contentDescription = null,
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(base.name, fontWeight = FontWeight.Bold)
                                Text("PS: $currentHp / $maxHp", fontSize = 12.sp, color = if(currentHp < maxHp/4) Color.Red else Color.Gray)
                                LinearProgressIndicator(
                                    progress = currentHp.toFloat() / maxHp.toFloat(),
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = if(currentHp < maxHp/4) Color.Red else Color.Green
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CERRAR") } }
    )
}
