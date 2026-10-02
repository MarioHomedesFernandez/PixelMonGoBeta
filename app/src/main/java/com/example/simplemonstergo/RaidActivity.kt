package com.example.simplemonstergo

import android.os.Bundle
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

sealed class RaidAttack {
    data class EnergyBlast(val center: Offset, var radius: Float, val maxRadius: Float) : RaidAttack()
    data class FireBall(var pos: Offset, val target: Offset, var speed: Float) : RaidAttack()
    data class ThunderStrike(val center: Offset, var timer: Int) : RaidAttack()
}

class RaidActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pokemonId = intent.getIntExtra("POKEMON_ID", 150) 

        setContent {
            MaterialTheme {
                RaidScreen(pokemonId) { finish() }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.batallamusica)
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun RaidScreen(pokemonId: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    val pokemonBase = remember { PokemonData.getBaseStats(pokemonId) }
    
    var pokemonHp by remember { mutableStateOf(5000f) }
    val maxPokemonHp = 5000f
    var playerHp by remember { mutableStateOf(100f) }
    val maxPlayerHp = 100f
    var timeLeft by remember { mutableIntStateOf(60) }
    
    val currentPathPoints = remember { mutableStateListOf<Offset>() }
    var circlesCompletedInCurrentStroke by remember { mutableIntStateOf(0) }
    
    val attacks = remember { mutableStateListOf<RaidAttack>() }
    val pokemonOffset = remember { Animatable(Offset(0f, 0f), Offset.VectorConverter) }
    val scope = rememberCoroutineScope()

    // --- EFECTO VISUAL COMPAÑEROS ---
    data class SupportVisual(val speciesId: Int, val pos: Animatable<Offset, AnimationVector2D>, val alpha: Animatable<Float, AnimationVector1D>)
    val activeVisualSupports = remember { mutableStateListOf<SupportVisual>() }

    // --- COMPAÑEROS RANGER ---
    val rangerTeam = remember {
        PokemonStorage.getCapturedPokemon(context)
            .filter { it.location == PokemonLocation.TEAM_2 }
            .take(6) // Mostrar todo el Equipo 2
    }
    val activeSupports = remember { mutableStateMapOf<String, Int>() } // uuid -> cooldown/timer

    // Efectos de soporte activos
    var damageMultiplier by remember { mutableStateOf(1f) }
    var enemyAttackSpeed by remember { mutableStateOf(1f) }
    var enemyMoveSpeed by remember { mutableStateOf(1f) }
    var resistanceRegen by remember { mutableStateOf(0f) }
    var poisonDamage by remember { mutableStateOf(0f) }
    var damageTakenMultiplier by remember { mutableStateOf(1f) }
    var doubleCircles by remember { mutableStateOf(false) }
    var circlesEasier by remember { mutableStateOf(false) }
    var enemyStunned by remember { mutableStateOf(false) }

    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (pokemonHp > 0 && playerHp > 0 && timeLeft > 0) {
            showExitDialog = true
        } else {
            onExit()
        }
    }

    LaunchedEffect(activeSupports.size) {
        // Reset multipliers
        damageMultiplier = 1f
        enemyAttackSpeed = 1f
        enemyMoveSpeed = 1f
        resistanceRegen = 0f
        poisonDamage = 0f
        damageTakenMultiplier = 1f
        doubleCircles = false
        circlesEasier = false
        enemyStunned = false

        activeSupports.forEach { (uuid, _) ->
            val p = rangerTeam.find { it.uuid == uuid } ?: return@forEach
            val base = PokemonData.getBaseStats(p.speciesId)
            val isFinal = base.evolutionId == null
            val isLegendary = listOf(144, 145, 146, 149, 150, 151).contains(p.speciesId)
            val boost = if (isLegendary) 2.0f else if (isFinal) 1.5f else 1.0f

            val mainType = base.types.firstOrNull() ?: "Normal"
            when(mainType) {
                "Normal" -> damageMultiplier += 0.2f * boost
                "Fuego" -> damageMultiplier += 0.8f * boost
                "Agua" -> enemyAttackSpeed -= 0.4f * boost
                "Planta" -> resistanceRegen += 0.1f * boost
                "Eléctrico" -> enemyMoveSpeed -= 0.5f * boost
                "Hielo" -> {
                    enemyAttackSpeed -= 0.3f * boost
                    enemyMoveSpeed -= 0.3f * boost
                }
                "Lucha" -> damageMultiplier += 1.2f * boost
                "Veneno" -> poisonDamage += 4f * boost
                "Tierra" -> damageTakenMultiplier *= (1f - 0.4f * boost).coerceAtLeast(0.1f)
                "Volador" -> circlesEasier = true
                "Psíquico" -> enemyAttackSpeed -= 0.6f * boost
                "Bicho" -> doubleCircles = true
                "Roca" -> damageTakenMultiplier *= (1f - 0.5f * boost).coerceAtLeast(0.1f)
                "Fantasma" -> damageTakenMultiplier *= 0.2f
                "Dragón" -> damageMultiplier += 2.0f * boost
                "Siniestro" -> enemyStunned = true
                "Acero" -> damageTakenMultiplier *= 0.3f
                "Hada" -> { /* Heal is instant */ }
            }
        }
    }

    LaunchedEffect(Unit) {
        while(timeLeft > 0 && playerHp > 0 && pokemonHp > 0) {
            delay(1000)
            timeLeft--
        }
    }

    LaunchedEffect(Unit) {
        var lastSpawnTime = 0L
        while(playerHp > 0 && pokemonHp > 0 && timeLeft > 0) {
            val currentTime = System.currentTimeMillis()
            delay(16)
            
            // --- LOGICA DE SPAWN (Cada 2 segundos aprox, ajustado por velocidad) ---
            if (!enemyStunned && currentTime - lastSpawnTime > (2000 / enemyAttackSpeed.coerceAtLeast(0.2f))) {
                lastSpawnTime = currentTime
                
                // Movimiento más amplio por toda la pantalla
                pokemonOffset.animateTo(
                    Offset(Random.nextInt(-350, 300).toFloat(), Random.nextInt(-450, 400).toFloat()),
                    tween((1200 / enemyMoveSpeed.coerceAtLeast(0.5f)).toInt())
                )
                
                val r = Random.nextFloat()
                when {
                    r < 0.4f -> {
                        val spawnPos = if (Random.nextBoolean()) {
                            Offset(500f + pokemonOffset.value.x, 800f + pokemonOffset.value.y)
                        } else {
                            Offset(Random.nextInt(50, 1000).toFloat(), Random.nextInt(300, 1600).toFloat())
                        }
                        attacks.add(RaidAttack.EnergyBlast(spawnPos, 10f, 650f))
                    }
                    r < 0.7f -> {
                        val startPos = Offset(500f + pokemonOffset.value.x, 800f + pokemonOffset.value.y)
                        attacks.add(RaidAttack.FireBall(startPos, Offset(Random.nextInt(0, 1080).toFloat(), 2200f), 20f))
                    }
                    else -> {
                        attacks.add(RaidAttack.ThunderStrike(Offset(Random.nextInt(50, 1000).toFloat(), Random.nextInt(200, 1800).toFloat()), 60))
                    }
                }
            }

            // --- ACTUALIZACION DE ESTADO ---
            if (resistanceRegen > 0) playerHp = (playerHp + resistanceRegen).coerceAtMost(maxPlayerHp)
            if (poisonDamage > 0) pokemonHp = (pokemonHp - poisonDamage).coerceAtLeast(1f)
            
            // Usamos una lista local para procesar y luego actualizamos el estado de golpe
            val currentAttacks = attacks.toList()
            val nextAttacks = mutableListOf<RaidAttack>()
            
            currentAttacks.forEach { attack ->
                when (attack) {
                    is RaidAttack.EnergyBlast -> {
                        val newRadius = attack.radius + 7f
                        if (newRadius <= attack.maxRadius) {
                            nextAttacks.add(attack.copy(radius = newRadius))
                        }
                    }
                    is RaidAttack.FireBall -> {
                        val dx = attack.target.x - attack.pos.x
                        val dy = attack.target.y - attack.pos.y
                        val dist = sqrt(dx*dx + dy*dy)
                        if (dist >= 20f) {
                            val nx = attack.pos.x + (dx / dist) * attack.speed
                            val ny = attack.pos.y + (dy / dist) * attack.speed
                            nextAttacks.add(attack.copy(pos = Offset(nx, ny)))
                        }
                    }
                    is RaidAttack.ThunderStrike -> {
                        if (attack.timer > 0) {
                            nextAttacks.add(attack.copy(timer = attack.timer - 1))
                        }
                    }
                }
            }
            
            // Actualización masiva de ataques
            attacks.clear()
            attacks.addAll(nextAttacks)

            // Update supports
            val keys = activeSupports.keys.toList()
            keys.forEach { uuid ->
                val time = activeSupports[uuid] ?: 0
                if (time <= 1) activeSupports.remove(uuid)
                else activeSupports[uuid] = time - 1
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

        Column(
            modifier = Modifier.align(Alignment.Center).offset(y = (-80).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TIEMPO: $timeLeft",
                color = if (timeLeft < 10) Color.Red else Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(pokemonBase.name.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            LinearProgressIndicator(
                progress = pokemonHp / maxPokemonHp,
                modifier = Modifier.width(180.dp).height(8.dp).clip(CircleShape),
                color = Color.Red,
                trackColor = Color.Gray
            )
            
            Spacer(Modifier.height(30.dp))
            
            Box(modifier = Modifier.size(160.dp).offset(x = pokemonOffset.value.x.dp / 8, y = pokemonOffset.value.y.dp / 8)) {
                val spriteName = PokemonData.getPokemonNameForSprite(pokemonId)
                AndroidView(
                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                    update = { view -> Glide.with(view.context).asGif().load("https://play.pokemonshowdown.com/sprites/gen5ani/$spriteName.gif").into(view) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // --- DIBUJADO DE COMPAÑEROS ACTIVOS ---
        activeVisualSupports.forEach { visual ->
            AsyncImage(
                model = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${visual.speciesId}.png",
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .offset(x = visual.pos.value.x.dp / 5, y = visual.pos.value.y.dp / 5)
                    .alpha(visual.alpha.value)
            )
        }

        Canvas(
            modifier = Modifier.fillMaxSize().pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        currentPathPoints.clear()
                        circlesCompletedInCurrentStroke = 0
                        currentPathPoints.add(Offset(event.x, event.y))
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val newPoint = Offset(event.x, event.y)
                        
                        // Colisión con ataques (Usando copia para evitar concurrent modification)
                        val attackList = attacks.toList()
                        attackList.forEach { attack ->
                            when(attack) {
                                is RaidAttack.EnergyBlast -> {
                                    val dx = newPoint.x - attack.center.x
                                    val dy = newPoint.y - attack.center.y
                                    val dist = sqrt(dx*dx + dy*dy)
                                    if (Math.abs(dist - attack.radius) < 30f) { // Hitbox un poco más generosa
                                        playerHp -= 3f * damageTakenMultiplier
                                        currentPathPoints.clear()
                                        circlesCompletedInCurrentStroke = 0
                                        return@pointerInteropFilter true
                                    }
                                }
                                is RaidAttack.FireBall -> {
                                    val dx = newPoint.x - attack.pos.x
                                    val dy = newPoint.y - attack.pos.y
                                    val dist = sqrt(dx*dx + dy*dy)
                                    if (dist < 70f) {
                                        playerHp -= 5f * damageTakenMultiplier
                                        currentPathPoints.clear()
                                        circlesCompletedInCurrentStroke = 0
                                        return@pointerInteropFilter true
                                    }
                                }
                                is RaidAttack.ThunderStrike -> {
                                    if (attack.timer < 12) { // Impacto
                                        val dx = newPoint.x - attack.center.x
                                        val dy = newPoint.y - attack.center.y
                                        val dist = sqrt(dx*dx + dy*dy)
                                        if (dist < 120f) {
                                            playerHp -= 10f * damageTakenMultiplier
                                            currentPathPoints.clear()
                                            circlesCompletedInCurrentStroke = 0
                                            return@pointerInteropFilter true
                                        }
                                    }
                                }
                            }
                        }

                        if (currentPathPoints.isNotEmpty()) {
                            // Reducir frecuencia de puntos para mejorar rendimiento
                            val lastPoint = currentPathPoints.last()
                            val distLast = sqrt((newPoint.x - lastPoint.x)*(newPoint.x - lastPoint.x) + (newPoint.y - lastPoint.y)*(newPoint.y - lastPoint.y))
                            if (distLast < 15f) return@pointerInteropFilter true 

                            for (i in 0 until currentPathPoints.size - 15) {
                                val oldP = currentPathPoints[i]
                                val dx = newPoint.x - oldP.x
                                val dy = newPoint.y - oldP.y
                                val intersectionDist = if (circlesEasier) 100f else 60f
                                if (sqrt(dx*dx + dy*dy) < intersectionDist) {
                                    circlesCompletedInCurrentStroke += if (doubleCircles) 2 else 1
                                    // Throttled sound
                                    if (System.currentTimeMillis() % 3 == 0L) {
                                        SoundManager.playSound(context, R.raw.clicksonido)
                                    }
                                    val remainingPoints = currentPathPoints.subList(i + 1, currentPathPoints.size).toMutableList()
                                    currentPathPoints.clear()
                                    currentPathPoints.addAll(remainingPoints)
                                    break
                                }
                            }
                        }
                        currentPathPoints.add(newPoint)
                        if (currentPathPoints.size > 80) currentPathPoints.removeAt(0) // Reducido de 100 a 80
                    }
                    MotionEvent.ACTION_UP -> {
                        val damage = circlesCompletedInCurrentStroke * 25f * damageMultiplier
                        if (damage > 0) pokemonHp = (pokemonHp - damage).coerceAtLeast(0f)
                        currentPathPoints.clear()
                        circlesCompletedInCurrentStroke = 0
                    }
                }
                true
            }
        ) {
            // Estela de seguimiento (Optimizada: usando copia segura y limitando segmentos)
            val pathPoints = currentPathPoints.toList()
            if (pathPoints.size > 1) {
                for (i in 0 until pathPoints.size - 1) {
                    val alpha = (i.toFloat() / pathPoints.size).coerceIn(0f, 1f)
                    val width = 4f + 12f * alpha
                    drawLine(
                        color = Color.Cyan.copy(alpha = alpha * 0.8f),
                        start = pathPoints[i],
                        end = pathPoints[i+1],
                        strokeWidth = width
                    )
                }
            }
            
            // Colisión con ataques (Usando copia para evitar concurrent modification)
            val drawAttacks = attacks.toList()
            drawAttacks.forEach { attack ->
                when(attack) {
                    is RaidAttack.EnergyBlast -> {
                        drawCircle(
                            color = Color(0xFF9B59B6).copy(alpha = 0.7f * (1f - (attack.radius / attack.maxRadius))),
                            radius = attack.radius,
                            center = attack.center,
                            style = Stroke(width = 14f)
                        )
                    }
                    is RaidAttack.FireBall -> {
                        drawCircle(Color.Red, radius = 35f, center = attack.pos)
                        drawCircle(Color.Yellow, radius = 18f, center = attack.pos)
                    }
                    is RaidAttack.ThunderStrike -> {
                        if (attack.timer > 12) {
                            drawCircle(Color.Yellow.copy(alpha = 0.4f), radius = 120f, center = attack.center, style = Stroke(3f))
                        } else {
                            drawCircle(Color.White, radius = 120f, center = attack.center)
                            drawLine(Color.Yellow, Offset(attack.center.x, 0f), attack.center, strokeWidth = 25f)
                            // Destello secundario
                            drawLine(Color.White, Offset(attack.center.x - 8f, 0f), attack.center.copy(x = attack.center.x - 8f), strokeWidth = 6f)
                        }
                    }
                }
            }
        }

        // --- UI COMPAÑEROS ---
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rangerTeam.forEach { p ->
                val isCooldown = activeSupports.containsKey(p.uuid)
                val base = PokemonData.getBaseStats(p.speciesId)
                val canActivate = !isCooldown && activeSupports.size < 2

                Box(
                    modifier = Modifier
                        .size(50.dp) // Un poco más pequeños para que quepan 6
                        .clip(CircleShape)
                        .background(if (isCooldown) Color.Gray else if (canActivate) Color.White.copy(alpha = 0.8f) else Color.DarkGray.copy(alpha = 0.5f))
                        .border(2.dp, if (canActivate) Color.Cyan else Color.Transparent, CircleShape)
                        .clickable(canActivate) {
                            SoundManager.playSound(context, R.raw.clicksonido)
                            val isLegendary = listOf(144, 145, 146, 149, 150, 151).contains(p.speciesId)
                            val isFinal = base.evolutionId == null
                            val duration = if (isLegendary) 1000 else if (isFinal) 600 else 400
                            activeSupports[p.uuid] = duration

                            val mainType = base.types.firstOrNull() ?: "Normal"
                            if (mainType == "Hada") {
                                playerHp = (playerHp + 30f).coerceAtMost(maxPlayerHp)
                            }

                            // Lanzar animación visual
                            val visual = SupportVisual(
                                p.speciesId,
                                Animatable(Offset(100f, 1500f), Offset.VectorConverter),
                                Animatable(0f)
                            )
                            activeVisualSupports.add(visual)
                            
                            scope.launch {
                                // Entrada rápida desde la esquina inferior
                                launch { visual.alpha.animateTo(1f, tween(300)) }
                                visual.pos.animateTo(Offset(180f, 1500f), tween(400, easing = LinearOutSlowInEasing))
                                
                                delay(2000) // Se queda un momento corto para mostrar el apoyo
                                
                                // Se retira rápidamente hacia un lado
                                launch { visual.alpha.animateTo(0f, tween(400)) }
                                visual.pos.animateTo(Offset(-400f, 1600f), tween(500, easing = FastOutLinearInEasing))
                                activeVisualSupports.remove(visual)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.speciesId}.png",
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).alpha(if(isCooldown || !canActivate) 0.5f else 1f)
                    )
                    if (isCooldown) {
                        CircularProgressIndicator(
                            progress = (activeSupports[p.uuid]?.toFloat() ?: 0f) / 1000f,
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Cyan,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(40.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RESISTENCIA RANGER", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = playerHp / maxPlayerHp,
                    modifier = Modifier.width(250.dp).height(12.dp).clip(CircleShape),
                    color = Color.Cyan,
                    trackColor = Color.DarkGray
                )
                Spacer(Modifier.height(10.dp))
                Text("Círculos acumulados: $circlesCompletedInCurrentStroke", color = Color.Yellow, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("¿Abandonar incursión?") },
                text = { Text("Si sales ahora perderás esta oportunidad de captura.") },
                confirmButton = { Button(onClick = onExit) { Text("SALIR") } },
                dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("QUEDARSE") } }
            )
        }
        
        if (pokemonHp <= 0) {
            val caughtLegendary = remember {
                PokemonInstance(
                    speciesId = pokemonId,
                    level = 50,
                    ivHp = Random.nextInt(20, 32),
                    ivAtk = Random.nextInt(20, 32),
                    ivDef = Random.nextInt(20, 32),
                    ivSpAtk = Random.nextInt(20, 32),
                    ivSpDef = Random.nextInt(20, 32),
                    ivSpeed = Random.nextInt(20, 32)
                )
            }
            
            LaunchedEffect(Unit) {
                val list = PokemonStorage.getCapturedPokemon(context).toMutableList()
                list.add(caughtLegendary)
                PokemonStorage.savePokemonList(context, list)
                PokemonStorage.markAsCaught(context, pokemonId)
            }

            RaidPokemonSummaryScreen(
                instance = caughtLegendary,
                onConfirm = onExit,
                onTransfer = { 
                    PokemonStorage.removePokemon(context, caughtLegendary.uuid)
                    onExit()
                }
            )
        } else if (playerHp <= 0 || timeLeft <= 0) {
            val failureMsg = if (timeLeft <= 0) "¡Se ha acabado el tiempo!" else "${pokemonBase.name} ha sobrepasado tu resistencia."
            RaidResultScreen("INCURSIÓN FALLIDA", failureMsg, false, onExit)
        }
    }
}

@Composable
private fun RaidResultScreen(title: String, msg: String, success: Boolean, onExit: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(32.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = if(success) Color(0xFF27AE60) else Color.Red)
                Spacer(Modifier.height(8.dp))
                Text(msg, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onExit) { Text("VOLVER AL MAPA") }
            }
        }
    }
}

@Composable
private fun RaidPokemonSummaryScreen(instance: PokemonInstance, onConfirm: () -> Unit, onTransfer: () -> Unit) {
    val base = remember(instance.speciesId) { PokemonData.getBaseStats(instance.speciesId) }
    val spriteUrl = "https://play.pokemonshowdown.com/sprites/gen5ani/${PokemonData.getPokemonNameForSprite(instance.speciesId)}.gif"
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).background(MaterialTheme.colorScheme.surface), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("¡${base.name} capturado!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, update = { view -> Glide.with(view.context).asGif().load(spriteUrl).into(view) }, modifier = Modifier.size(200.dp))
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Stats Individuales (IVs)", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("HP: ${instance.ivHp}/31"); Text("Ataque: ${instance.ivAtk}/31"); Text("Defensa: ${instance.ivDef}/31") }
                    Column { Text("At. Esp: ${instance.ivSpAtk}/31"); Text("Def. Esp: ${instance.ivSpDef}/31"); Text("Velocidad: ${instance.ivSpeed}/31") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Mandar al PC") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onTransfer, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text("Transferir al Profesor") }
    }
}
