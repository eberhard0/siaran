package com.iameberhard.siaran

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.iameberhard.siaran.ui.SiaranApp
import com.iameberhard.siaran.ui.SiaranTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var inPip by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var fullscreen by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(fullscreen) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (fullscreen) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    // The cover screen is a tall phone: turn it sideways for the video.
                    // Unfolded it's nearly square, so leave the orientation alone.
                    if (resources.configuration.smallestScreenWidthDp < 600) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }

            SiaranTheme {
                SiaranApp(
                    vm = vm,
                    inPictureInPicture = inPip,
                    fullscreen = fullscreen,
                    onFullscreenChange = { fullscreen = it },
                )
            }
        }
    }

    /** Leaving the app while a channel plays shrinks it to a floating window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // With the floating window off, onStop() pauses playback instead.
        if (vm.library.pipEnabled && vm.player.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // Closing the floating window (or leaving without one) stops the sound.
        vm.player.pause()
    }

    override fun onStart() {
        super.onStart()
        if (vm.nowPlaying.value?.let { it.error == null } == true) vm.player.play()
    }
}
