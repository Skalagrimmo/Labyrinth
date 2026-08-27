package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ContentModLoader
import com.example.ui.GameViewModel
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.NetcrawlerTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Load any .md mod files placed in assets/mods/ (see ContentModParser format).
    val loadedMods = ContentModLoader.loadFolderMods(this)
    if (loadedMods.isNotEmpty()) {
      android.util.Log.i("Netcrawler", "Loaded mod files: ${loadedMods.joinToString()}")
    }
    setContent {
      NetcrawlerTheme {
        val viewModel: GameViewModel = viewModel()
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          TerminalScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}

