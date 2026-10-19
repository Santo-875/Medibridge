package com.medibridge.moduleD_shell.ui

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * IntroVideoScreen — Full-screen intro video player.
 *
 * Plays the user's video (e.g. medibridge.mp4, intro_video.mp4) placed in res/raw/.
 * If no video is present, transitions immediately to Home (default motion graphics removed).
 */
@Composable
fun IntroVideoScreen(
    onIntroComplete: () -> Unit
) {
    val context = LocalContext.current
    var isFinished by remember { mutableStateOf(false) }

    fun finishIntro() {
        if (!isFinished) {
            isFinished = true
            onIntroComplete()
        }
    }

    // Automatically locate the user's video in res/raw/
    val rawId = remember {
        val candidateNames = listOf(
            "medibridge",
            "medibridge_intro",
            "intro_video",
            "intro_motion",
            "intro"
        )
        var resolvedId = 0
        for (name in candidateNames) {
            val id = context.resources.getIdentifier(name, "raw", context.packageName)
            if (id != 0) {
                resolvedId = id
                Log.i("IntroVideoScreen", "Located raw video resource: $name (id=$id)")
                break
            }
        }
        // Fallback: pick any video file present in R.raw via reflection
        if (resolvedId == 0) {
            try {
                val rRawClass = Class.forName("${context.packageName}.R\$raw")
                val fields = rRawClass.fields
                if (fields.isNotEmpty()) {
                    resolvedId = fields[0].getInt(null)
                    Log.i("IntroVideoScreen", "Located first raw resource: ${fields[0].name} (id=$resolvedId)")
                }
            } catch (e: Exception) {
                Log.w("IntroVideoScreen", "Could not locate raw resource via reflection: ${e.message}")
            }
        }
        resolvedId
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (rawId != 0) {
            // ── PLAY USER'S VIDEO FULL-SCREEN ─────────────────────────────────
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val uri = Uri.parse("android.resource://${ctx.packageName}/$rawId")
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            try {
                                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                            } catch (_: Exception) {}
                            start()
                        }
                        setOnCompletionListener {
                            finishIntro()
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.e("IntroVideoScreen", "VideoView playback error: what=$what extra=$extra")
                            finishIntro()
                            true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── TOP-RIGHT SKIP BUTTON ─────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 20.dp)
                    .clickable { finishIntro() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "Skip Intro",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            // If no video exists in res/raw/, transition immediately to Home
            LaunchedEffect(Unit) {
                finishIntro()
            }
        }
    }
}
