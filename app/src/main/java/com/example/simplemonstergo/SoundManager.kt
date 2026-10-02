package com.example.simplemonstergo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

object SoundManager {
    private var musicPlayer: MediaPlayer? = null
    private var currentResId: Int = -1
    
    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<Int, Int>()

    private const val PREFS_SOUND = "SoundSettings"
    private const val KEY_MUSIC_ON = "music_on"
    private const val KEY_SFX_ON = "sfx_on"

    private fun isMusicEnabled(context: Context): Boolean = 
        context.getSharedPreferences(PREFS_SOUND, Context.MODE_PRIVATE).getBoolean(KEY_MUSIC_ON, true)

    private fun isSfxEnabled(context: Context): Boolean = 
        context.getSharedPreferences(PREFS_SOUND, Context.MODE_PRIVATE).getBoolean(KEY_SFX_ON, true)

    fun setMusicEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_SOUND, Context.MODE_PRIVATE).edit().putBoolean(KEY_MUSIC_ON, enabled).apply()
        if (!enabled) stopMusic()
    }

    fun setSfxEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_SOUND, Context.MODE_PRIVATE).edit().putBoolean(KEY_SFX_ON, enabled).apply()
    }

    fun playMusic(context: Context, resId: Int, loop: Boolean = true) {
        if (!isMusicEnabled(context)) return
        if (currentResId == resId && musicPlayer?.isPlaying == true) return 

        try {
            stopMusic()
            musicPlayer = MediaPlayer.create(context.applicationContext, resId).apply {
                isLooping = loop
                start()
            }
            currentResId = resId
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopMusic() {
        musicPlayer?.stop()
        musicPlayer?.release()
        musicPlayer = null
        currentResId = -1
    }

    fun playSound(context: Context, resId: Int) {
        if (!isSfxEnabled(context)) return
        
        if (soundPool == null) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(attributes)
                .build()
        }

        val soundId = soundMap[resId] ?: run {
            val id = soundPool!!.load(context.applicationContext, resId, 1)
            soundMap[resId] = id
            id
        }
        
        soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
    }
    
    fun playClick(context: Context) {
        playSound(context, R.raw.clicksonido)
    }

    fun release() {
        stopMusic()
        soundPool?.release()
        soundPool = null
        soundMap.clear()
    }
}
