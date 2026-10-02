package com.example.simplemonstergo

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val progressBar = findViewById<ProgressBar>(R.id.progressBarSplash)
        val handler = Handler(Looper.getMainLooper())
        
        var progressStatus = 0
        
        Thread {
            while (progressStatus < 100) {
                progressStatus += 1
                try {
                    Thread.sleep(30) // 30ms * 100 = 3 segundos aprox
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                handler.post {
                    progressBar.progress = progressStatus
                }
            }
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }.start()
    }
}
