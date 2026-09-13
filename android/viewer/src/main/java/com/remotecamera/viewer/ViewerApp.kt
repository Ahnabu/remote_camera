package com.remotecamera.viewer

import android.app.Application
import com.google.firebase.FirebaseApp

class ViewerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
