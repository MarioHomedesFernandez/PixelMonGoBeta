package com.example.simplemonstergo

import org.osmdroid.util.GeoPoint
import java.util.Random

enum class GymTerrain(val displayName: String, val typeBoost: String, val damageMultiplier: Float, val accuracyMod: Float) {
    WATER("Agua", "Agua", 1.15f, 0.95f),
    FIRE("Fuego", "Fuego", 1.15f, 0.95f),
    GRASS("Planta", "Planta", 1.15f, 0.95f),
    ELECTRIC("Eléctrico", "Eléctrico", 1.15f, 0.95f),
    ROCK("Roca", "Roca", 1.15f, 0.90f),
    ICE("Hielo", "Hielo", 1.15f, 0.95f),
    NONE("Normal", "Normal", 1.0f, 1.0f);

    companion object {
        fun getForLocation(point: GeoPoint): GymTerrain {
            val now = System.currentTimeMillis()
            // Bloques de 15 minutos (15 * 60 * 1000 = 900.000 ms)
            val timeInterval = now / 900000L
            
            // Usar coordenadas + intervalo de tiempo como semilla
            val seed = (point.latitude * 10000).toLong() + (point.longitude * 10000).toLong() + timeInterval
            val random = Random(seed)
            
            val eligible = values().filter { it != NONE }
            return eligible[random.nextInt(eligible.size)]
        }

        fun getRandom(): GymTerrain = values().filter { it != NONE }.random()
    }
}

data class GymInfo(
    val type: GymTerrain,
    val floor: Int,
    val totalFloors: Int = 3
)
