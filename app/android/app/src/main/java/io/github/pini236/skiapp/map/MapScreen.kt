package io.github.pini236.skiapp.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.ui.Karantina
import io.github.pini236.skiapp.ui.Palette
import io.github.pini236.skiapp.ui.Plex

/** The 3D map screen: the GL mountain with a run sign and the fly-down button on top. */
@Composable
fun MapScreen(view: MapView, scene: MapScene?) {
    var selected by remember { mutableStateOf<Piste?>(view.selected) }
    var flying by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        view.onSelect = { selected = it }
        view.onFlying = { flying = it }
        onDispose { view.onSelect = null; view.onFlying = null }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize())
        if (scene == null) {
            Text("טוען את ההר…", Modifier.align(Alignment.Center), fontFamily = Plex, fontSize = 16.sp, color = Palette.ink)
        }
        val p = selected
        if (p != null) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // the run's sign: its colour and its name, square corners like the trail signs
                    Box(Modifier.background(Palette.run(p.color)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(p.name, fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Color.White)
                    }
                    Spacer1()
                    Button(if (flying) "עצירה" else "טיסה במורד") { if (flying) view.stopFly() else view.flyDown() }
                    Button("✕") { view.select(null) }
                }
            }
        } else if (scene != null) {
            Text("נגיעה במסלול בוחרת אותו. אצבע אחת מזיזה, שתיים מקרבות, מסובבות ומטות.",
                Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Color(0xE6FFFFFF)).padding(10.dp),
                fontFamily = Plex, fontSize = 13.sp, color = Palette.ink)
        }
    }
}

@Composable private fun Spacer1() = Box(Modifier.size(0.dp))

@Composable
fun Button(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).background(Palette.ink).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White) }
}
