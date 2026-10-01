package com.pini.gudauri.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.pini.gudauri.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable fun MeetingScreen(data: MountainData, onHome: () -> Unit, onTheme: () -> Unit,
    onMapLift: (Long) -> Unit, requested: MeetingChoice? = null, linkVersion: Int = 0) {
    val p=LocalPalette.current;val context=LocalContext.current;val now=LocalDaylight.current.actualTime
    val model=remember(data) { MeetingModel(data) }
    var stationId by rememberSaveable { mutableStateOf<String?>(null) }
    var day by rememberSaveable { mutableStateOf((model.days.firstOrNull() ?: now.toLocalDate()).toString()) }
    var time by rememberSaveable { mutableStateOf("12:30") }
    var preset by rememberSaveable { mutableStateOf("") }
    var undoId by rememberSaveable { mutableStateOf<String?>(null) }
    var undoPreset by rememberSaveable { mutableStateOf("") }
    var handledLink by rememberSaveable { mutableIntStateOf(-1) }
    var shareError by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val accessibility=LocalAccessibilityManager.current
    val undoTimeout=accessibility?.calculateRecommendedTimeoutMillis(4500,containsText=true,containsControls=true) ?: 4500L
    LaunchedEffect(linkVersion) {
        if(handledLink!=linkVersion) {
            requested?.let { incoming ->
                stationId=model.station(incoming.stationId)?.id
                day=incoming.day.toString();time=incoming.time.toString();preset="";undoId=null
            }
            handledLink=linkVersion
        }
    }
    LaunchedEffect(undoId,undoTimeout) { if(undoId!=null) { delay(undoTimeout);undoId=null } }
    LaunchedEffect(copied) { if(copied) { delay(2200);copied=false } }
    val clear: () -> Unit = { stationId?.let { undoId=it;undoPreset=preset;stationId=null;preset="" } }
    val pick: (String) -> Unit = { id -> if(id==stationId) clear() else { stationId=id;preset="";undoId=null } }
    val station=stationId?.let(model::station)
    val choice=station?.let { MeetingChoice(it.id,LocalDate.parse(day),LocalTime.parse(time)) }
    Column(Modifier.fillMaxSize().background(p.snow)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("נקודת מפגש",fontFamily=DisplayFont,fontSize=34.sp,modifier=Modifier.weight(1f))
            ThemeControl(onTheme);TextButton(onClick=onHome) { Text("בית ←") }
        }
        Column(Modifier.weight(1f).testTag("meeting-screen").verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            MeetingMap(model,stationId,pick,clear)
            Text(if(station==null) "איפה נפגשים? בחרו אחת מ-${model.stations.size} התחנות" else "${station.name} · ${station.elevation} מ׳\n${station.description}",fontWeight=FontWeight.SemiBold)
            if(station!=null) TextButton(onClick=clear,modifier=Modifier.testTag("clear-meeting")) { Text("ניקוי הבחירה") }
            Text("מתי?",fontFamily=DisplayFont,fontSize=28.sp)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (model.days + LocalDate.parse(day)).distinct().forEach { date ->
                    FilterChip(selected=day==date.toString(),onClick={day=date.toString();preset=""},label={Text(date.format(DateTimeFormatter.ofPattern("EEE d.M",java.util.Locale.forLanguageTag("he"))))})
                }
            }
            TextButton(onClick={val date=LocalDate.parse(day);DatePickerDialog(context,{_,year,month,dateOfMonth->day=LocalDate.of(year,month+1,dateOfMonth).toString();preset=""},date.year,date.monthValue-1,date.dayOfMonth).show()}) { Text("יום אחר") }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                model.times.forEach { hour -> FilterChip(selected=time==hour.toString(),onClick={time=hour.toString();preset=""},label={Text(hour.toString())}) }
            }
            OutlinedButton(onClick={val hour=LocalTime.parse(time);TimePickerDialog(context,{_,h,m->time=LocalTime.of(h,m).toString();preset=""},hour.hour,hour.minute,true).show()}) { Text("שעה אחרת · $time") }
            Text("נקודות קבועות לחבר׳ה",fontFamily=DisplayFont,fontSize=28.sp)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                model.presets.forEach { suggestion ->
                    FilterChip(selected=preset==suggestion.id,onClick={stationId=suggestion.stationId;time=suggestion.time.toString();preset=suggestion.id;undoId=null},label={Text("${suggestion.label} · ${suggestion.time}")},modifier=Modifier.testTag("meeting-preset-${suggestion.id}"))
                }
            }
            Hint("אלו הצעות לנקודת מפגש, לא אישור תיאום של הקבוצה או של פתיחת רכבל.")
            if(station==null || choice==null) Surface(color=p.paper,border=BorderStroke(1.dp,p.rule),modifier=Modifier.fillMaxWidth().testTag("meeting-empty")) {
                Column(Modifier.padding(20.dp)) { Text("כרטיס המפגש מחכה",fontFamily=DisplayFont,fontSize=30.sp);Hint("בוחרים תחנה על המפה, ברשימה או נקודה קבועה למעלה.") }
            } else {
                Surface(color=p.paper,border=BorderStroke(1.dp,p.rule),modifier=Modifier.fillMaxWidth().testTag("meeting-card")) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("כרטיס מפגש · גודאורי",color=p.muted,fontSize=12.sp)
                        Text("${station.name} · $time",fontFamily=DisplayFont,fontSize=38.sp)
                        Text("${choice.day.format(DateTimeFormatter.ofPattern("d.M.yyyy"))} · ${station.elevation} מ׳")
                        Text(station.description)
                        Text(MeetingModel.countdown(choice,now.toInstant()),fontWeight=FontWeight.SemiBold)
                        TextButton(onClick=clear) { Text("ביטול נקודת המפגש") }
                    }
                }
                Text("איך מגיעים?",fontFamily=DisplayFont,fontSize=28.sp)
                val routes=remember(model,station.id) { model.routes(station) }
                if(routes.isEmpty()) Hint("אין בנתונים מסלול שמגיע לכאן.")
                routes.forEach { route ->
                    Surface(color=p.paper,modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(route.from,color=p.muted,fontSize=12.sp)
                            Text(if(route.liftId!=null) "⇡ ${data.lifts.first { it.id==route.liftId }.name} ← נקודת המפגש" else route.runKeys.joinToString(" ← ")+" ← נקודת המפגש")
                        }
                    }
                }
                Hint("חיבורים לפי נתוני המפה בלבד. אין כאן הוראות ניווט, זמני הגעה או מידע על תורים ופתיחה.")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={scope.launch { sharing=true;shareError=null;try { shareMeetingCard(context,model,choice) } catch(error: CancellationException) { throw error } catch(_: Exception) { shareError="שיתוף התמונה לא הצליח. ניתן לשתף את הטקסט או להעתיק את הקישור." } finally { sharing=false } }},enabled=!sharing) { Text(if(sharing) "מכין תמונה…" else "שיתוף עם תמונה") }
                    OutlinedButton(onClick={runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,model.message(choice)),"שיתוף נקודת מפגש")) }.onFailure { shareError="אין אפליקציה זמינה לשיתוף. אפשר להעתיק את הקישור." }}) { Text("שיתוף טקסט") }
                    OutlinedButton(onClick={runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/?text="+Uri.encode(model.message(choice))))) }.onFailure { shareError="וואטסאפ אינו זמין. אפשר להשתמש בשיתוף טקסט." }}) { Text("שליחה בוואטסאפ") }
                    TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("נקודת מפגש",AppLinks.meeting(choice)));copied=true}) { Text(if(copied) "הקישור הועתק" else "העתקת הקישור") }
                    TextButton(onClick={onMapLift(station.ends.first().lift.id)}) { Text("לראות במפת המסלולים") }
                }
                shareError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            }
            Text("תחנות — חלופה נגישה למפה",fontFamily=DisplayFont,fontSize=28.sp)
            model.stations.forEach { item ->
                OutlinedButton(onClick={pick(item.id)},modifier=Modifier.fillMaxWidth().testTag("meeting-station-${item.id}").semantics { contentDescription="${item.description}, ${item.elevation} מטר" }) {
                    Column(Modifier.fillMaxWidth()) { Text(item.name,fontWeight=FontWeight.SemiBold);Text(item.description,fontSize=12.sp);Text("${item.elevation} מ׳",fontSize=12.sp) }
                }
            }
        }
        // Keep the timed action reachable even when cancellation happens in the
        // card or station list, far below the map. Respect Android accessibility.
        undoId?.let { previous ->
            Surface(color=p.paper,border=BorderStroke(1.dp,p.rule),modifier=Modifier.fillMaxWidth().testTag("meeting-undo").semantics { liveRegion=LiveRegionMode.Polite }) {
                Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("הבחירה בוטלה",modifier=Modifier.weight(1f))
                    TextButton(onClick={stationId=previous;preset=undoPreset;undoId=null}) { Text("החזרה") }
                }
            }
        }
    }
}

