package com.example.simplemonstergo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PokemonStorage {
    private const val PREFS_NAME = "PokemonPrefs"
    private const val KEY_POKEMON_LIST = "PokemonListV2"
    private const val KEY_CANDIES = "PokemonCandies"
    private const val KEY_CAPTURED_SPAWN_IDS = "CapturedSpawnIds"
    private const val KEY_POKEMON_CENTER_LAT = "PokemonCenterLat"
    private const val KEY_POKEMON_CENTER_LON = "PokemonCenterLon"
    private const val KEY_GYM_LAT = "GymLat"
    private const val KEY_GYM_LON = "GymLon"
    private const val KEY_POKEBALLS = "PokeballsV1"
    private const val KEY_PLAYER_XP = "PlayerExp"
    private const val KEY_PLAYER_LEVEL = "PlayerLevel"
    private const val KEY_FAVORITE_CARD = "FavoriteCard"
    private const val KEY_FRIENDS = "FriendsList"
    private const val KEY_POKEDEX_CAUGHT = "PokedexCaught"
    private const val KEY_POKEDEX_SEEN = "PokedexSeen"
    private const val KEY_MONEY = "PlayerMoney"
    private const val KEY_INVENTORY = "PlayerInventory"
    private const val KEY_LAST_TRADE_TIME = "LastTradeTime"
    private const val KEY_MEDALS = "GymMedals"
    private const val KEY_CLAIMED_REWARDS = "ClaimedLevelRewards"
    private const val KEY_TUTORIAL_DONE = "TutorialDoneV1"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private const val DB_URL = "https://pixelmongo-default-rtdb.europe-west1.firebasedatabase.app/"

    fun syncToCloud(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseDatabase.getInstance(DB_URL).reference.child("users").child(user.uid)
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val data = mutableMapOf<String, Any?>()
        
        prefs.all.forEach { (key, value) ->
            if (value is Set<*>) {
                data[key] = value.toList()
            } else if (key == "UserProfile" && value is String) {
                try {
                    val profile = gson.fromJson(value, UserProfile::class.java)
                    data[key] = mapOf(
                        "name" to profile.name,
                        "faceType" to profile.faceType,
                        "hairType" to profile.hairType,
                        "shirtColorIndex" to profile.shirtColorIndex,
                        "skinColorIndex" to profile.skinColorIndex,
                        "eyeColorIndex" to profile.eyeColorIndex,
                        "eyeType" to profile.eyeType,
                        "mouthType" to profile.mouthType
                    )
                } catch (e: Exception) {
                    data[key] = value
                }
            } else {
                data[key] = value
            }
        }
        db.updateChildren(data)
    }

    fun syncFromCloud(context: Context, onComplete: () -> Unit = {}) {
        val user = FirebaseAuth.getInstance().currentUser ?: run { onComplete(); return }
        val db = FirebaseDatabase.getInstance(DB_URL).reference.child("users").child(user.uid)

        db.get().addOnSuccessListener { snapshot ->
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            editor.clear().apply() // Limpiar datos locales para evitar mezcla de cuentas

            if (snapshot.exists()) {
                snapshot.children.forEach { child ->
                    val key = child.key ?: return@forEach
                    val value = child.value
                    
                    try {
                        if (key == KEY_CAPTURED_SPAWN_IDS || key == KEY_POKEDEX_SEEN || key == KEY_POKEDEX_CAUGHT || key == KEY_FRIENDS) {
                            val list = value as? List<*>
                            if (list != null) {
                                val stringSet = list.filterIsInstance<String>().toSet()
                                editor.putStringSet(key, stringSet)
                            }
                        } else if (key == "UserProfile") {
                            if (value is Map<*, *>) {
                                val profile = UserProfile(
                                    name = value["name"]?.toString() ?: "Entrenador",
                                    faceType = (value["faceType"] as? Number)?.toInt() ?: 0,
                                    hairType = (value["hairType"] as? Number)?.toInt() ?: 0,
                                    shirtColorIndex = (value["shirtColorIndex"] as? Number)?.toInt() ?: 0,
                                    skinColorIndex = (value["skinColorIndex"] as? Number)?.toInt() ?: 0,
                                    eyeColorIndex = (value["eyeColorIndex"] as? Number)?.toInt() ?: 0,
                                    eyeType = (value["eyeType"] as? Number)?.toInt() ?: 0,
                                    mouthType = (value["mouthType"] as? Number)?.toInt() ?: 0
                                )
                                editor.putString(key, gson.toJson(profile))
                            } else if (value is String) {
                                editor.putString(key, value)
                            }
                        } else if (value is String) {
                            editor.putString(key, value)
                        } else if (value is Number) {
                            editor.putInt(key, value.toInt())
                        } else if (value is Boolean) {
                            editor.putBoolean(key, value)
                        }
                    } catch (e: Exception) {
                        Log.e("Storage", "Error parsing $key", e)
                    }
                }
            }
            
            // Solo si la clave NO existe en el snapshot de la nube, ponemos el valor inicial de "Bienvenida"
            if (!snapshot.hasChild(KEY_PLAYER_LEVEL)) editor.putInt(KEY_PLAYER_LEVEL, 1)
            if (!snapshot.hasChild(KEY_POKEBALLS)) editor.putInt(KEY_POKEBALLS, 20)
            if (!snapshot.hasChild(KEY_MONEY)) editor.putInt(KEY_MONEY, 100)
            
            editor.apply()
            onComplete()
        }.addOnFailureListener { 
            onComplete() 
        }
    }

    fun savePokemon(context: Context, speciesId: Int) {
        val pokemonList = getCapturedPokemon(context).toMutableList()
        val newPokemon = PokemonInstance(speciesId = speciesId, level = 1, location = PokemonLocation.PC)
        pokemonList.add(newPokemon)
        savePokemonList(context, pokemonList)
        val base = PokemonData.getBaseStats(speciesId)
        addCandies(context, base.familyId, 3) 
    }

    fun getCapturedPokemon(context: Context): List<PokemonInstance> {
        return try {
            val json = getPrefs(context).getString(KEY_POKEMON_LIST, null) ?: return emptyList()
            val type = object : TypeToken<List<PokemonInstance>>() {}.type
            val list: List<PokemonInstance> = gson.fromJson(json, type)
            list.forEach { 
                it.getSafeMoves()
                it.getSafeHp()
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    fun savePokemonList(context: Context, list: List<PokemonInstance>) {
        val json = gson.toJson(list)
        getPrefs(context).edit().putString(KEY_POKEMON_LIST, json).apply()
        syncToCloud(context)
    }

    fun getCandies(context: Context, familyId: Int): Int {
        return try {
            val json = getPrefs(context).getString(KEY_CANDIES, null) ?: return 0
            val type = object : TypeToken<Map<Int, Int>>() {}.type
            val candies: Map<Int, Int> = gson.fromJson(json, type) ?: emptyMap()
            candies[familyId] ?: 0
        } catch (e: Exception) { 0 }
    }

    fun addCandies(context: Context, familyId: Int, amount: Int) {
        val json = getPrefs(context).getString(KEY_CANDIES, null)
        val type = object : TypeToken<MutableMap<Int, Int>>() {}.type
        val candies: MutableMap<Int, Int> = try {
            if (json == null) mutableMapOf() else gson.fromJson(json, type)
        } catch (e: Exception) { mutableMapOf() }
        val current = candies[familyId] ?: 0
        candies[familyId] = current + amount
        getPrefs(context).edit().putString(KEY_CANDIES, gson.toJson(candies)).apply()
        syncToCloud(context)
    }

    fun removePokemon(context: Context, uuid: String) {
        val list = getCapturedPokemon(context).toMutableList()
        val pokemon = list.find { it.uuid == uuid }
        if (pokemon != null) {
            val familyId = PokemonData.getBaseStats(pokemon.speciesId).familyId
            list.remove(pokemon)
            savePokemonList(context, list)
            addCandies(context, familyId, 1) 
        }
    }

    fun removeMultiplePokemon(context: Context, uuids: List<String>) {
        val list = getCapturedPokemon(context).toMutableList()
        uuids.forEach { uuid ->
            val pokemon = list.find { it.uuid == uuid }
            if (pokemon != null) {
                val familyId = PokemonData.getBaseStats(pokemon.speciesId).familyId
                list.remove(pokemon)
                addCandies(context, familyId, 1)
            }
        }
        savePokemonList(context, list)
    }

    fun assignToTeam(context: Context, uuid: String, team: PokemonLocation, slot: Int) {
        val list = getCapturedPokemon(context).toMutableList()
        list.find { it.location == team && it.teamSlot == slot }?.let {
            it.location = PokemonLocation.PC
            it.teamSlot = null
        }
        list.find { it.uuid == uuid }?.let {
            it.location = team
            it.teamSlot = slot
        }
        savePokemonList(context, list)
    }

    fun removeFromTeam(context: Context, uuid: String) {
        val list = getCapturedPokemon(context).toMutableList()
        list.find { it.uuid == uuid }?.let {
            it.location = PokemonLocation.PC
            it.teamSlot = null
        }
        savePokemonList(context, list)
    }

    fun saveUserProfile(context: Context, profile: UserProfile) {
        val json = gson.toJson(profile)
        getPrefs(context).edit().putString("UserProfile", json).apply()
        syncToCloud(context)
    }

    fun getUserProfile(context: Context): UserProfile {
        return try {
            val json = getPrefs(context).getString("UserProfile", null) ?: return UserProfile()
            gson.fromJson(json, UserProfile::class.java)
        } catch (e: Exception) { UserProfile() }
    }

    fun addCapturedSpawnId(context: Context, spawnId: String) {
        val set = getPrefs(context).getStringSet(KEY_CAPTURED_SPAWN_IDS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(spawnId)
        getPrefs(context).edit().putStringSet(KEY_CAPTURED_SPAWN_IDS, set).apply()
        syncToCloud(context)
    }

    fun getCapturedSpawnIds(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_CAPTURED_SPAWN_IDS, emptySet()) ?: emptySet()
    }

    fun savePokemonCenter(context: Context, lat: Double, lon: Double) {
        getPrefs(context).edit()
            .putFloat(KEY_POKEMON_CENTER_LAT, lat.toFloat())
            .putFloat(KEY_POKEMON_CENTER_LON, lon.toFloat())
            .apply()
        syncToCloud(context)
    }

    fun clearPokemonCenter(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_POKEMON_CENTER_LAT)
            .remove(KEY_POKEMON_CENTER_LON)
            .apply()
        syncToCloud(context)
    }

    fun getPokemonCenterLat(context: Context): Double? {
        val lat = getPrefs(context).getFloat(KEY_POKEMON_CENTER_LAT, 0f)
        return if (lat == 0.0f) null else lat.toDouble()
    }

    fun getPokemonCenterLon(context: Context): Double? {
        val lon = getPrefs(context).getFloat(KEY_POKEMON_CENTER_LON, 0f)
        return if (lon == 0.0f) null else lon.toDouble()
    }

    fun saveGym(context: Context, lat: Double, lon: Double) {
        getPrefs(context).edit()
            .putFloat(KEY_GYM_LAT, lat.toFloat())
            .putFloat(KEY_GYM_LON, lon.toFloat())
            .apply()
        syncToCloud(context)
    }

    fun getGymLat(context: Context): Double? {
        val lat = getPrefs(context).getFloat(KEY_GYM_LAT, 0f)
        return if (lat == 0.0f) null else lat.toDouble()
    }

    fun getGymLon(context: Context): Double? {
        val lon = getPrefs(context).getFloat(KEY_GYM_LON, 0f)
        return if (lon == 0.0f) null else lon.toDouble()
    }

    fun clearGym(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_GYM_LAT)
            .remove(KEY_GYM_LON)
            .apply()
        syncToCloud(context)
    }

    fun clearAllLocalData(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().clear().apply()
    }

    fun getPokeballs(context: Context): Int {
        return getPrefs(context).getInt(KEY_POKEBALLS, 20)
    }

    fun addPokeballs(context: Context, amount: Int) {
        val current = getPokeballs(context)
        getPrefs(context).edit().putInt(KEY_POKEBALLS, current + amount).apply()
        syncToCloud(context)
    }

    fun usePokeball(context: Context): Boolean {
        val current = getPokeballs(context)
        if (current > 0) {
            getPrefs(context).edit().putInt(KEY_POKEBALLS, current - 1).apply()
            syncToCloud(context)
            return true
        }
        return false
    }

    fun getPlayerExp(context: Context): Int {
        return getPrefs(context).getInt(KEY_PLAYER_XP, 0)
    }

    fun getPlayerLevel(context: Context): Int {
        return getPrefs(context).getInt(KEY_PLAYER_LEVEL, 1)
    }

    fun addPlayerExp(context: Context, amount: Int) {
        var xp = getPlayerExp(context) + amount
        var level = getPlayerLevel(context)
        
        if (level >= 100) {
            getPrefs(context).edit().putInt(KEY_PLAYER_XP, 0).apply()
            return
        }

        while (xp >= 1000 && level < 100) {
            xp -= 1000
            level++
        }
        
        if (level >= 100) xp = 0

        getPrefs(context).edit()
            .putInt(KEY_PLAYER_XP, xp)
            .putInt(KEY_PLAYER_LEVEL, level)
            .apply()
        syncToCloud(context)
    }

    fun getFavoriteCard(context: Context): String? {
        return getPrefs(context).getString(KEY_FAVORITE_CARD, null)
    }

    fun saveFavoriteCard(context: Context, cardId: String?) {
        getPrefs(context).edit().putString(KEY_FAVORITE_CARD, cardId).apply()
        syncToCloud(context)
    }

    fun getFriends(context: Context): Set<String> {
        return try {
            getPrefs(context).getStringSet(KEY_FRIENDS, emptySet()) ?: emptySet()
        } catch (e: Exception) { emptySet() }
    }

    fun addFriend(context: Context, friendUid: String) {
        val friends = getFriends(context).toMutableSet()
        friends.add(friendUid)
        getPrefs(context).edit().putStringSet(KEY_FRIENDS, friends).apply()
        syncToCloud(context)
    }

    fun removeFriend(context: Context, friendUid: String) {
        val friends = getFriends(context).toMutableSet()
        friends.remove(friendUid)
        getPrefs(context).edit().putStringSet(KEY_FRIENDS, friends).apply()
        syncToCloud(context)
    }

    fun getMoney(context: Context): Int {
        return getPrefs(context).getInt(KEY_MONEY, 100)
    }

    fun addMoney(context: Context, amount: Int) {
        val current = getMoney(context)
        getPrefs(context).edit().putInt(KEY_MONEY, current + amount).apply()
        syncToCloud(context)
    }

    fun useMoney(context: Context, amount: Int): Boolean {
        val current = getMoney(context)
        if (current >= amount) {
            getPrefs(context).edit().putInt(KEY_MONEY, current - amount).apply()
            syncToCloud(context)
            return true
        }
        return false
    }

    fun getInventory(context: Context): MutableMap<String, Int> {
        return try {
            val json = getPrefs(context).getString(KEY_INVENTORY, null) ?: return mutableMapOf()
            val type = object : TypeToken<MutableMap<String, Int>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) { mutableMapOf() }
    }

    fun addItem(context: Context, itemId: String, amount: Int) {
        val inv = getInventory(context)
        inv[itemId] = (inv[itemId] ?: 0) + amount
        getPrefs(context).edit().putString(KEY_INVENTORY, gson.toJson(inv)).apply()
        syncToCloud(context)
    }

    fun getItemCount(context: Context, itemId: String): Int {
        return getInventory(context)[itemId] ?: 0
    }

    fun useItem(context: Context, itemId: String): Boolean {
        val inv = getInventory(context)
        val count = inv[itemId] ?: 0
        if (count > 0) {
            inv[itemId] = count - 1
            getPrefs(context).edit().putString(KEY_INVENTORY, gson.toJson(inv)).apply()
            syncToCloud(context)
            return true
        }
        return false
    }

    fun canTrade(context: Context): Boolean {
        val lastTrade = getPrefs(context).getLong(KEY_LAST_TRADE_TIME, 0L)
        val now = System.currentTimeMillis()
        val oneWeek = 7 * 24 * 60 * 60 * 1000L
        return (now - lastTrade) > oneWeek
    }

    fun markTradeDone(context: Context) {
        getPrefs(context).edit().putLong(KEY_LAST_TRADE_TIME, System.currentTimeMillis()).apply()
        syncToCloud(context)
    }

    fun addPokemonInstance(context: Context, instance: PokemonInstance) {
        val list = getCapturedPokemon(context).toMutableList()
        list.add(instance.copy(location = PokemonLocation.PC, teamSlot = null))
        savePokemonList(context, list)
    }

    fun removePokemonByUuid(context: Context, uuid: String) {
        val list = getCapturedPokemon(context).toMutableList()
        list.removeAll { it.uuid == uuid }
        savePokemonList(context, list)
    }

    fun updatePokemonHp(context: Context, uuid: String, newHp: Int) {
        val list = getCapturedPokemon(context).toMutableList()
        list.find { it.uuid == uuid }?.let {
            it.currentHp = newHp
            savePokemonList(context, list)
        }
    }

    fun healPokemon(context: Context, uuid: String, amount: Int): Boolean {
        val list = getCapturedPokemon(context).toMutableList()
        val pokemon = list.find { it.uuid == uuid } ?: return false
        val base = PokemonData.getBaseStats(pokemon.speciesId)
        val maxHp = PokemonData.calculateHP(base.hp, pokemon.level, pokemon.ivHp)
        
        // Las pociones no pueden revivir, el Pokémon debe tener al menos 1 PS
        if (pokemon.getSafeHp() <= 0) return false
        if (pokemon.getSafeHp() >= maxHp) return false 

        pokemon.currentHp = (pokemon.getSafeHp() + amount).coerceAtMost(maxHp)
        savePokemonList(context, list)
        return true
    }

    fun healAllPokemon(context: Context) {
        val list = getCapturedPokemon(context).toMutableList()
        list.forEach { 
            val base = PokemonData.getBaseStats(it.speciesId)
            it.currentHp = PokemonData.calculateHP(base.hp, it.level, it.ivHp)
        }
        savePokemonList(context, list)
    }

    fun canSpinPokestop(context: Context, stopId: String): Boolean {
        val lastSpin = getPrefs(context).getLong("Stop_$stopId", 0L)
        val now = System.currentTimeMillis()
        return (now - lastSpin) > 300000L // 5 minutos
    }

    fun setPokestopSpun(context: Context, stopId: String) {
        getPrefs(context).edit().putLong("Stop_$stopId", System.currentTimeMillis()).apply()
    }

    fun getMedals(context: Context): Int {
        return getPrefs(context).getInt(KEY_MEDALS, 0)
    }

    fun getClaimedLevelRewards(context: Context): Set<Int> {
        return getPrefs(context).getStringSet(KEY_CLAIMED_REWARDS, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    fun markRewardAsClaimed(context: Context, level: Int) {
        val claimed = getClaimedLevelRewards(context).toMutableSet()
        claimed.add(level)
        getPrefs(context).edit().putStringSet(KEY_CLAIMED_REWARDS, claimed.map { it.toString() }.toSet()).apply()
        syncToCloud(context)
    }

    fun getLevelRewardInfo(level: Int): String? {
        return when (level) {
            2 -> "10 Pokéballs"
            5 -> "5 Pociones"
            10 -> "3 Revivir"
            15 -> "1 Intercambiador"
            20 -> "20 Pokéballs"
            25 -> "10 Pociones"
            30 -> "5 Revivir"
            40 -> "500 Monedas"
            50 -> "1000 Monedas"
            75 -> "Título de Campeón"
            100 -> "5000 Monedas"
            else -> if (level % 5 == 0 && level > 1) "10 Pokéballs" else null
        }
    }

    fun claimReward(context: Context, level: Int): Boolean {
        if (getPlayerLevel(context) < level) return false
        if (getClaimedLevelRewards(context).contains(level)) return false
        
        val reward = getLevelRewardInfo(level) ?: return false
        
        when {
            reward.contains("Pokéballs") -> addPokeballs(context, reward.split(" ")[0].toInt())
            reward.contains("Pociones") -> addItem(context, "potion", reward.split(" ")[0].toInt())
            reward.contains("Revivir") -> addItem(context, "revive", reward.split(" ")[0].toInt())
            reward.contains("Intercambiador") -> addItem(context, "trade_link", 1)
            reward.contains("Monedas") -> addMoney(context, reward.split(" ")[0].toInt())
            reward.contains("Título de Campeón") -> addItem(context, "champion_title", 1)
        }
        
        markRewardAsClaimed(context, level)
        return true
    }

    fun addMedal(context: Context) {
        val current = getMedals(context)
        getPrefs(context).edit().putInt(KEY_MEDALS, current + 1).apply()
        syncToCloud(context)
    }

    fun useMedals(context: Context, amount: Int): Boolean {
        val current = getMedals(context)
        if (current >= amount) {
            getPrefs(context).edit().putInt(KEY_MEDALS, current - amount).apply()
            syncToCloud(context)
            return true
        }
        return false
    }

    fun isTutorialDone(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TUTORIAL_DONE, false)
    }

    fun setTutorialDone(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_TUTORIAL_DONE, true).apply()
        syncToCloud(context)
    }

    fun canBattleGym(context: Context, gymId: String): Boolean {
        val lastBattle = getPrefs(context).getLong("GymBattle_$gymId", 0L)
        val now = System.currentTimeMillis()
        return (now - lastBattle) > 900000L // 15 minutos
    }

    fun setGymBattled(context: Context, gymId: String) {
        getPrefs(context).edit().putLong("GymBattle_$gymId", System.currentTimeMillis()).apply()
    }

    // --- POKEDEX LOGIC ---

    fun markAsSeen(context: Context, speciesId: Int) {
        val seen = getPrefs(context).getStringSet(KEY_POKEDEX_SEEN, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (seen.add(speciesId.toString())) {
            getPrefs(context).edit().putStringSet(KEY_POKEDEX_SEEN, seen).apply()
            syncToCloud(context)
        }
    }

    fun markAsCaught(context: Context, speciesId: Int) {
        markAsSeen(context, speciesId)
        val caught = getPrefs(context).getStringSet(KEY_POKEDEX_CAUGHT, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (caught.add(speciesId.toString())) {
            getPrefs(context).edit().putStringSet(KEY_POKEDEX_CAUGHT, caught).apply()
            syncToCloud(context)
        }
    }

    fun getSeenSpecies(context: Context): Set<Int> {
        return try {
            getPrefs(context).getStringSet(KEY_POKEDEX_SEEN, emptySet())?.map { it.toInt() }?.toSet() ?: emptySet()
        } catch (e: Exception) { emptySet() }
    }

    fun getCaughtSpecies(context: Context): Set<Int> {
        return try {
            getPrefs(context).getStringSet(KEY_POKEDEX_CAUGHT, emptySet())?.map { it.toInt() }?.toSet() ?: emptySet()
        } catch (e: Exception) { emptySet() }
    }
}

data class UserProfile(
    val name: String = "Entrenador",
    val skinColorIndex: Int = 0,
    val faceType: Int = 0,
    val hairType: Int = 0,
    val hairColorIndex: Int = 1,
    val eyeType: Int = 0,
    val eyeColorIndex: Int = 0,
    val mouthType: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val shirtColorIndex: Int = 0
)
