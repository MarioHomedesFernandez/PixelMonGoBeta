package com.example.simplemonstergo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.sin

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Auto-login activado: si hay usuario, sincronizar y entrar
        if (auth.currentUser != null) {
            PokemonStorage.syncFromCloud(this) {
                startMainActivity()
            }
            return
        }

        setContent {
            MaterialTheme {
                LoginScreen(
                    onLogin = { email, pass, onLoading ->
                        onLoading(true)
                        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                PokemonStorage.syncFromCloud(this) { startMainActivity() }
                            } else {
                                onLoading(false)
                                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRegister = { email, pass, onLoading ->
                        onLoading(true)
                        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                // Sincronizar datos iniciales por defecto a la nube para evitar errores en el primer login
                                PokemonStorage.syncToCloud(this@LoginActivity)
                                startMainActivity()
                            } else {
                                onLoading(false)
                                Toast.makeText(this@LoginActivity, "Error al registrar: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    private fun startMainActivity() {
        if (!PokemonStorage.isTutorialDone(this)) {
            startActivity(Intent(this, TutorialActivity::class.java))
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}

@Composable
fun LoginScreen(onLogin: (String, String, (Boolean) -> Unit) -> Unit, onRegister: (String, String, (Boolean) -> Unit) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        AnimatedWavesBackground()

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "PixelMon Go",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1A237E),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = if (isRegisterMode) "Crear cuenta" else "Iniciar Sesión",
                fontSize = 18.sp,
                color = Color(0xFF0277BD),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF0277BD))
            } else {
                Button(
                    onClick = { 
                        if (email.isNotEmpty() && password.isNotEmpty()) {
                            SoundManager.playClick(context)
                            if (isRegisterMode) {
                                onRegister(email, password) { isLoading = it }
                            } else {
                                onLogin(email, password) { isLoading = it } 
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD))
                ) {
                    Text(if (isRegisterMode) "REGISTRARSE" else "ENTRAR", fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = { 
                        SoundManager.playClick(context)
                        isRegisterMode = !isRegisterMode 
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(if (isRegisterMode) "¿Ya tienes cuenta? Inicia sesión" else "¿Eres nuevo? Regístrate aquí")
                }
            }
        }
    }
}

@Composable
fun AnimatedWavesBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "waves")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        drawWave(width, height, phase, 0.4f, Color(0xFFB3E5FC), 150f, 0.8f)
        drawWave(width, height, phase * 0.8f, 0.3f, Color(0xFF81D4FA), 120f, 0.85f)
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWave(
    width: Float, height: Float, phase: Float, alpha: Float, color: Color, amplitude: Float, baseHeightPercent: Float
) {
    val path = Path()
    val basePath = height * baseHeightPercent
    path.moveTo(0f, height)
    path.lineTo(0f, basePath)
    for (x in 0..width.toInt() step 5) {
        val y = basePath + amplitude * sin(x * 0.005f + phase)
        path.lineTo(x.toFloat(), y)
    }
    path.lineTo(width, height)
    path.close()
    drawPath(path, color = color, alpha = alpha)
}
