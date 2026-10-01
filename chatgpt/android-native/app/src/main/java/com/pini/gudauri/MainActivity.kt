package com.pini.gudauri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pini.gudauri.data.*
import com.pini.gudauri.ui.*

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        setContent {
            val prefs=remember { getSharedPreferences("gudauri-native",MODE_PRIVATE) }
            var theme by rememberSaveable { mutableIntStateOf(prefs.getInt("theme",0)) }
            val dark=when(theme){1->false;2->true;else->isSystemInDarkTheme()}
            LaunchedEffect(dark) {
                val transparent=android.graphics.Color.TRANSPARENT
                val bars=if(dark)SystemBarStyle.dark(transparent)else SystemBarStyle.light(transparent,transparent)
                enableEdgeToEdge(statusBarStyle=bars,navigationBarStyle=bars)
                val background=if(dark)0xFF0D1522.toInt()else 0xFFEEF2F5.toInt()
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background))
                // Older SurfaceView compositors need opaque system-bar backing.
                if(android.os.Build.VERSION.SDK_INT<35) {
                    @Suppress("DEPRECATION")
                    window.statusBarColor=background
                    @Suppress("DEPRECATION")
                    window.navigationBarColor=background
                }
            }
            GudauriTheme(dark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val vm:MountainViewModel=viewModel(); val state by vm.state.collectAsStateWithLifecycle()
                    Surface(Modifier.fillMaxSize(),color=LocalPalette.current.snow) {
                        when(val loaded=state) {
                            LoadState.Loading -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Column(horizontalAlignment=Alignment.CenterHorizontally) { CircularProgressIndicator();Spacer(Modifier.height(16.dp));Text("טוען את ההר…") } }
                            is LoadState.Failed -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text("לא ניתן לטעון את ההר");Text(loaded.message);Button(onClick=vm::load){Text("נסה שוב")} } }
                            is LoadState.Ready -> {
                                var map by rememberSaveable { mutableStateOf(false) }
                                BackHandler(map) { map=false }
                                var favorites by remember { mutableStateOf(prefs.getStringSet("favorites",emptySet())!!.toSet()) }
                                val toggleFavorite:(String)->Unit={ key -> favorites=if(key in favorites)favorites-key else favorites+key;prefs.edit().putStringSet("favorites",favorites).apply() }
                                val toggleTheme={theme=if(dark)1 else 2;prefs.edit().putInt("theme",theme).apply();Unit}
                                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                                    AnimatedContent(targetState=map,label="page",transitionSpec={fadeIn()+slideInHorizontally { it/12 } togetherWith fadeOut()}) { showMap ->
                                        if(showMap)MapScreen(loaded.data,favorites,toggleFavorite,{map=false},toggleTheme)
                                        else HomeScreen(loaded.data,{map=true},toggleTheme)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
