package com.grebeniuk.babylog

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val controller = AppController(Store(app.filesDir), viewModelScope)
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleLink(intent)
        setContent { App(vm.controller) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme == "kacperops") {
            val ok = vm.controller.applySetupCode(data.toString())
            val s = Strings(vm.controller.cfg.lang)
            Toast.makeText(this, if (ok) s["setupOk"] else s["setupBad"], Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun App(c: AppController) {
    val dark = when (c.cfg.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    LifecycleResumeEffect(Unit) {
        c.onResume()
        onPauseOrDispose { c.onPause() }
    }
    LaunchedEffect(Unit) {
        while (true) { c.now = System.currentTimeMillis(); delay(10_000) }
    }

    AppTheme(dark = dark) {
        CompositionLocalProvider(LocalStrings provides Strings(c.cfg.lang)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Root(c)
            }
        }
    }
}

@Composable
private fun Root(c: AppController) {
    var screen by rememberSaveable { mutableStateOf("main") }
    var logType by rememberSaveable { mutableStateOf<String?>(null) }
    val target = when {
        c.me() == null -> "welcome"
        logType != null -> "log:$logType"
        else -> screen
    }
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            if (targetState.startsWith("log:")) {
                (slideInVertically { it / 3 } + fadeIn()) togetherWith fadeOut()
            } else if (initialState.startsWith("log:")) {
                fadeIn() togetherWith (slideOutVertically { it / 3 } + fadeOut())
            } else if (targetState == "settings") {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "screens",
    ) { scr ->
        when (scr) {
            "welcome" -> WelcomeScreen(c)
            "settings" -> {
                BackHandler { screen = "main" }
                SettingsScreen(c) { screen = "main" }
            }
            "main" -> MainScreen(c, onSettings = { screen = "settings" }, onPick = { logType = it })
            else -> LogScreen(c, scr.removePrefix("log:"), onClose = { logType = null })
        }
    }
}
