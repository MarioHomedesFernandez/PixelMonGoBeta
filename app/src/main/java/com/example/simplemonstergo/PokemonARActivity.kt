package com.example.simplemonstergo

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberNodes
import com.google.android.filament.android.TextureHelper
import kotlinx.coroutines.launch

class PokemonARActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val speciesId = intent.getIntExtra("SPECIES_ID", 1)
        setContent {
            MaterialTheme {
                PokemonARScreen(speciesId, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonARScreen(speciesId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val childNodes = rememberNodes()
    var arSceneViewRef by remember { mutableStateOf<ARSceneView?>(null) }
    var isPlaced by remember { mutableStateOf(false) }
    var pokemonNode by remember { mutableStateOf<Node?>(null) }
    
    var pokemonBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Volvemos al sprite estático para RA como pidió el usuario
    val spriteUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$speciesId.png"
    
    val sizeEnum = remember(speciesId) { PokemonData.getPokemonSize(speciesId) }
    val visualScale = when(sizeEnum) {
        PokemonSize.PEQUENO -> 0.45f
        PokemonSize.MEDIANO -> 0.75f
        PokemonSize.GRANDE -> 1.3f
        PokemonSize.MUY_GRANDE -> 2.1f
    }

    LaunchedEffect(speciesId) {
        Glide.with(context).asBitmap().load(spriteUrl).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                pokemonBitmap = resource
            }
            override fun onLoadCleared(p0: android.graphics.drawable.Drawable?) {}
        })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            childNodes = childNodes,
            planeRenderer = !isPlaced,
            onViewCreated = { arSceneViewRef = this },
            onSessionUpdated = { _, _ ->
                arSceneViewRef?.let { view ->
                    pokemonNode?.lookAt(view.cameraNode.worldPosition)
                }
            },
            mainLightNode = rememberMainLightNode(engine, apply = { intensity = 100000f }),
            onTouchEvent = { e, _ ->
                if (e.action == MotionEvent.ACTION_DOWN && !isPlaced) {
                    val sceneView = arSceneViewRef
                    val bitmap = pokemonBitmap
                    if (sceneView != null && bitmap != null) {
                        val hitResults = sceneView.frame?.hitTest(e.x, e.y)
                        val planeHit = hitResults?.firstOrNull { 
                            val trackable = it.trackable
                            trackable is Plane && trackable.trackingState == TrackingState.TRACKING
                        }

                        if (planeHit != null) {
                            val anchorNode = AnchorNode(engine, planeHit.createAnchor())
                            val texture = com.google.android.filament.Texture.Builder()
                                .width(bitmap.width).height(bitmap.height)
                                .sampler(com.google.android.filament.Texture.Sampler.SAMPLER_2D)
                                .format(com.google.android.filament.Texture.InternalFormat.SRGB8_A8).build(engine)
                            
                            TextureHelper.setBitmap(engine, texture, 0, bitmap)
                            val material = sceneView.materialLoader.createImageInstance(texture)
                            val transparent = sceneView.materialLoader.createColorInstance(Color.TRANSPARENT)
                            
                            val cNode = CubeNode(
                                engine = engine,
                                size = Size(visualScale, visualScale, 0.01f),
                                center = Position(0f, visualScale / 2.2f, 0f),
                                materialInstances = List(6) { if(it == 2) material else transparent }
                            )
                            
                            anchorNode.addChildNode(cNode)
                            childNodes.add(anchorNode)
                            pokemonNode = cNode
                            isPlaced = true
                            Toast.makeText(context, "¡Pokémon aparecido!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                true
            }
        )

        TopAppBar(
            title = { Text("Ver en RA", color = ComposeColor.White) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atrás", tint = ComposeColor.White) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = ComposeColor.Black.copy(alpha = 0.3f))
        )

        if (!isPlaced) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    "Busca puntos blancos en el suelo y toca uno",
                    modifier = Modifier.padding(bottom = 120.dp).background(ComposeColor.Black.copy(alpha = 0.5f)).padding(16.dp),
                    color = ComposeColor.White
                )
            }
        }
    }
}
