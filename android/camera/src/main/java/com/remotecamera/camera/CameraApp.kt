package com.remotecamera.camera

import android.app.Application
import com.google.firebase.FirebaseApp

class CameraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
