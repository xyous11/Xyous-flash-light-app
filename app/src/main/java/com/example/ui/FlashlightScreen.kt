package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FlashlightPattern
import com.example.ui.theme.*
import com.example.viewmodel.FlashlightViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FlashlightScreen(
    viewModel: FlashlightViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Core states
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsStateWithLifecycle()
    val activeMode by viewModel.activeMode.collectAsStateWithLifecycle()
    val strobeFrequency by viewModel.strobeFrequency.collectAsStateWithLifecycle()
    val physicalBrightness by viewModel.physicalBrightness.collectAsStateWithLifecycle()
    val isPhysicalBrightnessSupported by viewModel.isPhysicalBrightnessSupported.collectAsStateWithLifecycle()
    
    // Fallback/Lantern states
    val screenLightMode by viewModel.screenLightMode.collectAsStateWithLifecycle()
    val screenBrightness by viewModel.screenBrightness.collectAsStateWithLifecycle()
    val screenColor by viewModel.screenColor.collectAsStateWithLifecycle()
    
    // Battery save State
    val isBatterySaverEnabled by viewModel.isBatterySaverEnabled.collectAsStateWithLifecycle()
    val statusBarMessage by viewModel.statusBarMessage.collectAsStateWithLifecycle()
    
    val savedPatterns by viewModel.savedPatterns.collectAsStateWithLifecycle()
    val selectedPattern by viewModel.selectedPattern.collectAsStateWithLifecycle()
    val isRecordingPattern by viewModel.isRecordingPattern.collectAsStateWithLifecycle()

    // Side-effect: Adjust system window backlight levels on physical screen brightness changes
    LaunchedEffect(screenBrightness, screenLightMode) {
        val targetBrightness = if (screenLightMode) screenBrightness else {
            if (isBatterySaverEnabled) 0.05f else -1f // -1f restores normal auto system brightness
        }
        context.findActivity()?.let { activity ->
            val lp = activity.window.attributes
            lp.screenBrightness = targetBrightness
            activity.window.attributes = lp
        }
    }

    // Single-view layout structure with dynamic colors based on active modes
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (screenLightMode && isFlashlightOn) {
                    screenColor.copy(alpha = screenBrightness)
                } else if (isBatterySaverEnabled) {
                    DeepBlack
                } else {
                    SlateBack
                }
            )
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Main Screen Interface container
        if (screenLightMode && isFlashlightOn) {
            // "Screen Light Lantern" Overlay: Screen acts as a pure color wash
            // Tap anywhere to turn off, providing intuitive quick-off function
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.turnOff()
                    }
                    .testTag("lantern_clickable_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Text(
                        text = "SCREEN LANTERN ACTIVE",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap anywhere to extinguish",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    IconButton(
                        onClick = { viewModel.toggleScreenLightMode() },
                        modifier = Modifier
                            .background(CardBack, CircleShape)
                            .border(1.dp, ElectricCyan, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Switch to LED",
                            tint = ElectricCyan
                        )
                    }
                }
            }
        } else {
            // Main Control Dashboard (LED controller or Screen Configurator)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top status bar (Header) matching Lumina Pro guidelines
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Lumina",
                            color = GeoTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = if (isBatterySaverEnabled) "BATTERY SAVER ACTIVE" else "LIGHTWEIGHT PRO",
                            color = if (isBatterySaverEnabled) GeoSosRed else GeoLuminaBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.alpha(0.7f)
                        )
                    }

                    // Battery saving toggle / pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(GeoCard)
                            .border(1.dp, GeoBorder, RoundedCornerShape(100.dp))
                            .clickable { viewModel.toggleBatterySaver() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("battery_saver_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Battery Saving Mod",
                            tint = if (isBatterySaverEnabled) GeoSosRed else GeoLuminaBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isBatterySaverEnabled) "84%" else "100%",
                            color = GeoTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Central Power Hub Section
                Spacer(modifier = Modifier.weight(0.1f))

                PowerConsoleButton(
                    isOn = isFlashlightOn,
                    activeMode = activeMode,
                    strobeFreq = strobeFrequency,
                    batterySaver = isBatterySaverEnabled,
                    onClick = { viewModel.toggleFlashlight() }
                )

                // Render dynamic details depending on battery saver layout constraints
                if (!isBatterySaverEnabled) {
                    Spacer(modifier = Modifier.weight(0.14f))

                    // Mode Selection via "Geometric Balance" Functional Modes Grid
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("mode_tabs")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Mode Steady/NORMAL
                            ModeGridItem(
                                title = "Steady",
                                subtitle = if (activeMode == "NORMAL") "ACTIVE" else "STEADY",
                                icon = Icons.Default.Star,
                                isSelected = activeMode == "NORMAL",
                                activeBg = GeoLuminaBlue,
                                activeText = GeoOnLuminaBlue,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setMode("NORMAL") }
                            )

                            // Mode STROBE
                            ModeGridItem(
                                title = "Strobe",
                                subtitle = if (activeMode == "STROBE") "${"%.1f".format(strobeFrequency)} HZ" else "PULSAR",
                                icon = Icons.Default.Refresh,
                                isSelected = activeMode == "STROBE",
                                activeBg = GeoLuminaBlue,
                                activeText = GeoOnLuminaBlue,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setMode("STROBE") }
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Mode SOS
                            ModeGridItem(
                                title = "SOS",
                                subtitle = if (activeMode == "SOS") "ACTIVE" else "SIGNAL",
                                icon = Icons.Default.Warning,
                                isSelected = activeMode == "SOS",
                                activeBg = GeoSosRed,
                                activeText = GeoOnSosRed,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setMode("SOS") }
                            )

                            // Mode CUSTOM
                            ModeGridItem(
                                title = "Custom",
                                subtitle = if (activeMode == "CUSTOM") "ACTIVE" else "PULSE",
                                icon = Icons.Default.Favorite,
                                isSelected = activeMode == "CUSTOM",
                                activeBg = GeoLuminaBlue,
                                activeText = GeoOnLuminaBlue,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setMode("CUSTOM") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Dynamically shifting controls list using simple Compose triggers
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.0f)
                    ) {
                        AnimatedContent(
                            targetState = activeMode,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(150)) with fadeOut(animationSpec = tween(150))
                            }
                        ) { mode ->
                            when (mode) {
                                "NORMAL" -> NormalModeControls(
                                    viewModel = viewModel,
                                    physicalSupported = isPhysicalBrightnessSupported,
                                    physicalBrightness = physicalBrightness,
                                    screenLightMode = screenLightMode,
                                    screenBrightness = screenBrightness,
                                    screenColor = screenColor
                                )
                                "STROBE" -> StrobeModeControls(
                                    frequency = strobeFrequency,
                                    onFrequencyChange = { viewModel.setStrobeFrequency(it) }
                                )
                                "SOS" -> SosModeControls()
                                "CUSTOM" -> CustomModeControls(
                                    savedPatterns = savedPatterns,
                                    selectedPattern = selectedPattern,
                                    isRecording = isRecordingPattern,
                                    viewModel = viewModel,
                                    onSelect = { viewModel.selectPattern(it) },
                                    onDelete = { viewModel.deletePattern(it) }
                                )
                            }
                        }
                    }
                } else {
                    // Minimal Battery Saving Display: OLED optimization blocks
                    Spacer(modifier = Modifier.weight(0.2f))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DeepBlack),
                        border = BorderStroke(1.dp, SoftOffGrey),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                tint = FlashAmber,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "OLED ULTRA BATTERY SAVER ACTIVE",
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Screen pixels dimmed to 5%. Heavy animations, strobe customization interfaces, and lists are disabled to halt CPU drawing loops.",
                                color = TextMuted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(0.2f))
                }

                // Bottom Bar (Legacy Support Info) matching Lumina design guidelines
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = GeoTextSec.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LEGACY OPTIMIZED INTERFACE",
                        color = GeoTextSec.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// 1. Core Glowing Power Button Component (Geometric Balance)
