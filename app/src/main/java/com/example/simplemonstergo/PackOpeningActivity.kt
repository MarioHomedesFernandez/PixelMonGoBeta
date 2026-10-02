package com.example.simplemonstergo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class PackOpeningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF111111)) {
                    // PackOpening SIN giroscopio (fijo) como pidió el usuario
                    PackOpeningScreen(onClose = { finish() })
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.musicaoutsetisland)
    }
}

@Composable
fun PackOpeningScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var step by remember { mutableStateOf("IDLE") } // "IDLE", "OPENING", "REVEALED"
    val revealedCards = remember { mutableStateListOf<String>() }
    
    // Imagen local del sobre
    val packDrawable = R.drawable.sobreprimeraedicion

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (step == "IDLE") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sobre de Expansión Base Set", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(32.dp))
                
                androidx.compose.foundation.Image(
                    painter = painterResource(id = packDrawable),
                    contentDescription = "Sobre Pokemon",
                    modifier = Modifier
                        .size(260.dp, 380.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (PokemonStorage.useItem(context, "booster_pack")) {
                                SoundManager.playClick(context)
                                step = "OPENING"
                                scope.launch {
                                    delay(1800)
                                    val newCards = generatePack()
                                    revealedCards.clear()
                                    revealedCards.addAll(newCards)
                                    CardStorage.addCards(context, newCards)
                                    step = "REVEALED"
                                }
                            } else {
                                Toast.makeText(context, "No tienes sobres para abrir", Toast.LENGTH_SHORT).show()
                                (context as? android.app.Activity)?.finish()
                            }
                        },
                    contentScale = ContentScale.Fit
                )
                
                Spacer(Modifier.height(24.dp))
                Text("Toca el sobre para abrirlo", color = Color.White.copy(alpha = 0.6f))
            }
        }

        if (step == "OPENING") {
            CircularProgressIndicator(color = Color(0xFFF1C40F), strokeWidth = 6.dp, modifier = Modifier.size(72.dp))
        }

        if (step == "REVEALED") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("¡Cartas Obtenidas!", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(32.dp))
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(440.dp),
                    contentPadding = PaddingValues(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(revealedCards) { cardId ->
                        // Efectos especiales básicos (sin giroscopio aquí)
                        CardRevealItem(cardId)
                    }
                }
                
                Spacer(Modifier.height(48.dp))
                
                Button(
                    onClick = {
                        SoundManager.playClick(context)
                        onClose()
                    },
                    modifier = Modifier.width(260.dp).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1C40F), contentColor = Color.Black),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("GUARDAR EN EL ÁLBUM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

private fun generatePack(): List<String> {
    val pack = mutableListOf<String>()
    repeat(5) {
        val rand = Random.nextDouble(100.0)
        val cardNum = when {
            rand < 5.0 -> (1..16).random()    // Holo Rare (5%)
            rand < 15.0 -> (17..22).random()  // Rare (10%)
            rand < 35.0 -> (23..42).random()  // Uncommon (20%)
            else -> (43..102).random()        // Common (65%)
        }
        pack.add("base/base1/$cardNum")
    }
    return pack
}

@Composable
fun CardRevealItem(cardId: String) {
    val imageUrl = "https://assets.tcgdex.net/en/$cardId/high.jpg"
    val idNum = remember(cardId) { cardId.split("/").last().toIntOrNull() ?: 99 }
    val isHolo = idNum <= 16
    val isRare = idNum in 17..22
    
    val rotation = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        rotation.animateTo(360f, tween(1200, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .width(280.dp)
            .aspectRatio(0.71f)
            .graphicsLayer { 
                rotationY = rotation.value
            }
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.FillBounds
        )
        
        if (isHolo) {
            MagicParticlesEffect()
            AdvancedHoloEffect(0f, 0f)
        } else if (isRare) {
            HolographicGlareEffect(0f, 0f)
        }
    }
}
