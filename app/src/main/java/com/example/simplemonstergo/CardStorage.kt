package com.example.simplemonstergo

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object CardStorage {
    private const val KEY_OWNED_CARDS_MAP = "OwnedCardsMapV1"
    private val gson = Gson()

    fun getOwnedCardsMap(context: Context): Map<String, Int> {
        val prefs = context.getSharedPreferences("PokemonPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_OWNED_CARDS_MAP, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(json, type)
    }

    fun addCards(context: Context, cardIds: List<String>) {
        val prefs = context.getSharedPreferences("PokemonPrefs", Context.MODE_PRIVATE)
        val current = getOwnedCardsMap(context).toMutableMap()
        
        cardIds.forEach { id ->
            val count = current[id] ?: 0
            current[id] = count + 1
        }
        
        val json = gson.toJson(current)
        prefs.edit().putString(KEY_OWNED_CARDS_MAP, json).apply()
        PokemonStorage.syncToCloud(context)
    }

    fun removeCard(context: Context, cardId: String) {
        val prefs = context.getSharedPreferences("PokemonPrefs", Context.MODE_PRIVATE)
        val current = getOwnedCardsMap(context).toMutableMap()
        val count = current[cardId] ?: 0
        if (count > 1) {
            current[cardId] = count - 1
        } else {
            current.remove(cardId)
        }
        val json = gson.toJson(current)
        prefs.edit().putString(KEY_OWNED_CARDS_MAP, json).apply()
        PokemonStorage.syncToCloud(context)
    }

    // For backwards compatibility with the UI if needed
    fun getOwnedCardsList(context: Context): List<String> {
        return getOwnedCardsMap(context).keys.toList().sortedBy { 
            it.split("/").last().toIntOrNull() ?: 999 
        }
    }
}
