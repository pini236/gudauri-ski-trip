package com.pini.gudauri

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pini.gudauri.data.*
import com.pini.gudauri.ui.*

class MainActivity:ComponentActivity() {
    private var incomingLink by mutableStateOf<String?>(null)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingLink = intent.dataString
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        if (savedInstanceState == null) incomingLink = intent.dataString
        setContent {
            val prefs=remember { getSharedPreferences("gudauri-native",MODE_PRIVATE) }
            var theme by rememberSaveable { mutableIntStateOf(prefs.getInt("theme",0)) }
            val now = rememberForegroundTime()
            val daylight = remember(now, theme) { GudauriTime.at(now, ThemeMode.fromSaved(theme)) }
            val dark = daylight.dark
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
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDaylight provides daylight) {
                    val vm:MountainViewModel=viewModel(); val state by vm.state.collectAsStateWithLifecycle()
                    Surface(Modifier.fillMaxSize(),color=LocalPalette.current.snow) {
                        when(val loaded=state) {
                            LoadState.Loading -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Column(horizontalAlignment=Alignment.CenterHorizontally) { CircularProgressIndicator();Spacer(Modifier.height(16.dp));Text("טוען את ההר…") } }
                            is LoadState.Failed -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text("לא ניתן לטעון את ההר");Text(loaded.message);Button(onClick=vm::load){Text("נסה שוב")} } }
                            is LoadState.Ready -> {
                                var page by rememberSaveable { mutableStateOf(AppPage.HOME.name) }
                                var requestedRun by rememberSaveable { mutableStateOf<String?>(null) }
                                var requestedLift by rememberSaveable { mutableStateOf<Long?>(null) }
                                var requestedMeetingLink by rememberSaveable { mutableStateOf<String?>(null) }
                                var meetingVersion by rememberSaveable { mutableIntStateOf(0) }
                                var returnPage by rememberSaveable { mutableStateOf(AppPage.HOME.name) }
                                var linkVersion by rememberSaveable { mutableIntStateOf(0) }
                                var importing by rememberSaveable { mutableStateOf(false) }
                                var linkText by rememberSaveable { mutableStateOf("") }
                                var importError by remember { mutableStateOf<String?>(null) }
                                var incomingError by remember { mutableStateOf(false) }
                                val pages = rememberSaveableStateHolder()
                                val meetingModel=remember(loaded.data) { MeetingModel(loaded.data) }
                                BackHandler(page != AppPage.HOME.name) { page=returnPage;returnPage=AppPage.HOME.name }
                                val acceptLink: (String) -> Boolean = { text ->
                                    val link = AppLinks.parse(text)
                                    if (link == null || (link.runKey != null && link.runKey !in loaded.data.pistesByKey) ||
                                        (link.meeting != null && meetingModel.station(link.meeting.stationId)==null)) false
                                    else {
                                        if(link.page==AppPage.MAP) { requestedRun=link.runKey;requestedLift=null;linkVersion++ }
                                        if(link.page==AppPage.MEET) { requestedMeetingLink=link.meeting?.let(AppLinks::meeting);meetingVersion++ }
                                        returnPage=AppPage.HOME.name;page=link.page.name
                                        true
                                    }
                                }
                                LaunchedEffect(incomingLink) {
                                    incomingLink?.let { incomingError = !acceptLink(it); incomingLink = null }
                                }
                                var favorites by remember { mutableStateOf(prefs.getStringSet("favorites",emptySet()).orEmpty().toSet()) }
                                val toggleFavorite:(String)->Unit={ key -> favorites=if(key in favorites)favorites-key else favorites+key;prefs.edit().putStringSet("favorites",favorites).apply() }
                                val toggleTheme={theme=ThemeMode.fromSaved(theme).next().savedValue;prefs.edit().putInt("theme",theme).apply();Unit}
                                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                                    AnimatedContent(targetState=page,label="page",transitionSpec={fadeIn()+slideInHorizontally { it/12 } togetherWith fadeOut()}) { currentPage ->
                                        pages.SaveableStateProvider(currentPage) {
                                            when(currentPage) {
                                                AppPage.MAP.name -> MapScreen(loaded.data,favorites,toggleFavorite,{page=AppPage.HOME.name},toggleTheme,requestedRun,linkVersion,requestedLift,
                                                    onMeet={returnPage=AppPage.HOME.name;page=AppPage.MEET.name})
                                                AppPage.MEET.name -> MeetingScreen(loaded.data,{page=AppPage.HOME.name},toggleTheme,
                                                    onMapLift={id->requestedLift=id;requestedRun=null;linkVersion++;returnPage=AppPage.MEET.name;page=AppPage.MAP.name},
                                                    requested=requestedMeetingLink?.let { AppLinks.parse(it)?.meeting },linkVersion=meetingVersion)
                                                else -> HomeScreen(loaded.data,{returnPage=AppPage.HOME.name;page=AppPage.MAP.name},toggleTheme,{importing=true;importError=null},
                                                    onMeet={returnPage=AppPage.HOME.name;page=AppPage.MEET.name})
                                            }
                                        }
                                    }
                                }
                                if (importing) AlertDialog(onDismissRequest={importing=false},title={Text("פתיחת קישור מהאתר")},text={
                                    Column {
                                        Text("הדביקו קישור למפה, למסלול או לנקודת מפגש. פתיחה אוטומטית תלויה בהגדרות המכשיר.")
                                        OutlinedTextField(value=linkText,onValueChange={linkText=it;importError=null},label={Text("קישור")},isError=importError!=null,
                                            singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,autoCorrectEnabled=false),modifier=Modifier.fillMaxWidth())
                                        importError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                                    }
                                },confirmButton={
                                    val dialogFocus=LocalFocusManager.current
                                    val dialogKeyboard=LocalSoftwareKeyboardController.current
                                    TextButton(onClick={dialogFocus.clearFocus();dialogKeyboard?.hide();if(acceptLink(linkText)){importing=false;linkText=""}else importError="הקישור, התאריך או השעה אינם תקינים, או שהמקום אינו נמצא בנתונים."}){Text("פתח")}
                                },dismissButton={TextButton(onClick={importing=false}){Text("ביטול")}})
                                if (incomingError) AlertDialog(onDismissRequest={incomingError=false},title={Text("לא ניתן לפתוח את הקישור")},text={Text("הקישור, התאריך או השעה אינם תקינים, או שהמקום אינו נמצא בנתונים. המפה והחיפוש עדיין זמינים.")},confirmButton={TextButton(onClick={incomingError=false}){Text("סגור")}})
                            }
                        }
                    }
                }
            }
        }
    }
}
