package com.example.simplemonstergo

import java.util.UUID
import kotlin.math.floor
import kotlin.random.Random

enum class PokemonLocation {
    PC, TEAM_1, TEAM_2
}

enum class PokemonSize {
    PEQUENO, MEDIANO, GRANDE, MUY_GRANDE
}

data class PokemonInstance(
    val uuid: String = UUID.randomUUID().toString(),
    val speciesId: Int,
    var level: Int = 1,
    var nickname: String? = null,
    val ivHp: Int = Random.nextInt(32),
    val ivAtk: Int = Random.nextInt(32),
    val ivDef: Int = Random.nextInt(32),
    val ivSpAtk: Int = Random.nextInt(32),
    val ivSpDef: Int = Random.nextInt(32),
    val ivSpeed: Int = Random.nextInt(32),
    var location: PokemonLocation = PokemonLocation.PC,
    var teamSlot: Int? = null, // 0-5 si está en un equipo
    var currentHp: Int? = null,
    var moves: MutableList<Move>? = PokemonData.getDefaultMoves(speciesId).toMutableList()
) {
    fun getSafeHp(): Int {
        val base = PokemonData.getBaseStats(speciesId)
        val maxHp = PokemonData.calculateHP(base.hp, level, ivHp)
        if (currentHp == null) currentHp = maxHp
        return currentHp!!
    }

    fun getSafeMoves(): List<Move> {
        val competitiveMoves = PokemonData.getDefaultMoves(speciesId)
        // Si no tiene ataques o si tiene el set genérico básico (Placaje/Ataque Rápido) 
        // lo actualizamos al set competitivo "perfecto"
        if (moves == null || (moves!!.size <= 2 && moves!!.any { it.name == "Placaje" } && competitiveMoves.size > 2)) {
            moves = competitiveMoves.toMutableList()
        }
        return moves!!
    }

    fun getTotalStats(): Int {
        val base = PokemonData.getBaseStats(speciesId)
        return PokemonData.calculateHP(base.hp, level, ivHp) +
               PokemonData.calculateStat(base.attack, level, ivAtk) +
               PokemonData.calculateStat(base.defense, level, ivDef) +
               PokemonData.calculateStat(base.spAttack, level, ivSpAtk) +
               PokemonData.calculateStat(base.spDefense, level, ivSpDef) +
               PokemonData.calculateStat(base.speed, level, ivSpeed)
    }
}

data class BaseStats(
    val id: Int,
    val name: String,
    val familyId: Int,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val spAttack: Int,
    val spDefense: Int,
    val speed: Int,
    val types: List<String>,
    val evolutionId: Int? = null,
    val evolutionCandies: Int = 0
)

