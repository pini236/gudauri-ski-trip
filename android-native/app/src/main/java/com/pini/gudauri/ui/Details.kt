package com.pini.gudauri.ui

import android.content.Intent
import android.graphics.Paint
import android.webkit.*
import android.annotation.SuppressLint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.pini.gudauri.data.*
import java.net.URLEncoder
import kotlin.math.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun PisteDetails(run:Piste,data:MountainData,choose:(String,Boolean)->Unit,chooseLift:(Long)->Unit) {
    val p=LocalPalette.current;val context=LocalContext.current
    val stats=data.stats.getValue(run.key)
    val videos=remember(data,run.key){data.videos.filter{it.piste==run.key}}
    var video by remember(run.key){mutableStateOf<Video?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Metric("אורך",length(run.length),Modifier.weight(1f))
                Metric("ירידה","${stats.drop} מ׳",Modifier.weight(1f))
                Metric("קטע תלול","${(stats.maxGrade*100).roundToInt()}%",Modifier.weight(1f))
            }
        }
        item { ElevationChart(run,data.terrain) }
        item { Hint("גבהים ממודל ברזולוציה של כ־30 מ׳, דיוק משוער ±15 מ׳. הקטע התלול מחושב לאורך כ־100 מ׳. האורך הוא אורך הקווים שנמצאו.") }
        run.research?.partial?.let { note -> item { Surface(color=p.snow,border=BorderStroke(1.dp,p.rule)){Column(Modifier.padding(12.dp)){Text("מיפוי חלקי",fontWeight=FontWeight.SemiBold,color=p.piste(run.color));Text(note,fontSize=13.sp)}} } }
        item {
            Section("נתוני המסלול")
            Info("גובה עליון","${stats.top} מ׳");Info("גובה תחתון","${stats.bottom} מ׳")
            Info("צבע וקושי","${when(run.color){"green"->"ירוק";"red"->"אדום";"black"->"שחור";else->"כחול"}} · ${difficulty(run.color)}")
            if(run.osmDifficulty.isNotEmpty())Info("דירוג ב־OSM",run.osmDifficulty.joinToString(" / "))
            if(run.refs.isNotEmpty())Info("סימון",run.refs.joinToString(", "))
            if(run.groom.isNotEmpty())Info("הכשרה",if("classic" in run.groom)"מוכשר" else run.groom.joinToString(", "))
            if("yes" in run.lit)Info("תאורה","מסומן כמואר בערב")
            if(run.kind=="ski-way")Hint("דרך מקשרת בין אזורים, לפי המקרא של המפה הרשמית.")
            if(run.kind=="beginner-area")Hint("אזור מתחילים. מוצג קו המרכז בלבד, ללא גבולות האזור.")
            if(!run.named)Hint("קטע בנתוני OSM ללא שם. לא שויך למסלול רשמי; הצבע לפי OSM.")
            if(run.segments.count{!it.area}>1)Hint("${run.segments.count{!it.area}} קטעים נפרדים. האורך הוא סכום הקטעים.")
        }
        item {
            Section("חיבורים")
            listOf("רכבל בראש" to run.fromLifts,"רכבל בתחתית" to run.toLifts).forEach { (title,names) ->
                Text(title,fontSize=12.sp,color=p.muted)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { names.forEach { name -> val l=data.lifts.find{it.name==name};AssistChip(onClick={l?.let{chooseLift(it.id)}},label={Text("⇡ $name")}) } }
                if(names.isEmpty())Text("—")
            }
            listOf("מתחבר אל" to run.joins,"מגיעים מ־" to run.fromPistes).forEach { (title,keys) ->
                Text(title,fontSize=12.sp,color=p.muted)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { keys.forEach { key->AssistChip(onClick={choose(key,true)},label={Text(key,color=data.pistesByKey[key]?.let{p.piste(it.color)}?:p.ink)}) } }
                if(keys.isEmpty())Text("—")
            }
            Hint("חיבורים מחושבים מקרבת קצוות: עד 200 מ׳ לתחנת רכבל, עד 60 מ׳ למסלול. יש לוודא מול המפה הרשמית.")
        }
        run.research?.let { r -> item {
            Section("מקור ומידת ודאות")
            Info("ודאות",when(r.confidence){"high"->"גבוהה";"medium"->"בינונית";else->"נמוכה"})
            Info("זיהוי",if(r.status=="osm-named")"שם שנמצא בהיסטוריית OSM" else "התאמה לפי מיקום — מסקנה מחקרית")
            if(r.historical)Hint("הדרך נמחקה ב־2024. הגאומטריה נלקחה מהיסטוריית העריכה.")
            if(r.gps>0)Info("הקלטות ציבוריות",r.gps.toString())
            Text(r.notes,fontSize=13.sp)
            Spacer(Modifier.height(8.dp));r.sources.forEach { Hint("• $it") }
        } }
        item {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { run.osmIds.forEach{id->AssistChip(onClick={openUrl(context,"https://www.openstreetmap.org/way/$id")},label={Text("OSM $id",fontSize=11.sp)})} }
            OutlinedButton(onClick={ val intent=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"${run.key} · ${length(run.length)}\nhttps://gudauri-ski-trip.vercel.app/#map\n${run.research?.partial.orEmpty()}")};context.startActivity(Intent.createChooser(intent,"שיתוף מסלול")) },modifier=Modifier.fillMaxWidth()){Text("שיתוף מסלול")}
        }
        if(run.named) {
            item { Section("סרטונים") }
            items(videos,key={it.url}) { v ->
                Surface(onClick={video=v},color=p.snow,border=BorderStroke(1.dp,p.rule)) {
                    Column {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Light.ink),contentAlignment=Alignment.Center) {
                            AsyncImage(model="https://i.ytimg.com/vi/${v.url.substringAfter("v=")}/hqdefault.jpg",contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                            Surface(color=Color(0xCE13233A),shape=androidx.compose.foundation.shape.CircleShape){Text("▶",Modifier.padding(horizontal=20.dp,vertical=10.dp),color=Color.White,fontSize=25.sp)}
                        }
                        Column(Modifier.padding(12.dp)){Text(v.title,fontWeight=FontWeight.SemiBold);Text("${v.channel} · ${v.length}",color=p.muted,fontSize=12.sp)}
                    }
                }
            }
            if(videos.isEmpty())item { Hint("עוד אין סרטונים למסלול הזה.") }
            item { TextButton(onClick={openUrl(context,"https://www.youtube.com/results?search_query="+URLEncoder.encode("Gudauri ${run.key} ski","UTF-8"))}){Text("חיפוש סרטונים ביוטיוב ↗")} }
        }
    }
    video?.let { selected -> VideoDialog(selected){video=null} }
}
@Composable private fun Metric(label:String,value:String,modifier:Modifier) {
    val p=LocalPalette.current
    Column(modifier.background(p.snow).padding(10.dp)) { Text(value,fontFamily=DisplayFont,fontSize=26.sp);Text(label,color=p.muted,fontSize=11.sp) }
}
@Composable fun Section(title:String) { Text(title,fontFamily=DisplayFont,fontSize=28.sp,modifier=Modifier.padding(top=8.dp,bottom=6.dp)) }
@Composable fun Hint(text:String) { Text(text,fontSize=12.sp,color=LocalPalette.current.muted) }
@Composable fun Info(label:String,value:String) { Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) { Text(label,color=LocalPalette.current.muted,fontSize=12.sp,modifier=Modifier.width(94.dp));Text(value,fontSize=14.sp,modifier=Modifier.weight(1f)) } }

