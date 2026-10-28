package online.db1k.safering.android.ui.splash

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import online.db1k.safering.android.R

private const val PREFS = "humananswer_splash"
private const val KEY = "turn"

/** One face per open. She first. He next. Each holds their own phone. */
@Composable
fun OpeningSplash(onDone: () -> Unit) {
    val context = LocalContext.current
    val mom = androidx.compose.runtime.remember {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val turn = prefs.getInt(KEY, 0)
        prefs.edit().putInt(KEY, turn + 1).apply()
        turn % 2 == 0
    }
    LaunchedEffect(Unit) {
        delay(1600)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF4EFE6))
            .clickable { onDone() }
    ) {
        Image(
            painter = painterResource(if (mom) R.drawable.splash_mom else R.drawable.splash_dad),
            contentDescription = if (mom) "A person answers her phone" else "A person answers his phone",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
