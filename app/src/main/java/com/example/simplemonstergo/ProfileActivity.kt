package com.example.simplemonstergo

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import coil.compose.AsyncImage
import com.google.firebase.database.FirebaseDatabase

class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFFC0392B),
                secondary = Color(0xFF2980B9)
            )) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F5F5)) {
                    TrainerProfileEditor(onBack = { finish() })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.musicaoutsetisland)
    }
}

val trainerNames = listOf(
    "red", "blue", "ethan", "lyra", "brendan", "may", 
    "lucas", "dawn", "hilbert", "hilda", "calem", "serena"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerProfileEditor(onBack: () -> Unit) {
    val context = LocalContext.current
    val userProfile = remember { PokemonStorage.getUserProfile(context) }
    
    var name by remember { mutableStateOf(userProfile.name) }
    var selectedTrainerIndex by remember { mutableIntStateOf(userProfile.faceType % trainerNames.size) }
    var selectedBuddyId by remember { mutableIntStateOf(if(userProfile.hairType > 0) userProfile.hairType else 25) } 
    var selectedTeamColor by remember { mutableIntStateOf(userProfile.shirtColorIndex % 3) }
    var selectedFavoriteCardId by remember { mutableStateOf(PokemonStorage.getFavoriteCard(context)) }
    
    var showBuddySelector by remember { mutableStateOf(false) }
    var showCardSelector by remember { mutableStateOf(false) }
    var showFriendsDialog by remember { mutableStateOf(false) }
    var showRewardsDialog by remember { mutableStateOf(false) }

    val teamColors = listOf(
        Color(0xFFE74C3C), // Valor
        Color(0xFF3498DB), // Mystic
        Color(0xFFF1C40F)  // Instinct
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ficha de Entrenador", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundManager.playClick(context)
                        onBack()
                    }) { Icon(Icons.Default.ArrowBack, "Volver") }
                },
                actions = {
                    Button(onClick = {
                        SoundManager.playClick(context)
                        val newProfile = userProfile.copy(
                            name = name,
                            faceType = selectedTrainerIndex,
                            hairType = selectedBuddyId,
                            shirtColorIndex = selectedTeamColor
                        )
                        PokemonStorage.saveUserProfile(context, newProfile)
                        PokemonStorage.saveFavoriteCard(context, selectedFavoriteCardId)
                        Toast.makeText(context, "¡Perfil guardado!", Toast.LENGTH_SHORT).show()
                        onBack()
                    }) {
                        Text("LISTO")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            
            ProfilePreviewHeader(
                name = name,
                trainerIndex = selectedTrainerIndex,
                buddyId = selectedBuddyId,
                teamColor = teamColors[selectedTeamColor],
                favoriteCardId = selectedFavoriteCardId,
                level = PokemonStorage.getPlayerLevel(context),
                exp = PokemonStorage.getPlayerExp(context)
            )

            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if(it.length <= 12) name = it },
                    label = { Text("Nombre del Entrenador") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(20.dp))

                Text("Elige tu Avatar", fontWeight = FontWeight.Bold, color = Color.Gray)
                LazyRow(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(trainerNames.size) { index ->
                        val url = "https://play.pokemonshowdown.com/sprites/trainers/${trainerNames[index]}.png"
                        Box(
                            modifier = Modifier.size(75.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (index == selectedTrainerIndex) teamColors[selectedTeamColor].copy(alpha = 0.2f) else Color.White)
                                .border(2.dp, if (index == selectedTrainerIndex) teamColors[selectedTeamColor] else Color.LightGray, RoundedCornerShape(12.dp))
                                .clickable { 
                                    SoundManager.playClick(context)
                                    selectedTrainerIndex = index 
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(65.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text("Tu Equipo", fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    teamColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier.size(50.dp).clip(CircleShape).background(color)
                                .border(3.dp, if (selectedTeamColor == index) Color.Black else Color.Transparent, CircleShape)
                                .clickable { 
                                    SoundManager.playClick(context)
                                    selectedTeamColor = index 
                                }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { SoundManager.playClick(context); showBuddySelector = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD6EAF8), contentColor = Color(0xFF2980B9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("COMPAÑERO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { SoundManager.playClick(context); showCardSelector = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFCF3CF), contentColor = Color(0xFFB7950B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("CARTA TCG", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { SoundManager.playClick(context); showFriendsDialog = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD5F5E3), contentColor = Color(0xFF1D8348)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text("AMIGOS", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { SoundManager.playClick(context); showRewardsDialog = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5EEF8), contentColor = Color(0xFF884EA0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text("RECOMPENSAS DE NIVEL", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))

                val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: "???"
                Text("Tu ID de Amigo: $myUid", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("UID", myUid))
                    Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                })

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        SoundManager.playClick(context)
                        shareInvite(context, myUid)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2980B9)),
                    border = BorderStroke(1.dp, Color(0xFF2980B9)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("INVITAR AMIGOS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(50.dp))
            }
        }
    }

    if (showBuddySelector) BuddySelectorDialog(onSelect = { selectedBuddyId = it; showBuddySelector = false }, onDismiss = { showBuddySelector = false })
    if (showCardSelector) CardFavoriteSelectorDialog(onSelect = { selectedFavoriteCardId = it; showCardSelector = false }, onDismiss = { showCardSelector = false })
    if (showFriendsDialog) FriendsDialog(onDismiss = { showFriendsDialog = false })
    if (showRewardsDialog) RewardsDialog(onDismiss = { showRewardsDialog = false })
}

@Composable
fun RewardsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentLevel = PokemonStorage.getPlayerLevel(context)
    val claimedRewards = remember { mutableStateOf(PokemonStorage.getClaimedLevelRewards(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recompensas de Nivel", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                (2..100).forEach { level ->
                    val rewardInfo = PokemonStorage.getLevelRewardInfo(level)
                    if (rewardInfo != null) {
                        val isClaimed = claimedRewards.value.contains(level)
                        val canClaim = currentLevel >= level && !isClaimed

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Nivel $level", fontWeight = FontWeight.Bold, color = if(currentLevel >= level) Color.Black else Color.Gray)
                                Text(rewardInfo, fontSize = 12.sp, color = if(currentLevel >= level) Color.DarkGray else Color.Gray)
                            }
                            
                            if (isClaimed) {
                                Icon(Icons.Default.Check, contentDescription = "Canjeado", tint = Color(0xFF27AE60))
                            } else {
                                Button(
                                    onClick = {
                                        if (PokemonStorage.claimReward(context, level)) {
                                            claimedRewards.value = PokemonStorage.getClaimedLevelRewards(context)
                                            Toast.makeText(context, "¡Recompensa canjeada!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = canClaim,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF884EA0)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(if(currentLevel >= level) "CANJEAR" else "BLOQUEADO", fontSize = 10.sp)
                                }
                            }
                        }
                        Divider(color = Color.LightGray.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CERRAR") } }
    )
}

private fun shareInvite(context: android.content.Context, uid: String) {
    val message = "Quieres ser mi amigo en Pixelmon GO? Nos esperan muchas aventuras juntos!!!\n\nMi ID: $uid"
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, message)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Invitar Amigos")
    context.startActivity(shareIntent)
}

@Composable
fun ProfilePreviewHeader(name: String, trainerIndex: Int, buddyId: Int, teamColor: Color, favoriteCardId: String?, level: Int, exp: Int) {
    val safeTrainerIdx = if (trainerIndex >= 0 && trainerIndex < trainerNames.size) trainerIndex else 0
    val trainerUrl = "https://play.pokemonshowdown.com/sprites/trainers/${trainerNames[safeTrainerIdx]}.png"
    val buddyUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$buddyId.png"
    val buddySizeEnum = remember(buddyId) { PokemonData.getPokemonSize(buddyId) }
    val isLarge = buddySizeEnum == PokemonSize.GRANDE || buddySizeEnum == PokemonSize.MUY_GRANDE
    val buddyVisualSize = when(buddySizeEnum) {
        PokemonSize.PEQUENO -> 70.dp
        PokemonSize.MEDIANO -> 110.dp
        PokemonSize.GRANDE -> 200.dp
        PokemonSize.MUY_GRANDE -> 260.dp
    }

    Box(modifier = Modifier.fillMaxWidth().height(340.dp).background(Brush.verticalGradient(listOf(teamColor, Color.White))), contentAlignment = Alignment.Center) {
        Box(Modifier.size(240.dp).background(Color.White.copy(alpha = 0.3f), CircleShape))
        
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            favoriteCardId?.let { cardId ->
                val cardUrl = "https://assets.tcgdex.net/en/${cardId.replace("-", "/")}/low.jpg"
                Card(modifier = Modifier.width(80.dp).aspectRatio(0.71f).offset(x = (-115).dp, y = (-40).dp).border(1.dp, Color.White, RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                    AsyncImage(model = cardUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }

            if (isLarge) {
                AsyncImage(model = buddyUrl, contentDescription = null, modifier = Modifier.size(buddyVisualSize).offset(y = (-30).dp, x = 60.dp))
            }
            AsyncImage(model = trainerUrl, contentDescription = null, modifier = Modifier.size(170.dp).offset(y = (-20).dp, x = if (isLarge) (-50).dp else 0.dp))
            if (!isLarge) {
                AsyncImage(model = buddyUrl, contentDescription = null, modifier = Modifier.size(buddyVisualSize).offset(y = 30.dp, x = (-45).dp).align(Alignment.Center))
            }
        }

        Surface(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 10.dp), shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = name.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = teamColor)
                Text(text = "Nivel $level", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Text(text = "XP: $exp / 1000", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun FriendsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var friendUidInput by remember { mutableStateOf("") }
    val friendUids = remember { mutableStateListOf<String>().apply { addAll(PokemonStorage.getFriends(context)) } }
    var selectedFriendData by remember { mutableStateOf<FriendProfileData?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Amigos", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = friendUidInput, onValueChange = { friendUidInput = it }, label = { Text("Añadir ID amigo") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        val trimmedId = friendUidInput.trim()
                        if (trimmedId.isNotEmpty()) {
                            PokemonStorage.addFriend(context, trimmedId)
                            friendUids.add(trimmedId)
                            friendUidInput = ""
                            Toast.makeText(context, "Amigo añadido", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Default.Add, "Añadir") }
                }
                
                Spacer(Modifier.height(16.dp))
                
                if (friendUids.isEmpty()) {
                    Text("No tienes amigos añadidos.", color = Color.Gray)
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        friendUids.forEach { uid ->
                            FriendRow(
                                uid = uid,
                                onShowProfile = { selectedFriendData = it },
                                onDelete = {
                                    PokemonStorage.removeFriend(context, uid)
                                    friendUids.remove(uid)
                                    Toast.makeText(context, "Amigo eliminado", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CERRAR") } }
    )

    if (selectedFriendData != null) {
        FriendProfileDialog(data = selectedFriendData!!, onDismiss = { selectedFriendData = null })
    }
}

data class FriendProfileData(val name: String, val level: Int, val exp: Int, val trainerIdx: Int, val buddyId: Int, val teamIdx: Int, val cardId: String?)

@Composable
fun FriendRow(uid: String, onShowProfile: (FriendProfileData) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("Cargando...") }
    var data by remember { mutableStateOf<FriendProfileData?>(null) }

    DisposableEffect(uid) {
        val dbUrl = "https://pixelmongo-default-rtdb.europe-west1.firebasedatabase.app/"
        val db = FirebaseDatabase.getInstance(dbUrl).getReference("users").child(uid)
        
        val listener = db.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (!snapshot.exists()) {
                    name = "No encontrado"
                    return
                }
                
                try {
                    val userProfileData = snapshot.child("UserProfile").value
                    var profileName = "Entrenador"
                    var face = 0
                    var hair = 25
                    var shirt = 0

                    if (userProfileData is Map<*, *>) {
                        profileName = userProfileData["name"]?.toString() ?: "Entrenador"
                        face = (userProfileData["faceType"] as? Number)?.toInt() ?: 0
                        hair = (userProfileData["hairType"] as? Number)?.toInt() ?: 25
                        shirt = (userProfileData["shirtColorIndex"] as? Number)?.toInt() ?: 0
                    } else if (userProfileData is String) {
                        try {
                            val p = com.google.gson.Gson().fromJson(userProfileData, UserProfile::class.java)
                            profileName = p.name
                            face = p.faceType
                            hair = p.hairType
                            shirt = p.shirtColorIndex
                        } catch (e: Exception) {}
                    }
                    
                    val level = (snapshot.child("PlayerLevel").value as? Number)?.toInt() ?: 1
                    val exp = (snapshot.child("PlayerExp").value as? Number)?.toInt() ?: 0
                    val card = snapshot.child("FavoriteCard").getValue(String::class.java)
                    
                    name = profileName
                    data = FriendProfileData(
                        name = profileName,
                        level = level,
                        exp = exp,
                        trainerIdx = face % trainerNames.size,
                        buddyId = hair,
                        teamIdx = shirt % 3,
                        cardId = card
                    )
                } catch (e: Exception) {
                    name = "Error datos"
                    e.printStackTrace()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                name = "Error acceso"
            }
        })

        onDispose { db.removeEventListener(listener) }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f).clickable { data?.let { onShowProfile(it) } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, fontWeight = FontWeight.Bold)
                Text("ID: ${uid.take(8)}...", fontSize = 10.sp, color = Color.Gray)
            }
        }
        
        IconButton(onClick = {
            SoundManager.playClick(context)
            onDelete()
        }) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFC0392B))
        }
    }
}

@Composable
fun FriendProfileDialog(data: FriendProfileData, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val teamColors = listOf(Color(0xFFE74C3C), Color(0xFF3498DB), Color(0xFFF1C40F))
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().wrapContentHeight(), shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column {
                ProfilePreviewHeader(
                    name = data.name,
                    trainerIndex = data.trainerIdx,
                    buddyId = data.buddyId,
                    teamColor = teamColors[data.teamIdx],
                    favoriteCardId = data.cardId,
                    level = data.level,
                    exp = data.exp
                )
                
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            SoundManager.playClick(context)
                            if (PokemonStorage.getItemCount(context, "trade_link") > 0) {
                                if (PokemonStorage.canTrade(context)) {
                                    context.startActivity(Intent(context, TradeActivity::class.java))
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Solo puedes realizar un intercambio por semana", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Necesitas un Intercambiador de la tienda", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2980B9))
                    ) {
                        Text("INTERCAMBIAR")
                    }
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black)
                    ) {
                        Text("CERRAR")
                    }
                }
            }
        }
    }
}