object PokemonData {
    val species = mapOf(
        1 to BaseStats(1, "Bulbasaur", 1, 45, 49, 49, 65, 65, 45, listOf("Planta", "Veneno"), 2, 25),
        2 to BaseStats(2, "Ivysaur", 1, 60, 62, 63, 80, 80, 60, listOf("Planta", "Veneno"), 3, 100),
        3 to BaseStats(3, "Venusaur", 1, 80, 82, 83, 100, 100, 80, listOf("Planta", "Veneno")),
        4 to BaseStats(4, "Charmander", 4, 39, 52, 43, 60, 50, 65, listOf("Fuego"), 5, 25),
        5 to BaseStats(5, "Charmeleon", 4, 58, 64, 58, 80, 65, 80, listOf("Fuego"), 6, 100),
        6 to BaseStats(6, "Charizard", 4, 78, 84, 78, 109, 85, 100, listOf("Fuego", "Volador")),
        7 to BaseStats(7, "Squirtle", 7, 44, 48, 65, 50, 64, 43, listOf("Agua"), 8, 25),
        8 to BaseStats(8, "Wartortle", 7, 59, 63, 80, 65, 80, 58, listOf("Agua"), 9, 100),
        9 to BaseStats(9, "Blastoise", 7, 79, 83, 100, 85, 105, 78, listOf("Agua")),
        10 to BaseStats(10, "Caterpie", 10, 45, 30, 35, 20, 20, 45, listOf("Bicho"), 11, 12),
        11 to BaseStats(11, "Metapod", 10, 50, 20, 55, 25, 25, 30, listOf("Bicho"), 12, 50),
        12 to BaseStats(12, "Butterfree", 10, 60, 45, 50, 90, 80, 70, listOf("Bicho", "Volador")),
        13 to BaseStats(13, "Weedle", 13, 40, 35, 30, 20, 20, 50, listOf("Bicho", "Veneno"), 14, 12),
        14 to BaseStats(14, "Kakuna", 13, 45, 25, 50, 25, 25, 35, listOf("Bicho", "Veneno"), 15, 50),
        15 to BaseStats(15, "Beedrill", 13, 65, 90, 40, 45, 80, 75, listOf("Bicho", "Veneno")),
        16 to BaseStats(16, "Pidgey", 16, 40, 45, 40, 35, 35, 56, listOf("Normal", "Volador"), 17, 12),
        17 to BaseStats(17, "Pidgeotto", 16, 63, 60, 55, 50, 50, 71, listOf("Normal", "Volador"), 18, 50),
        18 to BaseStats(18, "Pidgeot", 16, 83, 80, 75, 70, 70, 101, listOf("Normal", "Volador")),
        19 to BaseStats(19, "Rattata", 19, 30, 56, 35, 25, 35, 72, listOf("Normal"), 20, 25),
        20 to BaseStats(20, "Raticate", 19, 55, 81, 60, 50, 70, 97, listOf("Normal")),
        21 to BaseStats(21, "Spearow", 21, 40, 60, 30, 31, 31, 70, listOf("Normal", "Volador"), 22, 50),
        22 to BaseStats(22, "Fearow", 21, 65, 90, 65, 61, 61, 100, listOf("Normal", "Volador")),
        23 to BaseStats(23, "Ekans", 23, 35, 60, 44, 40, 54, 55, listOf("Veneno"), 24, 50),
        24 to BaseStats(24, "Arbok", 23, 60, 95, 69, 65, 79, 80, listOf("Veneno")),
        25 to BaseStats(25, "Pikachu", 25, 35, 55, 40, 50, 50, 90, listOf("Eléctrico"), 26, 50),
        26 to BaseStats(26, "Raichu", 25, 60, 90, 55, 90, 80, 110, listOf("Eléctrico")),
        27 to BaseStats(27, "Sandshrew", 27, 50, 75, 85, 20, 30, 40, listOf("Tierra"), 28, 50),
        28 to BaseStats(28, "Sandslash", 27, 75, 100, 110, 45, 55, 65, listOf("Tierra")),
        29 to BaseStats(29, "Nidoran♀", 29, 55, 47, 52, 40, 40, 41, listOf("Veneno"), 30, 25),
        30 to BaseStats(30, "Nidorina", 29, 70, 62, 67, 55, 55, 56, listOf("Veneno"), 31, 100),
        31 to BaseStats(31, "Nidoqueen", 29, 90, 92, 87, 75, 85, 76, listOf("Veneno", "Tierra")),
        32 to BaseStats(32, "Nidoran♂", 32, 46, 57, 40, 40, 40, 50, listOf("Veneno"), 33, 25),
        33 to BaseStats(33, "Nidorino", 32, 61, 72, 57, 55, 55, 65, listOf("Veneno"), 34, 100),
        34 to BaseStats(34, "Nidoking", 32, 81, 102, 77, 85, 75, 85, listOf("Veneno", "Tierra")),
        35 to BaseStats(35, "Clefairy", 35, 70, 45, 48, 60, 65, 35, listOf("Hada"), 36, 50),
        36 to BaseStats(36, "Clefable", 35, 95, 70, 73, 95, 90, 60, listOf("Hada")),
        37 to BaseStats(37, "Vulpix", 37, 38, 41, 40, 50, 65, 65, listOf("Fuego"), 38, 50),
        38 to BaseStats(38, "Ninetales", 37, 73, 76, 75, 81, 100, 100, listOf("Fuego")),
        39 to BaseStats(39, "Jigglypuff", 39, 115, 45, 20, 45, 25, 20, listOf("Normal", "Hada"), 40, 50),
        40 to BaseStats(40, "Wigglytuff", 39, 140, 70, 45, 85, 50, 45, listOf("Normal", "Hada")),
        41 to BaseStats(41, "Zubat", 41, 40, 45, 35, 30, 40, 55, listOf("Veneno", "Volador"), 42, 50),
        42 to BaseStats(42, "Golbat", 41, 75, 80, 70, 65, 75, 90, listOf("Veneno", "Volador")),
        43 to BaseStats(43, "Oddish", 43, 45, 50, 55, 75, 65, 30, listOf("Planta", "Veneno"), 44, 25),
        44 to BaseStats(44, "Gloom", 43, 60, 65, 70, 85, 75, 40, listOf("Planta", "Veneno"), 45, 100),
        45 to BaseStats(45, "Vileplume", 43, 75, 80, 85, 110, 90, 50, listOf("Planta", "Veneno")),
        46 to BaseStats(46, "Paras", 46, 35, 70, 55, 45, 55, 25, listOf("Bicho", "Planta"), 47, 50),
        47 to BaseStats(47, "Parasect", 46, 60, 95, 80, 60, 80, 30, listOf("Bicho", "Planta")),
        48 to BaseStats(48, "Venonat", 48, 60, 55, 50, 40, 55, 45, listOf("Bicho", "Veneno"), 49, 50),
        49 to BaseStats(49, "Venomoth", 48, 70, 65, 60, 90, 75, 90, listOf("Bicho", "Veneno")),
        50 to BaseStats(50, "Diglett", 50, 10, 55, 25, 35, 45, 95, listOf("Tierra"), 51, 50),
        51 to BaseStats(51, "Dugtrio", 50, 35, 100, 50, 50, 70, 120, listOf("Tierra")),
        52 to BaseStats(52, "Meowth", 52, 40, 45, 35, 40, 40, 90, listOf("Normal"), 53, 50),
        53 to BaseStats(53, "Persian", 52, 65, 70, 60, 65, 65, 115, listOf("Normal")),
        54 to BaseStats(54, "Psyduck", 54, 50, 52, 48, 65, 50, 55, listOf("Agua"), 55, 50),
        55 to BaseStats(55, "Golduck", 54, 80, 82, 78, 95, 80, 85, listOf("Agua")),
        56 to BaseStats(56, "Mankey", 56, 40, 80, 35, 35, 45, 70, listOf("Lucha"), 57, 50),
        57 to BaseStats(57, "Primeape", 56, 65, 105, 60, 60, 70, 95, listOf("Lucha")),
        58 to BaseStats(58, "Growlithe", 58, 55, 70, 45, 70, 50, 60, listOf("Fuego"), 59, 50),
        59 to BaseStats(59, "Arcanine", 58, 90, 110, 80, 100, 80, 95, listOf("Fuego")),
        60 to BaseStats(60, "Poliwag", 60, 40, 50, 40, 40, 40, 90, listOf("Agua"), 61, 25),
        61 to BaseStats(61, "Poliwhirl", 60, 65, 65, 65, 50, 50, 90, listOf("Agua"), 62, 100),
        62 to BaseStats(62, "Poliwrath", 60, 90, 95, 95, 70, 90, 70, listOf("Agua", "Lucha")),
        63 to BaseStats(63, "Abra", 63, 25, 20, 15, 105, 55, 90, listOf("Psíquico"), 64, 25),
        64 to BaseStats(64, "Kadabra", 63, 40, 35, 30, 120, 70, 105, listOf("Psíquico"), 65, 100),
        65 to BaseStats(65, "Alakazam", 63, 55, 50, 45, 135, 95, 120, listOf("Psíquico")),
        66 to BaseStats(66, "Machop", 66, 70, 80, 50, 35, 35, 35, listOf("Lucha"), 67, 25),
        67 to BaseStats(67, "Machoke", 66, 80, 100, 70, 50, 60, 45, listOf("Lucha"), 68, 100),
        68 to BaseStats(68, "Machamp", 66, 90, 130, 80, 65, 85, 55, listOf("Lucha")),
        69 to BaseStats(69, "Bellsprout", 69, 50, 75, 35, 70, 30, 40, listOf("Planta", "Veneno"), 70, 25),
        70 to BaseStats(70, "Weepinbell", 69, 65, 90, 50, 85, 45, 55, listOf("Planta", "Veneno"), 71, 100),
        71 to BaseStats(71, "Victreebel", 69, 80, 105, 65, 100, 70, 70, listOf("Planta", "Veneno")),
        72 to BaseStats(72, "Tentacool", 72, 40, 40, 35, 50, 100, 70, listOf("Agua", "Veneno"), 73, 50),
        73 to BaseStats(73, "Tentacruel", 72, 80, 70, 65, 80, 120, 100, listOf("Agua", "Veneno")),
        74 to BaseStats(74, "Geodude", 74, 40, 80, 100, 30, 30, 20, listOf("Roca", "Tierra"), 75, 25),
        75 to BaseStats(75, "Graveler", 74, 55, 95, 115, 45, 45, 35, listOf("Roca", "Tierra"), 76, 100),
        76 to BaseStats(76, "Golem", 74, 80, 120, 130, 55, 65, 45, listOf("Roca", "Tierra")),
        77 to BaseStats(77, "Ponyta", 77, 50, 85, 55, 65, 65, 90, listOf("Fuego"), 78, 50),
        78 to BaseStats(78, "Rapidash", 77, 65, 100, 70, 80, 80, 105, listOf("Fuego")),
        79 to BaseStats(79, "Slowpoke", 79, 90, 65, 65, 40, 40, 15, listOf("Agua", "Psíquico"), 80, 50),
        80 to BaseStats(80, "Slowbro", 79, 95, 75, 110, 100, 80, 30, listOf("Agua", "Psíquico")),
        81 to BaseStats(81, "Magnemite", 81, 25, 35, 70, 95, 55, 45, listOf("Eléctrico", "Acero"), 82, 50),
        82 to BaseStats(82, "Magneton", 81, 50, 60, 95, 120, 70, 70, listOf("Eléctrico", "Acero")),
        83 to BaseStats(83, "Farfetch'd", 83, 52, 90, 55, 58, 62, 60, listOf("Normal", "Volador")),
        84 to BaseStats(84, "Doduo", 84, 35, 85, 45, 35, 35, 75, listOf("Normal", "Volador"), 85, 50),
        85 to BaseStats(85, "Dodrio", 84, 60, 110, 70, 60, 60, 110, listOf("Normal", "Volador")),
        86 to BaseStats(86, "Seel", 86, 65, 45, 55, 45, 70, 45, listOf("Agua"), 87, 50),
        87 to BaseStats(87, "Dewgong", 86, 90, 70, 80, 70, 95, 70, listOf("Agua", "Hielo")),
        88 to BaseStats(88, "Grimer", 88, 80, 80, 50, 40, 50, 25, listOf("Veneno"), 89, 50),
        89 to BaseStats(89, "Muk", 88, 105, 105, 75, 65, 100, 50, listOf("Veneno")),
        90 to BaseStats(90, "Shellder", 90, 30, 65, 100, 45, 25, 40, listOf("Agua"), 91, 50),
        91 to BaseStats(91, "Cloyster", 90, 50, 95, 180, 85, 45, 70, listOf("Agua", "Hielo")),
        92 to BaseStats(92, "Gastly", 92, 30, 35, 30, 100, 35, 80, listOf("Fantasma", "Veneno"), 93, 25),
        93 to BaseStats(93, "Haunter", 92, 45, 50, 45, 115, 55, 95, listOf("Fantasma", "Veneno"), 94, 100),
        94 to BaseStats(94, "Gengar", 92, 60, 65, 60, 130, 75, 110, listOf("Fantasma", "Veneno")),
        95 to BaseStats(95, "Onix", 95, 35, 45, 160, 30, 45, 70, listOf("Roca", "Tierra")),
        96 to BaseStats(96, "Drowzee", 96, 60, 48, 45, 43, 90, 42, listOf("Psíquico"), 97, 50),
        97 to BaseStats(97, "Hypno", 96, 85, 73, 70, 73, 115, 67, listOf("Psíquico")),
        98 to BaseStats(98, "Krabby", 98, 30, 105, 90, 25, 25, 50, listOf("Agua"), 99, 50),
        99 to BaseStats(99, "Kingler", 98, 55, 130, 115, 50, 50, 75, listOf("Agua")),
        100 to BaseStats(100, "Voltorb", 100, 40, 30, 50, 55, 55, 100, listOf("Eléctrico"), 101, 50),
        101 to BaseStats(101, "Electrode", 100, 60, 50, 70, 80, 80, 150, listOf("Eléctrico")),
        102 to BaseStats(102, "Exeggcute", 102, 60, 40, 80, 60, 45, 40, listOf("Planta", "Psíquico"), 103, 50),
        103 to BaseStats(103, "Exeggutor", 102, 95, 95, 85, 125, 75, 55, listOf("Planta", "Psíquico")),
        104 to BaseStats(104, "Cubone", 104, 50, 50, 95, 40, 50, 35, listOf("Tierra"), 105, 50),
        105 to BaseStats(105, "Marowak", 104, 60, 80, 110, 50, 80, 45, listOf("Tierra")),
        106 to BaseStats(106, "Hitmonlee", 106, 50, 120, 53, 35, 110, 87, listOf("Lucha")),
        107 to BaseStats(107, "Hitmonchan", 107, 50, 105, 79, 35, 110, 76, listOf("Lucha")),
        108 to BaseStats(108, "Lickitung", 108, 90, 55, 75, 60, 75, 30, listOf("Normal")),
        109 to BaseStats(109, "Koffing", 109, 40, 65, 95, 60, 45, 35, listOf("Veneno"), 110, 50),
        110 to BaseStats(110, "Weezing", 109, 65, 90, 120, 85, 70, 60, listOf("Veneno")),
        111 to BaseStats(111, "Rhyhorn", 111, 80, 85, 95, 30, 30, 25, listOf("Tierra", "Roca"), 112, 50),
        112 to BaseStats(112, "Rhydon", 111, 105, 130, 120, 45, 45, 40, listOf("Tierra", "Roca")),
        113 to BaseStats(113, "Chansey", 113, 250, 5, 5, 35, 105, 50, listOf("Normal")),
        114 to BaseStats(114, "Tangela", 114, 65, 55, 115, 100, 40, 60, listOf("Planta")),
        115 to BaseStats(115, "Kangaskhan", 115, 105, 95, 80, 40, 80, 90, listOf("Normal")),
        116 to BaseStats(116, "Horsea", 116, 30, 40, 70, 70, 25, 60, listOf("Agua"), 117, 50),
        117 to BaseStats(117, "Seadra", 116, 55, 65, 95, 95, 45, 85, listOf("Agua")),
        118 to BaseStats(118, "Goldeen", 118, 45, 67, 60, 35, 50, 63, listOf("Agua"), 119, 50),
        119 to BaseStats(119, "Seaking", 118, 80, 92, 65, 65, 80, 68, listOf("Agua")),
        120 to BaseStats(120, "Staryu", 120, 30, 45, 55, 70, 55, 85, listOf("Agua"), 121, 50),
        121 to BaseStats(121, "Starmie", 120, 60, 75, 85, 100, 85, 115, listOf("Agua", "Psíquico")),
        122 to BaseStats(122, "Mr. Mime", 122, 40, 45, 65, 100, 120, 90, listOf("Psíquico", "Hada")),
        123 to BaseStats(123, "Scyther", 123, 70, 110, 80, 55, 80, 105, listOf("Bicho", "Volador")),
        124 to BaseStats(124, "Jynx", 124, 65, 50, 35, 115, 95, 95, listOf("Hielo", "Psíquico")),
        125 to BaseStats(125, "Electabuzz", 125, 65, 83, 57, 95, 85, 105, listOf("Eléctrico")),
        126 to BaseStats(126, "Magmar", 126, 65, 95, 57, 100, 85, 93, listOf("Fuego")),
        127 to BaseStats(127, "Pinsir", 127, 65, 125, 100, 55, 70, 85, listOf("Bicho")),
        128 to BaseStats(128, "Tauros", 128, 75, 100, 95, 40, 70, 110, listOf("Normal")),
        129 to BaseStats(129, "Magikarp", 129, 20, 10, 55, 15, 20, 80, listOf("Agua"), 130, 400),
        130 to BaseStats(130, "Gyarados", 129, 95, 125, 79, 60, 100, 81, listOf("Agua", "Volador")),
        131 to BaseStats(131, "Lapras", 131, 130, 85, 80, 85, 95, 60, listOf("Agua", "Hielo")),
        132 to BaseStats(132, "Ditto", 132, 48, 48, 48, 48, 48, 48, listOf("Normal")),
        133 to BaseStats(133, "Eevee", 133, 55, 55, 50, 45, 65, 55, listOf("Normal"), 134, 25), // Simplificado: evoluciona a Vaporeon con 25
        134 to BaseStats(134, "Vaporeon", 133, 130, 65, 60, 110, 95, 65, listOf("Agua")),
        135 to BaseStats(135, "Jolteon", 133, 65, 65, 60, 110, 95, 130, listOf("Eléctrico")),
        136 to BaseStats(136, "Flareon", 133, 65, 130, 60, 95, 110, 65, listOf("Fuego")),
        137 to BaseStats(137, "Porygon", 137, 65, 60, 70, 85, 75, 40, listOf("Normal")),
        138 to BaseStats(138, "Omanyte", 138, 35, 40, 100, 90, 55, 35, listOf("Roca", "Agua"), 139, 50),
        139 to BaseStats(139, "Omastar", 138, 70, 60, 125, 115, 70, 55, listOf("Roca", "Agua")),
        140 to BaseStats(140, "Kabuto", 140, 30, 80, 90, 55, 45, 55, listOf("Roca", "Agua"), 141, 50),
        141 to BaseStats(141, "Kabutops", 140, 60, 115, 105, 65, 70, 80, listOf("Roca", "Agua")),
        142 to BaseStats(142, "Aerodactyl", 142, 80, 105, 65, 60, 75, 130, listOf("Roca", "Volador")),
        143 to BaseStats(143, "Snorlax", 143, 160, 110, 65, 65, 110, 30, listOf("Normal")),
        144 to BaseStats(144, "Articuno", 144, 90, 85, 100, 95, 125, 85, listOf("Hielo", "Volador")),
        145 to BaseStats(145, "Zapdos", 145, 90, 90, 85, 125, 90, 100, listOf("Eléctrico", "Volador")),
        146 to BaseStats(146, "Moltres", 146, 90, 100, 90, 125, 85, 90, listOf("Fuego", "Volador")),
        147 to BaseStats(147, "Dratini", 147, 41, 64, 45, 50, 50, 50, listOf("Dragón"), 148, 25),
        148 to BaseStats(148, "Dragonair", 147, 61, 84, 65, 70, 70, 70, listOf("Dragón"), 149, 100),
        149 to BaseStats(149, "Dragonite", 147, 91, 134, 95, 100, 100, 80, listOf("Dragón", "Volador")),
        150 to BaseStats(150, "Mewtwo", 150, 106, 110, 90, 154, 90, 130, listOf("Psíquico")),
        151 to BaseStats(151, "Mew", 151, 100, 100, 100, 100, 100, 100, listOf("Psíquico"))
    )

