package com.example.simplemonstergo

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun HolographicGlareEffect(tiltX: Float = 0f, tiltY: Float = 0f) {
    // Brillo 100% responsivo al giroscopio (sin bucle)
    // El offset se calcula en base a la inclinación del teléfono
    val combinedOffset = (tiltX * 1.5f) + 0.5f

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    0.0f to Color.Transparent,
                    0.4f to Color.White.copy(alpha = 0.4f),
                    0.5f to Color.White.copy(alpha = 0.6f),
                    0.6f to Color.White.copy(alpha = 0.4f),
                    1.0f to Color.Transparent,
                    start = Offset(combinedOffset * 1000f - 300f, -300f),
                    end = Offset(combinedOffset * 1000f + 300f, 1300f)
                )
            )
    )
}

@Composable
fun AdvancedHoloEffect(tiltX: Float, tiltY: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "advanced_holo")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "pulse"
    )

    // Posición reactiva del brillo radial
    val centerX = 500f + (tiltX * 700f)
    val centerY = 500f + (tiltY * 700f)

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xAA00E5FF).copy(alpha = pulseAlpha), // Cyan pastel
                        Color(0xAAFF00FF).copy(alpha = pulseAlpha * 0.6f), // Magenta suave
                        Color(0xAAFFEA00).copy(alpha = pulseAlpha * 0.4f), // Amarillo pálido
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = 900f
                )
            )
    ) {
        // Reflejo metálico arcoíris DESATURADO (menos opaco y colores más suaves)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0.0f to Color.Transparent,
                        0.3f to Color(0x33FF6666), // Rojo muy suave
                        0.5f to Color(0x66FFFFFF), // Blanco medio
                        0.7f to Color(0x336666FF), // Azul muy suave
                        1.0f to Color.Transparent,
                        start = Offset((tiltX * 1200f), 0f),
                        end = Offset((tiltX * 1200f) + 500f, 1000f)
                    )
                )
        )
    }
}

@Composable
fun MagicParticlesEffect() {
    val particleCount = 15
    val infiniteTransition = rememberInfiniteTransition(label = "magic")
    
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "progress"
    )

    val particles = remember { 
        List(particleCount) { 
            object {
                val xRel = Random.nextFloat()
                val yRel = Random.nextFloat()
                val speed = Random.nextFloat() * 0.4f + 0.3f
                val size = Random.nextFloat() * 4f + 2f
            }
        } 
    }

    Canvas(Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val currentY = ((p.yRel - (animProgress * p.speed)) % 1.0f + 1.0f) % 1.0f
            val alpha = sin(currentY * Math.PI).toFloat().coerceIn(0f, 1f)
            
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.7f),
                radius = p.size,
                center = Offset(p.xRel * size.width, currentY * size.height)
            )
        }
    }
}
