package com.example.simplemonstergo

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

class TcgActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private val _tiltX = mutableStateOf(0f)
    private val _tiltY = mutableStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

        setContent {
            MaterialTheme {
                TcgAlbumScreen(
                    tiltX = _tiltX.value,
                    tiltY = _tiltY.value,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.musicaoutsetisland)
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GRAVITY) {
            // Normalizar valores de inclinación
            _tiltX.value = event.values[0] / 9.8f
            _tiltY.value = event.values[1] / 9.8f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TcgAlbumScreen(tiltX: Float, tiltY: Float, onBack: () -> Unit) {
    val context = LocalContext.current
    var ownedCards by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCardId by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf("NUMBER") } // "NUMBER", "RARITY"

    fun refreshCards() {
        ownedCards = CardStorage.getOwnedCardsMap(context)
        isLoading = false
        // Forzar sincronización para que los amigos vean las nuevas cartas
        PokemonStorage.syncToCloud(context)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshCards()
    }

    LaunchedEffect(Unit) {
        refreshCards()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Álbum Base Set", fontWeight = FontWeight.Bold) },
                navigationIcon = { 
                    IconButton(onClick = { 
                        SoundManager.playClick(context)
                        onBack() 
                    }) { 
                        Icon(Icons.Default.ArrowBack, "Atrás") 
                    } 
                },
                actions = {
                    IconButton(onClick = { 
                        SoundManager.playClick(context)
                        sortMode = if (sortMode == "NUMBER") "RARITY" else "NUMBER" 
                    }) {
                        Icon(if (sortMode == "NUMBER") Icons.Default.Star else Icons.Default.List, contentDescription = "Ordenar")
                    }
                }
            )
        },
        floatingActionButton = {
            val packs = remember(ownedCards) { PokemonStorage.getItemCount(context, "booster_pack") }
            if (packs > 0) {
                ExtendedFloatingActionButton(
                    onClick = { 
                        SoundManager.playClick(context)
                        launcher.launch(Intent(context, PackOpeningActivity::class.java)) 
                    },
                    containerColor = Color(0xFFE67E22),
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Abrir Sobre (x$packs)") }
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            if (ownedCards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tu álbum está vacío. ¡Abre tu primer sobre!", color = Color.Gray)
                }
            } else {
                val sortedIds = remember(ownedCards, sortMode) {
                    ownedCards.keys.toList().sortedWith { a, b ->
                        if (sortMode == "NUMBER") {
                            val idA = a.split("/").last().toIntOrNull() ?: 999
                            val idB = b.split("/").last().toIntOrNull() ?: 999
                            idA.compareTo(idB)
                        } else {
                            val rareA = a.split("/").last().toIntOrNull() ?: 999
                            val rareB = b.split("/").last().toIntOrNull() ?: 999
                            // En Base Set, IDs 1-16 son Holo, 17-22 Raras, 23-42 Incomunes, rest Comunes
                            fun getRarityValue(id: Int) = when {
                                id <= 16 -> 0
                                id <= 22 -> 1
                                id <= 42 -> 2
                                else -> 3
                            }
                            getRarityValue(rareA).compareTo(getRarityValue(rareB))
                        }
                    }
                }
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedIds) { cardId ->
                        CardItem(cardId, ownedCards[cardId] ?: 1, tiltX, tiltY) { 
                            SoundManager.playClick(context)
                            selectedCardId = it 
                        }
                    }
                }
            }
        }
    }

    if (selectedCardId != null) {
        CardDetailDialog(cardId = selectedCardId!!, tiltX = tiltX, tiltY = tiltY, onDismiss = { 
            SoundManager.playClick(context)
            selectedCardId = null 
        })
    }
}

@Composable
fun CardItem(cardId: String, count: Int, tiltX: Float, tiltY: Float, onClick: (String) -> Unit) {
    val imageUrl = "https://assets.tcgdex.net/en/${cardId.replace("-", "/")}/low.jpg"
    val idNum = remember(cardId) { cardId.split("/").last().toIntOrNull() ?: 99 }
    val isHolo = idNum <= 16
    val isRare = idNum in 17..22

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.71f)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick(cardId) }
            .border(2.dp, if(isHolo) Color(0xFFF1C40F) else if(isRare) Color(0xFFBDC3C7) else Color.Transparent, RoundedCornerShape(8.dp))
    ) {
        AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        
        if (isHolo) {
            MagicParticlesEffect()
            AdvancedHoloEffect(tiltX, tiltY)
        } else if (isRare) {
            HolographicGlareEffect(tiltX, tiltY)
        }

        if (count > 1) {
            Box(
                modifier = Modifier.padding(4.dp).size(22.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape).align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Text("x$count", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CardDetailDialog(cardId: String, tiltX: Float, tiltY: Float, onDismiss: () -> Unit) {
    val imageUrl = "https://assets.tcgdex.net/en/${cardId.replace("-", "/")}/high.jpg"
    val idNum = remember(cardId) { cardId.split("/").last().toIntOrNull() ?: 99 }
    val isHolo = idNum <= 16
    val isRare = idNum in 17..22

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(0.71f)
                        .graphicsLayer {
                            rotationY = tiltX * 15f
                            rotationX = -tiltY * 15f
                            cameraDistance = 12f * density
                        }
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    
                    if (isHolo) {
                        MagicParticlesEffect()
                        AdvancedHoloEffect(tiltX, tiltY)
                    } else if (isRare) {
                        HolographicGlareEffect(tiltX, tiltY)
                    }
                }
                
                Spacer(Modifier.height(40.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
        }
    }
}