    fun getBaseStats(id: Int): BaseStats {
        return species[id] ?: BaseStats(id, "Desconocido", id, 50, 50, 50, 50, 50, 50, listOf("Desconocido"))
    }

    // Obtener nombre formateado para los GIFs animados de Gen 5 (Pixel Art)
    fun getPokemonNameForSprite(id: Int): String {
        val name = species[id]?.name?.lowercase() ?: "missingno"
        return when(id) {
            29 -> "nidoranf"
            32 -> "nidoranm"
            83 -> "farfetchd"
            122 -> "mr-mime"
            else -> name.replace(" ", "").replace("'", "").replace(".", "").replace("-", "")
        }
    }

    fun getBaseCaptureRate(speciesId: Int): Double {
        return when {
            listOf(144, 145, 146, 149, 150, 151).contains(speciesId) -> 0.10 // Legendarios
            listOf(131, 143, 137, 142, 113, 147, 123, 127, 128, 115).contains(speciesId) -> 0.20 // Ultra raros
            listOf(1, 4, 7, 25, 95, 124, 125, 126, 106, 107, 108).contains(speciesId) -> 0.35 // Raros
            listOf(35, 37, 39, 58, 63, 66, 74, 77, 81, 83, 84, 86, 90, 100, 102, 104, 111, 114, 116, 120, 138, 140).contains(speciesId) -> 0.50 // Poco frecuentes
            listOf(21, 23, 27, 29, 32, 43, 46, 48, 50, 52, 54, 56, 60, 69, 72, 79, 88, 92, 98, 109, 118, 129).contains(speciesId) -> 0.70 // Comunes
            else -> 0.90 // Muy comunes
        }
    }