@Composable
fun BuddySelectorDialog(onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val captured = remember { PokemonStorage.getCapturedPokemon(context) }
    val team1 = captured.filter { it.location == PokemonLocation.TEAM_1 }.sortedBy { it.teamSlot }
    val team2 = captured.filter { it.location == PokemonLocation.TEAM_2 }.sortedBy { it.teamSlot }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir Compañero", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (team1.isEmpty() && team2.isEmpty()) {
                    Text("No tienes Pokémon en tus equipos.", color = Color.Gray)
                } else {
                    if (team1.isNotEmpty()) {
                        Text("EQUIPO 1", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        team1.forEach { p -> BuddyRow(p) { onSelect(p.speciesId) } }
                    }
                    if (team2.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("EQUIPO 2", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        team2.forEach { p -> BuddyRow(p) { onSelect(p.speciesId) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

@Composable
fun CardFavoriteSelectorDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ownedCards = remember { CardStorage.getOwnedCardsMap(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Presumir Carta TCG", fontWeight = FontWeight.Bold) },
        text = {
            if (ownedCards.isEmpty()) {
                Text("Aún no tienes cartas en tu colección.")
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(400.dp), contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sortedIds = ownedCards.keys.toList().sortedBy { it.split("/").last().toIntOrNull() ?: 999 }
                    items(sortedIds) { cardId ->
                        val cardUrl = "https://assets.tcgdex.net/en/${cardId.replace("-", "/")}/low.jpg"
                        AsyncImage(model = cardUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(0.71f).clip(RoundedCornerShape(4.dp)).clickable { onSelect(cardId) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

@Composable
fun BuddyRow(p: PokemonInstance, onClick: () -> Unit) {
    val base = PokemonData.getBaseStats(p.speciesId)
    val url = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.speciesId}.png"
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(50.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(base.name, fontWeight = FontWeight.Bold)
            Text("Nivel ${p.level}", fontSize = 12.sp, color = Color.Gray)
        }
    }
}
