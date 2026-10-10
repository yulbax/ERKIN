package io.github.yulbax.erkin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import io.github.yulbax.erkin.ui.screens.MainScreen
import io.github.yulbax.erkin.ui.theme.ERKINTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ERKINTheme {
                MainScreen()
            }
        }
    }
}