    fun getPokemonSize(speciesId: Int): PokemonSize {
        val pequenos = listOf(10, 13, 16, 19, 21, 29, 32, 43, 46, 50, 60, 69, 81, 90, 100, 104, 120, 137, 138, 140, 147, 151)
        val grandes = listOf(3, 6, 9, 18, 31, 34, 38, 59, 65, 68, 71, 73, 89, 103, 106, 107, 112, 113, 115, 126, 127, 128, 131, 143, 144, 145, 146, 149, 150)
        val muyGrandes = listOf(95, 130)

        return when {
            pequenos.contains(speciesId) -> PokemonSize.PEQUENO
            grandes.contains(speciesId) -> PokemonSize.GRANDE
            muyGrandes.contains(speciesId) -> PokemonSize.MUY_GRANDE
            else -> PokemonSize.MEDIANO
        }
    }

    fun getPokemonScaleFactor(speciesId: Int): Float {
        return when (getPokemonSize(speciesId)) {
            PokemonSize.PEQUENO -> 0.55f
            PokemonSize.MEDIANO -> 0.9f
            PokemonSize.GRANDE -> 1.3f
            PokemonSize.MUY_GRANDE -> 1.7f
        }
    }

    fun calculateStat(base: Int, level: Int, iv: Int = 0): Int {
        return floor(((2 * base + iv) * level) / 100.0 + 5).toInt()
    }