@Composable private fun MeetingMap(model: MeetingModel, selected: String?, pick: (String) -> Unit, clear: () -> Unit) {
    val p=LocalPalette.current
    if(model.stations.isEmpty()) { Hint("אין תחנות זמינות בנתוני המפה.");return }
    val bounds=remember(model) { val points=model.stations.map { it.point };floatArrayOf(points.minOf { it.x },points.minOf { it.z },points.maxOf { it.x },points.maxOf { it.z }) }
    var cx by rememberSaveable { mutableFloatStateOf((bounds[0]+bounds[2])/2) }
    var cz by rememberSaveable { mutableFloatStateOf((bounds[1]+bounds[3])/2) }
    var span by rememberSaveable { mutableFloatStateOf(max(bounds[2]-bounds[0],bounds[3]-bounds[1])*1.5f) }
    var focused by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selected) { if(selected==null) focused=null else if(focused!=selected) model.station(selected)?.let { cx=it.point.x;cz=it.point.z;span=1800f;focused=selected } }
    val pickLatest by rememberUpdatedState(pick);val clearLatest by rememberUpdatedState(clear)
    Box(Modifier.fillMaxWidth().height(320.dp).border(BorderStroke(1.dp,p.rule))) {
        Canvas(Modifier.fillMaxSize().background(p.snow).semantics { contentDescription="מפת ${model.stations.size} תחנות. רשימת תחנות נגישה מופיעה למטה." }
            .pointerInput(Unit) { detectTransformGestures { _,pan,zoom,_ -> cx-=pan.x*span/size.width;cz-=pan.y*span/size.width;span=(span/zoom).coerceIn(350f,18000f) } }
            .pointerInput(cx,cz,span,selected) { detectTapGestures { tap ->
                val scale=size.width/span
                val closest=model.stations.minByOrNull { s ->
                    val x=(s.point.x-cx)*scale+size.width/2;val y=(s.point.z-cz)*scale+size.height/2
                    min(hypot(x-tap.x,y-tap.y),hypot(x-tap.x,y-24.dp.toPx()-tap.y))
                }
                closest?.let { s ->
                    val x=(s.point.x-cx)*scale+size.width/2;val y=(s.point.z-cz)*scale+size.height/2
                    if(min(hypot(x-tap.x,y-tap.y),hypot(x-tap.x,y-24.dp.toPx()-tap.y))<=44.dp.toPx()) pickLatest(s.id) else clearLatest()
                }
            } }) {
            val scale=size.width/span
            fun at(point: Point)=Offset((point.x-cx)*scale+size.width/2,(point.z-cz)*scale+size.height/2)
            fun line(points: List<Point>,color: androidx.compose.ui.graphics.Color,width: Float) { if(points.size>=2) drawPath(Path().apply { points.forEachIndexed { i,point -> val q=at(point);if(i==0)moveTo(q.x,q.y)else lineTo(q.x,q.y) } },color,style=Stroke(width)) }
            model.data.terrain.contours.forEach { line(it.points,p.muted.copy(alpha=.12f),1f) }
            model.pistes.forEach { run -> run.segments.filterNot { it.area }.forEach { line(it.points,p.piste(run.color),2.dp.toPx()) } }
            model.lifts.forEach { line(it.points,p.muted,1.dp.toPx()) }
            model.stations.forEach { station ->
                val q=at(station.point);val head=q-Offset(0f,24.dp.toPx());val color=if(station.id==selected)p.accent else p.blue
                drawLine(color,q,head,5.dp.toPx());drawCircle(color,12.dp.toPx(),head);drawCircle(p.paper,4.dp.toPx(),head)
                if(station.id==selected) drawCircle(p.ink,15.dp.toPx(),head,style=Stroke(2.dp.toPx()))
            }
        }
        OutlinedButton(onClick={cx=(bounds[0]+bounds[2])/2;cz=(bounds[1]+bounds[3])/2;span=max(bounds[2]-bounds[0],bounds[3]-bounds[1])*1.5f},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=p.paper)) { Text("כל ההר") }
    }
}
