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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.pini.gudauri.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.time.temporal.ChronoUnit
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
@Composable fun HomeScreen(data:MountainData,onMap:()->Unit,onTheme:()->Unit) {
    val p=LocalPalette.current
    var privacy by remember { mutableStateOf(false) }
    val sky by produceState<Skyline?>(null,data) { value=withContext(Dispatchers.Default){skyline(data)} }
    val today=LocalDate.now(ZoneId.of("Asia/Jerusalem"))
    val days=max(0,ChronoUnit.DAYS.between(today,LocalDate.parse(data.trip.outbound.date))).toInt()
    LazyColumn(Modifier.fillMaxSize().background(p.snow),contentPadding=PaddingValues(bottom=28.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(440.dp)) {
                Canvas(Modifier.fillMaxWidth().height(300.dp).then(if(sky!=null)Modifier.testTag("home-ridge-ready")else Modifier)) {
                    drawRect(p.sky)
                    sky?.let { s ->
                        val lo=s.mid.min();val hi=s.far.max()
                        listOf(s.far to p.far,s.mid to p.mid,s.near to p.snow).forEach { (heights,color) ->
                            val path=Path();heights.forEachIndexed { i,a -> val x=i.toFloat()/heights.lastIndex*size.width;val y=size.height*.97f-(a-lo)/(hi-lo)*size.height*.67f
                                if(i==0)path.moveTo(x,y)else path.lineTo(x,y) }
                            path.lineTo(size.width,size.height);path.lineTo(0f,size.height);path.close();drawPath(path,color)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("גודאורי, גאורגיה",fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                    TextButton(onClick=onTheme) { Text(if(p.dark)"☀" else "☾",fontSize=25.sp) }
                }
                Text("חופשת סקי, ינואר 2027",Modifier.padding(top=64.dp,start=20.dp),color=p.muted,fontSize=13.sp)
                Ticket(data.trip,days,Modifier.align(Alignment.BottomCenter).padding(horizontal=20.dp,vertical=16.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal=20.dp)) {
                Box(Modifier.fillMaxWidth().padding(start=18.dp)) {
                    Box(Modifier.align(Alignment.CenterEnd).width(6.dp).height(116.dp).offset(x=18.dp).background(p.ink))
                    Surface(onClick=onMap,shape=SignShape(),color=p.blue,modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal=22.dp,vertical=16.dp)) { Text("מפת מסלולים",fontFamily=DisplayFont,fontSize=38.sp,color=if(p.dark)p.snow else Color.White);Text("27 מסלולים, פרטים וסרטונים",color=if(p.dark)p.snow else Color.White,fontSize=13.sp) }
                    }
                }
                Spacer(Modifier.height(24.dp));Text("מסלולים במפה הרשמית",color=p.muted,fontSize=13.sp);Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    listOf("green" to "ירוק","blue" to "כחול","red" to "אדום","black" to "שחור").forEach { (c,label) ->
                        Column(Modifier.weight(1f)) { Box(Modifier.fillMaxWidth().height(6.dp).background(p.piste(c)));Text(data.pistes.count { it.named&&it.color==c }.toString(),fontFamily=DisplayFont,fontSize=42.sp);Text(label,color=p.muted,fontSize=13.sp) }
                    }
                }
                Spacer(Modifier.height(28.dp));Text("החבר׳ה · ${data.trip.members.size}",color=p.muted,fontSize=13.sp);Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    data.trip.members.forEach { name -> Surface(color=p.paper,border=BorderStroke(1.dp,p.rule)) { Text(name,Modifier.padding(horizontal=12.dp,vertical=10.dp)) } }
                }
                Spacer(Modifier.height(24.dp));Text("נתוני המפה: ${data.fetched}\n© OpenStreetMap contributors · ODbL\nגבהים: AWS Terrain Tiles / SRTM",fontSize=11.sp,color=p.muted)
                TextButton(onClick={privacy=true}) { Text("מידע על פרטיות") }
            }
        }
    }
    if(privacy)PrivacyDialog { privacy=false }
}
private fun date(iso:String)=LocalDate.parse(iso).let { "${it.dayOfMonth}.${it.monthValue}.${it.year}" }
@Composable private fun Ticket(trip:Trip,days:Int,modifier:Modifier) {
    val p=LocalPalette.current;val f=trip.outbound
    Surface(modifier.rotate(-2f).shadow(10.dp),color=p.paper) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Column(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("כרטיס עלייה למטוס",color=p.muted,fontSize=12.sp)
                Text("${f.from} ← ${f.to}",fontFamily=DisplayFont,fontSize=34.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) { Column{Text("תאריך",color=p.muted,fontSize=11.sp);Text(date(f.date),fontSize=14.sp)};Column{Text("טיסה",color=p.muted,fontSize=11.sp);Text(f.flight,fontSize=14.sp)} }
                Row(horizontalArrangement=Arrangement.spacedBy(22.dp)) { Column{Text("המראה",color=p.muted,fontSize=11.sp);Text(f.departs)};Column{Text("נחיתה",color=p.muted,fontSize=11.sp);Text(f.arrives)} }
                Text("חזרה: ${date(trip.inbound.date)} · ${trip.inbound.departs}\n${trip.inbound.note}",color=p.muted,fontSize=11.sp)
            }
            Column(Modifier.width(88.dp).fillMaxHeight().background(p.accent).padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Text(days.toString(),fontFamily=DisplayFont,fontSize=72.sp,color=if(p.dark)p.snow else Color.White)
                Text(if(days>0)"ימים לטיסה" else "יוצאים לדרך",fontSize=12.sp,color=if(p.dark)p.snow else Color.White)
            }
        }
    }
}
