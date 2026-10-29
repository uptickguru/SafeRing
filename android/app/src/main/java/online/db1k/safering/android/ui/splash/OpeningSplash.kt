package online.db1k.safering.android.ui.splash

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.db1k.safering.android.R

private const val PREFS = "humananswer_splash"
private const val KEY = "turn"

/** One face. The name fades in on the shirt, not over the face. Tap skips. */
@Composable
fun OpeningSplash(onDone: () -> Unit) {
    val context = LocalContext.current
    val mom = remember {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val turn = prefs.getInt(KEY, 0)
        prefs.edit().putInt(KEY, turn + 1).apply()
        turn % 2 == 0
    }
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
    val settle = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val name = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val cover = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (finished) return
        finished = true
        scope.launch {
            if (!reduceMotion) cover.animateTo(0f, tween(800))
            onDone()
        }
    }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        if (!reduceMotion) {
            settle.animateTo(1f, tween(900))
            name.animateTo(1f, tween(600))
        }
        val left = 3000L - (System.currentTimeMillis() - start)
        if (left > 0) delay(left)
        finish()
    }
    Box(
        Modifier
            .fillMaxSize()
            .alpha(cover.value)
            .background(Color(0xFF161412))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { finish() }
    ) {
        Image(
            painter = painterResource(if (mom) R.drawable.splash_mom else R.drawable.splash_dad),
            contentDescription = if (mom) "HumanAnswer. A person answers her phone" else "HumanAnswer. A person answers his phone",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1.03f - (0.03f * settle.value)
                    scaleX = s
                    scaleY = s
                    alpha = 0.2f + (0.8f * settle.value)
                },
            contentScale = ContentScale.Crop
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 52.dp)
                .alpha(name.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.22f)
                    .height(2.dp)
                    .background(Color(0xFFC4A46A))
            )
            Text(
                "HumanAnswer",
                color = Color(0xFFF4EFE6),
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