@Composable private fun ElevationChart(run:Piste,terrain:Terrain) {
    val p=LocalPalette.current
    val profile=remember(run,terrain){terrain.profile(run.segments.filterNot{it.area}.maxByOrNull{it.points.size}?.points.orEmpty())}
    if(profile.size<2)return
    val paint=remember{Paint(Paint.ANTI_ALIAS_FLAG)}
    Column {
        Text("פרופיל גובה",color=p.muted,fontSize=12.sp)
        Canvas(Modifier.fillMaxWidth().height(108.dp).padding(top=8.dp).semantics{contentDescription="פרופיל הגובה של הקטע הארוך ביותר במסלול"}) {
            val lo=profile.minOf{it.second};val hi=profile.maxOf{it.second};val total=max(1f,profile.last().first)
            val path=Path();profile.forEachIndexed { i,(d,h)->val x=d/total*size.width;val y=8+(hi-h)/max(1f,hi-lo)*(size.height-24);if(i==0)path.moveTo(x,y)else path.lineTo(x,y) }
            val area=Path().apply{addPath(path);lineTo(size.width,size.height);lineTo(0f,size.height);close()}
            drawPath(area,Brush.verticalGradient(listOf(p.piste(run.color).copy(alpha=.23f),p.piste(run.color).copy(alpha=.02f))))
            drawPath(path,p.piste(run.color),style=Stroke(2.5f*density,cap=StrokeCap.Round))
        }
        if(run.segments.count{!it.area}>1)Hint("מוצג הקטע הארוך ביותר; הפערים בין הקווים לא חוברו.")
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun LiftDetails(lift:Lift,data:MountainData,choose:(String,Boolean)->Unit) {
    val t=data.terrain;val a=t.elevation(lift.points.first().x,lift.points.first().z).roundToInt();val b=t.elevation(lift.points.last().x,lift.points.last().z).roundToInt()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Info("סוג",liftKind(lift.kind));Info("אורך",length(lift.length));Info("תחנה תחתונה","${min(a,b)} מ׳");Info("תחנה עליונה","${max(a,b)} מ׳");Info("הפרש גובה","${abs(a-b)} מ׳")
            if(lift.duration.isNotBlank())Info("זמן נסיעה","${lift.duration} דק׳")
            if(lift.occupancy.isNotBlank())Info("מקומות",lift.occupancy)
            if(lift.capacity.isNotBlank())Info("קיבולת","${lift.capacity} לשעה")
            if(lift.year.isNotBlank())Info("נבנה",lift.year)
            if(lift.status=="inactive")Hint("לא פעיל לפי נתוני OSM. אין מידע על מצב פעילות בזמן אמת.")
            Hint("הגבהים חושבו ממודל פני השטח, כ־30 מ׳.")
        }
        item { Section("מסלולים מהתחנה העליונה");FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { data.pistes.filter{lift.name in it.fromLifts}.forEach{run->AssistChip(onClick={choose(run.key,true)},label={Text(run.key)})} } }
        item { Section("מסלולים שמסתיימים בתחתית");FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { data.pistes.filter{lift.name in it.toLifts}.forEach{run->AssistChip(onClick={choose(run.key,true)},label={Text(run.key)})} } }
        item { val context=LocalContext.current;TextButton(onClick={openUrl(context,"https://www.openstreetmap.org/way/${lift.id}")}){Text("מקור: OSM ${lift.id}")} }
    }
}

