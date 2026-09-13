package com.remotecamera.camera

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class CameraApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CameraApp", "Fatal crash in thread ${thread.name}", throwable)
            try {
                CrashActivity.launch(this, throwable.stackTraceToString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("CameraApp", "FirebaseApp init failed: ${e.message}", e)
        }
    }
}
