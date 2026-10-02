package com.example.simplemonstergo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import kotlin.math.floor
import kotlin.random.Random

class NearbyBattleActivity : AppCompatActivity() {

    private val STRATEGY = Strategy.P2P_STAR
    private val SERVICE_ID = "com.example.simplemonstergo.BATTLE_V3"

    private lateinit var connectionsClient: ConnectionsClient
    private var opponentEndpointId: String? = null
    private var opponentName: String? = null

    private lateinit var playerTeam: MutableList<PokemonInstance>
    private var enemyTeam: MutableList<PokemonInstance>? = null
    
    private var currentPlayerIdx = 0
    private var currentEnemyIdx = 0
    private var playerCurrentHp = 0
    private var enemyCurrentHp = 0

    private lateinit var tvBattleLog: TextView
    private lateinit var pbPlayerHp: ProgressBar
    private lateinit var pbEnemyHp: ProgressBar
    private lateinit var tvPlayerHpText: TextView
    private lateinit var tvEnemyHpText: TextView
    private lateinit var ivPlayer: ImageView
    private lateinit var ivEnemy: ImageView
    
    private lateinit var layoutActions: View
    private lateinit var layoutMoves: View
    private lateinit var layoutSwitch: View
    private lateinit var containerSwitchButtons: LinearLayout
    
    private lateinit var layoutConnection: LinearLayout
    private lateinit var layoutBattle: View