    fun calculateHP(base: Int, level: Int, iv: Int = 0): Int {
        return floor(((2 * base + iv) * level) / 100.0 + level + 10).toInt()
    }

    fun getEffectiveness(moveType: String, defenderTypes: List<String>): Float {
        var effectiveness = 1.0f
        for (defType in defenderTypes) {
            effectiveness *= typeChart[moveType]?.get(defType) ?: 1.0f
        }
        return effectiveness
    }

    fun getRangerEffectDescription(speciesId: Int): String {
        val base = getBaseStats(speciesId)
        val type = base.types.firstOrNull() ?: "Normal"
        return when(type) {
            "Normal" -> "Aumenta ligeramente el daño de los círculos."
            "Fuego" -> "Aumenta significativamente el daño de los círculos."
            "Agua" -> "Ralentiza la frecuencia de los ataques del jefe."
            "Planta" -> "Regenera tu barra de resistencia gradualmente."
            "Eléctrico" -> "Paraliza al jefe, ralentizando su movimiento."
            "Hielo" -> "Congela al jefe, ralentizando ataques y movimiento."
            "Lucha" -> "Potencia masivamente el daño de los círculos."
            "Veneno" -> "El jefe recibe daño continuo automáticamente."
            "Tierra" -> "Reduce el daño recibido por los impactos."
            "Volador" -> "Los círculos son más fáciles de completar."
            "Psíquico" -> "Confunde al jefe, reduciendo su velocidad de ataque."
            "Bicho" -> "Cada círculo completado cuenta por dos."
            "Roca" -> "Crea un escudo que reduce el daño recibido."
            "Fantasma" -> "Inmunidad parcial: Reduce mucho el daño de impactos."
            "Dragón" -> "Incremento drástico en el daño de los círculos."
            "Siniestro" -> "Aturde al jefe, impidiendo que ataque temporalmente."
            "Acero" -> "Blindaje: Gran reducción de daño recibido."
            "Hada" -> "Restaura una parte de tu resistencia inmediatamente."
            else -> "Aumenta el daño de los círculos."
        }
    }

