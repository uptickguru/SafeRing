package online.db1k.safering.android.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import online.db1k.safering.android.MainActivity
import online.db1k.safering.android.R

class OpeningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mom = (System.currentTimeMillis() / 1000) % 2 == 0L
        setContent {
            Box(Modifier.fillMaxSize().clickable { openApp() }, contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(if (mom) R.drawable.splash_mom else R.drawable.splash_dad),
                    contentDescription = "Opening photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        window.decorView.postDelayed({ if (!isFinishing) openApp() }, 2800)
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
