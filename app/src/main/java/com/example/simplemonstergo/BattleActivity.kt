package com.example.simplemonstergo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import kotlin.math.floor
import kotlin.random.Random

class BattleActivity : AppCompatActivity() {

    private lateinit var playerTeam: MutableList<PokemonInstance>
    private lateinit var enemyTeam: MutableList<PokemonInstance>

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

    private lateinit var layoutActions: LinearLayout
    private lateinit var layoutMoves: GridLayout
    private lateinit var layoutSwitch: HorizontalScrollView
    private lateinit var containerSwitchButtons: LinearLayout
    private lateinit var layoutBag: HorizontalScrollView
    private lateinit var containerBagButtons: LinearLayout

    private var isGymBattle = false
    private var isLeague = false
    private var leaguePhase = 1
    private var currentTerrain = GymTerrain.NONE
    private var isBattleOver = false

    // Control de estados y efectos
    private var playerStatus: String? = null
    private var enemyStatus: String? = null
    private var playerRecharge = false
    private var enemyRecharge = false
    private var playerSleepTurns = 0
    private var enemySleepTurns = 0
    private var playerPoisonMultiplier = 1
    private var enemyPoisonMultiplier = 1

    // Transformación de Ditto
    private var playerTransformedSpeciesId: Int? = null
    private var playerTransformedMoves: List<Move>? = null
    private var enemyTransformedSpeciesId: Int? = null
    private var enemyTransformedMoves: List<Move>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battle)

        isGymBattle = intent.getBooleanExtra("IS_GYM_BATTLE", false)
        isLeague = intent.getBooleanExtra("IS_LEAGUE", false)
        leaguePhase = intent.getIntExtra("LEAGUE_PHASE", 1)

        val terrainName = intent.getStringExtra("GYM_TERRAIN") ?: "NONE"
        currentTerrain = try { GymTerrain.valueOf(terrainName) } catch(e: Exception) { GymTerrain.NONE }

        playerTeam = PokemonStorage.getCapturedPokemon(this)
            .filter { it.location == PokemonLocation.TEAM_1 }
            .sortedBy { it.teamSlot }
            .toMutableList()

        if (playerTeam.isEmpty()) {
            Toast.makeText(this, "¡Necesitas un equipo para pelear!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Buscar el primer Pokémon con vida para empezar
        val firstHealthyIdx = playerTeam.indexOfFirst { it.getSafeHp() > 0 }
        if (firstHealthyIdx == -1) {
            Toast.makeText(this, "¡Todos tus Pokémon están debilitados! Cúralos antes de luchar.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentPlayerIdx = firstHealthyIdx

        // Cargar vida persistente del Pokémon seleccionado
        playerCurrentHp = playerTeam[currentPlayerIdx].getSafeHp()

        // Generar equipo enemigo
        if (isLeague) {
            val leagueTeamIds = when(leaguePhase) {
                1 -> listOf(86, 91, 80, 124, 131) // Lorelei
                2 -> listOf(95, 106, 107, 68, 112) // Bruno
                3 -> listOf(89, 24, 94, 42, 3)     // Agatha
                4 -> listOf(148, 130, 6, 149, 142) // Lance
                else -> listOf(18, 130, 65, 103, 112, 6) // Campeón
            }
            enemyTeam = leagueTeamIds.map { id ->
                val baseLevel = when(leaguePhase) { 1 -> 70; 2 -> 72; 3 -> 74; 4 -> 76; else -> 85 }
                PokemonInstance(speciesId = id, level = baseLevel + Random.nextInt(5))
            }.toMutableList()
        } else {
            val avgLevel = if(playerTeam.isNotEmpty()) playerTeam.map { it.level }.average().toInt() else 5
            val maxLevel = if(playerTeam.isNotEmpty()) playerTeam.map { it.level }.maxOrNull() ?: 5 else 5

            enemyTeam = MutableList(6) { index ->
                val speciesId = PokemonData.species.keys.random()
                val level = if (index == 5) {
                    maxLevel.coerceAtMost(85)
                } else {
                    (avgLevel + Random.nextInt(-2, 3)).coerceIn(1, 85)
                }
                PokemonInstance(speciesId = speciesId, level = level)
            }
        }

        // El enemigo siempre empieza con vida completa
        enemyCurrentHp = PokemonData.calculateHP(PokemonData.getBaseStats(enemyTeam[0].speciesId).hp, enemyTeam[0].level, enemyTeam[0].ivHp)

        resetPlayerSideEffects()
        resetEnemySideEffects()

        initUi()
        loadBattleBackground()
        updateBattleState()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isBattleOver) {
                    finish()
                    return
                }

                if (layoutMoves.visibility == View.VISIBLE) {
                    layoutMoves.visibility = View.GONE
                    layoutActions.visibility = View.VISIBLE
                    return
                }

                if (layoutSwitch.visibility == View.VISIBLE) {
                    if (playerCurrentHp <= 0) {
                        Toast.makeText(this@BattleActivity, "Debes elegir un Pokémon para continuar", Toast.LENGTH_SHORT).show()
                        return
                    }
                    layoutSwitch.visibility = View.GONE
                    layoutActions.visibility = View.VISIBLE
                    return
                }

                if (layoutBag.visibility == View.VISIBLE) {
                    layoutBag.visibility = View.GONE
                    layoutActions.visibility = View.VISIBLE
                    return
                }

                androidx.appcompat.app.AlertDialog.Builder(this@BattleActivity)
                    .setTitle("¿Abandonar combate?")
                    .setMessage("Si sales ahora, se contará como una derrota.")
                    .setPositiveButton("ABANDONAR") { _, _ ->
                        showResultDialog("Has abandonado el combate. Derrota.")
                    }
                    .setNegativeButton("SEGUIR LUCHANDO", null)
                    .show()
            }
        })
    }

    private fun resetPlayerSideEffects() {
        playerStatus = null
        playerSleepTurns = 0
        playerRecharge = false
        playerPoisonMultiplier = 1
        playerTransformedSpeciesId = null
        playerTransformedMoves = null
    }

    private fun resetEnemySideEffects() {
        enemyStatus = null
        enemySleepTurns = 0
        enemyRecharge = false
        enemyPoisonMultiplier = 1
        enemyTransformedSpeciesId = null
        enemyTransformedMoves = null
    }

    private fun loadBattleBackground() {
        Glide.with(this).load(R.drawable.fondobatalla).into(findViewById<ImageView>(R.id.ivBattleBackground))
    }

    private fun initUi() {
        tvBattleLog = findViewById(R.id.tvBattleLog)
        pbPlayerHp = findViewById(R.id.pbPlayerHp)
        pbEnemyHp = findViewById(R.id.pbEnemyHp)
        tvPlayerHpText = findViewById(R.id.tvPlayerHpText)
        tvEnemyHpText = findViewById(R.id.tvEnemyHpText)
        ivPlayer = findViewById(R.id.ivPlayer)
        ivEnemy = findViewById(R.id.ivEnemy)
        layoutActions = findViewById(R.id.layoutActions)
        layoutMoves = findViewById(R.id.layoutMoves)
        layoutSwitch = findViewById(R.id.layoutSwitch)
        containerSwitchButtons = findViewById(R.id.containerSwitchButtons)
        layoutBag = findViewById(R.id.layoutBag)
        containerBagButtons = findViewById(R.id.containerBagButtons)

        findViewById<Button>(R.id.btnActionAttack).setOnClickListener {
            if (playerCurrentHp <= 0) {
                Toast.makeText(this, "¡Este Pokémon no puede luchar!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            layoutActions.visibility = View.GONE
            layoutMoves.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.btnActionBag).setOnClickListener {
            showBagMenu()
        }

        findViewById<Button>(R.id.btnActionSwitch).setOnClickListener {
            showSwitchMenu(canCancel = true)
        }

        val moveButtons = listOf<Button>(
            findViewById(R.id.btnMove1),
            findViewById(R.id.btnMove2),
            findViewById(R.id.btnMove3),
            findViewById(R.id.btnMove4)
        )

        moveButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                if (playerCurrentHp > 0 && enemyCurrentHp > 0) {
                    executeTurn(index)
                    layoutMoves.visibility = View.GONE
                    layoutActions.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showSwitchMenu(canCancel: Boolean = true) {
        containerSwitchButtons.removeAllViews()
        playerTeam.forEachIndexed { index, p ->
            val btn = Button(this).apply {
                val hpStatus = if(p.getSafeHp() <= 0) " (DEBILITADO)" else ""
                text = "${PokemonData.getBaseStats(p.speciesId).name} (Lv.${p.level})$hpStatus"
                
                // Si el Pokémon está debilitado, solo se puede elegir si se está usando un Revivir (no implementado aquí directamente en switch)
                // O si queremos permitir elegirlo para ver que no se puede.
                // En juegos originales no te deja sacarlo si está a 0 HP.
                
                isEnabled = p.getSafeHp() > 0 && index != currentPlayerIdx
                
                setOnClickListener {
                    performSwitch(index)
                }
            }
            containerSwitchButtons.addView(btn)
        }
        
        if (canCancel && playerCurrentHp > 0) {
            val btnCancel = Button(this).apply {
                text = "CANCELAR"
                setOnClickListener {
                    layoutSwitch.visibility = View.GONE
                    layoutActions.visibility = View.VISIBLE
                }
            }
            containerSwitchButtons.addView(btnCancel)
        }

        layoutActions.visibility = View.GONE
        layoutMoves.visibility = View.GONE
        layoutBag.visibility = View.GONE
        layoutSwitch.visibility = View.VISIBLE
    }

    private fun showBagMenu() {
        containerBagButtons.removeAllViews()
        val inventory = PokemonStorage.getInventory(this)
        
        // Solo mostraremos Revivir y Poción por ahora si existen
        val itemsToShow = listOf("revive" to "Revivir", "potion" to "Poción")
        
        itemsToShow.forEach { (id, name) ->
            val count = inventory[id] ?: 0
            if (count > 0) {
                val btn = Button(this).apply {
                    text = "$name ($count)"
                    setOnClickListener {
                        showTargetSelectionForItem(id)
                    }
                }
                containerBagButtons.addView(btn)
            }
        }
        
        val btnCancel = Button(this).apply {
            text = "CANCELAR"
            setOnClickListener {
                layoutBag.visibility = View.GONE
                layoutActions.visibility = View.VISIBLE
            }
        }
        containerBagButtons.addView(btnCancel)

        layoutActions.visibility = View.GONE
        layoutMoves.visibility = View.GONE
        layoutSwitch.visibility = View.GONE
        layoutBag.visibility = View.VISIBLE
    }

    private fun showTargetSelectionForItem(itemId: String) {
        containerBagButtons.removeAllViews()
        playerTeam.forEachIndexed { index, p ->
            val btn = Button(this).apply {
                val currentHp = p.getSafeHp()
                val base = PokemonData.getBaseStats(p.speciesId)
                val maxHp = PokemonData.calculateHP(base.hp, p.level, p.ivHp)
                text = "${base.name} ($currentHp/$maxHp)"
                
                setOnClickListener {
                    useItemOnPokemon(itemId, index)
                }
            }
            containerBagButtons.addView(btn)
        }
        val btnBack = Button(this).apply {
            text = "ATRÁS"
            setOnClickListener { showBagMenu() }
        }
        containerBagButtons.addView(btnBack)
    }

    private fun useItemOnPokemon(itemId: String, pIdx: Int) {
        val p = playerTeam[pIdx]
        val base = PokemonData.getBaseStats(p.speciesId)
        val maxHp = PokemonData.calculateHP(base.hp, p.level, p.ivHp)
        
        var success = false
        when (itemId) {
            "revive" -> {
                if (p.getSafeHp() <= 0) {
                    p.currentHp = maxHp / 2
                    success = true
                } else {
                    Toast.makeText(this, "¡Ya está en pie!", Toast.LENGTH_SHORT).show()
                }
            }
            "potion" -> {
                if (p.getSafeHp() > 0 && p.getSafeHp() < maxHp) {
                    p.currentHp = (p.getSafeHp() + 50).coerceAtMost(maxHp)
                    success = true
                } else if (p.getSafeHp() <= 0) {
                    Toast.makeText(this, "¡Necesitas un Revivir!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Ya tiene la vida llena", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (success) {
            PokemonStorage.useItem(this, itemId)
            PokemonStorage.updatePokemonHp(this, p.uuid, p.currentHp!!)
            tvBattleLog.text = "Has usado ${if(itemId=="revive") "Revivir" else "Poción"} en ${base.name}."
            layoutBag.visibility = View.GONE
            layoutActions.visibility = View.VISIBLE
            
            // Si usamos item en el pokemon activo, actualizamos UI
            if (pIdx == currentPlayerIdx) {
                playerCurrentHp = p.currentHp!!
                updateBattleState()
            }
            
            // Gastamos turno
            setButtonsEnabled(false)
            ivPlayer.postDelayed({ enemyTurn() }, 1000)
        }
    }

    private fun performSwitch(newIdx: Int) {
        val wasDead = playerCurrentHp <= 0

        playerTeam[currentPlayerIdx].currentHp = playerCurrentHp
        PokemonStorage.updatePokemonHp(this, playerTeam[currentPlayerIdx].uuid, playerCurrentHp)

        currentPlayerIdx = newIdx
        playerCurrentHp = playerTeam[currentPlayerIdx].getSafeHp()
        resetPlayerSideEffects()

        layoutSwitch.visibility = View.GONE
        layoutActions.visibility = View.VISIBLE
        updateBattleState()

        tvBattleLog.text = "¡Adelante, ${playerBaseName(playerTeam[currentPlayerIdx])}!"
        
        if (playerCurrentHp > 0) {
            if (wasDead) {
                // Si el Pokémon anterior estaba debilitado, el cambio no consume el turno de ataque del nuevo
                setButtonsEnabled(true)
            } else {
                // Si el cambio es manual, el enemigo ataca
                setButtonsEnabled(false)
                ivPlayer.postDelayed({ enemyTurn() }, 1000)
            }
        } else {
            Toast.makeText(this, "¡Está debilitado!", Toast.LENGTH_SHORT).show()
            showSwitchMenu(canCancel = !wasDead)
        }
    }

    private fun getActiveMoves(isPlayer: Boolean): List<Move> {
        return if (isPlayer) {
            val player = playerTeam[currentPlayerIdx]
            if (player.speciesId == 132 && playerTransformedMoves == null) {
                listOf(Move("Transformación", "Normal", MoveCategory.ESTADO, 0, 100, 10))
            } else {
                playerTransformedMoves ?: player.getSafeMoves()
            }
        } else {
            val enemy = enemyTeam[currentEnemyIdx]
            if (enemy.speciesId == 132 && enemyTransformedMoves == null) {
                listOf(Move("Transformación", "Normal", MoveCategory.ESTADO, 0, 100, 10))
            } else {
                enemyTransformedMoves ?: enemy.getSafeMoves()
            }
        }
    }

    private fun updateBattleState() {
        val player = playerTeam[currentPlayerIdx]
        val enemy = enemyTeam[currentEnemyIdx]

        val playerEffectiveSpecies = playerTransformedSpeciesId ?: player.speciesId
        val enemyEffectiveSpecies = enemyTransformedSpeciesId ?: enemy.speciesId

        val pBase = PokemonData.getBaseStats(playerEffectiveSpecies)
        val pOriginalBase = PokemonData.getBaseStats(player.speciesId)
        val eBase = PokemonData.getBaseStats(enemyEffectiveSpecies)
        val eOriginalBase = PokemonData.getBaseStats(enemy.speciesId)

        PokemonStorage.markAsSeen(this, enemy.speciesId)

        val pMax = PokemonData.calculateHP(pOriginalBase.hp, player.level, player.ivHp)
        pbPlayerHp.max = pMax
        pbPlayerHp.progress = playerCurrentHp
        tvPlayerHpText.text = "$playerCurrentHp / $pMax"
        findViewById<TextView>(R.id.tvPlayerName).text = "${pBase.name} Lv.${player.level}"

        val eMax = PokemonData.calculateHP(eOriginalBase.hp, enemy.level, enemy.ivHp)
        pbEnemyHp.max = eMax
        pbEnemyHp.progress = enemyCurrentHp
        tvEnemyHpText.text = "$enemyCurrentHp / $eMax"
        findViewById<TextView>(R.id.tvEnemyName).text = "${eBase.name} Lv.${enemy.level}"

        val pName = PokemonData.getPokemonNameForSprite(playerEffectiveSpecies)
        val eName = PokemonData.getPokemonNameForSprite(enemyEffectiveSpecies)

        Glide.with(this).asGif().load("https://play.pokemonshowdown.com/sprites/gen5ani-back/$pName.gif").into(ivPlayer)
        Glide.with(this).asGif().load("https://play.pokemonshowdown.com/sprites/gen5ani/$eName.gif").into(ivEnemy)

        val pScale = PokemonData.getPokemonScaleFactor(playerEffectiveSpecies) * 1.1f
        val eScale = PokemonData.getPokemonScaleFactor(enemyEffectiveSpecies) * 1.1f
        ivPlayer.scaleX = pScale
        ivPlayer.scaleY = pScale
        ivEnemy.scaleX = eScale
        ivEnemy.scaleY = eScale

        ivPlayer.visibility = View.VISIBLE
        ivEnemy.visibility = View.VISIBLE
        ivPlayer.alpha = if(playerCurrentHp > 0) 1f else 0.5f
        ivEnemy.alpha = 1f

        val pMoves = getActiveMoves(true)
        val buttons = listOf<Button>(findViewById(R.id.btnMove1), findViewById(R.id.btnMove2), findViewById(R.id.btnMove3), findViewById(R.id.btnMove4))
        buttons.forEachIndexed { i, btn ->
            if (i < pMoves.size) {
                btn.text = pMoves[i].name
                btn.isEnabled = playerCurrentHp > 0
            } else {
                btn.text = "-"
                btn.isEnabled = false
            }
        }
    }

    private fun executeTurn(moveIdx: Int) {
        if (isBattleOver) return
        val player = playerTeam[currentPlayerIdx]
        val enemy = enemyTeam[currentEnemyIdx]
        val moves = getActiveMoves(true)
        if (moveIdx >= moves.size) return
        val move = moves[moveIdx]

        setButtonsEnabled(false)

        if (playerRecharge) {
            tvBattleLog.text = "¡${playerBaseName(player)} debe recargar!"
            playerRecharge = false
            applyEndOfTurnEffects(true) {
                ivPlayer.postDelayed({ enemyTurn() }, 1000)
            }
            return
        }

        if (playerStatus == "SUEÑO") {
            playerSleepTurns--
            tvBattleLog.text = "¡${playerBaseName(player)} está dormido!"
            if (playerSleepTurns <= 0) playerStatus = null
            applyEndOfTurnEffects(true) {
                ivPlayer.postDelayed({ enemyTurn() }, 1000)
            }
            return
        }

        if (playerStatus == "PARALISIS" && Random.nextInt(100) < 25) {
            tvBattleLog.text = "¡${playerBaseName(player)} está paralizado y no puede moverse!"
            applyEndOfTurnEffects(true) {
                ivPlayer.postDelayed({ enemyTurn() }, 1000)
            }
            return
        }

        handleMove(true, player, enemy, move)
    }

    private fun handleMove(isPlayer: Boolean, attacker: PokemonInstance, defender: PokemonInstance, move: Move) {
        val attackerIv = if (isPlayer) ivPlayer else ivEnemy
        val defenderIv = if (isPlayer) ivEnemy else ivPlayer
        val attackerSpecies = if (isPlayer) (playerTransformedSpeciesId ?: attacker.speciesId) else (enemyTransformedSpeciesId ?: attacker.speciesId)

        animateAttack(attackerIv, isPlayer)

        tvBattleLog.text = "¡${PokemonData.getBaseStats(attackerSpecies).name} usó ${move.name}!"

        attackerIv.postDelayed({
            when (move.name) {
                "Transformación" -> {
                    if (isPlayer) {
                        val enemyEffective = enemyTransformedSpeciesId ?: defender.speciesId
                        playerTransformedSpeciesId = enemyEffective
                        playerTransformedMoves = getActiveMoves(false)
                        updateBattleState()
                        tvBattleLog.text = "¡Ditto se transformó en ${PokemonData.getBaseStats(enemyEffective).name}!"
                    } else {
                        val playerEffective = playerTransformedSpeciesId ?: defender.speciesId
                        enemyTransformedSpeciesId = playerEffective
                        enemyTransformedMoves = getActiveMoves(true)
                        updateBattleState()
                        tvBattleLog.text = "¡El rival se transformó en ${PokemonData.getBaseStats(playerEffective).name}!"
                    }
                }
                "Tóxico" -> {
                    if (isPlayer) {
                        enemyStatus = "VENENO"
                        enemyPoisonMultiplier = 1
                        tvBattleLog.text = "¡El rival ha sido gravemente envenenado!"
                    } else {
                        playerStatus = "VENENO"
                        playerPoisonMultiplier = 1
                        tvBattleLog.text = "¡Tu Pokémon ha sido gravemente envenenado!"
                    }
                }
                "Somnífero" -> {
                    if (isPlayer) {
                        enemyStatus = "SUEÑO"
                        enemySleepTurns = Random.nextInt(1, 4)
                    } else {
                        playerStatus = "SUEÑO"
                        playerSleepTurns = Random.nextInt(1, 4)
                    }
                    tvBattleLog.text = "¡El rival se quedó dormido!"
                }
                "Onda Trueno" -> {
                    if (isPlayer) enemyStatus = "PARALISIS" else playerStatus = "PARALISIS"
                    tvBattleLog.text = "¡El rival está paralizado!"
                }
                "Recuperación", "Descanso" -> {
                    val max = if(isPlayer) pbPlayerHp.max else pbEnemyHp.max
                    if (isPlayer) playerCurrentHp = (playerCurrentHp + max/2).coerceAtMost(max)
                    else enemyCurrentHp = (enemyCurrentHp + max/2).coerceAtMost(max)
                    if (move.name == "Descanso") {
                        if (isPlayer) {
                            playerStatus = "SUEÑO"
                            playerSleepTurns = 2
                        } else {
                            enemyStatus = "SUEÑO"
                            enemySleepTurns = 2
                        }
                    }
                    tvBattleLog.text = "¡Ha recuperado PS!"
                }
                "Autodestrucción", "Explosión" -> {
                    val damage = calculateDamage(isPlayer, attacker, defender, move)
                    animateDamage(defenderIv)

                    if (isPlayer) {
                        enemyCurrentHp = (enemyCurrentHp - damage).coerceAtLeast(0)
                        playerCurrentHp = 0
                    } else {
                        playerCurrentHp = (playerCurrentHp - damage).coerceAtLeast(0)
                        enemyCurrentHp = 0
                    }
                    tvBattleLog.text = "¡${PokemonData.getBaseStats(attackerSpecies).name} se inmoló!"
                }
                else -> {
                    val damage = calculateDamage(isPlayer, attacker, defender, move)
                    animateDamage(defenderIv)

                    if (isPlayer) {
                        enemyCurrentHp = (enemyCurrentHp - damage).coerceAtLeast(0)
                        if (move.name == "Hiperrayo") playerRecharge = true

                        if (move.name in listOf("Megaagotar", "Absorber", "Gigadrenado")) {
                            val healAmount = (damage / 2).coerceAtLeast(1)
                            val max = pbPlayerHp.max
                            playerCurrentHp = (playerCurrentHp + healAmount).coerceAtMost(max)
                            tvBattleLog.text = "¡${PokemonData.getBaseStats(attackerSpecies).name} absorbió salud!"
                        }
                    } else {
                        playerCurrentHp = (playerCurrentHp - damage).coerceAtLeast(0)
                        if (move.name == "Hiperrayo") enemyRecharge = true

                        if (move.name in listOf("Megaagotar", "Absorber", "Gigadrenado")) {
                            val healAmount = (damage / 2).coerceAtLeast(1)
                            val max = pbEnemyHp.max
                            enemyCurrentHp = (enemyCurrentHp + healAmount).coerceAtMost(max)
                            tvBattleLog.text = "¡El rival absorbió salud!"
                        }
                    }
                }
            }

            updateHPs()

            playerTeam[currentPlayerIdx].currentHp = playerCurrentHp
            PokemonStorage.updatePokemonHp(this, playerTeam[currentPlayerIdx].uuid, playerCurrentHp)

            if (isPlayer) {
                if (enemyCurrentHp <= 0) {
                    animateFaint(ivEnemy)
                    tvBattleLog.text = "¡El rival se debilitó!"
                    if (playerCurrentHp <= 0) animateFaint(ivPlayer)
                    ivEnemy.postDelayed({ nextEnemy() }, 1200)
                } else {
                    applyEndOfTurnEffects(true) {
                        if (playerCurrentHp <= 0) {
                            animateFaint(ivPlayer)
                            tvBattleLog.text = "¡Tu Pokémon se debilitó!"
                            ivPlayer.postDelayed({ nextPlayer() }, 1200)
                        } else {
                            ivEnemy.postDelayed({ enemyTurn() }, 1000)
                        }
                    }
                }
            } else {
                if (playerCurrentHp <= 0) {
                    animateFaint(ivPlayer)
                    tvBattleLog.text = "¡Tu Pokémon se debilitó!"
                    if (enemyCurrentHp <= 0) animateFaint(ivEnemy)
                    ivPlayer.postDelayed({ 
                        if (enemyCurrentHp <= 0) nextEnemy() else nextPlayer()
                    }, 1200)
                } else {
                    applyEndOfTurnEffects(false) {
                        if (enemyCurrentHp <= 0) {
                            animateFaint(ivEnemy)
                            tvBattleLog.text = "¡El rival se debilitó!"
                            ivEnemy.postDelayed({ nextEnemy() }, 1200)
                        } else {
                            setButtonsEnabled(true)
                        }
                    }
                }
            }
        }, 500)
    }

    private fun applyEndOfTurnEffects(isPlayerSide: Boolean, onComplete: () -> Unit) {
        if (isPlayerSide && playerStatus == "VENENO" && playerCurrentHp > 0) {
            val maxHp = pbPlayerHp.max
            val poisonDmg = ((maxHp / 16) * playerPoisonMultiplier).coerceAtLeast(1)
            playerCurrentHp = (playerCurrentHp - poisonDmg).coerceAtLeast(0)
            playerPoisonMultiplier++
            updateHPs()
            playerTeam[currentPlayerIdx].currentHp = playerCurrentHp
            PokemonStorage.updatePokemonHp(this, playerTeam[currentPlayerIdx].uuid, playerCurrentHp)
            animateDamage(ivPlayer)
            tvBattleLog.text = "¡El veneno resta salud a tu Pokémon!"
            ivPlayer.postDelayed({ onComplete() }, 700)
        } else if (!isPlayerSide && enemyStatus == "VENENO" && enemyCurrentHp > 0) {
            val maxHp = pbEnemyHp.max
            val poisonDmg = ((maxHp / 16) * enemyPoisonMultiplier).coerceAtLeast(1)
            enemyCurrentHp = (enemyCurrentHp - poisonDmg).coerceAtLeast(0)
            enemyPoisonMultiplier++
            updateHPs()
            animateDamage(ivEnemy)
            tvBattleLog.text = "¡El veneno resta salud al rival!"
            ivEnemy.postDelayed({ onComplete() }, 700)
        } else {
            onComplete()
        }
    }

    private fun enemyTurn() {
        val enemy = enemyTeam[currentEnemyIdx]
        val player = playerTeam[currentPlayerIdx]

        if (enemyRecharge) {
            tvBattleLog.text = "¡El rival debe recargar!"
            enemyRecharge = false
            applyEndOfTurnEffects(false) {
                ivEnemy.postDelayed({ setButtonsEnabled(true) }, 1000)
            }
            return
        }

        if (enemyStatus == "SUEÑO") {
            enemySleepTurns--
            tvBattleLog.text = "¡El rival está dormido!"
            if (enemySleepTurns <= 0) enemyStatus = null
            applyEndOfTurnEffects(false) {
                ivEnemy.postDelayed({ setButtonsEnabled(true) }, 1000)
            }
            return
        }

        if (enemyStatus == "PARALISIS" && Random.nextInt(100) < 25) {
            tvBattleLog.text = "¡El rival está paralizado y no puede moverse!"
            applyEndOfTurnEffects(false) {
                ivEnemy.postDelayed({ setButtonsEnabled(true) }, 1000)
            }
            return
        }

        val availableMoves = getActiveMoves(false)
        val move = availableMoves.random()
        handleMove(false, enemy, player, move)
    }

    private fun updateHPs() {
        pbPlayerHp.progress = playerCurrentHp
        tvPlayerHpText.text = "$playerCurrentHp / ${pbPlayerHp.max}"
        pbEnemyHp.progress = enemyCurrentHp
        tvEnemyHpText.text = "$enemyCurrentHp / ${pbEnemyHp.max}"
    }

    private fun playerBaseName(p: PokemonInstance): String {
        val species = playerTransformedSpeciesId ?: p.speciesId
        return PokemonData.getBaseStats(species).name
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        findViewById<Button>(R.id.btnActionAttack).isEnabled = enabled
        findViewById<Button>(R.id.btnActionSwitch).isEnabled = enabled
        val buttons = listOf<Button>(findViewById(R.id.btnMove1), findViewById(R.id.btnMove2), findViewById(R.id.btnMove3), findViewById(R.id.btnMove4))
        buttons.forEach { it.isEnabled = enabled }
    }

    private fun animateAttack(view: View, isPlayer: Boolean) {
        val translationX = if (isPlayer) 60f else -60f
        val translationY = if (isPlayer) -40f else 40f
        val anim = TranslateAnimation(0f, translationX, 0f, translationY).apply {
            duration = 150
            repeatCount = 1
            repeatMode = Animation.REVERSE
        }
        view.startAnimation(anim)
    }

    private fun animateDamage(view: View) {
        val anim = AlphaAnimation(1f, 0f).apply {
            duration = 100
            repeatCount = 3
        }
        view.startAnimation(anim)
    }

    private fun animateFaint(view: View) {
        val animSet = AnimationSet(true)
        animSet.addAnimation(AlphaAnimation(1f, 0f))
        animSet.addAnimation(TranslateAnimation(0f, 0f, 0f, 150f))
        animSet.duration = 800
        animSet.fillAfter = true
        view.startAnimation(animSet)
    }

    private fun calculateDamage(isPlayerAttacking: Boolean, attacker: PokemonInstance, defender: PokemonInstance, move: Move): Int {
        if (move.power <= 0) return 0

        val attackerSpecies = if (isPlayerAttacking) (playerTransformedSpeciesId ?: attacker.speciesId) else (enemyTransformedSpeciesId ?: attacker.speciesId)
        val defenderSpecies = if (isPlayerAttacking) (enemyTransformedSpeciesId ?: defender.speciesId) else (playerTransformedSpeciesId ?: defender.speciesId)

        val aBase = PokemonData.getBaseStats(attackerSpecies)
        val dBase = PokemonData.getBaseStats(defenderSpecies)

        val attackStat = if (move.category == MoveCategory.FISICO) PokemonData.calculateStat(aBase.attack, attacker.level, attacker.ivAtk) else PokemonData.calculateStat(aBase.spAttack, attacker.level, attacker.ivSpAtk)
        val defenseStat = if (move.category == MoveCategory.FISICO) {
            val defValue = if (dBase.defense > 0) dBase.defense else 10
            PokemonData.calculateStat(defValue, defender.level, defender.ivDef)
        } else {
            val spDefValue = if (dBase.spDefense > 0) dBase.spDefense else 10
            PokemonData.calculateStat(spDefValue, defender.level, defender.ivSpDef)
        }

        if (isGymBattle && Random.nextFloat() > currentTerrain.accuracyMod) return 0

        val effectiveness = PokemonData.getEffectiveness(move.type, dBase.types)
        
        var finalDefense = defenseStat.toDouble()
        if (move.name == "Explosión" || move.name == "Autodestrucción") {
            finalDefense /= 2.0
        }

        val levelFactor = (2 * attacker.level / 5.0) + 2
        var baseDamage = (levelFactor * move.power * (attackStat.toDouble() / finalDefense.coerceAtLeast(1.0)) / 50.0) + 2

        if (isGymBattle && move.type == currentTerrain.typeBoost) baseDamage *= currentTerrain.damageMultiplier

        return floor(baseDamage * effectiveness * (Random.nextInt(85, 101) / 100.0)).toInt()
    }

    private fun nextEnemy() {
        if (isBattleOver) return
        currentEnemyIdx++
        if (currentEnemyIdx >= enemyTeam.size) {
            showResultDialog("¡VICTORIA! Has derrotado al oponente.")
        } else {
            resetEnemySideEffects()
            val eBase = PokemonData.getBaseStats(enemyTeam[currentEnemyIdx].speciesId)
            enemyCurrentHp = PokemonData.calculateHP(eBase.hp, enemyTeam[currentEnemyIdx].level, enemyTeam[currentEnemyIdx].ivHp)

            ivEnemy.clearAnimation()
            ivEnemy.translationY = 0f
            ivEnemy.alpha = 1f
            ivEnemy.visibility = View.VISIBLE
            updateBattleState()
            
            if (playerCurrentHp <= 0) {
                nextPlayer()
            } else {
                setButtonsEnabled(true)
            }
        }
    }

    private fun nextPlayer() {
        if (isBattleOver) return

        val anyAlive = playerTeam.any { it.getSafeHp() > 0 }

        if (!anyAlive) {
            showResultDialog("DERROTA. Tu equipo ha sido debilitado.")
        } else {
            resetPlayerSideEffects()
            tvBattleLog.text = "¡${playerBaseName(playerTeam[currentPlayerIdx])} se debilitó! Elige un cambio."
            showSwitchMenu(canCancel = false)
        }
    }

    override fun onResume() {
        super.onResume()
        SoundManager.stopMusic()
        SoundManager.playMusic(this, R.raw.batallamusica)
    }

    override fun onPause() {
        super.onPause()
        SoundManager.stopMusic()
    }

    private fun showResultDialog(message: String) {
        if (isBattleOver) return
        isBattleOver = true

        playerTeam[currentPlayerIdx].currentHp = playerCurrentHp
        PokemonStorage.updatePokemonHp(this, playerTeam[currentPlayerIdx].uuid, playerCurrentHp)

        if (message.contains("VICTORIA")) {
            if (isLeague) {
                if (leaguePhase < 5) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("¡Fase Superada!")
                        .setMessage("Has ganado este combate de liga. ¿Preparado para el siguiente?")
                        .setCancelable(false)
                        .setPositiveButton("SIGUIENTE COMBATE") { _, _ ->
                            val intent = Intent(this, LeagueActivity::class.java).apply {
                                putExtra("PHASE", leaguePhase + 1)
                            }
                            startActivity(intent)
                            finish()
                        }.show()
                    return
                } else {
                    PokemonStorage.addItem(this, "champion_title", 1)
                    Toast.makeText(this, "¡ERES EL NUEVO CAMPEÓN! Has ganado el Título.", Toast.LENGTH_LONG).show()
                }
            } else {
                val reward = Random.nextInt(40, 81)
                PokemonStorage.addMoney(this, reward)
                PokemonStorage.addMedal(this)
                val gymId = intent.getStringExtra("GYM_ID")
                if (gymId != null) PokemonStorage.setGymBattled(this, gymId)
                Toast.makeText(this, "¡Has ganado $reward monedas y una medalla!", Toast.LENGTH_LONG).show()
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Fin del Combate")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("VOLVER AL MAPA") { _, _ -> finish() }
            .show()
    }
}