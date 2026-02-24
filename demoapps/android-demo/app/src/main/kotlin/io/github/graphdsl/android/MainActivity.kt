package io.github.graphdsl.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import io.github.graphdsl.android.ui.CountriesScreen
import io.github.graphdsl.android.ui.CountriesViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CountriesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CountriesScreen(viewModel)
        }
    }
}
