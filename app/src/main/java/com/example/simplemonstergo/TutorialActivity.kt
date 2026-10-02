package com.example.simplemonstergo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

class TutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TutorialScreen {
                    PokemonStorage.setTutorialDone(this)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}

@Composable
fun TutorialScreen(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 15

    val titles = listOf(
        "¡Bienvenido Entrenador!",
        "El Mapa del Mundo",
        "Tu Perfil",
        "Captura Pokémon",
        "Menú Pokéball",
        "Equipo y Colección",
        "Mochila y Objetos",
        "Pokedex y TCG",
        "Tienda y Monedas",
        "Configuración",
        "Prueba: Combate Pokémon",
        "Prueba: Incursión Ranger",
        "Gimnasios y Medallas",
        "Liga y Legendarios",
        "¡A JUGAR!",
    )

    val descriptions = listOf(
        "Estás a punto de comenzar tu aventura en PixelMon GO. Pulsa 'Siguiente' para aprender lo básico.",
        "En el mapa verás Pokémon salvajes (tócalos para capturar), Pokeparadas (tocalas para obtener bolas y pociones), Centros Pokémon(tienen el almacenamiento de los pokémon capturados y puedes organizar tus equipos) y Gimnasios (para ganar medallas y dinero).",
        "Pulsa en tu foto abajo a la izquierda. Podrás ver tu nivel, medallas, añadir amigos e incluso presumir tu carta TCG favorita.",
        "Desliza la Pokéball hacia el Pokémon. Si el círculo de color es pequeño al golpear, tendrás más probabilidades de éxito.",
        "Pulsa la Pokéball central para abrir el menú de utilidades.",
        "En 'Equipo' gestionas tus dos grupos: el Equipo 1 para combates tradicionales y el Equipo 2 para apoyarte en Incursiones Ranger.",
        "En la 'Mochila' puedes usar Pociones para curar a tus Pokémon heridos y Revivir para aquellos que se hayan debilitado. También podrás usar los pases de liga y el titulo de campeón(más adelante explicare para que sirve)",
        "La 'Pokedex' registra todos los Pokémon vistos y capturados. En 'Cartas TCG' puedes coleccionar cartas de las expansiones oficiales.",
        "Usa las monedas ganadas para comprar Pokéballs, Pociones o Revivir y las medallas para comprar pases a la liga Pokémon.",
        "Pulsa el engranaje arriba a la derecha en el menú para ajustar el volumen, cerrar sesión, guardar partida en la nube o ver la versión.",
        "Simulacro: Tu Squirtle contra un Charmander. Pulsa Atacar y elige un movimiento efectivo.",
        "Simulacro: Incursión Ranger. Dibuja círculos rápidos alrededor de Pikachu. Los Pokémon de tu Equipo 2 te darán poderes especiales.",
        "Gana a los líderes de gimnasio para obtener Medallas. Necesitarás 100 medallas para el siguiente paso.",
        "Con 100 medallas, usa un Pase de Liga. ¡Al ganar el Título de Campeón, podrás rastrear y capturar Pokémon Legendarios!",
        "Ya eres un experto. ¡Sal ahí fuera y hazte con todos!"
    )

    BackHandler {
        if (step > 0) step--
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2980B9)), contentAlignment = Alignment.Center) {
        when (step) {
            10 -> {
                BattleSimulator { step++ }
            }
            11 -> {
                RaidSimulator { step++ }
            }
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = titles[step],
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC0392B),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Text(
                            text = descriptions[step],
                            fontSize = 16.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        
                        Spacer(Modifier.height(32.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (step > 0) {
                                TextButton(onClick = { step-- }) {
                                    Text("ATRÁS", color = Color.Gray)
                                }
                            } else {
                                Spacer(Modifier.width(60.dp))
                            }
                            
                            Button(
                                onClick = {
                                    if (step < totalSteps - 1) step++
                                    else onFinish()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B))
                            ) {
                                Text(if (step < totalSteps - 1) "SIGUIENTE" else "¡A JUGAR!")
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        LinearProgressIndicator(
                            progress = { (step + 1).toFloat() / totalSteps.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color(0xFFC0392B),
                            trackColor = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BattleSimulator(onDone: () -> Unit) {
    var enemyHp by remember { mutableFloatStateOf(100f) }
    var playerHp by remember { mutableFloatStateOf(100f) }
    var log by remember { mutableStateOf("¡Un Charmander salvaje aparece!") }
    var showMoves by remember { mutableStateOf(false) }
    var isEnabled by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.fondobatalla),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Enemy Info
        Column(Modifier.padding(32.dp).align(Alignment.TopStart).background(Color.Black.copy(0.6f)).padding(8.dp)) {
            Text("Charmander", color = Color.White, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { enemyHp / 100f }, color = Color.Red, modifier = Modifier.width(150.dp))
        }

        // Enemy Sprite
        AsyncImage(
            model = "https://play.pokemonshowdown.com/sprites/gen5ani/charmander.gif",
            contentDescription = null,
            modifier = Modifier.size(180.dp).align(Alignment.TopEnd).padding(top = 80.dp, end = 40.dp)
        )

        // Player Info
        Column(Modifier.padding(bottom = 200.dp, end = 32.dp).align(Alignment.BottomEnd).background(Color.Black.copy(0.6f)).padding(8.dp)) {
            Text("Squirtle", color = Color.White, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { playerHp / 100f }, color = Color.Green, modifier = Modifier.width(150.dp))
        }

        // Player Sprite
        AsyncImage(
            model = "https://play.pokemonshowdown.com/sprites/gen5ani-back/squirtle.gif",
            contentDescription = null,
            modifier = Modifier.size(220.dp).align(Alignment.BottomStart).padding(bottom = 160.dp, start = 40.dp)
        )

        // Log and Controls
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 48.dp)) {
            Box(Modifier.fillMaxWidth().height(60.dp).background(Color.White.copy(0.8f)).padding(8.dp), contentAlignment = Alignment.Center) {
                Text(log, color = Color.Black, textAlign = TextAlign.Center)
            }

            if (enemyHp > 0) {
                Row(Modifier.fillMaxWidth().height(100.dp).background(Color.DarkGray).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!showMoves) {
                        Button(onClick = { showMoves = true }, modifier = Modifier.weight(1f).fillMaxHeight()) { Text("ATACAR") }
                        Button(onClick = { }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2980B9))) { Text("CAMBIAR") }
                    } else {
                        Button(onClick = { 
                            if (!isEnabled) return@Button
                            isEnabled = false
                            enemyHp = 0f
                            log = "¡Squirtle usó Burbuja! Es súper efectivo."
                        }, modifier = Modifier.weight(1f).fillMaxHeight()) { Text("BURBUJA") }
                        Button(onClick = { 
                            if (!isEnabled) return@Button
                            isEnabled = false
                            enemyHp -= 20f
                            log = "¡Squirtle usó Placaje!"
                        }, modifier = Modifier.weight(1f).fillMaxHeight()) { Text("PLACAJE") }
                    }
                }
            } else {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))) { 
                    Text("¡VICTORIA! CONTINUAR", fontWeight = FontWeight.Bold, fontSize = 20.sp) 
                }
            }
        }
    }
}