    private val typeChart = mapOf(
        "Normal" to mapOf("Roca" to 0.5f, "Fantasma" to 0f, "Acero" to 0.5f),
        "Fuego" to mapOf("Fuego" to 0.5f, "Agua" to 0.5f, "Planta" to 2f, "Hielo" to 2f, "Bicho" to 2f, "Roca" to 0.5f, "Dragón" to 0.5f, "Acero" to 2f),
        "Agua" to mapOf("Fuego" to 2f, "Agua" to 0.5f, "Planta" to 0.5f, "Tierra" to 2f, "Roca" to 2f, "Dragón" to 0.5f),
        "Planta" to mapOf("Fuego" to 0.5f, "Agua" to 2f, "Planta" to 0.5f, "Veneno" to 0.5f, "Tierra" to 2f, "Volador" to 0.5f, "Bicho" to 0.5f, "Roca" to 2f, "Dragón" to 0.5f, "Acero" to 0.5f),
        "Eléctrico" to mapOf("Agua" to 2f, "Planta" to 0.5f, "Eléctrico" to 0.5f, "Tierra" to 0f, "Volador" to 2f, "Dragón" to 0.5f),
        "Hielo" to mapOf("Fuego" to 0.5f, "Agua" to 0.5f, "Planta" to 2f, "Hielo" to 0.5f, "Tierra" to 2f, "Volador" to 2f, "Dragón" to 2f, "Acero" to 0.5f),
        "Lucha" to mapOf("Normal" to 2f, "Hielo" to 2f, "Veneno" to 0.5f, "Volador" to 0.5f, "Psíquico" to 0.5f, "Bicho" to 0.5f, "Roca" to 2f, "Fantasma" to 0f, "Siniestro" to 2f, "Acero" to 2f, "Hada" to 0.5f),
        "Veneno" to mapOf("Planta" to 2f, "Veneno" to 0.5f, "Tierra" to 0.5f, "Roca" to 0.5f, "Fantasma" to 0.5f, "Acero" to 0f, "Hada" to 2f),
        "Tierra" to mapOf("Fuego" to 2f, "Eléctrico" to 2f, "Planta" to 0.5f, "Veneno" to 2f, "Volador" to 0f, "Bicho" to 0.5f, "Roca" to 2f, "Acero" to 2f),
        "Volador" to mapOf("Planta" to 2f, "Eléctrico" to 0.5f, "Lucha" to 2f, "Bicho" to 2f, "Roca" to 0.5f, "Acero" to 0.5f),
        "Psíquico" to mapOf("Lucha" to 2f, "Veneno" to 2f, "Psíquico" to 0.5f, "Siniestro" to 0f, "Acero" to 0.5f),
        "Bicho" to mapOf("Fuego" to 0.5f, "Planta" to 2f, "Lucha" to 0.5f, "Veneno" to 0.5f, "Volador" to 0.5f, "Psíquico" to 2f, "Fantasma" to 0.5f, "Siniestro" to 2f, "Acero" to 0.5f, "Hada" to 0.5f),
        "Roca" to mapOf("Fuego" to 2f, "Hielo" to 2f, "Lucha" to 0.5f, "Tierra" to 0.5f, "Volador" to 2f, "Bicho" to 2f, "Acero" to 0.5f),
        "Fantasma" to mapOf("Normal" to 0f, "Psíquico" to 2f, "Fantasma" to 2f, "Siniestro" to 0.5f),
        "Dragón" to mapOf("Dragón" to 2f, "Acero" to 0.5f, "Hada" to 0f),
        "Siniestro" to mapOf("Lucha" to 0.5f, "Psíquico" to 2f, "Fantasma" to 2f, "Siniestro" to 0.5f, "Hada" to 0.5f),
        "Acero" to mapOf("Fuego" to 0.5f, "Agua" to 0.5f, "Eléctrico" to 0.5f, "Hielo" to 2f, "Roca" to 2f, "Acero" to 0.5f, "Hada" to 2f),
        "Hada" to mapOf("Fuego" to 0.5f, "Lucha" to 2f, "Veneno" to 0.5f, "Dragón" to 2f, "Siniestro" to 2f, "Acero" to 0.5f)
    )

