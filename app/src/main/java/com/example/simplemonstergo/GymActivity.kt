package com.example.simplemonstergo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.GeoPoint

class GymActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val lat = intent.getDoubleExtra("LAT", 0.0)
        val lon = intent.getDoubleExtra("LON", 0.0)
        val terrain = GymTerrain.getForLocation(GeoPoint(lat, lon))
        
        setContent {
            MaterialTheme {
                GymLobbyScreen(terrain, onBack = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.musicaoutsetisland)
    }

    override fun onPause() {
        super.onPause()
    }
}

@Composable
fun GymLobbyScreen(terrain: GymTerrain, onBack: () -> Unit) {
    val context = LocalContext.current
    val playerTeam = remember { 
        PokemonStorage.getCapturedPokemon(context).filter { it.location == PokemonLocation.TEAM_1 } 
    }
    
    val avgLevel = if (playerTeam.isNotEmpty()) playerTeam.map { it.level }.average().toInt() else 1
    
    val (category, bannerRes) = when {
        avgLevel <= 25 -> "Novato" to R.drawable.combatenovato
        avgLevel <= 60 -> "Profesional" to R.drawable.combateprofesional
        avgLevel <= 85 -> "Líder de Gimnasio" to R.drawable.combateprofesional
        else -> "Campeón" to R.drawable.combatecampeon
    }

    val categoryColor = when(category) {
        "Novato" -> Color(0xFF27AE60)
        "Profesional" -> Color(0xFF2980B9)
        "Líder de Gimnasio" -> Color(0xFF8E44AD)
        else -> Color(0xFFC0392B)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // IMAGEN DE FONDO
        Image(
            painter = painterResource(id = bannerRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Degradado oscuro para lectura
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 300f
                    )
                )
        )

        // Botón Volver arriba
        IconButton(
            onClick = { 
                SoundManager.playClick(context)
                onBack() 
            },
            modifier = Modifier.padding(16.dp).statusBarsPadding()
        ) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
        }

        // CONTENIDO ENCIMA
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = category.uppercase(), 
                fontSize = 32.sp, 
                fontWeight = FontWeight.ExtraBold, 
                color = Color.White
            )
            Text(
                text = "Gimnasio Nivel $avgLevel", 
                fontSize = 16.sp, 
                color = Color.LightGray
            )

            Spacer(Modifier.height(24.dp))

            // INFO TERRENO COMPACTA
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Campo de Batalla: ${terrain.displayName}", fontWeight = FontWeight.Bold, color = Color.Cyan)
                    Text("• Bonus ${terrain.typeBoost}: +15% Daño", color = Color.White, fontSize = 14.sp)
                    Text("• Clima: Precisión -${((1.0f - terrain.accuracyMod) * 100).toInt()}%", color = Color.White, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(32.dp))

            if (playerTeam.isEmpty()) {
                Text("Necesitas Pokémon en tu equipo", color = Color.Red, fontWeight = FontWeight.Bold)
            } else {
                Button(
                    onClick = {
                        SoundManager.playClick(context)
                        val intent = Intent(context, BattleActivity::class.java).apply {
                            putExtra("GYM_TERRAIN", terrain.name)
                            putExtra("IS_GYM_BATTLE", true)
                        }
                        context.startActivity(intent)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text("EMPEZAR PELEA", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Advertencia: No podrás curar a tu equipo", color = Color.LightGray, fontSize = 12.sp)
            }
        }
    }
}
