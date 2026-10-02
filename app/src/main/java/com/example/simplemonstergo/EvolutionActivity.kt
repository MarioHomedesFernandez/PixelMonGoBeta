package com.example.simplemonstergo

import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class EvolutionActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        // No tocar música aquí, se maneja en la secuencia
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_evolution)

        val oldId = intent.getIntExtra("OLD_ID", 1)
        val newId = intent.getIntExtra("NEW_ID", 2)
        
        // Empezar música de evolución
        SoundManager.playMusic(this, R.raw.musicaevolucion, loop = true)

        val ivPokemon = findViewById<ImageView>(R.id.ivEvolutionPokemon)
        val tvStatus = findViewById<TextView>(R.id.tvEvolutionStatus)
        val tvResult = findViewById<TextView>(R.id.tvEvolutionResult)
        val btnDone = findViewById<Button>(R.id.btnEvolutionDone)
        val viewFlash = findViewById<View>(R.id.viewFlash)

        val nameOld = PokemonData.getPokemonNameForSprite(oldId)
        val nameNew = PokemonData.getPokemonNameForSprite(newId)
        val oldUrl = "https://play.pokemonshowdown.com/sprites/gen5ani/$nameOld.gif"
        val newUrl = "https://play.pokemonshowdown.com/sprites/gen5ani/$nameNew.gif"
        
        Glide.with(this).load(oldUrl).into(ivPokemon)

        // 1. Animación de pulsación (preparación) - Aumentada duración
        val pulse = ScaleAnimation(
            1f, 1.2f, 1f, 1.2f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 600
            repeatCount = 5 // Más repeticiones para generar tensión
            repeatMode = Animation.REVERSE
        }

        ivPokemon.startAnimation(pulse)

        // 2. Secuencia de evolución tras la pulsación
        pulse.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                startEvolutionSequence(ivPokemon, viewFlash, tvStatus, tvResult, btnDone, newUrl, newId)
            }
        })

        btnDone.setOnClickListener {
            SoundManager.playClick(this)
            finish()
        }
    }

    private fun startEvolutionSequence(
        ivPokemon: ImageView,
        viewFlash: View,
        tvStatus: TextView,
        tvResult: TextView,
        btnDone: Button,
        newUrl: String,
        newId: Int
    ) {
        // Animación de Flash Blanco - Más lenta
        val flashIn = AlphaAnimation(0f, 1f).apply {
            duration = 2000
            fillAfter = true
        }

        flashIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                // Cambiar el Pokémon mientras está el flash
                Glide.with(this@EvolutionActivity).load(newUrl).into(ivPokemon)
                
                val flashOut = AlphaAnimation(1f, 0f).apply {
                    duration = 2000
                    startOffset = 1000
                    fillAfter = true
                }
                
                flashOut.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationRepeat(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        val newBase = PokemonData.getBaseStats(newId)
                        tvStatus.text = "¡Tu Pokémon ha evolucionado!"
                        tvResult.text = "¡Felicidades! ¡Ahora tienes un ${newBase.name}!"
                        tvResult.visibility = View.VISIBLE
                        btnDone.visibility = View.VISIBLE
                        
                        // Sonido de evolución completada
                        SoundManager.stopMusic()
                        SoundManager.playMusic(this@EvolutionActivity, R.raw.pokemonevolucionconseguida, loop = false)
                    }
                })
                viewFlash.startAnimation(flashOut)
            }
        })

        viewFlash.startAnimation(flashIn)
    }
}