@Composable
fun PowerConsoleButton(
    isOn: Boolean,
    activeMode: String,
    strobeFreq: Float,
    batterySaver: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isOn && !batterySaver) 1.05f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (activeMode == "STROBE") {
                    (1000f / strobeFreq).toInt().coerceIn(120, 1500)
                } else 1000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag("power_console")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(pulseScale)
                .size(192.dp)
        ) {
            // Layered Radial Blur background matching Tailwind blur-3xl
            if (isOn && !batterySaver) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    (if (activeMode == "SOS") GeoSosRed else GeoLuminaBlue).copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Main geometric circular center button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(if (isOn) {
                        if (activeMode == "SOS") GeoSosRed else GeoLuminaBlue
                    } else GeoCard)
                    .border(
                        width = 4.dp,
                        color = if (isOn) {
                            if (activeMode == "SOS") GeoSosRed else GeoLuminaBlue
                        } else GeoBorder,
                        shape = CircleShape
                    )
                    .clickable { onClick() }
                    .testTag("power_main_button")
            ) {
                // Outer inset border ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(164.dp)
                        .border(
                            width = 4.dp,
                            color = (if (isOn) Color.Black else Color.White).copy(alpha = 0.08f),
                            shape = CircleShape
                        )
                ) {
                    // Custom Power settings canvas drawing
                    Canvas(modifier = Modifier.size(56.dp)) {
                        val strokeWidth = 7.dp.toPx()
                        val color = if (isOn) {
                            if (activeMode == "SOS") GeoOnSosRed else GeoOnLuminaBlue
                        } else GeoTextPrimary

                        drawArc(
                            color = color,
                            startAngle = -240f,
                            sweepAngle = 300f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                        drawLine(
                            color = color,
                            start = androidx.compose.ui.geometry.Offset(size.width / 2, 2.dp.toPx()),
                            end = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.48f),
                            strokeWidth = strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // State indicator badge below main toggle
        Text(
            text = if (isOn) "ACTIVE • ${activeMode}" else "READY (OFF)",
            color = if (isOn) {
                if (activeMode == "SOS") GeoSosRed else GeoLuminaBlue
            } else GeoTextSec,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag("power_status_badge")
        )
    }
}

// Support Composable for Selection Grid Items (Geometric Balance)
@Composable
fun ModeGridItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeBg: Color,
    activeText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) activeBg else GeoCard)
            .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent else GeoBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isSelected) activeText.copy(alpha = 0.12f) else GeoBorder,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) activeText else GeoTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    color = if (isSelected) activeText else GeoTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = if (isSelected) activeText.copy(alpha = 0.7f) else GeoTextSec,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// 2. Normal / Static Mode Configurator (LED brightness + Screen lantern fallback)