    private val gson = Gson()
    private var myAction: BattleAction? = null
    private var opponentAction: BattleAction? = null
    private var isBattleOver = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nearby_battle)

        connectionsClient = Nearby.getConnectionsClient(this)

        tvBattleLog = findViewById(R.id.tvBattleLogNearby)
        pbPlayerHp = findViewById(R.id.pbPlayerHpNearby)
        pbEnemyHp = findViewById(R.id.pbEnemyHpNearby)
        tvPlayerHpText = findViewById(R.id.tvPlayerHpTextNearby)
        tvEnemyHpText = findViewById(R.id.tvEnemyHpTextNearby)
        ivPlayer = findViewById(R.id.ivPlayerNearby)
        ivEnemy = findViewById(R.id.ivEnemyNearby)
        
        layoutActions = findViewById(R.id.layoutActionsNearby)
        layoutMoves = findViewById(R.id.layoutMovesNearby)
        layoutSwitch = findViewById(R.id.layoutSwitchNearby)
        containerSwitchButtons = findViewById(R.id.containerSwitchButtonsNearby)
        
        layoutConnection = findViewById(R.id.layoutConnectionNearby)
        layoutBattle = findViewById(R.id.layoutBattleNearby)

        findViewById<Button>(R.id.btnHostBattle).setOnClickListener { 
            SoundManager.playClick(this)
            startAdvertising() 
        }
        findViewById<Button>(R.id.btnJoinBattle).setOnClickListener { 
            SoundManager.playClick(this)
            startDiscovery() 
        }

        findViewById<Button>(R.id.btnActionAttackNearby).setOnClickListener {
            if (playerCurrentHp <= 0) {
                Toast.makeText(this, "¡Este Pokémon no puede luchar!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            layoutActions.visibility = View.GONE
            layoutMoves.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.btnActionSwitchNearby).setOnClickListener {
            showSwitchMenu()
        }

        playerTeam = PokemonStorage.getCapturedPokemon(this)
            .filter { it.location == PokemonLocation.TEAM_1 }
            .sortedBy { it.teamSlot }
            .toMutableList()

        if (playerTeam.isEmpty()) { 
            Toast.makeText(this, "¡Necesitas un equipo!", Toast.LENGTH_SHORT).show()
            finish() 
            return 
        }
        checkPermissions()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isBattleOver || opponentEndpointId == null) {
                    finish()
                    return
                }
                
                if (layoutMoves.visibility == View.VISIBLE) {
                    layoutMoves.visibility = View.GONE
                    layoutActions.visibility = View.VISIBLE
                    return
                }
                
                if (layoutSwitch.visibility == View.VISIBLE) {
                    layoutSwitch.visibility = View.GONE
                    layoutActions.visibility = View.VISIBLE
                    return
                }

                AlertDialog.Builder(this@NearbyBattleActivity)
                    .setTitle("¿Abandonar combate?")
                    .setMessage("Si sales ahora, perderás la partida y tu oponente ganará.")
                    .setPositiveButton("ABANDONAR") { _, _ ->
                        sendData(BattleData(type = "FORFEIT"))
                        showResultDialog("Has abandonado el combate. Derrota.")
                    }
                    .setNegativeButton("SEGUIR LUCHANDO", null)
                    .show()
            }
        })
    }

    private fun showSwitchMenu() {
        containerSwitchButtons.removeAllViews()
        playerTeam.forEachIndexed { index, p ->
            if (index != currentPlayerIdx) {
                val btn = Button(this).apply {
                    val status = if(p.getSafeHp() <= 0) " (DEBILITADO)" else ""
                    text = "${PokemonData.getBaseStats(p.speciesId).name} (Lv.${p.level})$status"
                    setOnClickListener {
                        myAction = BattleAction("SWITCH", switchIdx = index)
                        setControlsEnabled(false)
                        sendData(BattleData(type = "ACTION", action = myAction))
                        checkTurnResolution()
                    }
                }
                containerSwitchButtons.addView(btn)
            }
        }
        val btnBack = Button(this).apply {
            text = "VOLVER"
            setOnClickListener {
                layoutSwitch.visibility = View.GONE
                layoutActions.visibility = View.VISIBLE
            }
        }
        containerSwitchButtons.addView(btnBack)
        layoutActions.visibility = View.GONE
        layoutSwitch.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        SoundManager.playMusic(this, R.raw.musicaoutsetisland)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN); permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE); permissions.add(Manifest.permission.BLUETOOTH_CONNECT); permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
    }

    private fun startAdvertising() {
        connectionsClient.startAdvertising("Entrenador", SERVICE_ID, connectionLifecycleCallback, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnSuccessListener { tvBattleLog.text = "Esperando oponente..." }
    }

    private fun startDiscovery() {
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnSuccessListener { tvBattleLog.text = "Buscando oponentes..." }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            AlertDialog.Builder(this@NearbyBattleActivity).setTitle("Conexión").setMessage("¿Aceptar combate de ${info.endpointName}?").setPositiveButton("Sí") { _, _ -> connectionsClient.acceptConnection(endpointId, payloadCallback); opponentName = info.endpointName }.setNegativeButton("No") { _, _ -> connectionsClient.rejectConnection(endpointId) }.show()
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) { 
                opponentEndpointId = endpointId
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
                sendTeamInfo() 
            }
        }
        override fun onDisconnected(endpointId: String) {
            if (!isBattleOver) {
                Toast.makeText(this@NearbyBattleActivity, "El oponente se ha desconectado", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) { connectionsClient.requestConnection("Entrenador", endpointId, connectionLifecycleCallback) }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) { payload.asBytes()?.let { handleReceivedData(String(it)) } }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun sendData(data: BattleData) { opponentEndpointId?.let { connectionsClient.sendPayload(it, Payload.fromBytes(gson.toJson(data).toByteArray())) } }
    private fun sendTeamInfo() { sendData(BattleData(type = "TEAM", team = playerTeam)) }

    private fun handleReceivedData(json: String) {
        try {
            val data = gson.fromJson(json, BattleData::class.java)
            when (data.type) {
                "TEAM" -> { 
                    enemyTeam = data.team?.toMutableList()
                    layoutConnection.visibility = View.GONE
                    layoutBattle.visibility = View.VISIBLE
                    initBattle() 
                }
                "ACTION" -> { 
                    opponentAction = data.action
                    checkTurnResolution() 
                }
                "FORFEIT" -> {
                    showResultDialog("¡Tu oponente ha abandonado! Victoria para ti.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initBattle() {
        val player = playerTeam[0]; val enemy = enemyTeam!![0]
        playerCurrentHp = player.getSafeHp()
        enemyCurrentHp = enemy.getSafeHp()
        
        SoundManager.stopMusic()
        SoundManager.playMusic(this, R.raw.batallamusica)

        updateUi()
    }

    private fun updateUi() {
        if (isBattleOver) return
        val player = playerTeam[currentPlayerIdx]; val enemy = enemyTeam!![currentEnemyIdx]
        val pBase = PokemonData.getBaseStats(player.speciesId); val eBase = PokemonData.getBaseStats(enemy.speciesId)

        PokemonStorage.markAsSeen(this, enemy.speciesId)

        findViewById<TextView>(R.id.tvPlayerNameNearby).text = "${pBase.name} Lv.${player.level}"
        findViewById<TextView>(R.id.tvEnemyNameNearby).text = "${eBase.name} Lv.${enemy.level}"

        val pMax = PokemonData.calculateHP(pBase.hp, player.level, player.ivHp)
        pbPlayerHp.max = pMax
        pbPlayerHp.progress = playerCurrentHp
        tvPlayerHpText.text = "$playerCurrentHp / $pMax"

        val eMax = PokemonData.calculateHP(eBase.hp, enemy.level, enemy.ivHp)
        pbEnemyHp.max = eMax
        pbEnemyHp.progress = enemyCurrentHp
        tvEnemyHpText.text = "$enemyCurrentHp / $eMax"

        Glide.with(this).asGif().load("https://play.pokemonshowdown.com/sprites/gen5ani-back/${PokemonData.getPokemonNameForSprite(player.speciesId)}.gif").into(ivPlayer)
        Glide.with(this).asGif().load("https://play.pokemonshowdown.com/sprites/gen5ani/${PokemonData.getPokemonNameForSprite(enemy.speciesId)}.gif").into(ivEnemy)

        val pScale = PokemonData.getPokemonScaleFactor(player.speciesId) * 1.1f
        val eScale = PokemonData.getPokemonScaleFactor(enemy.speciesId) * 1.1f
        ivPlayer.scaleX = pScale; ivPlayer.scaleY = pScale
        ivEnemy.scaleX = eScale; ivEnemy.scaleY = eScale
        
        ivPlayer.clearAnimation(); ivEnemy.clearAnimation()
        ivPlayer.alpha = if(playerCurrentHp > 0) 1f else 0.5f
        ivEnemy.alpha = if(enemyCurrentHp > 0) 1f else 0.5f
        ivPlayer.translationY = 0f; ivEnemy.translationY = 0f

        val pMoves = player.getSafeMoves()
        for (i in 0..3) {
            val btnId = resources.getIdentifier("btnMoveNearby${i+1}", "id", packageName)
            val btn = findViewById<Button>(btnId)
            if (i < pMoves.size) {
                btn.text = pMoves[i].name; btn.isEnabled = myAction == null && playerCurrentHp > 0
                btn.setOnClickListener { 
                    myAction = BattleAction("ATTACK", moveIdx = i)
                    setControlsEnabled(false)
                    sendData(BattleData(type = "ACTION", action = myAction))
                    checkTurnResolution() 
                }
            } else { btn.text = "-"; btn.isEnabled = false }
        }
        
        layoutMoves.visibility = View.GONE
        layoutSwitch.visibility = View.GONE
        layoutActions.visibility = View.VISIBLE
        setControlsEnabled(true)
    }

    private fun setControlsEnabled(enabled: Boolean) {
        findViewById<Button>(R.id.btnActionAttackNearby).isEnabled = enabled
        findViewById<Button>(R.id.btnActionSwitchNearby).isEnabled = enabled
    }

    private fun checkTurnResolution() { if (myAction != null && opponentAction != null) resolveTurn() }

    private fun resolveTurn() {
        if (isBattleOver) return
        
        val myIsSwitch = myAction?.type == "SWITCH"
        val opIsSwitch = opponentAction?.type == "SWITCH"

        if (myIsSwitch && opIsSwitch) {
            doSwitch(true, myAction!!.switchIdx)
            doSwitch(false, opponentAction!!.switchIdx)
            finishTurn()
        } else if (myIsSwitch) {
            doSwitch(true, myAction!!.switchIdx)
            ivPlayer.postDelayed({
                executeAttack(false, enemyTeam!![currentEnemyIdx].getSafeMoves()[opponentAction!!.moveIdx])
                finishTurn()
            }, 1000)
        } else if (opIsSwitch) {
            doSwitch(false, opponentAction!!.switchIdx)
            ivPlayer.postDelayed({
                executeAttack(true, playerTeam[currentPlayerIdx].getSafeMoves()[myAction!!.moveIdx])
                finishTurn()
            }, 1000)
        } else {
            val player = playerTeam[currentPlayerIdx]; val enemy = enemyTeam!![currentEnemyIdx]
            val mySpeed = PokemonData.calculateStat(PokemonData.getBaseStats(player.speciesId).speed, player.level, player.ivSpeed)
            val opSpeed = PokemonData.calculateStat(PokemonData.getBaseStats(enemy.speciesId).speed, enemy.level, enemy.ivSpeed)
            
            if (mySpeed >= opSpeed) { 
                executeAttack(true, player.getSafeMoves()[myAction!!.moveIdx])
                ivPlayer.postDelayed({
                    if (!isBattleOver && enemyCurrentHp > 0) executeAttack(false, enemy.getSafeMoves()[opponentAction!!.moveIdx])
                    finishTurn()
                }, 1000)
            } else { 
                executeAttack(false, enemy.getSafeMoves()[opponentAction!!.moveIdx])
                ivPlayer.postDelayed({
                    if (!isBattleOver && playerCurrentHp > 0) executeAttack(true, player.getSafeMoves()[myAction!!.moveIdx])
                    finishTurn()
                }, 1000)
            }
        }
    }
    
    private fun doSwitch(isPlayer: Boolean, newIdx: Int) {
        if (isPlayer) {
            playerTeam[currentPlayerIdx].currentHp = playerCurrentHp
            PokemonStorage.updatePokemonHp(this, playerTeam[currentPlayerIdx].uuid, playerCurrentHp)
            currentPlayerIdx = newIdx
            playerCurrentHp = playerTeam[currentPlayerIdx].getSafeHp()
            tvBattleLog.text = "¡Adelante, ${PokemonData.getBaseStats(playerTeam[currentPlayerIdx].speciesId).name}!"
        } else {
            enemyTeam!![currentEnemyIdx].currentHp = enemyCurrentHp
            currentEnemyIdx = newIdx
            enemyCurrentHp = enemyTeam!![currentEnemyIdx].getSafeHp()
            tvBattleLog.text = "¡El rival envía a ${PokemonData.getBaseStats(enemyTeam!![currentEnemyIdx].speciesId).name}!"
        }
    }

    private fun finishTurn() {
        myAction = null
        opponentAction = null
        ivPlayer.postDelayed({ if (!isBattleOver) updateUi() }, 1500)
    }

    private fun executeAttack(isPlayer: Boolean, move: Move) {
        if (isBattleOver) return
        val attacker = if (isPlayer) playerTeam[currentPlayerIdx] else enemyTeam!![currentEnemyIdx]
        val defender = if (isPlayer) enemyTeam!![currentEnemyIdx] else playerTeam[currentPlayerIdx]
        val damage = calculateDamage(attacker, defender, move)
        
        tvBattleLog.text = "¡${PokemonData.getBaseStats(attacker.speciesId).name} usó ${move.name}!"
        
        if (isPlayer) {
            animateAttack(ivPlayer, true)
            ivPlayer.postDelayed({ 
                if (isBattleOver) return@postDelayed
                animateDamage(ivEnemy); enemyCurrentHp = (enemyCurrentHp - damage).coerceAtLeast(0)
                if (enemyCurrentHp <= 0) { 
                    animateFaint(ivEnemy)
                    ivPlayer.postDelayed({ checkFaint(false) }, 1000)
                } 
            }, 300)
        } else {
            animateAttack(ivEnemy, false)
            ivEnemy.postDelayed({ 
                if (isBattleOver) return@postDelayed
                animateDamage(ivPlayer); playerCurrentHp = (playerCurrentHp - damage).coerceAtLeast(0)
                
                playerTeam[currentPlayerIdx].currentHp = playerCurrentHp
                PokemonStorage.updatePokemonHp(this, playerTeam[currentPlayerIdx].uuid, playerCurrentHp)
                
                if (playerCurrentHp <= 0) { 
                    animateFaint(ivPlayer)
                    ivPlayer.postDelayed({ checkFaint(true) }, 1000)
                } 
            }, 300)
        }
    }
    
    private fun checkFaint(isPlayer: Boolean) {
        if (isPlayer) {
            val anyAlive = playerTeam.any { it.getSafeHp() > 0 }
            if (!anyAlive) showResultDialog("DERROTA. Tu equipo ha sido debilitado.")
            else {
                tvBattleLog.text = "¡${playerBaseName(playerTeam[currentPlayerIdx])} se debilitó! Elige un cambio."
                showSwitchMenu()
            }
        } else {
            val anyAlive = enemyTeam!!.any { it.getSafeHp() > 0 }
            if (!anyAlive) showResultDialog("¡VICTORIA! Has derrotado a tu oponente.")
            else tvBattleLog.text = "Esperando a que el rival cambie de Pokémon..."
        }
    }

    private fun animateAttack(view: View, isPlayer: Boolean) {
        val tx = if (isPlayer) 50f else -50f; val ty = if (isPlayer) -30f else 30f
        view.startAnimation(TranslateAnimation(0f, tx, 0f, ty).apply { duration = 150; repeatCount = 1; repeatMode = Animation.REVERSE })
    }

    private fun animateDamage(view: View) { view.startAnimation(AlphaAnimation(1f, 0f).apply { duration = 100; repeatCount = 3 }) }
    private fun animateFaint(view: View) {
        val s = AnimationSet(true); s.addAnimation(AlphaAnimation(1f, 0f)); s.addAnimation(TranslateAnimation(0f, 0f, 0f, 100f)); s.duration = 800; s.fillAfter = true; view.startAnimation(s)
    }

    private fun calculateDamage(attacker: PokemonInstance, defender: PokemonInstance, move: Move): Int {
        val aBase = PokemonData.getBaseStats(attacker.speciesId); val dBase = PokemonData.getBaseStats(defender.speciesId)
        val attackStat = if (move.category == MoveCategory.FISICO) PokemonData.calculateStat(aBase.attack, attacker.level, attacker.ivAtk) else PokemonData.calculateStat(aBase.spAttack, attacker.level, attacker.ivSpAtk)
        val defenseStat = if (move.category == MoveCategory.FISICO) PokemonData.calculateStat(dBase.defense, defender.level, defender.ivDef) else PokemonData.calculateStat(dBase.spDefense, defender.level, defender.ivSpDef)
        val effectiveness = PokemonData.getEffectiveness(move.type, dBase.types)
        val baseDamage = (((2 * attacker.level / 5.0) + 2) * move.power * (attackStat.toDouble() / defenseStat.toDouble()) / 50.0) + 2
        return floor(baseDamage * effectiveness * (Random.nextInt(85, 101) / 100.0)).toInt()
    }
    
    private fun playerBaseName(p: PokemonInstance) = PokemonData.getBaseStats(p.speciesId).name

    private fun showResultDialog(message: String) {
        if (isBattleOver) return
        isBattleOver = true
        
        playerTeam[currentPlayerIdx].currentHp = playerCurrentHp
        PokemonStorage.updatePokemonHp(this, playerTeam[currentPlayerIdx].uuid, playerCurrentHp)

        AlertDialog.Builder(this)
            .setTitle("Fin del Combate")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("VOLVER AL MAPA") { _, _ ->
                finish()
            }
            .show()
    }

    override fun onDestroy() { super.onDestroy(); connectionsClient.stopAllEndpoints() }
    
    data class BattleAction(val type: String, val moveIdx: Int = 0, val switchIdx: Int = 0)
    data class BattleData(val type: String, val team: List<PokemonInstance>? = null, val action: BattleAction? = null)
}
