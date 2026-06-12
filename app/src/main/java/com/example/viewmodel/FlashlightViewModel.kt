package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FlashlightDatabase
import com.example.data.FlashlightPattern
import com.example.data.FlashlightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FlashlightViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FlashlightDatabase.getDatabase(application, viewModelScope)
    private val repository = FlashlightRepository(database.patternDao())

    // UI state for saved patterns
    val savedPatterns: StateFlow<List<FlashlightPattern>> = MutableStateFlow<List<FlashlightPattern>>(emptyList()).let { flow ->
        viewModelScope.launch {
            repository.allPatterns.distinctUntilChanged().collect {
                (flow as MutableStateFlow).value = it
            }
        }
        flow.asStateFlow()
    }

    // Camera control states
    private var cameraManager: CameraManager? = null
    private var backCameraId: String? = null
    private var isTorchAvailable = false
    private var maxPhysicalBrightnessLevel = 1

    // Core states
    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _physicalBrightness = MutableStateFlow(1.0f) // 0.1 to 1.0
    val physicalBrightness: StateFlow<Float> = _physicalBrightness.asStateFlow()

    private val _isPhysicalBrightnessSupported = MutableStateFlow(false)
    val isPhysicalBrightnessSupported: StateFlow<Boolean> = _isPhysicalBrightnessSupported.asStateFlow()

    // Mode control ("NORMAL", "STROBE", "SOS", "CUSTOM")
    private val _activeMode = MutableStateFlow("NORMAL")
    val activeMode: StateFlow<String> = _activeMode.asStateFlow()

    // Frequency in Hz for Strobe mode (1Hz to 15Hz)
    private val _strobeFrequency = MutableStateFlow(5f)
    val strobeFrequency: StateFlow<Float> = _strobeFrequency.asStateFlow()

    // Screen light fallback mod states
    private val _screenLightMode = MutableStateFlow(false)
    val screenLightMode: StateFlow<Boolean> = _screenLightMode.asStateFlow()

    private val _screenBrightness = MutableStateFlow(1.0f) // user slider
    val screenBrightness: StateFlow<Float> = _screenBrightness.asStateFlow()

    private val _screenColor = MutableStateFlow(Color.White)
    val screenColor: StateFlow<Color> = _screenColor.asStateFlow()

    // Battery saver mode
    private val _isBatterySaverEnabled = MutableStateFlow(false)
    val isBatterySaverEnabled: StateFlow<Boolean> = _isBatterySaverEnabled.asStateFlow()

    // Active pattern running
    private val _selectedPattern = MutableStateFlow<FlashlightPattern?>(null)
    val selectedPattern: StateFlow<FlashlightPattern?> = _selectedPattern.asStateFlow()

    // Status message for error handling or helpful logs
    private val _statusBarMessage = MutableStateFlow("Device Ready")
    val statusBarMessage: StateFlow<String> = _statusBarMessage.asStateFlow()

    // Custom pattern recording state
    private val _isRecordingPattern = MutableStateFlow(false)
    val isRecordingPattern: StateFlow<Boolean> = _isRecordingPattern.asStateFlow()
    
    private val recordingTimeline = mutableListOf<Long>()
    private var lastRecordedActionTime: Long = 0L

    // Strobe job
    private var torchLoopJob: Job? = null

    init {
        initCameraStack()
    }

    private fun initCameraStack() {
        try {
            cameraManager = getApplication<Application>().getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val manager = cameraManager
            if (manager == null) {
                _statusBarMessage.value = "Camera hardware not accessible. Using Screen-Light fallback."
                isTorchAvailable = false
                return
            }

            val idList = manager.cameraIdList
            if (idList.isEmpty()) {
                _statusBarMessage.value = "No camera unit found."
                isTorchAvailable = false
                return
            }

            // Find camera with a flash unit on the back
            for (id in idList) {
                val characteristics = manager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraId = id
                    isTorchAvailable = true

                    // Check for modern physical flash brightness adjustment (API level 33+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                            if (maxLevel > 1) {
                                maxPhysicalBrightnessLevel = maxLevel
                                _isPhysicalBrightnessSupported.value = true
                                Log.d("FlashViewModel", "Physical flashlight brightness supported (Max level: $maxLevel)")
                            }
                        } catch (e: Exception) {
                            Log.e("FlashViewModel", "Error checking level supports", e)
                        }
                    }
                    break
                }
            }

            if (backCameraId == null) {
                // Try first camera with flash regardless of facing
                for (id in idList) {
                    val characteristics = manager.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    if (hasFlash) {
                        backCameraId = id
                        isTorchAvailable = true
                        break
                    }
                }
            }

            if (backCameraId == null) {
                _statusBarMessage.value = "Hardware flash missing. Using Screen-light fallbacks."
                isTorchAvailable = false
            } else {
                _statusBarMessage.value = "Modded Flashlight Engine active"
            }

        } catch (e: Exception) {
            _statusBarMessage.value = "Error initialization: ${e.localizedMessage}"
            Log.e("FlashViewModel", "Error setting up camera services", e)
            isTorchAvailable = false
        }
    }

    fun toggleFlashlight() {
        if (_isFlashlightOn.value) {
            turnOff()
        } else {
            turnOn()
        }
    }

    fun setMode(mode: String) {
        if (_activeMode.value == mode && _isFlashlightOn.value) return
        _activeMode.value = mode
        if (_isFlashlightOn.value) {
            restartEngineState()
        }
    }

    fun selectPattern(pattern: FlashlightPattern?) {
        _selectedPattern.value = pattern
        if (pattern != null) {
            _activeMode.value = "CUSTOM"
            if (_isFlashlightOn.value) {
                restartEngineState()
            }
        }
    }

    fun setStrobeFrequency(freq: Float) {
        _strobeFrequency.value = freq
        if (_isFlashlightOn.value && _activeMode.value == "STROBE") {
            restartEngineState()
        }
    }

    fun setPhysicalBrightness(level: Float) {
        _physicalBrightness.value = level.coerceIn(0.1f, 1.0f)
        if (_isFlashlightOn.value && _activeMode.value == "NORMAL") {
            // Apply immediately
            applyTorchHardware(_isFlashlightOn.value)
        }
    }

    fun toggleScreenLightMode() {
        _screenLightMode.value = !_screenLightMode.value
        // Ensure flash updates to avoid double illumination
        applyEngineToggle()
    }

    fun setScreenBrightness(brightness: Float) {
        _screenBrightness.value = brightness.coerceIn(0.01f, 1.0f)
    }

    fun setScreenColor(color: Color) {
        _screenColor.value = color
    }

    fun toggleBatterySaver() {
        val next = !_isBatterySaverEnabled.value
        _isBatterySaverEnabled.value = next
        if (next) {
            // Under battery saving mode we default screen state to very dark and lock screen orientation
            _screenBrightness.value = 0.05f
            _statusBarMessage.value = "Battery Saver active - Screen dimmed"
        } else {
            _screenBrightness.value = 1.0f
            _statusBarMessage.value = "Standard mode restored"
        }
    }

    // Custom pattern recording interactions
    fun startRecordingPattern() {
        recordingTimeline.clear()
        _isRecordingPattern.value = true
        lastRecordedActionTime = System.currentTimeMillis()
        _statusBarMessage.value = "Recording starts... Tap the pad!"
    }

    fun handleRecordTapDown() {
        if (!_isRecordingPattern.value) return
        val now = System.currentTimeMillis()
        val durationOff = now - lastRecordedActionTime
        if (recordingTimeline.isNotEmpty() || durationOff > 50) {
            // Record the off-duration
            recordingTimeline.add(durationOff)
        }
        lastRecordedActionTime = now
        // Simulate physical/visual cue on tap
        setTorchStateDirect(true)
    }

    fun handleRecordTapUp() {
        if (!_isRecordingPattern.value) return
        val now = System.currentTimeMillis()
        val durationOn = now - lastRecordedActionTime
        if (durationOn > 10) {
            // Record the on-duration
            recordingTimeline.add(durationOn)
        }
        lastRecordedActionTime = now
        setTorchStateDirect(false)
    }

    fun stopAndSaveRecording(name: String, desc: String) {
        if (!_isRecordingPattern.value) return
        _isRecordingPattern.value = false
        setTorchStateDirect(false)

        if (recordingTimeline.isEmpty()) {
            _statusBarMessage.value = "Recording empty, no pattern was captured."
            return
        }

        // Format pattern as string: "on_ms,off_ms,on_ms..."
        val patternStr = recordingTimeline.joinToString(",")
        
        viewModelScope.launch(Dispatchers.IO) {
            val newPattern = FlashlightPattern(
                name = name.ifEmpty { "My Preset ${System.currentTimeMillis() % 1000}" },
                patternString = patternStr,
                isPreset = false,
                description = desc.ifEmpty { "User-recorded custom rhythm sequence" }
            )
            repository.insertPattern(newPattern)
            _statusBarMessage.value = "Custom pattern saved!"
        }
    }

    fun deletePattern(pattern: FlashlightPattern) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePattern(pattern)
            if (_selectedPattern.value?.id == pattern.id) {
                _selectedPattern.value = null
                _activeMode.value = "NORMAL"
            }
            _statusBarMessage.value = "Pattern removed"
        }
    }

    fun turnOn() {
        _isFlashlightOn.value = true
        _statusBarMessage.value = "${_activeMode.value} mode activated"
        applyEngineToggle()
    }

    fun turnOff() {
        _isFlashlightOn.value = false
        _statusBarMessage.value = "Flashlight deactivated"
        applyEngineToggle()
    }

    private fun restartEngineState() {
        // Clear loops and restart
        cancelStrobeJob()
        if (_isFlashlightOn.value) {
            applyEngineToggle()
        }
    }

    private fun cancelStrobeJob() {
        torchLoopJob?.cancel()
        torchLoopJob = null
    }

    private fun applyEngineToggle() {
        cancelStrobeJob()

        if (!_isFlashlightOn.value) {
            setTorchStateDirect(false)
            return
        }

        // Run based on selected mode
        when (_activeMode.value) {
            "NORMAL" -> {
                setTorchStateDirect(true)
            }
            "STROBE" -> {
                // Calculate delay based on frequency (Hz)
                // If frequency = 5Hz, full cycle (ON + OFF) = 200ms, each phase = 100ms
                val phaseDelay = (1000f / (_strobeFrequency.value * 2f)).toLong().coerceIn(15, 1000)
                runStrobePattern(listOf(phaseDelay, phaseDelay))
            }
            "SOS" -> {
                // 3 Short (200ms ON / 200ms OFF)
                // 3 Long (600ms ON / 200ms OFF)
                // Loop break = 1200ms OFF
                val sosList = listOf(
                    200L, 200L, 200L, 200L, 200L, 400L, // S
                    600L, 200L, 600L, 200L, 600L, 400L, // O
                    200L, 200L, 200L, 200L, 200L, 1200L // S
                )
                runStrobePattern(sosList)
            }
            "CUSTOM" -> {
                val pattern = _selectedPattern.value
                if (pattern != null) {
                    val intervals = pattern.patternString.split(",").mapNotNull { it.trim().toLongOrNull() }
                    if (intervals.isNotEmpty()) {
                        runStrobePattern(intervals)
                    } else {
                        setTorchStateDirect(true)
                    }
                } else {
                    setTorchStateDirect(true)
                }
            }
        }
    }

    private fun runStrobePattern(intervals: List<Long>) {
        torchLoopJob = viewModelScope.launch(Dispatchers.Default) {
            var index = 0
            while (isActive) {
                val duration = intervals[index % intervals.size]
                val mustBeOn = (index % 2 == 0)

                setTorchStateDirect(mustBeOn)
                delay(duration)
                index++
            }
        }
    }

    // Direct helper to toggle physical or screen state
    private fun setTorchStateDirect(isOn: Boolean) {
        if (_screenLightMode.value) {
            // Screen Light Mode simply controls state that Compose UI watches
            // But we must make sure physical LED is turned off
            applyLiveHardware(false)
        } else {
            // Physical Flashlight Mode
            applyLiveHardware(isOn)
        }
    }

    private fun applyLiveHardware(isOn: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            applyTorchHardware(isOn)
        }
    }

    private fun applyTorchHardware(isOn: Boolean) {
        if (!isTorchAvailable || cameraManager == null || backCameraId == null) return
        try {
            val isEnabled = isOn
            if (isEnabled) {
                // Apply strength level mapping if supported and in normal steady mode
                if (_isPhysicalBrightnessSupported.value && _activeMode.value == "NORMAL") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            // physical brightness slider goes from 0.1 to 1.0f
                            val rawLevel = (_physicalBrightness.value * maxPhysicalBrightnessLevel).toInt().coerceIn(1, maxPhysicalBrightnessLevel)
                            cameraManager?.turnOnTorchWithStrengthLevel(backCameraId!!, rawLevel)
                            return
                        } catch (e: Exception) {
                            Log.e("FlashViewModel", "turnOnTorchWithStrengthLevel failed, falling back to setTorchMode", e)
                        }
                    }
                }
                // Default steady/pulsed fallback code
                cameraManager?.setTorchMode(backCameraId!!, true)
            } else {
                cameraManager?.setTorchMode(backCameraId!!, false)
            }
        } catch (e: Exception) {
            Log.e("FlashViewModel", "Error adjusting flashlight state", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelStrobeJob()
        // Ensure physical torch is extinguished when ViewModel is closed
        if (isTorchAvailable && cameraManager != null && backCameraId != null) {
            try {
                cameraManager?.setTorchMode(backCameraId!!, false)
            } catch (e: Exception) {
                Log.e("FlashViewModel", "Error clearing torch hardware", e)
            }
        }
    }
}