/** WebView is isolated to YouTube's required iframe player; the app and map are native. */
@Composable private fun VideoDialog(video:Video,onClose:()->Unit) {
    val context=LocalContext.current;var player by remember{mutableStateOf<WebView?>(null)}
    DisposableEffect(Unit){onDispose{player?.stopLoading();player?.destroy()}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(video.title,fontWeight=FontWeight.SemiBold)
                AndroidView(factory={ctx->
                    @SuppressLint("SetJavaScriptEnabled") // Required for YouTube's isolated, fixed-origin iframe. No native JS bridge.
                    val webView=WebView(ctx).apply {
                    settings.javaScriptEnabled=true;settings.domStorageEnabled=true
                    settings.mediaPlaybackRequiresUserGesture=false
                    settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
                        val host=request.url.host.orEmpty();return if(host=="www.youtube-nocookie.com"||host.endsWith(".youtube.com"))false else {openUrl(context,request.url.toString());true}
                    }}
                    val id=video.url.substringAfter("v=").takeWhile{it.isLetterOrDigit()||it=='-'||it=='_'}
                    loadDataWithBaseURL("https://gudauri-ski-trip.vercel.app/","<html><body style='margin:0;background:#13233a'><iframe style='width:100%;height:100%;border:0' src='https://www.youtube-nocookie.com/embed/$id?autoplay=1&rel=0' allow='autoplay;encrypted-media;picture-in-picture' allowfullscreen></iframe></body></html>","text/html","utf-8",null)
                    player=this
                };webView},modifier=Modifier.fillMaxWidth().aspectRatio(16f/9f).padding(top=10.dp))
                Row { TextButton(onClick={openUrl(context,video.url)}){Text("פתח ביוטיוב ↗")};TextButton(onClick=onClose){Text("סגור")} }
            }
        }
    }
}
