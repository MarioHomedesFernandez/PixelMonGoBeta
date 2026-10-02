package com.example.simplemonstergo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson

class TradeActivity : ComponentActivity() {

    private val STRATEGY = Strategy.P2P_STAR
    private val SERVICE_ID = "com.example.simplemonstergo.TRADE_V2"

    private lateinit var connectionsClient: ConnectionsClient
    private var opponentEndpointId = mutableStateOf<String?>(null)
    private val gson = Gson()

    // Estado del intercambio
    private var myTradeSelection = mutableStateOf<TradeOffer?>(null)
    private var opponentTradeSelection = mutableStateOf<TradeOffer?>(null)
    private var tradeStep = mutableStateOf("CHOICE") // CHOICE, SELECTION, NEARBY, CONFIRMATION
    private var tradeType = mutableStateOf("NONE") // POKEMON, CARD
    private var statusText = mutableStateOf("Selecciona qué quieres intercambiar")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectionsClient = Nearby.getConnectionsClient(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFD6EAF8)) {
                    TradeScreen(
                        step = tradeStep.value,
                        type = tradeType.value,
                        status = statusText.value,
                        mySelection = myTradeSelection.value,
                        opponentSelection = opponentTradeSelection.value,
                        opponentId = opponentEndpointId.value,
                        onChoice = { type ->
                            tradeType.value = type
                            tradeStep.value = "SELECTION"
                        },
                        onSelectOffer = { offer ->
                            myTradeSelection.value = offer
                            tradeStep.value = "NEARBY"
                        },
                        onStartAdvertising = { startAdvertising() },
                        onStartDiscovery = { startDiscovery() },
                        onAccept = { sendAccept() },
                        onBack = { finish() }
                    )
                }
            }
        }
        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1002)
    }

    private fun startAdvertising() {
        connectionsClient.startAdvertising("Entrenador", SERVICE_ID, connectionLifecycleCallback, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnSuccessListener { statusText.value = "Esperando socio de intercambio..." }
    }

    private fun startDiscovery() {
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnSuccessListener { statusText.value = "Buscando socios..." }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                opponentEndpointId.value = endpointId
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
                sendTradeOffer()
            }
        }
        override fun onDisconnected(endpointId: String) { finish() }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection("Entrenador", endpointId, connectionLifecycleCallback)
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { 
                val data = gson.fromJson(String(it), TradeMessage::class.java)
                handleTradeMessage(data)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun sendTradeOffer() {
        myTradeSelection.value?.let {
            val msg = TradeMessage(type = "OFFER", offer = it)
            connectionsClient.sendPayload(opponentEndpointId.value!!, Payload.fromBytes(gson.toJson(msg).toByteArray()))
        }
    }

    private fun sendAccept() {
        val msg = TradeMessage(type = "ACCEPT")
        connectionsClient.sendPayload(opponentEndpointId.value!!, Payload.fromBytes(gson.toJson(msg).toByteArray()))
        executeTrade()
    }

    private fun handleTradeMessage(msg: TradeMessage) {
        when (msg.type) {
            "OFFER" -> {
                opponentTradeSelection.value = msg.offer
                tradeStep.value = "CONFIRMATION"
            }
            "ACCEPT" -> {
                executeTrade()
            }
        }
    }

    private fun executeTrade() {
        val myOffer = myTradeSelection.value ?: return
        val opOffer = opponentTradeSelection.value ?: return

        runOnUiThread {
            // 1. Quitar lo mío
            if (myOffer.type == "POKEMON") {
                PokemonStorage.removePokemonByUuid(this, myOffer.pokemon!!.uuid)
            } else {
                CardStorage.removeCard(this, myOffer.cardId!!)
            }

            // 2. Añadir lo recibido
            if (opOffer.type == "POKEMON") {
                var received = opOffer.pokemon!!
                val oldId = received.speciesId
                val newId = checkTradeEvolution(oldId)
                if (newId != oldId) {
                    received = received.copy(speciesId = newId, moves = null)
                    Toast.makeText(this, "¡Ha evolucionado!", Toast.LENGTH_SHORT).show()
                }
                PokemonStorage.addPokemonInstance(this, received)
            } else {
                CardStorage.addCards(this, listOf(opOffer.cardId!!))
            }

            // 3. Consumir item y marcar tiempo
            PokemonStorage.useItem(this, "trade_link")
            PokemonStorage.markTradeDone(this)

            statusText.value = "¡Intercambio completado!"

            android.app.AlertDialog.Builder(this)
                .setTitle("¡Éxito!")
                .setMessage("Intercambio realizado correctamente.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun checkTradeEvolution(speciesId: Int): Int {
        return when (speciesId) {
            64 -> 65 // Kadabra -> Alakazam
            67 -> 68 // Machoke -> Machamp
            75 -> 76 // Graveler -> Golem
            93 -> 94 // Haunter -> Gengar
            else -> speciesId
        }
    }

    data class TradeOffer(
        val type: String, // POKEMON or CARD
        val pokemon: PokemonInstance? = null,
        val cardId: String? = null
    )

    data class TradeMessage(val type: String, val offer: TradeOffer? = null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(
    step: String,
    type: String,
    status: String,
    mySelection: TradeActivity.TradeOffer?,
    opponentSelection: TradeActivity.TradeOffer?,
    opponentId: String?,
    onChoice: (String) -> Unit,
    onSelectOffer: (TradeActivity.TradeOffer) -> Unit,
    onStartAdvertising: () -> Unit,
    onStartDiscovery: () -> Unit,
    onAccept: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intercambio", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 24.dp))

            when (step) {
                "CHOICE" -> {
                    Button(onClick = { onChoice("POKEMON") }, modifier = Modifier.fillMaxWidth().height(80.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2980B9))) {
                        Text("INTERCAMBIAR POKÉMON", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { onChoice("CARD") }, modifier = Modifier.fillMaxWidth().height(80.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE67E22))) {
                        Text("INTERCAMBIAR CARTA TCG", fontWeight = FontWeight.Bold)
                    }
                }
                "SELECTION" -> {
                    if (type == "POKEMON") PokemonGridSelection(onSelect = { onSelectOffer(TradeActivity.TradeOffer("POKEMON", pokemon = it)) })
                    else CardGridSelection(onSelect = { onSelectOffer(TradeActivity.TradeOffer("CARD", cardId = it)) })
                }
                "NEARBY" -> {
                    Button(onClick = onStartAdvertising, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("CREAR SALA") }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onStartDiscovery, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))) { Text("BUSCAR RIVAL") }
                }
                "CONFIRMATION" -> {
                    opponentSelection?.let { offer ->
                        TradeConfirmationView(offer, onAccept)
                    }
                }
            }
        }
    }
}