@Composable
fun NormalModeControls(
    viewModel: FlashlightViewModel,
    physicalSupported: Boolean,
    physicalBrightness: Float,
    screenLightMode: Boolean,
    screenBrightness: Float,
    screenColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(GeoCard)
            .border(1.dp, GeoBorder, RoundedCornerShape(28.dp))
            .padding(20.dp)
            .testTag("normal_controls")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LIGHT MODE MODIFIER",
                color = ElectricCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            
            // Switch for Screen Lantern Fallback
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.toggleScreenLightMode() }
                    .background(SlateBack)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("lantern_switch_container")
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (screenLightMode) ElectricCyan else TextGray,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (screenLightMode) "SCREEN" else "CAMERA LED",
                    color = if (screenLightMode) ElectricCyan else TextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (screenLightMode) {
            // Screen Lantern Customization Mod block
            Text(
                text = "Screen Backlight Intensity",
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = screenBrightness,
                onValueChange = { viewModel.setScreenBrightness(it) },
                colors = SliderDefaults.colors(
                    thumbColor = GeoLuminaBlue,
                    activeTrackColor = GeoLuminaBlue,
                    inactiveTrackColor = GeoBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("screen_brightness_slider")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Lantern Chromatic Tint Preset",
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Dynamic Row containing multiple Color selection chips
            val colorPresets = listOf(
                Pair(Color.White, "WHITE"),
                Pair(Color(0xFFFFA000), "AMBER"),
                Pair(Color(0xFFFF1E56), "TACTICAL RED"),
                Pair(Color(0xFF00ADB5), "BEACON CYAN"),
                Pair(Color(0xFF393E46), "STEEL GREY")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                colorPresets.forEach { (colorItem, colorName) ->
                    val isSelected = screenColor == colorItem
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(colorItem)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) GeoLuminaBlue else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { viewModel.setScreenColor(colorItem) }
                            .testTag("color_preset_$colorName"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                tint = if (colorItem == Color.White) Color.Black else Color.White,
                                contentDescription = "Active color $colorName",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

        } else {
            // Hardware LED flashlight section
            Text(
                text = "Physical LED Brightness Strength",
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Slider(
                value = physicalBrightness,
                onValueChange = { viewModel.setPhysicalBrightness(it) },
                valueRange = 0.1f..1.0f,
                enabled = true,
                colors = SliderDefaults.colors(
                    thumbColor = GeoLuminaBlue,
                    activeTrackColor = GeoLuminaBlue,
                    inactiveTrackColor = GeoBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("physical_brightness_slider")
            )

            if (!physicalSupported) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateBack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            tint = FlashAmber,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Variable LED strength requires Android 13+ and supported manufacturer driver pipelines. Simulated fallbacks are active.",
                            color = TextGray,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Strength Min", color = TextMuted, fontSize = 10.sp)
                    Text(text = "Strength Maximum", color = FlashAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 3. Strobe / Frequency Speed Customization Mod
@Composable
fun StrobeModeControls(
    frequency: Float,
    onFrequencyChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(GeoCard)
            .border(1.dp, GeoBorder, RoundedCornerShape(28.dp))
            .padding(20.dp)
            .testTag("strobe_controls")
    ) {
        Text(
            text = "STROBE SPEED SYNCHRONIZER",
            color = ElectricCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "Oscillation Frequency",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Pulsing ticks per second",
                    color = TextGray,
                    fontSize = 11.sp
                )
            }
            Text(
                text = "${"%.1f".format(frequency)} Hz",
                color = GeoLuminaBlue,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = frequency,
            onValueChange = onFrequencyChange,
            valueRange = 1.0f..15.0f,
            colors = SliderDefaults.colors(
                thumbColor = GeoLuminaBlue,
                activeTrackColor = GeoLuminaBlue,
                inactiveTrackColor = GeoBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("strobe_frequency_slider")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Visual pulse feedback ring representing physical timing cycles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            PulseTimingIndicator(freq = frequency)
        }
    }
}

@Composable
fun PulseTimingIndicator(freq: Float) {
    val infiniteTransition = rememberInfiniteTransition()
    val rawPeriod = (1000f / freq).toInt().coerceIn(60, 1000)
    
    val ringGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rawPeriod, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(ringGlowScale)
            .border(2.dp, GeoLuminaBlue.copy(alpha = (1.5f - ringGlowScale).coerceIn(0f, 1f)), CircleShape)
    )
}

// 4. SOS distress guidance controls
@Composable
fun SosModeControls() {
    Card(
        colors = CardDefaults.cardColors(containerColor = GeoCard),
        border = BorderStroke(1.dp, GeoBorder),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sos_controls")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "SAFETY EMERGENCY SOS MOD",
                    color = CrimsonSos,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Traditional telegraph dot-dash visual representation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateBack, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // S: 3 Dots
                repeat(3) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CrimsonSos))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                // O: 3 Dashes
                repeat(3) {
                    Box(modifier = Modifier.size(width = 24.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(CrimsonSos))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                // S: 3 Dots
                repeat(3) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CrimsonSos))
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Distress Signal looping is active at system-level prioritization. Runs even if device dims to save battery screen draw.",
                color = TextWhite,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
        }
    }
}

// 5. Custom Presets Panel with Tap-to-Record Loop editor
@Composable
fun CustomModeControls(
    savedPatterns: List<FlashlightPattern>,
    selectedPattern: FlashlightPattern?,
    isRecording: Boolean,
    viewModel: FlashlightViewModel,
    onSelect: (FlashlightPattern?) -> Unit,
    onDelete: (FlashlightPattern) -> Unit
) {
    var showRecordPanel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("custom_controls")
    ) {
        if (showRecordPanel || isRecording) {
            // Tap Sequence Recording console
            PatternRecorderConsole(
                viewModel = viewModel,
                isRecording = isRecording,
                onDismiss = { showRecordPanel = false }
            )
        } else {
            // List of active custom patterns and pre-loaded models
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CUSTOM MOD RHYTHMS",
                    color = ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Button(
                    onClick = { showRecordPanel = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CardBack),
                    border = BorderStroke(1.dp, ElectricCyan),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New pattern",
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "TAP-RECORD", color = ElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("pattern_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedPatterns) { pattern ->
                    val isRunningThis = selectedPattern?.id == pattern.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRunningThis) FlashAmber.copy(alpha = 0.08f) else CardBack
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isRunningThis) FlashAmber else SoftOffGrey
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(pattern) }
                            .testTag("pattern_item_${pattern.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = pattern.name,
                                        color = if (isRunningThis) FlashAmber else TextWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (pattern.isPreset) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "PRESET",
                                            color = ElectricCyan,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier
                                                .background(
                                                    ElectricCyan.copy(alpha = 0.15f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = pattern.description,
                                    color = TextGray,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isRunningThis) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Running",
                                        tint = FlashAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                
                                if (!pattern.isPreset) {
                                    IconButton(
                                        onClick = { onDelete(pattern) },
                                        modifier = Modifier.size(32.dp).testTag("delete_pattern_${pattern.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Pattern",
                                            tint = CrimsonSos.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 6. Interactive Tap Drum Pad Recorder Console Component
@Composable
fun PatternRecorderConsole(
    viewModel: FlashlightViewModel,
    isRecording: Boolean,
    onDismiss: () -> Unit
) {
    var rawName by remember { mutableStateOf("") }
    var rawDesc by remember { mutableStateOf("") }
    var pulsesCaptured by remember { mutableStateOf(0) }

    // Coroutine ticker updates tap sequence feedback on-screen
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(150.dp.value.toLong()) // Simple responsive counter sync tick
            pulsesCaptured++ // Just visual ticks to keep layout reactively alive
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBack),
        border = BorderStroke(1.dp, ElectricCyan),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recorder_panel")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TAP SEQUENCE COMPOSER",
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                IconButton(
                    onClick = {
                        if (isRecording) {
                            // Cancel recording
                            viewModel.stopAndSaveRecording("", "")
                        }
                        onDismiss()
                    },
                    modifier = Modifier.size(24.dp).testTag("close_recorder")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = TextGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isRecording) {
                // Ready Screen
                TextField(
                    value = rawName,
                    onValueChange = { rawName = it },
                    placeholder = { Text("Pattern Name (e.g. S.O.S Ext)") },
                    textStyle = TextStyle(color = TextWhite, fontSize = 13.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SlateBack,
                        unfocusedContainerColor = SlateBack,
                        focusedIndicatorColor = ElectricCyan,
                        unfocusedIndicatorColor = SoftOffGrey
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recorder_name_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = rawDesc,
                    onValueChange = { rawDesc = it },
                    placeholder = { Text("Short description") },
                    textStyle = TextStyle(color = TextGray, fontSize = 12.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SlateBack,
                        unfocusedContainerColor = SlateBack,
                        focusedIndicatorColor = ElectricCyan,
                        unfocusedIndicatorColor = SoftOffGrey
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recorder_desc_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.startRecordingPattern() },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_record_button")
                ) {
                    Text(text = "INITIALIZE RECORDING", color = DeepBlack, fontWeight = FontWeight.Bold)
                }
            } else {
                // Interactive Recording Pad
                Text(
                    text = "Tap & Hold down the Pad below to capture pulse signals",
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // The Big Tap pad - captures pointer downs/ups
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateBack)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    try {
                                        viewModel.handleRecordTapDown()
                                        awaitRelease()
                                    } finally {
                                        viewModel.handleRecordTapUp()
                                    }
                                }
                            )
                        }
                        .testTag("recording_pad"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(ElectricCyan.copy(alpha = 0.1f), CircleShape)
                                .border(1.dp, ElectricCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                tint = ElectricCyan,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "HOLD PAD TO EMIT LIGHT",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recording timeline captured...",
                        color = TextGray,
                        fontSize = 11.sp
                    )

                    Button(
                        onClick = { viewModel.stopAndSaveRecording(rawName, rawDesc) },
                        colors = ButtonDefaults.buttonColors(containerColor = FlashAmber),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("save_record_button")
                    ) {
                        Text(text = "SAVE PATTERN", color = DeepBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Extension to safely resolve Context to find MainActivity
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