    val allMoves = listOf(
        // Eléctrico
        Move("Rayo", "Eléctrico", MoveCategory.ESPECIAL, 90, 100, 15),
        Move("Trueno", "Eléctrico", MoveCategory.ESPECIAL, 120, 70, 10),
        Move("Onda Trueno", "Eléctrico", MoveCategory.ESTADO, 0, 100, 20),
        Move("Puño Trueno", "Eléctrico", MoveCategory.FISICO, 75, 100, 15),
        // Fuego
        Move("Lanzallamas", "Fuego", MoveCategory.ESPECIAL, 90, 100, 15),
        Move("Llamarada", "Fuego", MoveCategory.ESPECIAL, 120, 85, 5),
        Move("Giro Fuego", "Fuego", MoveCategory.ESPECIAL, 35, 85, 15),
        Move("Puño Fuego", "Fuego", MoveCategory.FISICO, 75, 100, 15),
        // Agua
        Move("Surf", "Agua", MoveCategory.ESPECIAL, 90, 100, 15),
        Move("Hidrobomba", "Agua", MoveCategory.ESPECIAL, 120, 80, 5),
        Move("Rayo Burbuja", "Agua", MoveCategory.ESPECIAL, 65, 100, 20),
        Move("Tenaza", "Agua", MoveCategory.FISICO, 35, 85, 15),
        Move("Martillazo", "Agua", MoveCategory.FISICO, 100, 90, 10),
        // Planta
        Move("Hoja Afilada", "Planta", MoveCategory.FISICO, 55, 95, 25),
        Move("Somnífero", "Planta", MoveCategory.ESTADO, 0, 75, 15),
        Move("Drenadoras", "Planta", MoveCategory.ESTADO, 0, 90, 10),
        Move("Rayo Solar", "Planta", MoveCategory.ESPECIAL, 120, 100, 10),
        Move("Megaagotar", "Planta", MoveCategory.ESPECIAL, 40, 100, 15),
        Move("Espora", "Planta", MoveCategory.ESTADO, 0, 100, 15),
        // Hielo
        Move("Ventisca", "Hielo", MoveCategory.ESPECIAL, 110, 70, 5),
        Move("Rayo Hielo", "Hielo", MoveCategory.ESPECIAL, 90, 100, 10),
        Move("Puño Hielo", "Hielo", MoveCategory.FISICO, 75, 100, 15),
        // Normal
        Move("Golpe Cuerpo", "Normal", MoveCategory.FISICO, 85, 100, 15),
        Move("Hiperrayo", "Normal", MoveCategory.FISICO, 150, 90, 5),
        Move("Danza Espada", "Normal", MoveCategory.ESTADO, 0, 100, 20),
        Move("Autodestrucción", "Normal", MoveCategory.FISICO, 200, 100, 5),
        Move("Explosión", "Normal", MoveCategory.FISICO, 250, 100, 5),
        Move("Recuperación", "Normal", MoveCategory.ESTADO, 0, 100, 20),
        Move("Descanso", "Psíquico", MoveCategory.ESTADO, 0, 100, 10),
        Move("Sustituto", "Normal", MoveCategory.ESTADO, 0, 100, 10),
        Move("Ataque Rápido", "Normal", MoveCategory.FISICO, 40, 100, 30),
        Move("Placaje", "Normal", MoveCategory.FISICO, 40, 100, 35),
        Move("Acuchillar", "Normal", MoveCategory.FISICO, 70, 100, 20),
        Move("Rapidez", "Normal", MoveCategory.ESPECIAL, 60, 1000, 20),
        // Tierra / Roca
        Move("Terremoto", "Tierra", MoveCategory.FISICO, 100, 100, 10),
        Move("Avalancha", "Roca", MoveCategory.FISICO, 75, 90, 10),
        Move("Lanzarrocas", "Roca", MoveCategory.FISICO, 50, 90, 15),
        // Psíquico / Fantasma
        Move("Psíquico", "Psíquico", MoveCategory.ESPECIAL, 90, 100, 10),
        Move("Amnesia", "Psíquico", MoveCategory.ESTADO, 0, 100, 20),
        Move("Tinieblas", "Fantasma", MoveCategory.ESPECIAL, 0, 100, 15),
        Move("Bola Sombra", "Fantasma", MoveCategory.ESPECIAL, 80, 100, 15),
        // Veneno
        Move("Bomba Lodo", "Veneno", MoveCategory.ESPECIAL, 90, 100, 10),
        Move("Tóxico", "Veneno", MoveCategory.ESTADO, 0, 90, 10),
        // Otros
        Move("Agilidad", "Psíquico", MoveCategory.ESTADO, 0, 100, 30),
        Move("Taladradora", "Tierra", MoveCategory.FISICO, 80, 95, 20),
        Move("Pico Taladro", "Volador", MoveCategory.FISICO, 80, 100, 20),
        Move("Ataque Ala", "Volador", MoveCategory.FISICO, 60, 100, 35),
        Move("Patada Salto Alta", "Lucha", MoveCategory.FISICO, 130, 90, 10),
        Move("Sumisión", "Lucha", MoveCategory.FISICO, 80, 80, 20),
        Move("Envoltura", "Normal", MoveCategory.FISICO, 15, 85, 20),
        Move("Mordisco", "Normal", MoveCategory.FISICO, 60, 100, 25),
        Move("Confusión", "Psíquico", MoveCategory.ESPECIAL, 50, 100, 25)
    )

    fun getMove(name: String): Move {
        return allMoves.find { it.name == name }?.copy() ?: Move(name, "Normal", MoveCategory.FISICO, 40, 100, 35)
    }

