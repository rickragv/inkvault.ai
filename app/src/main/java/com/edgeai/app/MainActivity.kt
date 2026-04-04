package com.edgeai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.edgeai.app.ui.navigation.EdgeAiNavGraph
import com.edgeai.app.ui.theme.EdgeAiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdgeAiTheme {
                EdgeAiNavGraph()
            }
        }
    }
}
