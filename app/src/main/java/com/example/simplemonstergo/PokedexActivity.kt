package com.example.simplemonstergo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

class PokedexActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PokedexScreen(onBack = { finish() })
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
fun PokedexScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val seenSpecies = remember { PokemonStorage.getSeenSpecies(context) }
    val caughtSpecies = remember { PokemonStorage.getCaughtSpecies(context) }
    
    // Todos los IDs de Kanto (1-151)
    val allSpeciesIds = (1..151).toList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Pokédex Kanto", fontWeight = FontWeight.Bold)
                        Text(
                            "Atrapados: ${caughtSpecies.size}  Vistos: ${seenSpecies.size}", 
                            fontSize = 12.sp, 
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { SoundManager.playClick(context); onBack() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F5F5)),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allSpeciesIds) { id ->
                PokedexItem(
                    id = id,
                    isSeen = seenSpecies.contains(id),
                    isCaught = caughtSpecies.contains(id)
                )
            }
        }
    }
}

@Composable
fun PokedexItem(id: Int, isSeen: Boolean, isCaught: Boolean) {
    val base = PokemonData.getBaseStats(id)
    val spriteUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCaught) Color.White else Color.LightGray.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("#%03d", id),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSeen) Color.Black else Color.Gray
                )
                
                if (isSeen) {
                    AsyncImage(
                        model = spriteUrl,
                        contentDescription = base.name,
                        modifier = Modifier.size(70.dp),
                        colorFilter = if (isCaught) null else ColorFilter.tint(Color.Black.copy(alpha = 0.6f)),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = if (isCaught) base.name else "???",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text("???", fontSize = 24.sp, color = Color.Gray.copy(alpha = 0.5f))
                }
            }
        }
    }
}