    fun getDefaultMoves(speciesId: Int): List<Move> {
        return when (speciesId) {
            // Venusaur, Victreebel, Vileplume
            1, 2, 3, 69, 70, 71, 43, 44, 45 -> listOf(getMove("Somnífero"), getMove("Hoja Afilada"), getMove("Golpe Cuerpo"), getMove("Hiperrayo"))
            // Charizard, Arcanine, Rapidash, Flareon
            4, 5, 6, 58, 59, 77, 78, 136 -> listOf(getMove("Llamarada"), getMove("Lanzallamas"), getMove("Terremoto"), getMove("Golpe Cuerpo"))
            // Blastoise, Lapras, Dewgong, Cloyster
            7, 8, 9, 131, 86, 87, 90, 91 -> listOf(getMove("Surf"), getMove("Ventisca"), getMove("Golpe Cuerpo"), getMove("Descanso"))
            // Butterfree, Venomoth
            10, 11, 12, 48, 49 -> listOf(getMove("Somnífero"), getMove("Psíquico"), getMove("Megaagotar"), getMove("Sustituto"))
            // Beedrill, Scyther, Pinsir
            13, 14, 15, 123, 127 -> listOf(getMove("Danza Espada"), getMove("Agilidad"), getMove("Golpe Cuerpo"), getMove("Hiperrayo"))
            // Pidgeot, Fearow, Dodrio, Aerodactyl
            16, 17, 18, 21, 22, 83, 84, 85, 142 -> listOf(getMove("Pico Taladro"), getMove("Ataque Ala"), getMove("Hiperrayo"), getMove("Agilidad"))
            // Raticate, Persian, Kangaskhan, Tauros, Snorlax
            19, 20, 52, 53, 115, 128, 143 -> listOf(getMove("Golpe Cuerpo"), getMove("Hiperrayo"), getMove("Terremoto"), getMove("Ventisca"))
            // Arbok, Muk, Weezing
            23, 24, 88, 89, 109, 110 -> listOf(getMove("Bomba Lodo"), getMove("Tóxico"), getMove("Terremoto"), getMove("Explosión"))
            // Pikachu, Raichu, Jolteon, Electabuzz
            25, 26, 135, 125 -> listOf(getMove("Rayo"), getMove("Onda Trueno"), getMove("Agilidad"), getMove("Onda Trueno"))
            // Sandslash, Marowak
            27, 28, 104, 105 -> listOf(getMove("Terremoto"), getMove("Avalancha"), getMove("Danza Espada"), getMove("Golpe Cuerpo"))
            // Nidoqueen, Nidoking, Rhydon, Golem
            29, 30, 31, 32, 33, 34, 111, 112, 74, 75, 76 -> listOf(getMove("Terremoto"), getMove("Avalancha"), getMove("Sustituto"), getMove("Golpe Cuerpo"))
            // Clefable, Wigglytuff, Chansey
            35, 36, 39, 40, 113 -> listOf(getMove("Ventisca"), getMove("Rayo"), getMove("Recuperación"), getMove("Onda Trueno"))
            // Ninetales, Magmar
            37, 38, 126 -> listOf(getMove("Llamarada"), getMove("Giro Fuego"), getMove("Ataque Rápido"), getMove("Confusión"))
            // Golbat
            41, 42 -> listOf(getMove("Ataque Ala"), getMove("Mordisco"), getMove("Hiperrayo"), getMove("Confusión"))
            // Parasect
            46, 47 -> listOf(getMove("Espora"), getMove("Hoja Afilada"), getMove("Golpe Cuerpo"), getMove("Hiperrayo"))
            // Dugtrio
            50, 51 -> listOf(getMove("Terremoto"), getMove("Avalancha"), getMove("Acuchillar"), getMove("Golpe Cuerpo"))
            // Golduck, Poliwrath, Vaporeon, Seadra
            54, 55, 60, 61, 62, 116, 117, 134 -> listOf(getMove("Surf"), getMove("Hidrobomba"), getMove("Ventisca"), getMove("Psíquico"))
            // Primeape, Machamp, Hitmonlee, Hitmonchan
            56, 57, 66, 67, 68, 106, 107 -> listOf(getMove("Sumisión"), getMove("Patada Salto Alta"), getMove("Terremoto"), getMove("Avalancha"))
            // Alakazam, Hypno, Mr. Mime, Jynx
            63, 64, 65, 96, 97, 122, 124 -> listOf(getMove("Psíquico"), getMove("Recuperación"), getMove("Onda Trueno"), getMove("Sustituto"))
            // Slowbro
            79, 80 -> listOf(getMove("Psíquico"), getMove("Surf"), getMove("Amnesia"), getMove("Descanso"))
            // Magneton
            81, 82 -> listOf(getMove("Rayo"), getMove("Onda Trueno"), getMove("Rapidez"), getMove("Sustituto"))
            // Gengar
            92, 93, 94 -> listOf(getMove("Rayo"), getMove("Psíquico"), getMove("Explosión"), getMove("Tinieblas"))
            // Onix
            95 -> listOf(getMove("Terremoto"), getMove("Avalancha"), getMove("Explosión"), getMove("Sustituto"))
            // Kingler
            98, 99 -> listOf(getMove("Martillazo"), getMove("Golpe Cuerpo"), getMove("Hiperrayo"), getMove("Surf"))
            // Electrode
            100, 101 -> listOf(getMove("Rayo"), getMove("Onda Trueno"), getMove("Explosión"), getMove("Rapidez"))
            // Exeggutor, Tangela
            102, 103, 114 -> listOf(getMove("Psíquico"), getMove("Somnífero"), getMove("Explosión"), getMove("Megaagotar"))
            // Lickitung
            108 -> listOf(getMove("Golpe Cuerpo"), getMove("Terremoto"), getMove("Ventisca"), getMove("Hiperrayo"))
            // Seaking
            118, 119 -> listOf(getMove("Cascada"), getMove("Agilidad"), getMove("Golpe Cuerpo"), getMove("Rayo Hielo"))
            // Starmie
            120, 121 -> listOf(getMove("Surf"), getMove("Rayo"), getMove("Recuperación"), getMove("Onda Trueno"))
            // Gyarados
            129, 130 -> listOf(getMove("Hidrobomba"), getMove("Ventisca"), getMove("Rayo"), getMove("Hiperrayo"))
            // Porygon
            137 -> listOf(getMove("Rayo"), getMove("Ventisca"), getMove("Recuperación"), getMove("Psíquico"))
            // Omastar, Kabutops
            138, 139, 140, 141 -> listOf(getMove("Surf"), getMove("Ventisca"), getMove("Golpe Cuerpo"), getMove("Hidrobomba"))
            // Articuno
            144 -> listOf(getMove("Ventisca"), getMove("Agilidad"), getMove("Ataque Ala"), getMove("Hiperrayo"))
            // Zapdos
            145 -> listOf(getMove("Rayo"), getMove("Pico Taladro"), getMove("Onda Trueno"), getMove("Agilidad"))
            // Moltres
            146 -> listOf(getMove("Llamarada"), getMove("Ataque Ala"), getMove("Giro Fuego"), getMove("Agilidad"))
            // Dragonite
            147, 148, 149 -> listOf(getMove("Envoltura"), getMove("Giro Fuego"), getMove("Hiperrayo"), getMove("Surf"))
            // Mewtwo
            150 -> listOf(getMove("Amnesia"), getMove("Psíquico"), getMove("Recuperación"), getMove("Rayo Hielo"))
            // Mew
            151 -> listOf(getMove("Psíquico"), getMove("Hiperrayo"), getMove("Terremoto"), getMove("Autodestrucción"))
            else -> listOf(getMove("Placaje"), getMove("Ataque Rápido"))
        }
    }
}

enum class MoveCategory {
    FISICO, ESPECIAL, ESTADO
}

data class Move(
    val name: String,
    val type: String,
    val category: MoveCategory,
    val power: Int,
    val accuracy: Int,
    val pp: Int,
    var currentPp: Int = pp
)
