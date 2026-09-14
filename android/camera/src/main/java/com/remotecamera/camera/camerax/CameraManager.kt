package com.remotecamera.camera.camerax

import android.content.Context
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed class CameraState {
    object Idle : CameraState()
    object Initializing : CameraState()
    object Capturing : CameraState()
    data class Error(val message: String) : CameraState()
}

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Idle)
    val cameraState: StateFlow<CameraState> = _cameraState

    private var lensFacing = CameraSelector.LENS_FACING_BACK

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        targetResolution: Size = Size(1280, 720),
        onSurfacePrepared: ((SurfaceRequest) -> Unit)? = null
    ) {
        _cameraState.value = CameraState.Initializing
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                val preview = Preview.Builder()
                    .setTargetResolution(targetResolution)
                    .build()

                if (onSurfacePrepared != null) {
                    preview.setSurfaceProvider { surfaceRequest ->
                        onSurfacePrepared(surfaceRequest)
                    }
                }

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )

                _cameraState.value = CameraState.Capturing
            } catch (e: Exception) {
                _cameraState.value = CameraState.Error("Failed to initialize CameraX: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, onSurfacePrepared: ((SurfaceRequest) -> Unit)? = null) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera(lifecycleOwner, onSurfacePrepared = onSurfacePrepared)
    }

    fun setTorchEnabled(enabled: Boolean) {
        // Method 1: CameraX enableTorch (Primary if CameraX is active)
        if (camera != null) {
            try {
                camera?.cameraControl?.enableTorch(enabled)
                com.remotecamera.camera.debug.DebugLogger.log(
                    "CameraManager",
                    "💡 CameraX enableTorch($enabled) executed on active camera!",
                    com.remotecamera.camera.debug.LogLevel.SUCCESS
                )
                return
            } catch (e: Exception) {
                com.remotecamera.camera.debug.DebugLogger.log(
                    "CameraManager",
                    "CameraX enableTorch error: ${e.message}, falling back to System setTorchMode",
                    com.remotecamera.camera.debug.LogLevel.WARNING
                )
            }
        }

        // Method 2: System Camera2 setTorchMode Fallback
        try {
            val sysCameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            val cameraId = sysCameraManager?.cameraIdList?.firstOrNull { id ->
                val characteristics = sysCameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK && hasFlash
            } ?: sysCameraManager?.cameraIdList?.firstOrNull()

            if (sysCameraManager != null && cameraId != null) {
                sysCameraManager.setTorchMode(cameraId, enabled)
                com.remotecamera.camera.debug.DebugLogger.log(
                    "CameraManager",
                    "💡 System setTorchMode($enabled) executed on camera: $cameraId",
                    com.remotecamera.camera.debug.LogLevel.SUCCESS
                )
            }
        } catch (e: Exception) {
            com.remotecamera.camera.debug.DebugLogger.log(
                "CameraManager",
                "System setTorchMode failed: ${e.message}",
                com.remotecamera.camera.debug.LogLevel.ERROR
            )
        }
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            _cameraState.value = CameraState.Idle
        } catch (e: Exception) {
            _cameraState.value = CameraState.Error("Failed to stop camera: ${e.message}")
        }
    }

    fun shutdown() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
