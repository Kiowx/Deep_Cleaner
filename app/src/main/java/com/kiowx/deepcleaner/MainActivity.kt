package com.kiowx.deepcleaner

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.kiowx.deepcleaner.ui.DeepCleanerRoot
import com.kiowx.deepcleaner.ui.theme.DeepCleanerTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: DeepCleanerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[DeepCleanerViewModel::class.java]
        handleIntent(intent)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            DeepCleanerTheme(themeMode = state.themeMode) {
                DeepCleanerRoot(state = state, viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) viewModel.refreshPermissionAndStorage()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_QUICK_SCAN && ::viewModel.isInitialized) {
            viewModel.selectSection(com.kiowx.deepcleaner.core.MainSection.CLEAN)
            viewModel.runQuickScan()
            intent.action = null
        }
    }

    companion object {
        const val ACTION_QUICK_SCAN = "com.kiowx.deepcleaner.action.QUICK_SCAN"
    }
}
