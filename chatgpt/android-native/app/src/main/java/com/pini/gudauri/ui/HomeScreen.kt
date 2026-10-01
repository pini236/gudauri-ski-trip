package com.pini.gudauri.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.pini.gudauri.data.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import kotlin.math.*

private data class Skyline(val far:List<Float>,val mid:List<Float>,val near:List<Float>)
private fun skyline(data:MountainData):Skyline {
    val t=data.terrain;val place=t.places.firstOrNull { it.name=="Gudauri" }?.point ?: Point(0f,3000f)
    val origin=t.elevation(place.x,place.z)+30
    val target=Point(-1000f,-2500f);val az=atan2(target.x-place.x,-(target.z-place.z))
    val layers=List(3){mutableListOf<Float>()}
    for(i in 0..180) { val angle=az-.7f+1.4f*i/180;val dx=sin(angle);val dz=-cos(angle)
        var f=-1f;var m=-1f;var n=-1f;var d=80f
        while(d<16000) { val x=place.x+dx*d;val z=place.z+dz*d
            if(x<t.x0||x>t.x1||z<t.z0||z>t.z1)break
            val slope=(t.elevation(x,z)-origin)/d;f=max(f,slope);if(d<3500)m=max(m,slope);if(d<800)n=max(n,slope);d+=if(d<2500)30 else 70
        };layers[0].add(atan(f));layers[1].add(atan(m));layers[2].add(atan(n))
    };return Skyline(layers[0],layers[1],layers[2])
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun HomeScreen(data:MountainData,onMap:()->Unit,onTheme:()->Unit,onImport:()->Unit = {},onMeet:()->Unit = {}) {
    val p=LocalPalette.current
    val context=LocalContext.current
    val daylight = LocalDaylight.current
    val trip = data.trip
    var privacy by remember { mutableStateOf(false) }
    LazyColumn(Modifier.testTag("home-content").fillMaxSize().background(p.snow),contentPadding=PaddingValues(bottom=28.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                HomeLandscape(data, Modifier.fillMaxSize())
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Surface(color=p.snow.copy(alpha=.85f),modifier=Modifier.weight(1f)) {
                        Text(trip?.destination?.ifBlank { "גודאורי, גאורגיה" } ?: "גודאורי, גאורגיה",Modifier.padding(8.dp),fontWeight=FontWeight.SemiBold)
                    }
                    Surface(color=p.snow.copy(alpha=.85f)) { ThemeControl(onTheme) }
                }
                Surface(color=p.snow.copy(alpha=.85f),modifier=Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${daylight.actualTime.format(DateTimeFormatter.ofPattern("HH:mm"))} בגודאורי · ${daylight.phase}",fontSize=13.sp)
                        trip?.skiDays?.let { Text("${it.dates.size} ימי סקי · ${formatTripDate(it.from.toString())}–${formatTripDate(it.to.toString())}",fontSize=12.sp) }
                    }
                }
            }
        }
        item {
            if (trip != null) FlightTickets(trip, Modifier.padding(horizontal=20.dp,vertical=20.dp))
            else Surface(color=p.paper,modifier=Modifier.fillMaxWidth().padding(20.dp)) {
                Column(Modifier.padding(16.dp)) { Text("פרטי הטיסה אינם זמינים",fontWeight=FontWeight.SemiBold);Hint("אפשר להמשיך להשתמש במפה ובפרטי המסלולים.") }
            }
        }
        item {
            Column(Modifier.padding(horizontal=20.dp)) {
                Box(Modifier.fillMaxWidth().padding(start=18.dp)) {
                    Box(Modifier.align(Alignment.CenterEnd).width(6.dp).height(116.dp).offset(x=18.dp).background(p.ink))
                    Surface(onClick=onMap,shape=SignShape(),color=p.blue,modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal=22.dp,vertical=16.dp)) { Text("מפת מסלולים",fontFamily=DisplayFont,fontSize=38.sp,color=if(p.dark)p.snow else Color.White);Text("${data.pistes.count { it.named }} מסלולים, פרטים וסרטונים",color=if(p.dark)p.snow else Color.White,fontSize=13.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(onClick=onMeet,shape=SignShape(),color=p.accent,modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal=22.dp,vertical=16.dp)) { Text("נקודת מפגש",fontFamily=DisplayFont,fontSize=38.sp,color=if(p.dark)p.snow else Color.White);Text("בוחרים תחנה ושעה ומשתפים עם החבר׳ה",color=if(p.dark)p.snow else Color.White,fontSize=13.sp) }
                }
                Spacer(Modifier.height(24.dp));Text("מסלולים במפה הרשמית",color=p.muted,fontSize=13.sp);Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    listOf("green" to "ירוק","blue" to "כחול","red" to "אדום","black" to "שחור").forEach { (c,label) ->
                        Column(Modifier.weight(1f)) { Box(Modifier.fillMaxWidth().height(6.dp).background(p.piste(c)));Text(data.pistes.count { it.named&&it.color==c }.toString(),fontFamily=DisplayFont,fontSize=42.sp);Text(label,color=p.muted,fontSize=13.sp) }
                    }
                }
                Spacer(Modifier.height(28.dp));Text("החבר׳ה · ${trip?.members?.size ?: 0}",color=p.muted,fontSize=13.sp);Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    trip?.members.orEmpty().forEach { name -> Surface(color=p.paper,border=BorderStroke(1.dp,p.rule)) { Text(name,Modifier.padding(horizontal=12.dp,vertical=10.dp)) } }
                }
                TextButton(onClick=onImport) { Text("פתיחת קישור מהאתר") }
                Spacer(Modifier.height(24.dp));Text("נתוני המפה: ${data.fetched}\n© OpenStreetMap contributors · ODbL\nגבהים: AWS Terrain Tiles / SRTM",fontSize=11.sp,color=p.muted)
                Text("צליל הקריעה: everythingsounds / Freesound · CC BY 4.0\nצלילי הקלפים: Kenney · CC0",fontSize=11.sp,color=p.muted)
                TextButton(onClick={openUrl(context,"https://creativecommons.org/licenses/by/4.0/")}) { Text("רישיון צליל הקריעה ↗") }
                TextButton(onClick={openUrl(context,"https://freesound.org/people/everythingsounds/sounds/198233/")}) { Text("מקור צליל הקריעה ↗") }
                TextButton(onClick={privacy=true}) { Text("מידע על פרטיות") }
            }
        }
    }
    if(privacy)PrivacyDialog { privacy=false }
}
@Composable private fun HomeLandscape(data: MountainData, modifier: Modifier) {
    val p = LocalPalette.current; val light = LocalDaylight.current
    var failed by remember(light.first.image, light.second.image) { mutableStateOf(false) }
    var firstReady by remember(light.first.image) { mutableStateOf(false) }
    var secondReady by remember(light.second.image) { mutableStateOf(false) }
    val fallback by produceState<Skyline?>(null, data, failed) {
        if (failed) value = withContext(Dispatchers.Default) { skyline(data) }
    }
    val ready = (firstReady && (light.first.image == light.second.image || secondReady)) || fallback != null
    BoxWithConstraints(modifier.then(if (ready) Modifier.testTag("home-ridge-ready") else Modifier)) {
        val wide = maxWidth > 560.dp
        val imageWidth = if (wide) 1440f else 390f
        val scale = max(maxWidth.value / imageWidth, maxHeight.value / 400f)
        val imageX = (maxWidth.value - imageWidth * scale) / 2
        val imageY = (maxHeight.value - 400f * scale) / 2
        val skyTop = Color(light.mixColor(light.first.top, light.second.top))
        val skyBottom = Color(light.mixColor(light.first.bottom, light.second.bottom))
        Canvas(Modifier.fillMaxSize()) { drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom))) }
        if (!failed) {
            fun source(name: String) = "file:///android_asset/pano/pano-${if(wide) "wide-" else ""}$name.webp"
            AsyncImage(model=source(light.first.image),contentDescription=null,contentScale=ContentScale.Crop,
                modifier=Modifier.fillMaxSize(),onSuccess={firstReady=true},onError={failed=true})
            if (light.first.image != light.second.image) AsyncImage(model=source(light.second.image),contentDescription=null,contentScale=ContentScale.Crop,
                modifier=Modifier.fillMaxSize().alpha(light.blend),onSuccess={secondReady=true},onError={failed=true})
        } else Canvas(Modifier.fillMaxSize()) {
            fallback?.let { s ->
                val lo=s.mid.min();val hi=s.far.max()
                listOf(s.far to p.far,s.mid to p.mid,s.near to p.snow).forEach { (heights,color) ->
                    val path=Path();heights.forEachIndexed { i,a ->
                        val x=i.toFloat()/heights.lastIndex*size.width;val y=size.height*.97f-(a-lo)/max(.001f,hi-lo)*size.height*.67f
                        if(i==0)path.moveTo(x,y)else path.lineTo(x,y)
                    };path.lineTo(size.width,size.height);path.lineTo(0f,size.height);path.close();drawPath(path,color)
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val stars = light.mix(light.first.stars, light.second.stars)
            listOf(4 to 8,11 to 19,18 to 5,25 to 14,33 to 7,40 to 21,47 to 4,55 to 16,61 to 9,68 to 24,74 to 6,81 to 15,88 to 10,94 to 22).forEach { (x,y) ->
                drawCircle(Color.White.copy(alpha=stars),1.2f*density,androidx.compose.ui.geometry.Offset(size.width*x/100,size.height*y/100))
            }
            val moon = light.mix(light.first.moon,light.second.moon)
            val moonAt=androidx.compose.ui.geometry.Offset(size.width*.86f,size.height*.25f)
            drawCircle(Color(0xFFF6F1D8).copy(alpha=moon),14*density,moonAt)
            drawCircle(skyTop.copy(alpha=moon),13*density,moonAt+androidx.compose.ui.geometry.Offset(6*density,-3*density))
            val snow = light.mix(light.first.snow,light.second.snow)
            repeat(14) { i -> drawCircle(Color.White.copy(alpha=snow*.6f),1.5f*density,androidx.compose.ui.geometry.Offset(size.width*(3+i*7)/100,size.height*(.3f+(i*17%65)/100f))) }
        }
        if (!failed) {
            val positions = if(wide) listOf("Sadzele" to .5659f,"Bidara" to .4347f) else listOf("Sadzele" to .6215f,"Bidara" to .3796f)
            positions.forEach { (name,x) ->
                val height=data.terrain.peaks.firstOrNull { it.name == name }?.height
                Surface(color=p.snow.copy(alpha=.8f),modifier=Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x=(imageX+x*imageWidth*scale-40).dp,y=(imageY+(if(name=="Sadzele") .3989f else .4325f)*400*scale).dp)) {
                    Text("${if(name=="Sadzele") "סדזלה" else "בידרה"}${height?.let { " · $it מ׳" }.orEmpty()}",Modifier.padding(4.dp),fontSize=10.sp)
                }
            }
        }
    }
}
