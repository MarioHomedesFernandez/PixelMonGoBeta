package com.example.simplemonstergo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("SoundSettings", android.content.Context.MODE_PRIVATE) }
    
    var musicEnabled by remember { mutableStateOf(prefs.getBoolean("music_on", true)) }
    var sfxEnabled by remember { mutableStateOf(prefs.getBoolean("sfx_on", true)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundManager.playClick(context)
                        onBack()
                    }) { Icon(Icons.Default.ArrowBack, "Volver") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
                .padding(24.dp)
        ) {
            Text("Sonido y Música", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Música de fondo", fontWeight = FontWeight.Bold)
                            Text("Activar/Desactivar melodías", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = musicEnabled,
                            onCheckedChange = {
                                musicEnabled = it
                                SoundManager.setMusicEnabled(context, it)
                            }
                        )
                    }
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Efectos de sonido", fontWeight = FontWeight.Bold)
                            Text("Sonidos de clics y acciones", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = sfxEnabled,
                            onCheckedChange = {
                                sfxEnabled = it
                                SoundManager.setSfxEnabled(context, it)
                                if (it) SoundManager.playClick(context)
                            }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            Text("Sincronización", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    SoundManager.playClick(context)
                    PokemonStorage.syncToCloud(context)
                    Toast.makeText(context, "¡Datos guardados en la nube!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("GUARDAR EN LA NUBE", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    SoundManager.playClick(context)
                    // Cerrar sesión
                    PokemonStorage.clearAllLocalData(context)
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    context.startActivity(Intent(context, LoginActivity::class.java))
                    (context as? android.app.Activity)?.finishAffinity()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("CERRAR SESIÓN", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))
            Text("Información", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Versión de la App", fontWeight = FontWeight.Bold)
                    Text("PixelMon Go v2.5.0", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
    }
}
