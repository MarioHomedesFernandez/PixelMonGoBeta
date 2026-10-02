package com.example.simplemonstergo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    
    private val listaPokemonMarcadores = ArrayList<Marker>()
    private val listaCentrosMundiales = ArrayList<Marker>()
    private val listaGimnasiosMundiales = ArrayList<Marker>()
    private val listaPokeparadasMundiales = ArrayList<Marker>()
    
    private var trainerMarker: Marker? = null
    private var centerMarker: Marker? = null
    private var myGymMarker: Marker? = null
    private var visionCircle: Polygon? = null
    private var trackedPokemonSpawnId: String? = null
    private var trackingPolyline: Polyline? = null

    private data class PokemonSalvaje(
        val id: Int, 
        val posicion: GeoPoint, 
        val tiempoAparicion: Long,
        val spawnId: String
    )
    private val pokemonActivos = mutableListOf<PokemonSalvaje>()
    private val centrosActivos = mutableListOf<GeoPoint>()
    private val gimnasiosActivos = mutableListOf<GeoPoint>()
    private val pokeparadasActivas = mutableListOf<GeoPoint>()
    private var capturedSpawnIds = mutableSetOf<String>()

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnableSpawn: Runnable
    private lateinit var runnableRefresh: Runnable

    private var xInicial = 0f
    private var yInicial = 0f
    private var orientacionInicial = 0f
    private var esDeslizamiento = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) activarGPS()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val ctx = applicationContext
        Configuration.getInstance().userAgentValue = "PixelmonGO-v2"
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        Configuration.getInstance().load(ctx, prefs)

        setContentView(R.layout.activity_main)

        map = findViewById(R.id.mapview)
        map.isTilesScaledToDpi = true
        map.setMultiTouchControls(false)
        map.setBuiltInZoomControls(false)
        map.minZoomLevel = 10.0
        map.maxZoomLevel = 19.0
        map.controller.setZoom(19.0)

        val cartoVoyager = XYTileSource(
            "CartoDBVoyager", 1, 20, 256, ".png",
            arrayOf("https://basemaps.cartocdn.com/rastertiles/voyager/")
        )
        map.setTileSource(cartoVoyager)

        val matrix = ColorMatrix().apply { setSaturation(0.2f) }
        map.mapOverlay.setColorFilter(ColorMatrixColorFilter(matrix))

        map.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    xInicial = event.x; yInicial = event.y
                    orientacionInicial = map.mapOrientation; esDeslizamiento = false
                    false 
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - xInicial; val deltaY = event.y - yInicial
                    if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
                        esDeslizamiento = true
                        map.mapOrientation = orientacionInicial + (deltaX * 0.25f)
                        map.invalidate()
                    }
                    esDeslizamiento
                }
                MotionEvent.ACTION_UP -> {
                    locationOverlay.myLocation?.let { map.controller.animateTo(it) }
                    if (!esDeslizamiento) { v.performClick(); false } else true
                }
                else -> false
            }
        }

        setupButtons()
        setupRunnables()
        comprobarPermisos()
        checkUpdates()
    }

    private fun checkUpdates() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Valores por defecto
        val defaultValues = mapOf("min_version_code" to 1L, "update_url" to "https://tusitio.com/app.apk")
        remoteConfig.setDefaultsAsync(defaultValues)

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val minVersion = remoteConfig.getLong("min_version_code")
                val updateUrl = remoteConfig.getString("update_url")
                
                val currentVersion = try {
                    val pInfo = packageManager.getPackageInfo(packageName, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pInfo.versionCode.toLong()
                    }
                } catch (e: Exception) { 1L }

                if (currentVersion < minVersion) {
                    showUpdateDialog(updateUrl)
                }
            }
        }
    }

    private fun showUpdateDialog(url: String) {
        AlertDialog.Builder(this)
            .setTitle("Actualización disponible")
            .setMessage("Hay una nueva versión de Pixelmon GO. ¿Quieres actualizar ahora?")
            .setPositiveButton("Actualizar") { _, _ ->
                val intent = Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                startActivity(intent)
            }
            .setNegativeButton("Más tarde", null)
            .setCancelable(false)
            .show()
    }

    private fun setupButtons() {
        val layoutSubMenu = findViewById<View>(R.id.layoutSubMenu)
        
        // Perfil abajo a la izquierda
        findViewById<View>(R.id.btnPerfil).setOnClickListener {
            SoundManager.playClick(this)
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Botón principal de menú (Pokéball)
        findViewById<View>(R.id.btnMainMenu).setOnClickListener {
            SoundManager.playClick(this)
            layoutSubMenu.visibility = View.VISIBLE
        }

        // Botones del submenú
        findViewById<View>(R.id.btnCloseMenu).setOnClickListener {
            SoundManager.playClick(this)
            layoutSubMenu.visibility = View.GONE
        }
        findViewById<View>(R.id.btnMenuTCG).setOnClickListener {
            SoundManager.playClick(this)
            startActivity(Intent(this, TcgActivity::class.java))
        }
        findViewById<View>(R.id.btnMenuBag).setOnClickListener {
            SoundManager.playClick(this)
            startActivity(Intent(this, BagActivity::class.java))
        }
        findViewById<View>(R.id.btnMenuNearby).setOnClickListener {
            SoundManager.playClick(this)
            startActivity(Intent(this, NearbyBattleActivity::class.java))
        }
        findViewById<View>(R.id.btnMenuPokedex).setOnClickListener {
            SoundManager.playClick(this)
            startActivity(Intent(this, PokedexActivity::class.java))
        }
        findViewById<View>(R.id.btnMenuCollection).setOnClickListener {
            SoundManager.playClick(this)
            startActivity(Intent(this, CollectionActivity::class.java))
        }
        findViewById<View>(R.id.btnMenuShop).setOnClickListener {
            SoundManager.playClick(this)
            startActivity(Intent(this, ShopActivity::class.java))
        }

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            SoundManager.playClick(this)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.cardNearby).setOnClickListener {
            SoundManager.playClick(this)
            mostrarNearbyCompleto()
        }

        findViewById<Button>(R.id.btnCancelTracking).setOnClickListener {
            SoundManager.playClick(this)
            detenerRastreo()
        }
    }

    private fun setupRunnables() {
        runnableSpawn = Runnable { generarContenido(); handler.postDelayed(runnableSpawn, 60000) }
        runnableRefresh = Runnable { dibujarTodo(); updatePlayerStatsUI(); handler.postDelayed(runnableRefresh, 4000) }
    }

    private fun updatePlayerStatsUI() {
        val level = PokemonStorage.getPlayerLevel(this)
        val exp = PokemonStorage.getPlayerExp(this)
        findViewById<TextView>(R.id.tvPlayerLevel).text = level.toString()
        findViewById<ProgressBar>(R.id.pbPlayerExp).progress = exp
        actualizarNearbySummary()
    }

    private fun comprobarPermisos() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) activarGPS()
        else requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    private fun activarGPS() {
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        locationOverlay.enableMyLocation(); locationOverlay.enableFollowLocation()
        val transparent = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        locationOverlay.setPersonIcon(transparent); locationOverlay.setDirectionIcon(transparent)
        map.overlays.add(locationOverlay)
        locationOverlay.runOnFirstFix {
            runOnUiThread {
                map.controller.setCenter(locationOverlay.myLocation)
                handler.post(runnableSpawn); handler.post(runnableRefresh)
            }
        }
    }

    private fun generarContenido() {
        val miPos = locationOverlay.myLocation ?: return
        val tiempoActual = System.currentTimeMillis()
        val gridSize = 0.0006; val timeBlock = tiempoActual / 900000L
        for (i in -4..4) {
            for (j in -4..4) {
                val lat = ((miPos.latitude / gridSize).toInt() + i) * gridSize
                val lon = ((miPos.longitude / gridSize).toInt() + j) * gridSize
                val spawnId = "${lat}_${lon}_$timeBlock"
                if (pokemonActivos.any { it.spawnId == spawnId }) continue
                val random = Random((lat * 1000000).toLong() xor (lon * 1000000).toLong() xor timeBlock)
                if (random.nextDouble() < 0.25) pokemonActivos.add(PokemonSalvaje(obtenerIdPokemonPorRareza(random), GeoPoint(lat, lon), tiempoActual, spawnId))
            }
        }
        val cGrid = 0.002; val nuevosC = mutableListOf<GeoPoint>(); val nuevosG = mutableListOf<GeoPoint>(); val nuevosP = mutableListOf<GeoPoint>()
        for (i in -10..10) {
            for (j in -10..10) {
                val lat = ((miPos.latitude / cGrid).toInt() + i) * cGrid
                val lon = ((miPos.longitude / cGrid).toInt() + j) * cGrid
                val random = Random((lat * 1000000).toLong() xor (lon * 1000000).toLong())
                val prob = random.nextDouble()
                if (prob < 0.125) nuevosC.add(GeoPoint(lat, lon))
                else if (prob < 0.19) nuevosG.add(GeoPoint(lat, lon))
                else if (prob < 0.5) nuevosP.add(GeoPoint(lat, lon))
            }
        }
        centrosActivos.clear(); centrosActivos.addAll(nuevosC)
        gimnasiosActivos.clear(); gimnasiosActivos.addAll(nuevosG)
        pokeparadasActivas.clear(); pokeparadasActivas.addAll(nuevosP)
    }

    private fun obtenerIdPokemonPorRareza(random: Random): Int {
        val prob = random.nextDouble(100.0)
        return when {
            // Legendarios eliminados del spawn normal del mapa
            prob < 0.5 -> listOf(131, 143, 137, 142, 113, 147, 123, 127, 128, 115).random(random)
            prob < 2.5 -> listOf(1, 4, 7, 25, 95, 124, 125, 126, 106, 107, 108).random(random)
            prob < 12.0 -> listOf(35, 37, 39, 58, 63, 66, 74, 77, 81, 83, 84, 86, 90, 100, 102, 104, 111, 114, 116, 120, 138, 140).random(random)
            prob < 40.0 -> listOf(21, 23, 27, 29, 32, 43, 46, 48, 50, 52, 54, 56, 60, 69, 72, 79, 88, 92, 98, 109, 118, 129).random(random)
            else -> listOf(10, 13, 16, 19, 41, 96, 133).random(random)
        }
    }

    private fun dibujarTodo() {
        val miPos = locationOverlay.myLocation ?: return
        listaPokemonMarcadores.forEach { map.overlays.remove(it) }; listaPokemonMarcadores.clear()
        listaCentrosMundiales.forEach { map.overlays.remove(it) }; listaCentrosMundiales.clear()
        listaGimnasiosMundiales.forEach { map.overlays.remove(it) }; listaGimnasiosMundiales.clear()
        listaPokeparadasMundiales.forEach { map.overlays.remove(it) }; listaPokeparadasMundiales.clear()
        centerMarker?.let { map.overlays.remove(it) }

        centrosActivos.forEach { p -> val m = crearMarcadorEspecial(p, "Centro Pokémon", R.drawable.centropokemon, "CENTER"); listaCentrosMundiales.add(m); map.overlays.add(m) }
        gimnasiosActivos.forEach { p -> val m = crearMarcadorEspecial(p, "Gimnasio Pokémon", android.R.drawable.btn_star_big_on, "GYM"); listaGimnasiosMundiales.add(m); map.overlays.add(m) }
        pokeparadasActivas.forEach { p -> val m = crearMarcadorEspecial(p, "Pokeparada", R.drawable.pokeparada, "STOP"); listaPokeparadasMundiales.add(m); map.overlays.add(m) }

        PokemonStorage.getPokemonCenterLat(this)?.let { lat ->
            centerMarker = crearMarcadorEspecial(GeoPoint(lat, PokemonStorage.getPokemonCenterLon(this)!!), "Mi Centro", R.drawable.centropokemon, "CENTER")
            map.overlays.add(centerMarker)
        }
        myGymMarker?.let { map.overlays.remove(it) }
        PokemonStorage.getGymLat(this)?.let { lat ->
            myGymMarker = crearMarcadorEspecial(GeoPoint(lat, PokemonStorage.getGymLon(this)!!), "Mi Gimnasio", android.R.drawable.btn_star_big_on, "GYM")
            map.overlays.add(myGymMarker)
        }

        capturedSpawnIds = PokemonStorage.getCapturedSpawnIds(this).toMutableSet()
        val caughtSpecies = PokemonStorage.getCaughtSpecies(this)
        
        var trackedStillActive = false
        val trackedPokemon = pokemonActivos.find { it.spawnId == trackedPokemonSpawnId }

        pokemonActivos.forEach { p ->
            val dist = p.posicion.distanceToAsDouble(miPos)
            if (!capturedSpawnIds.contains(p.spawnId) && dist < 60) { // Rango bajado a 60m
                val m = Marker(map).apply {
                    position = p.posicion; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); infoWindow = null
                    setOnMarkerClickListener { _, _ ->
                        PokemonStorage.addCapturedSpawnId(this@MainActivity, p.spawnId); dibujarTodo()
                        startActivity(Intent(this@MainActivity, CaptureActivity::class.java).apply { putExtra("POKEMON_ID", p.id); putExtra("SPAWN_ID", p.spawnId) })
                        true 
                    }
                }
                Glide.with(this).asBitmap().load("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.id}.png").into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        m.icon = BitmapDrawable(resources, Bitmap.createScaledBitmap(resource, 180, 180, false))
                        m.rotation = -map.mapOrientation; listaPokemonMarcadores.add(m); map.overlays.add(m)
                    }
                    override fun onLoadCleared(p0: Drawable?) {}
                })
            }
            if (p.spawnId == trackedPokemonSpawnId && !capturedSpawnIds.contains(p.spawnId)) {
                trackedStillActive = true
            }
        }

        if (trackedStillActive && trackedPokemon != null) {
            actualizarLineaRastreo(miPos, trackedPokemon.posicion)
        } else {
            detenerRastreo()
        }

        if (trainerMarker == null) {
            trainerMarker = Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); icon = BitmapDrawable(resources, Bitmap.createScaledBitmap((ContextCompat.getDrawable(this@MainActivity, R.drawable.red) as BitmapDrawable).bitmap, 38, 45, false)); infoWindow = null }
        }
        map.overlays.remove(trainerMarker); map.overlays.add(trainerMarker); trainerMarker?.position = miPos

        // Vision Hitbox (Círculo de 60m)
        visionCircle?.let { map.overlays.remove(it) }
        visionCircle = Polygon(map).apply {
            points = Polygon.pointsAsCircle(miPos, 60.0)
            fillPaint.color = Color.parseColor("#332E86C1") // Azul transparente
            outlinePaint.color = Color.parseColor("#882E86C1")
            outlinePaint.strokeWidth = 2f
            infoWindow = null
        }
        visionCircle?.setOnClickListener { _, _, _ -> false }
        map.overlays.add(visionCircle)

        map.invalidate()
    }

    private fun actualizarNearbySummary() {
        val miPos = locationOverlay.myLocation ?: return
        val layout = findViewById<LinearLayout>(R.id.layoutNearbySummary)
        layout.removeAllViews()

        val nearbyList = pokemonActivos
            .filter { !capturedSpawnIds.contains(it.spawnId) }
            .sortedBy { it.posicion.distanceToAsDouble(miPos) }
            .take(3)

        val caughtSpecies = PokemonStorage.getCaughtSpecies(this)

        nearbyList.forEach { p ->
            val iv = ImageView(this)
            iv.layoutParams = LinearLayout.LayoutParams(34.dpToPx(), 34.dpToPx()).apply { marginEnd = 4.dpToPx() }
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            
            val url = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.id}.png"
            val isCaught = caughtSpecies.contains(p.id)
            
            Glide.with(this).asBitmap().load(url).into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    if (!isCaught) {
                        iv.setImageBitmap(resource)
                        iv.colorFilter = ColorMatrixColorFilter(floatArrayOf(
                            0f, 0f, 0f, 0f, 50f,
                            0f, 0f, 0f, 0f, 50f,
                            0f, 0f, 0f, 0f, 50f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    } else {
                        iv.setImageBitmap(resource)
                    }
                }
                override fun onLoadCleared(p0: Drawable?) {}
            })
            layout.addView(iv)
        }
    }

    private fun mostrarNearbyCompleto() {
        val miPos = locationOverlay.myLocation ?: return
        val nearbyList = pokemonActivos
            .filter { !capturedSpawnIds.contains(it.spawnId) }
            .sortedBy { it.posicion.distanceToAsDouble(miPos) }
            .take(9)

        if (nearbyList.isEmpty()) {
            Toast.makeText(this, "No hay Pokémon cerca...", Toast.LENGTH_SHORT).show()
            return
        }

        val caughtSpecies = PokemonStorage.getCaughtSpecies(this)
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nearby, null)
        val grid = dialogView.findViewById<GridLayout>(R.id.gridNearby)

        nearbyList.forEach { p ->
            val item = LayoutInflater.from(this).inflate(R.layout.item_nearby, grid, false)
            val iv = item.findViewById<ImageView>(R.id.ivNearbyPokemon)
            val isCaught = caughtSpecies.contains(p.id)
            
            val url = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.id}.png"
            Glide.with(this).asBitmap().load(url).into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    if (!isCaught) {
                        iv.setImageBitmap(resource)
                        iv.colorFilter = ColorMatrixColorFilter(floatArrayOf(
                            0f, 0f, 0f, 0f, 80f,
                            0f, 0f, 0f, 0f, 80f,
                            0f, 0f, 0f, 0f, 80f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    } else {
                        iv.setImageBitmap(resource)
                    }
                }
                override fun onLoadCleared(p0: Drawable?) {}
            })

            item.setOnClickListener {
                SoundManager.playClick(this@MainActivity)
                iniciarRastreo(p.spawnId)
                Toast.makeText(this@MainActivity, "Rastreando Pokémon...", Toast.LENGTH_SHORT).show()
                // El diálogo se cerrará con el alertDialog.dismiss() que definimos abajo
            }

            grid.addView(item)
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnCloseNearby).setOnClickListener {
            SoundManager.playClick(this)
            alertDialog.dismiss()
        }

        // Añadir clic a los items para cerrar el diálogo al elegir uno
        for (i in 0 until grid.childCount) {
            grid.getChildAt(i).setOnClickListener {
                val p = nearbyList[i]
                iniciarRastreo(p.spawnId)
                alertDialog.dismiss()
            }
        }

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        alertDialog.show()
    }

    private fun iniciarRastreo(spawnId: String) {
        trackedPokemonSpawnId = spawnId
        findViewById<Button>(R.id.btnCancelTracking).visibility = View.VISIBLE
        dibujarTodo()
    }

    private fun actualizarLineaRastreo(userPos: GeoPoint, targetPos: GeoPoint) {
        if (trackingPolyline == null) {
            trackingPolyline = Polyline(map).apply {
                outlinePaint.color = Color.parseColor("#E67E22") // Naranja rastro
                outlinePaint.strokeWidth = 8f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 20f), 0f)
            }
        }
        
        map.overlays.remove(trackingPolyline)
        trackingPolyline?.setPoints(listOf(userPos, targetPos))
        map.overlays.add(trackingPolyline)
    }

    private fun detenerRastreo() {
        trackedPokemonSpawnId = null
        trackingPolyline?.let { map.overlays.remove(it) }
        trackingPolyline = null
        findViewById<Button>(R.id.btnCancelTracking).visibility = View.GONE
        map.invalidate()
    }

    private fun Int.dpToPx() = (this * resources.displayMetrics.density).toInt()

    private fun crearMarcadorEspecial(p: GeoPoint, t: String, resId: Int, type: String) = Marker(map).apply {
        position = p; title = t; isFlat = true; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); infoWindow = null
        val d = ContextCompat.getDrawable(this@MainActivity, if(type == "GYM") R.drawable.gymicon else resId)
        val size = 55 // Tamaño unificado igual a pokeparadas
        if (d is BitmapDrawable) icon = BitmapDrawable(resources, Bitmap.createScaledBitmap(d.bitmap, size, size, false)) else icon = d
        setOnMarkerClickListener { _, _ ->
            when(type) {
                "CENTER" -> startActivity(Intent(this@MainActivity, PokemonCenterActivity::class.java))
                "GYM" -> {
                    val gymId = "${p.latitude}_${p.longitude}"
                    if (PokemonStorage.canBattleGym(this@MainActivity, gymId)) {
                        val intent = Intent(this@MainActivity, GymActivity::class.java)
                        intent.putExtra("LAT", p.latitude)
                        intent.putExtra("LON", p.longitude)
                        intent.putExtra("GYM_ID", gymId)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@MainActivity, "Este Gimnasio está en descanso. Vuelve más tarde.", Toast.LENGTH_SHORT).show()
                    }
                }
                "STOP" -> girarPokeparada(p)
            }
            true 
        }
    }

    private fun girarPokeparada(p: GeoPoint) {
        val stopId = "${p.latitude}_${p.longitude}"
        if (PokemonStorage.canSpinPokestop(this, stopId)) {
            val balls = Random.nextInt(2, 5)
            PokemonStorage.addPokeballs(this, balls)
            
            val extras = mutableListOf<String>()
            if (Random.nextFloat() < 0.3f) {
                val potionCount = Random.nextInt(1, 3)
                PokemonStorage.addItem(this, "potion", potionCount)
                extras.add("$potionCount Pociones")
            }
            if (Random.nextFloat() < 0.15f) {
                val reviveCount = 1
                PokemonStorage.addItem(this, "revive", reviveCount)
                extras.add("$reviveCount Revivir")
            }
            
            PokemonStorage.setPokestopSpun(this, stopId)
            PokemonStorage.addPlayerExp(this, 50)
            
            val msg = StringBuilder("¡Has conseguido $balls Pokeballs")
            if (extras.isNotEmpty()) {
                msg.append(", ")
                msg.append(extras.joinToString(" y "))
            }
            msg.append(" y 50 XP!")
            
            Toast.makeText(this, msg.toString(), Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this, "Esta Pokeparada está recargándose...", Toast.LENGTH_SHORT).show()
    }

    private fun spawnRandomPokemonDebug() {
        locationOverlay.myLocation?.let { p ->
            pokemonActivos.add(PokemonSalvaje(Random.nextInt(1, 152), GeoPoint(p.latitude + 0.0001, p.longitude + 0.0001), System.currentTimeMillis(), "debug_${System.currentTimeMillis()}"))
            dibujarTodo()
        }
    }

    override fun onResume() { 
        super.onResume()
        map.onResume() 
        // Música principal
        SoundManager.playMusic(this, R.raw.musicaoutsetisland)
        
        // Restaurar estado del botón de rastreo si es necesario
        findViewById<Button>(R.id.btnCancelTracking).visibility = if (trackedPokemonSpawnId != null) View.VISIBLE else View.GONE
    }

    override fun onPause() { 
        super.onPause()
        map.onPause()
    }
}
