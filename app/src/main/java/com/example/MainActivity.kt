package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ai.configureAppCheck
import com.example.nativebridge.FloatingOverlayManager
import com.example.nativebridge.FocusFlowWidgetProvider
import com.example.nativebridge.ShieldStateBridge
import com.example.ui.FocusFlowApp
import com.example.ui.FocusFlowViewModel
import com.example.ui.TopTab

class MainActivity : ComponentActivity() {

    private lateinit var appViewModel: FocusFlowViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check before any UI, ViewModels, or Firebase AI Logic calls.
        configureAppCheck(this, intent)

        appViewModel = ViewModelProvider(this)[FocusFlowViewModel::class.java]
        handleNavigationIntent(intent)

        enableEdgeToEdge()
        setContent {
            FocusFlowApp(viewModel = appViewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        ShieldStateBridge.setAppInForeground(true)
        FloatingOverlayManager.hide(this)
        if (::appViewModel.isInitialized) {
            appViewModel.refreshPermissions()
        }
    }

    override fun onStop() {
        super.onStop()
        ShieldStateBridge.setAppInForeground(false)
        FloatingOverlayManager.sync(this)
        FocusFlowWidgetProvider.refreshAllWidgets(this)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_FOCUS, false) == true && ::appViewModel.isInitialized) {
            appViewModel.selectTopTab(TopTab.FOCUS)
            intent.removeExtra(EXTRA_OPEN_FOCUS)
        }
    }

    companion object {
        const val EXTRA_OPEN_FOCUS = "extra_open_focus"
    }
}