@Composable
fun PokemonGridSelection(onSelect: (PokemonInstance) -> Unit) {
    val context = LocalContext.current
    val pcPokemon = remember { PokemonStorage.getCapturedPokemon(context).filter { it.location == PokemonLocation.PC } }

    if (pcPokemon.isEmpty()) {
        Text("No tienes Pokémon disponibles en el PC para intercambiar.", color = Color.Gray)
    } else {
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pcPokemon) { p ->
                val url = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.speciesId}.png"
                Column(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).clickable { onSelect(p) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(70.dp))
                    Text(PokemonData.getBaseStats(p.speciesId).name, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Nv.${p.level}", fontSize = 9.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun CardGridSelection(onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val ownedCards = remember { CardStorage.getOwnedCardsMap(context) }

    if (ownedCards.isEmpty()) {
        Text("No tienes cartas para intercambiar.")
    } else {
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val sortedIds = ownedCards.keys.toList().sortedBy { it.split("/").last().toIntOrNull() ?: 999 }
            items(sortedIds) { cardId ->
                val url = "https://assets.tcgdex.net/en/${cardId.replace("-", "/")}/low.jpg"
                AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(0.71f).clip(RoundedCornerShape(4.dp)).clickable { onSelect(cardId) })
            }
        }
    }
}

@Composable
fun TradeConfirmationView(offer: TradeActivity.TradeOffer, onAccept: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("¿Aceptar este intercambio?", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        
        if (offer.type == "POKEMON") {
            val p = offer.pokemon!!
            val url = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.speciesId}.png"
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(120.dp))
            Text(PokemonData.getBaseStats(p.speciesId).name + " Nv.${p.level}", fontWeight = FontWeight.Bold)
        } else {
            val url = "https://assets.tcgdex.net/en/${offer.cardId!!.replace("-", "/")}/low.jpg"
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.width(150.dp).aspectRatio(0.71f).clip(RoundedCornerShape(8.dp)))
        }

        Spacer(Modifier.height(32.dp))
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))) {
            Text("ACEPTAR INTERCAMBIO", fontWeight = FontWeight.Bold)
        }
    }
}
