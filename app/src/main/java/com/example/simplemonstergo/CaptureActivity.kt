package com.example.simplemonstergo

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.CubeNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberMainLightNode
import com.google.android.filament.android.TextureHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

class CaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pokemonId = intent.getIntExtra("POKEMON_ID", 1)
        val spawnId = intent.getStringExtra("SPAWN_ID")

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CaptureScreen(
                        pokemonId = pokemonId,
                        onCaptureAction = { instance, isTransfer ->
                            if (spawnId != null) {
                                PokemonStorage.addCapturedSpawnId(this, spawnId)
                            }
                            val base = PokemonData.getBaseStats(pokemonId)
                            if (isTransfer) {
                                PokemonStorage.addCandies(this, base.familyId, 4)
                                Toast.makeText(this, "¡Pokémon transferido! +4 caramelos", Toast.LENGTH_SHORT).show()
                            } else {
                                val list = PokemonStorage.getCapturedPokemon(this).toMutableList()
                                list.add(instance)
                                PokemonStorage.savePokemonList(this, list)
                                PokemonStorage.addCandies(this, base.familyId, 3)
                                Toast.makeText(this, "¡Enviado al PC! +3 caramelos", Toast.LENGTH_SHORT).show()
                            }
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.pokemonsalvajemusica)
    }
}

