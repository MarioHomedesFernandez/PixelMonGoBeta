package com.example.simplemonstergo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

class LeagueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phase = intent.getIntExtra("PHASE", 0) // 0 es Intro, 1-5 son combates
        
        setContent {
            MaterialTheme {
                LeagueFlowScreen(phase) { finish() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.musicaligapokemon)
    }

    override fun onPause() {
        super.onPause()
    }
}

@Composable
fun LeagueFlowScreen(currentPhase: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    
    if (currentPhase == 0) {
        LeagueIntroScreen { 
            val intent = Intent(context, LeagueActivity::class.java).apply { putExtra("PHASE", 1) }
            context.startActivity(intent)
            onExit()
        }
    } else {
        LeagueVersusScreen(currentPhase, onExit)
    }
}

@Composable
fun LeagueIntroScreen(onStart: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1C2833))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("LIGA POKÉMON", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(24.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("REGLAS DEL DESAFÍO:", fontWeight = FontWeight.Bold, color = Color.Yellow)
                    Text("• Te enfrentarás a 5 entrenadores de élite seguidos.", color = Color.White, fontSize = 14.sp)
                    Text("• Solo podrás curar a tus Pokémon entre combates.", color = Color.White, fontSize = 14.sp)
                    Text("• Una vez que entras, no hay vuelta atrás.", color = Color.White, fontSize = 14.sp)
                    Text("• Si pierdes, puedes reintentar el combate actual.", color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("ACEPTO EL DESAFÍO", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LeagueVersusScreen(phase: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    var potionCount by remember { mutableIntStateOf(PokemonStorage.getItemCount(context, "potion")) }
    var showHealDialog by remember { mutableStateOf(false) }

    val (bossName, bossTeamText, bannerRes) = when(phase) {
        1 -> Triple("LORELEI", "Dewgong, Cloyster, Slowbro, Jynx, Lapras", R.drawable.combatelorelei)
        2 -> Triple("BRUNO", "Onix, Hitmonlee, Hitmonchan, Machamp, Rhydon", R.drawable.combatebruno)
        3 -> Triple("AGATHA", "Muk, Arbok, Gengar, Golbat, Venusaur", R.drawable.combateagatha)
        4 -> Triple("LANCE", "Dragonair, Gyarados, Charizard, Dragonite, Aerodactyl", R.drawable.combatelance)
        else -> Triple("EL CAMPEÓN", "Pidgeot, Alakazam, Rhydon, Gyarados, Exeggutor, Charizard", R.drawable.combateazul)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(model = bannerRes, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)), startY = 300f)))

        MagicParticlesEffect()

        Column(modifier = Modifier.fillMaxSize().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            Text("FASE $phase/5", color = Color.Yellow, fontWeight = FontWeight.Bold)
            Text("VS $bossName", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)

            Spacer(Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = R.drawable.pocion, contentDescription = null, modifier = Modifier.size(40.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Pociones: $potionCount", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { if(potionCount > 0) showHealDialog = true else Toast.makeText(context, "Sin pociones", Toast.LENGTH_SHORT).show() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))) {
                        Text("CURAR")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    SoundManager.playClick(context)
                    val intent = Intent(context, BattleActivity::class.java).apply {
                        putExtra("IS_GYM_BATTLE", true)
                        putExtra("IS_LEAGUE", true)
                        putExtra("LEAGUE_PHASE", phase)
                        putExtra("GYM_TERRAIN", GymTerrain.NONE.name)
                    }
                    context.startActivity(intent)
                    onExit()
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(32.dp)
            ) {
                Text("¡A LUCHAR!", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }

    if (showHealDialog) {
        LeagueHealDialog(onDismiss = { showHealDialog = false }) { uuid ->
            if (PokemonStorage.healPokemon(context, uuid, 50)) {
                PokemonStorage.useItem(context, "potion")
                potionCount = PokemonStorage.getItemCount(context, "potion")
                Toast.makeText(context, "Curado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun LeagueHealDialog(onDismiss: () -> Unit, onHeal: (String) -> Unit) {
    val context = LocalContext.current
    val team = remember { PokemonStorage.getCapturedPokemon(context).filter { it.location == PokemonLocation.TEAM_1 } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mochila de Liga", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                team.forEach { p ->
                    val base = PokemonData.getBaseStats(p.speciesId)
                    Row(modifier = Modifier.fillMaxWidth().clickable { onHeal(p.uuid) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.speciesId}.png", contentDescription = null, modifier = Modifier.size(45.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(base.name, fontWeight = FontWeight.Bold)
                            Text("PS: ${p.getSafeHp()}", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CERRAR") } }
    )
}
