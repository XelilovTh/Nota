package com.xelilovth.nota
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.xelilovth.nota.presentation.NotaApp
import com.xelilovth.nota.ui.theme.NotaTheme
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NotaTheme { NotaApp() } }
    }
}