@Composable
fun CaptureScreen(pokemonId: Int, onCaptureAction: (PokemonInstance, Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var isArMode by remember { mutableStateOf(false) }

    val animatedSpriteUrl = remember(pokemonId) {
        val name = PokemonData.getPokemonNameForSprite(pokemonId)
        "https://play.pokemonshowdown.com/sprites/gen5ani/$name.gif"
    }
    val staticSpriteUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$pokemonId.png"

    var capturedInstance by remember { mutableStateOf<PokemonInstance?>(null) }
    var isPokemonVisible by remember { mutableStateOf(true) }
    var isCapturingSequence by remember { mutableStateOf(false) }

    val introScale = remember { Animatable(0f) }
    val introFlashAlpha = remember { Animatable(1f) }
    val pokemonBase = remember(pokemonId) { PokemonData.getBaseStats(pokemonId) }
    val scaleFactor = remember(pokemonId) { PokemonData.getPokemonScaleFactor(pokemonId) }

    val spriteSize = (200 * scaleFactor).dp

    // Posiciones para detección de impacto
    var pokemonTargetPos by remember { mutableStateOf(Offset.Zero) }
    var ballStartPos by remember { mutableStateOf(Offset.Zero) }
    val hitRadiusPx = with(density) { (spriteSize.toPx() / 2.2f) }

    // AR
    val engine = rememberEngine()
    val childNodes = rememberNodes()
    var arSceneViewRef by remember { mutableStateOf<ARSceneView?>(null) }
    var arPokemonNode by remember { mutableStateOf<Node?>(null) }
    var pokemonBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Animaciones
    val pokeballScale = remember { Animatable(1f) }
    val pokeballOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val pokeballRotation = remember { Animatable(0f) }
    val captureSceneScale = remember { Animatable(1f) }
    val pokemonOffsetX = remember { Animatable(0f) }
    val pokemonOffsetY = remember { Animatable(0f) }

    var isThrowing by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isHoldingBall by remember { mutableStateOf(false) }
    var pokeballCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        pokeballCount = PokemonStorage.getPokeballs(context)
    }

    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(pokemonId) {
        PokemonStorage.markAsSeen(context, pokemonId)
        Glide.with(context).asBitmap().load(staticSpriteUrl).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                pokemonBitmap = resource
            }
            override fun onLoadCleared(p0: android.graphics.drawable.Drawable?) {}
        })
        launch { introScale.animateTo(1f, tween(1000)) }
        launch { introFlashAlpha.animateTo(0f, tween(1200)) }
    }

    if (capturedInstance != null) {
        PokemonSummaryScreen(
            instance = capturedInstance!!,
            onConfirm = { onCaptureAction(capturedInstance!!, false) },
            onTransfer = { onCaptureAction(capturedInstance!!, true) }
        )
        return
    }

    LaunchedEffect(isArMode, isCapturingSequence) {
        if (!isArMode && !isCapturingSequence) {
            while (true) {
                delay(Random.nextLong(2000, 4000))
                val moveType = Random.nextInt(4)
                when (moveType) {
                    0 -> { pokemonOffsetY.animateTo(-150f, tween(300)); pokemonOffsetY.animateTo(0f, tween(300)) }
                    1 -> { pokemonOffsetX.animateTo(-200f, tween(500)); delay(800); pokemonOffsetX.animateTo(0f, tween(500)) }
                    2 -> { pokemonOffsetX.animateTo(200f, tween(500)); delay(800); pokemonOffsetX.animateTo(0f, tween(500)) }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        if (isArMode) {
            ARScene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                childNodes = childNodes,
                planeRenderer = arPokemonNode == null,
                onViewCreated = { arSceneViewRef = this },
                onSessionUpdated = { _, _ ->
                    arSceneViewRef?.let { view ->
                        arPokemonNode?.lookAt(view.cameraNode.worldPosition)
                    }
                },
                mainLightNode = rememberMainLightNode(engine, apply = { intensity = 100000f }),
                onTouchEvent = { e, _ ->
                    if (e.action == MotionEvent.ACTION_DOWN && arPokemonNode == null) {
                        val sceneView = arSceneViewRef
                        val bitmap = pokemonBitmap
                        val hit = sceneView?.frame?.hitTest(e.x, e.y)?.firstOrNull {
                            val trackable = it.trackable
                            trackable is Plane && trackable.trackingState == TrackingState.TRACKING
                        }
                        if (hit != null && bitmap != null) {
                            val anchorNode = AnchorNode(engine, hit.createAnchor())
                            val texture = com.google.android.filament.Texture.Builder()
                                .width(bitmap.width).height(bitmap.height)
                                .sampler(com.google.android.filament.Texture.Sampler.SAMPLER_2D)
                                .format(com.google.android.filament.Texture.InternalFormat.SRGB8_A8).build(engine)
                            TextureHelper.setBitmap(engine, texture, 0, bitmap)
                            val material = sceneView.materialLoader.createImageInstance(texture)
                            val transparent = sceneView.materialLoader.createColorInstance(android.graphics.Color.TRANSPARENT)
                            val visualScale = 0.75f
                            val cNode = CubeNode(
                                engine = engine,
                                size = Size(visualScale, visualScale, 0.01f),
                                center = Position(0f, visualScale / 2.2f, 0f),
                                materialInstances = List(6) { if(it == 2) material else transparent }
                            )
                            anchorNode.addChildNode(cNode)
                            childNodes.add(anchorNode)
                            arPokemonNode = cNode
                            Toast.makeText(context, "¡Pokémon aparecido!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = captureSceneScale.value
                scaleY = captureSceneScale.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.55f)
            }) {
                Image(painter = painterResource(id = R.drawable.fondocaptura), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (isPokemonVisible) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center).offset { IntOffset(pokemonOffsetX.value.toInt(), pokemonOffsetY.value.toInt()) }
                    ) {
                        Text(text = pokemonBase.name, color = ComposeColor.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.background(ComposeColor.Black.copy(alpha = 0.4f)).padding(horizontal = 12.dp, vertical = 4.dp))
                        Spacer(Modifier.height(8.dp))

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.onGloballyPositioned { coords: LayoutCoordinates ->
                            val windowPos = coords.positionInWindow()
                            pokemonTargetPos = Offset(
                                windowPos.x + coords.size.width / 2f,
                                windowPos.y + coords.size.height / 2f
                            )
                        }) {
                            AndroidView(
                                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                update = { view -> Glide.with(view.context).asGif().load(animatedSpriteUrl).into(view) },
                                modifier = Modifier.size(spriteSize).graphicsLayer { scaleX = introScale.value; scaleY = introScale.value }
                            )
                        }
                    }
                }
            }
        }

        // TOP BAR
        Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
            IconButton(
                onClick = { (context as? Activity)?.finish() },
                modifier = Modifier.align(Alignment.CenterStart).background(ComposeColor.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, "Huir", tint = ComposeColor.White)
            }

            IconButton(
                onClick = {
                    SoundManager.playClick(context)
                    scope.launch {
                        flashAlpha.animateTo(0.8f, tween(100))
                        flashAlpha.animateTo(0f, tween(300))
                        Toast.makeText(context, "¡Foto guardada!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.Center).background(ComposeColor.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.Refresh, "Foto", tint = ComposeColor.White)
            }

            // Interruptor RA
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .background(ComposeColor.Black.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RA", color = ComposeColor.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = isArMode,
                    onCheckedChange = {
                        SoundManager.playClick(context)
                        isArMode = it
                        if(!it) arPokemonNode = null
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor.Green,
                        checkedTrackColor = ComposeColor.Green.copy(alpha = 0.5f)
                    )
                )
            }
        }

        // CONTADOR DE POKÉBALLS
        Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.BottomEnd)) {
                Box(
                    modifier = Modifier.size(60.dp).background(ComposeColor.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(id = R.drawable.pokeball), contentDescription = null, modifier = Modifier.size(40.dp))
                }
                Text("x$pokeballCount", color = ComposeColor.White, fontWeight = FontWeight.Bold)
            }
        }

        // LANZAMIENTO DE POKÉBALL
        if (isPokemonVisible || isCapturingSequence || isThrowing) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp).size(100.dp)
                    .onGloballyPositioned { coords: LayoutCoordinates ->
                        if (ballStartPos == Offset.Zero) {
                            val windowPos = coords.positionInWindow()
                            ballStartPos = Offset(
                                windowPos.x + coords.size.width / 2f,
                                windowPos.y + coords.size.height / 2f
                            )
                        }
                    }
                    .offset {
                        val currentOffset = if (isThrowing || isCapturingSequence) pokeballOffset.value else dragOffset
                        IntOffset(currentOffset.x.toInt(), currentOffset.y.toInt())
                    }
                    .graphicsLayer {
                        scaleX = pokeballScale.value
                        scaleY = pokeballScale.value
                        rotationZ = pokeballRotation.value
                    }
                    .pointerInput(isThrowing, isCapturingSequence) {
                        if (isThrowing || isCapturingSequence) return@pointerInput
                        detectDragGestures(
                            onDragStart = { if (pokeballCount > 0) isHoldingBall = true },
                            onDragEnd = {
                                if (!isHoldingBall) return@detectDragGestures
                                isHoldingBall = false
                                if (dragOffset.y < -100f) {
                                    isThrowing = true
                                    PokemonStorage.usePokeball(context)
                                    pokeballCount = PokemonStorage.getPokeballs(context)
                                    scope.launch {
                                        pokeballOffset.snapTo(dragOffset)
                                        val startX = dragOffset.x
                                        val startY = dragOffset.y
                                        val strengthY = -startY
                                        val finalY = startY + (strengthY * -2.5f)
                                        val finalX = startX * 3f
                                        val arcHeight = -400f

                                        launch { pokeballScale.animateTo(0.4f, tween(800)) }
                                        val progress = androidx.compose.animation.core.Animatable(0f)
                                        progress.animateTo(1f, tween(800)) {
                                            val t = value
                                            val currX = startX + t * (finalX - startX)
                                            val currY = startY + t * (finalY - startY) + arcHeight * (4 * t * (1 - t))
                                            scope.launch { pokeballOffset.snapTo(Offset(currX, currY)) }
                                        }

                                        val currentBallPos = ballStartPos + pokeballOffset.value
                                        val distance = sqrt(
                                            (currentBallPos.x - pokemonTargetPos.x).let { it * it } +
                                                    (currentBallPos.y - pokemonTargetPos.y).let { it * it }
                                        )

                                        if (distance < hitRadiusPx) {
                                            isCapturingSequence = true
                                            isPokemonVisible = false
                                            val targetOffsetY = pokemonTargetPos.y - ballStartPos.y
                                            val targetOffsetX = pokemonTargetPos.x - ballStartPos.x

                                            launch { pokeballOffset.animateTo(Offset(targetOffsetX, targetOffsetY), tween(500)) }
                                            launch { captureSceneScale.animateTo(2.2f, tween(1000)) }
                                            launch { pokeballScale.animateTo(0.8f, tween(1000)) }
                                            delay(500)

                                            val finalRate = PokemonData.getBaseCaptureRate(pokemonId)
                                            val isSuccess = Math.random() < finalRate
                                            val shakes = if (isSuccess) 3 else (0..2).random()
                                            for (i in 1..shakes) {
                                                delay(400)
                                                pokeballRotation.animateTo(25f, tween(150))
                                                pokeballRotation.animateTo(-25f, tween(150))
                                                pokeballRotation.animateTo(0f, tween(150))
                                            }
                                            delay(600)
                                            if (isSuccess) {
                                                capturedInstance = PokemonInstance(speciesId = pokemonId)
                                                PokemonStorage.addPlayerExp(context, 100)
                                                PokemonStorage.markAsCaught(context, pokemonId)
                                            } else {
                                                Toast.makeText(context, "¡Se ha escapado de la bola!", Toast.LENGTH_SHORT).show()
                                                val fleeChance = 0.15
                                                if (Math.random() < fleeChance) {
                                                    Toast.makeText(context, "¡El Pokémon ha huido!", Toast.LENGTH_SHORT).show()
                                                    delay(1000)
                                                    (context as? Activity)?.finish()
                                                } else {
                                                    isPokemonVisible = true
                                                    isThrowing = false
                                                    isCapturingSequence = false
                                                    pokeballOffset.snapTo(Offset.Zero)
                                                    pokeballScale.snapTo(1f)
                                                    dragOffset = Offset.Zero
                                                    launch { captureSceneScale.animateTo(1f, tween(500)) }
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, "¡Fallo!", Toast.LENGTH_SHORT).show()
                                            delay(600)
                                            pokeballOffset.snapTo(Offset.Zero)
                                            dragOffset = Offset.Zero
                                            pokeballScale.snapTo(1f)
                                            isThrowing = false
                                        }
                                    }
                                } else {
                                    dragOffset = Offset.Zero
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                            }
                        )
                    }
            ) {
                Image(painter = painterResource(id = R.drawable.pokeball), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Box(Modifier.fillMaxSize().background(ComposeColor.White.copy(alpha = flashAlpha.value)))
    }
}

@Composable
fun PokemonSummaryScreen(instance: PokemonInstance, onConfirm: () -> Unit, onTransfer: () -> Unit) {
    val base = remember(instance.speciesId) { PokemonData.getBaseStats(instance.speciesId) }
    val spriteUrl = "https://play.pokemonshowdown.com/sprites/gen5ani/${PokemonData.getPokemonNameForSprite(instance.speciesId)}.gif"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("¡${base.name} capturado!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        AndroidView(
            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = { view -> Glide.with(view.context).asGif().load(spriteUrl).into(view) },
            modifier = Modifier.size(200.dp)
        )
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Stats Individuales (IVs)", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("HP: ${instance.ivHp}/31")
                        Text("Ataque: ${instance.ivAtk}/31")
                        Text("Defensa: ${instance.ivDef}/31")
                    }
                    Column {
                        Text("At. Esp: ${instance.ivSpAtk}/31")
                        Text("Def. Esp: ${instance.ivSpDef}/31")
                        Text("Velocidad: ${instance.ivSpeed}/31")
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Mandar al PC")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onTransfer,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor.Red)
        ) {
            Text("Transferir al Profesor")
        }
    }
}