@Composable
fun RaidSimulator(onDone: () -> Unit) {
    var bossHp by remember { mutableFloatStateOf(1000f) }
    var playerHp by remember { mutableFloatStateOf(100f) }
    var circles by remember { mutableIntStateOf(0) }
    val currentPathPoints = remember { mutableStateListOf<Offset>() }
    val thunderAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(circles) {
        if (circles > 0) {
            bossHp = (1000f - circles * 100f).coerceAtLeast(0f)
            if (circles % 3 == 0) {
                scope.launch {
                    thunderAlpha.animateTo(0.6f, tween(100))
                    delay(300)
                    thunderAlpha.animateTo(0f, tween(300))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.fondobatallaranger),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Boss Info
        Column(Modifier.align(Alignment.TopCenter).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PIKACHU", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            LinearProgressIndicator(progress = { bossHp / 1000f }, color = Color.Red, modifier = Modifier.width(250.dp).height(10.dp).clip(CircleShape))
        }

        // Boss Sprite
        AsyncImage(
            model = "https://play.pokemonshowdown.com/sprites/gen5ani/pikachu.gif",
            contentDescription = null,
            modifier = Modifier.size(150.dp).align(Alignment.Center)
        )
        
        // Attack Effect (Thunder)
        Box(Modifier.fillMaxSize().background(Color.Yellow.copy(alpha = thunderAlpha.value)))

        // Drawing Area
        Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { currentPathPoints.clear() },
                onDragEnd = { currentPathPoints.clear() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val newPoint = change.position
                    
                    if (currentPathPoints.isNotEmpty()) {
                        for (i in 0 until currentPathPoints.size - 15) {
                            val oldP = currentPathPoints[i]
                            if (sqrt((newPoint.x - oldP.x)*(newPoint.x - oldP.x) + (newPoint.y - oldP.y)*(newPoint.y - oldP.y)) < 60f) {
                                circles++
                                SoundManager.playSound(context, R.raw.clicksonido)
                                bossHp = (bossHp - 100f).coerceAtLeast(0f)
                                currentPathPoints.clear()
                                break
                            }
                        }
                    }
                    currentPathPoints.add(newPoint)
                }
            )
        }) {
            val pathPoints = currentPathPoints.toList()
            if (pathPoints.size > 1) {
                val path = Path()
                path.moveTo(pathPoints[0].x, pathPoints[0].y)
                for (i in 1 until pathPoints.size) {
                    path.lineTo(pathPoints[i].x, pathPoints[i].y)
                }
                drawPath(path, Color.Cyan, style = Stroke(width = 10f))
            }
        }

        // Resistance Bar
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RESISTENCIA RANGER", color = Color.Cyan, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { playerHp / 100f }, color = Color.Cyan, modifier = Modifier.width(300.dp).height(12.dp).clip(CircleShape))
                Text("Progreso: $circles / 10", color = Color.Yellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (bossHp <= 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)), contentAlignment = Alignment.Center) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("¡CAPTURADO!", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color(0xFF27AE60))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onDone) { Text("CONTINUAR") }
                    }
                }
            }
        }
    }
}
