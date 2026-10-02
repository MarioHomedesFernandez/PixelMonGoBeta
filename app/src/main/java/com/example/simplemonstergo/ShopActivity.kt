package com.example.simplemonstergo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

class ShopActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ShopScreen(onBack = { finish() })
            }
        }
    }
}

data class ShopItem(val id: String, val name: String, val price: Int, val iconRes: Int, val description: String, val currency: String = "MONEY")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var playerMoney by remember { mutableIntStateOf(PokemonStorage.getMoney(context)) }
    var playerMedals by remember { mutableIntStateOf(PokemonStorage.getMedals(context)) }

    val items = remember {
        listOf(
            ShopItem("pokeball", "Pokéball x10", 100, R.drawable.pokeball, "Bolas para capturar Pokémon salvajes."),
            ShopItem("potion", "Poción x5", 150, R.drawable.pocion, "Cura 50 PS a un Pokémon herido."),
            ShopItem("revive", "Revivir x3", 250, R.drawable.revivir, "Despierta a un Pokémon debilitado con 50% de PS."),
            ShopItem("trade_link", "Intercambiador", 200, R.drawable.intercambio, "Objeto necesario para realizar intercambios."),
            ShopItem("league_pass", "Pase de Liga", 100, R.drawable.gymicon, "Enfréntate a la Élite (Requiere 100 medallas).", "MEDALS")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tienda Pokémon", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { SoundManager.playClick(context); onBack() }) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF2F3F4))) {
            // CABECERA RECURSOS
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 4.dp) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = android.R.drawable.star_big_on), contentDescription = null, tint = Color(0xFFF1C40F))
                        Spacer(Modifier.width(8.dp)); Text("$playerMoney Monedas", fontWeight = FontWeight.Bold)
                    }
                }
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 4.dp) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Cambiado Icon por AsyncImage para evitar errores con bitmaps en el header
                        AsyncImage(
                            model = R.drawable.gymicon, 
                            contentDescription = null, 
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(Color(0xFFC0392B))
                        )
                        Spacer(Modifier.width(8.dp)); Text("$playerMedals Medallas", fontWeight = FontWeight.Bold)
                    }
                }
            }

            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                model = item.iconRes, 
                                contentDescription = null, 
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)), 
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(item.description, fontSize = 10.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.height(30.dp))
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    SoundManager.playClick(context)
                                    val success = if (item.currency == "MONEY") {
                                        if (PokemonStorage.useMoney(context, item.price)) {
                                            when(item.id) {
                                                "pokeball" -> PokemonStorage.addPokeballs(context, 10)
                                                "potion" -> PokemonStorage.addItem(context, "potion", 5)
                                                "revive" -> PokemonStorage.addItem(context, "revive", 3)
                                                "trade_link" -> PokemonStorage.addItem(context, "trade_link", 1)
                                            }
                                            true
                                        } else false
                                    } else {
                                        if (PokemonStorage.useMedals(context, item.price)) {
                                            PokemonStorage.addItem(context, "league_pass", 1)
                                            true
                                        } else false
                                    }

                                    if (success) {
                                        playerMoney = PokemonStorage.getMoney(context)
                                        playerMedals = PokemonStorage.getMedals(context)
                                        Toast.makeText(context, "¡Compra realizada!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No tienes suficientes ${if(item.currency == "MONEY") "monedas" else "medallas"}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if(item.currency == "MONEY") Color(0xFF2E86C1) else Color(0xFFC0392B))
                            ) {
                                Text("${item.price} ${if(item.currency == "MONEY") "M" else "🏆"}", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
