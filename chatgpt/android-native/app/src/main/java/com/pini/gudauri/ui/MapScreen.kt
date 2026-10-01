package com.pini.gudauri.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.res.ResourcesCompat
import com.pini.gudauri.R
import com.pini.gudauri.data.*
import com.pini.gudauri.map.*
import kotlinx.coroutines.launch
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MapScreen(data:MountainData,favorites:Set<String>,onFavorite:(String)->Unit,onHome:()->Unit,onTheme:()->Unit,requestedRun:String?=null,linkVersion:Int=0,requestedLift:Long?=null,onMeet:()->Unit={}) {
    val p=LocalPalette.current;val context=LocalContext.current
    val chipColors=FilterChipDefaults.filterChipColors(containerColor=p.snow,labelColor=p.muted,selectedContainerColor=p.paper,selectedLabelColor=p.ink)
    val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var liftId by rememberSaveable { mutableStateOf<Long?>(null) }
    var top by rememberSaveable { mutableStateOf(false) }
    var hidden by rememberSaveable(stateSaver=listSaver<Set<String>,String>(save={it.toList()},restore={it.toSet()})) { mutableStateOf(emptySet<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var surface by remember { mutableStateOf<MountainSurface?>(null) }
    var snapshot by remember { mutableStateOf(MapSnapshot(emptyList(),0f,10f,false)) }
    val supportsGL=remember { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.reqGlEsVersion>=0x30000 }
    var fallback by remember { mutableStateOf(!supportsGL) }
    val cameraSaver=remember { listSaver<CameraState,Float>(save={listOf(it.x,it.z,it.distance,it.azimuth,it.pitch,if(it.topView)1f else 0f)},restore={CameraState(it[0],it[1],it[2],it[3],it[4],it[5]==1f)}) }
    var fallbackCamera by rememberSaveable(stateSaver=cameraSaver) { mutableStateOf(CameraState(-1000f,1800f,7000f,topView=true)) }
    var savedCamera by rememberSaveable { mutableStateOf(floatArrayOf()) }
    val sheet=rememberBottomSheetScaffoldState();val scope=rememberCoroutineScope()
    val named=remember(data) { data.pistes.filter { it.named }.sortedWith(compareBy<Piste> { listOf("green","blue","red","black").indexOf(it.color) }.thenBy { it.key }) }
    val choose:(String,Boolean)->Unit={ key,focus ->
        focusManager.clearFocus();keyboard?.hide()
        selected=key;liftId=null
        data.pistesByKey[key]?.let { run ->
            hidden=hidden-run.color-"unnamed";surface?.filters(hidden);surface?.selection(key)
            if(focus)surface?.focus(piste=run)
            if(focus&&fallback) {
                val points=run.segments.flatMap { it.points }
                fallbackCamera=fallbackCamera.copy(x=(points.minOf{it.x}+points.maxOf{it.x})/2,z=(points.minOf{it.z}+points.maxOf{it.z})/2,distance=max(1000f,max(points.maxOf{it.x}-points.minOf{it.x},points.maxOf{it.z}-points.minOf{it.z})*2))
            }
        }
        scope.launch { sheet.bottomSheetState.partialExpand() }
    }
    val chooseLift:(Long)->Unit={ id ->
        selected=null;liftId=id;hidden=hidden-"lifts";surface?.filters(hidden);surface?.selection(null)
        data.lifts.find { it.id==id }?.let { lift ->
            surface?.focus(lift=lift)
            if(fallback) fallbackCamera=fallbackCamera.copy(x=lift.points.map { it.x }.average().toFloat(),z=lift.points.map { it.z }.average().toFloat(),distance=max(1000f,lift.length*2f))
        }
        scope.launch { sheet.bottomSheetState.partialExpand() }
    }
    val chooseLatest by rememberUpdatedState(choose);val chooseLiftLatest by rememberUpdatedState(chooseLift)
    var handledLink by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(linkVersion, surface, fallback) {
        if (handledLink != linkVersion && (surface != null || fallback)) {
            if (requestedRun != null) chooseLatest(requestedRun, true)
            else if(requestedLift!=null) chooseLiftLatest(requestedLift)
            else { selected=null;liftId=null;surface?.selection(null) }
            handledLink = linkVersion
        }
    }
    BackHandler(selected!=null || liftId!=null || sheet.bottomSheetState.currentValue==SheetValue.Expanded) {
        if(sheet.bottomSheetState.currentValue==SheetValue.Expanded)scope.launch { sheet.bottomSheetState.partialExpand() }
        else { selected=null;liftId=null;surface?.selection(null) }
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(surface,lifecycle) {
        val observer=LifecycleEventObserver { _,event -> when(event) {
            Lifecycle.Event.ON_RESUME->surface?.onResume()
            Lifecycle.Event.ON_PAUSE->surface?.onPause()
            else->Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);surface?.onPause() }
    }
    BottomSheetScaffold(scaffoldState=sheet,sheetPeekHeight=116.dp,
        sheetContainerColor=p.paper,sheetContentColor=p.ink,
        sheetShape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=16.dp,topEnd=16.dp),
        topBar={
            Column(Modifier.background(p.snow)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("מפת מסלולים",fontFamily=DisplayFont,fontSize=34.sp,modifier=Modifier.weight(1f))
                    ThemeControl(onTheme)
                    TextButton(onClick=onMeet){Text("מפגש")}
                    TextButton(onClick=onHome){Text("בית ←")}
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=!top,onClick={top=false;surface?.mode(false)},label={Text("תלת־ממד")},enabled=!fallback,colors=chipColors,shape=androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                    FilterChip(selected=top||fallback,onClick={top=true;surface?.mode(true)},label={Text("מבט על")},colors=chipColors,shape=androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                    listOf("green" to "ירוק","blue" to "כחול","red" to "אדום","black" to "שחור","lifts" to "רכבלים","unnamed" to "ללא שם").forEach { (c,label) ->
                        FilterChip(selected=c !in hidden,onClick={hidden=if(c in hidden)hidden-c else hidden+c;surface?.filters(hidden)},label={Text(label)},leadingIcon={Box(Modifier.size(10.dp).background(if(c=="lifts"||c=="unnamed")p.muted else p.piste(c)))},colors=chipColors,shape=androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                    }
                }
            }
        },
        sheetContent={
            val run=selected?.let { data.pistesByKey[it] };val lift=liftId?.let { id->data.lifts.find { it.id==id } }
            Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { scope.launch { if(sheet.bottomSheetState.currentValue==SheetValue.Expanded)sheet.bottomSheetState.partialExpand() else sheet.bottomSheetState.expand() } }.padding(vertical=4.dp)) {
                        Text(run?.key ?: lift?.name ?: "כל המסלולים",fontFamily=DisplayFont,fontSize=34.sp,color=run?.let { p.piste(it.color) } ?: p.ink,
                            modifier=Modifier.testTag(if(run!=null) "selected-run-${run.key}" else if(lift!=null) "selected-lift-${lift.id}" else "all-runs-heading"))
                        Text(if(run!=null)"${length(run.length)} · ${if(data.stats.getValue(run.key).available) "ירידה ${data.stats.getValue(run.key).drop} מ׳" else "ללא פרופיל קווי"} · ${difficulty(run.color)}" else if(lift!=null)"${length(lift.length)} · ${liftKind(lift.kind)}" else "${named.size} מסלולים · משכו למעלה או לחצו לרשימה",fontSize=12.sp,color=p.muted)
                    }
                    if(run!=null)IconButton(onClick={onFavorite(run.key)}) { Text(if(run.key in favorites)"★" else "☆",fontSize=30.sp,color=p.accent,modifier=Modifier.semantics { contentDescription=if(run.key in favorites)"הסר ממועדפים" else "שמור במועדפים" }) }
                    if(run!=null || lift!=null)TextButton(onClick={selected=null;liftId=null;surface?.selection(null);scope.launch{sheet.bottomSheetState.expand()}}){Text("הכול")}
                    else TextButton(onClick={scope.launch{sheet.bottomSheetState.expand()}}){Text("פתח")}
                }
                HorizontalDivider(Modifier.padding(top=12.dp),color=p.rule)
                if(run!=null)PisteDetails(run,data,chooseLatest,chooseLiftLatest)
                else if(lift!=null)LiftDetails(lift,data,chooseLatest)
                else {
                    Row(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        OutlinedTextField(query,{query=it},placeholder={Text("חיפוש מסלול")},singleLine=true,modifier=Modifier.weight(1f),textStyle=MaterialTheme.typography.bodyMedium)
                        IconToggleButton(onlyFavorites,{onlyFavorites=it}){Text(if(onlyFavorites)"★" else "☆",fontSize=27.sp,modifier=Modifier.semantics { contentDescription="הצג מועדפים" })}
                    }
                    val list=named.filter { (query.isBlank() || it.key.contains(query,ignoreCase=true)) && (!onlyFavorites || it.key in favorites) }
                    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
                        items(list,key={it.key}) { run ->
                            Row(Modifier.fillMaxWidth().clickable{chooseLatest(run.key,true)}.padding(horizontal=18.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.width(5.dp).height(28.dp).background(p.piste(run.color)))
                                Column(Modifier.weight(1f)) { Text(run.key,fontWeight=FontWeight.SemiBold);run.research?.partial?.let { Text("מיפוי חלקי",fontSize=11.sp,color=p.muted) } }
                                if(run.key in favorites)Text("★",color=p.accent)
                                Text(length(run.length),fontSize=12.sp,color=p.muted)
                            };HorizontalDivider(Modifier.padding(horizontal=18.dp),color=p.rule)
                        }
                        if(list.isEmpty())item{Text("אין מסלולים להצגה",Modifier.padding(24.dp),color=p.muted)}
                        item { Text("קטעים ללא שם",fontFamily=DisplayFont,fontSize=25.sp,modifier=Modifier.padding(18.dp)) }
                        if(!onlyFavorites && query.isBlank())items(data.pistes.filterNot { it.named },key={it.key}) { run ->
                            TextButton(onClick={chooseLatest(run.key,true)},modifier=Modifier.fillMaxWidth()){Text("קטע ללא שם · ${length(run.length)}",color=p.piste(run.color))}
                        }
                        item { Text("קווים: OpenStreetMap · ${data.fetched}\nצבעים ושמות: המפה הרשמית של MTA\nגבהים: מודל פני השטח, כ־30 מ׳\n© OpenStreetMap contributors · ODbL",Modifier.padding(18.dp),fontSize=11.sp,color=p.muted) }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if(fallback)FallbackMap(data,hidden,selected,fallbackCamera,{fallbackCamera=it},chooseLatest)
            else AndroidView(factory={ ctx -> MountainSurface(ctx,data,{value->
                snapshot=value
                surface?.mountain?.camera?.let { camera -> savedCamera=floatArrayOf(camera.x,camera.z,camera.distance,camera.azimuth,camera.pitch,if(camera.topView)1f else 0f) }
            },{pick->pick.piste?.let { chooseLatest(it,true) } ?: pick.lift?.let(chooseLiftLatest)},{fallback=true}).also {
                surface=it;it.selection(selected);it.filters(hidden);it.theme(p.dark)
                if(savedCamera.size==6) { val c=savedCamera;it.mountain.camera=CameraState(c[0],c[1],c[2],c[3],c[4],c[5]==1f);it.requestRender() }
                else if(top)it.mode(true)
            } },
                update={it.theme(p.dark);it.selection(selected);it.filters(hidden)},modifier=Modifier.fillMaxSize())
            if(!fallback)MapLabels(snapshot)
            Column(Modifier.align(Alignment.TopStart).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                MapButton("+","התקרב"){if(fallback)fallbackCamera=fallbackCamera.copy(distance=(fallbackCamera.distance*.75f).coerceAtLeast(350f))else surface?.zoom(.75f)}
                MapButton("−","התרחק"){if(fallback)fallbackCamera=fallbackCamera.copy(distance=(fallbackCamera.distance*1.33f).coerceAtMost(32000f))else surface?.zoom(1.33f)}
                MapButton("⤢","הצג הכל"){if(fallback)fallbackCamera=CameraState(-1000f,1800f,7000f,topView=true)else surface?.home()}
                MapButton("↑","אפס כיוון",snapshot.heading*180f/PI.toFloat()){surface?.north()}
            }
            TextButton(onClick={if(fallback){val q=DataParser.project(42.532,44.4945);fallbackCamera=fallbackCamera.copy(x=q.x,z=q.z,distance=8000f)}else surface?.kobi()},modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp).background(p.paper)) { Text("צד Kobi",color=p.ink) }
            if(!snapshot.ready && !fallback)CircularProgressIndicator(Modifier.align(Alignment.Center))
            if(snapshot.ready&&!fallback) {
                val mpp=snapshot.metersPerPixel
                val meters=listOf(50,100,200,250,500,1000,2000).firstOrNull{it/mpp>=60*LocalDensity.current.density}?:2000
                Column(Modifier.testTag("native-map-ready").align(Alignment.BottomStart).padding(12.dp).background(p.paper.copy(alpha=.85f)).padding(6.dp)) {
                    Box(Modifier.width((meters/mpp/LocalDensity.current.density).dp).height(4.dp).border(BorderStroke(1.dp,p.muted)))
                    Text(if(meters>=1000)"${meters/1000} km" else "$meters m",fontSize=10.sp,color=p.muted)
                }
            }
            if(fallback)Text("מבט על · תלת־ממד אינו זמין במכשיר הזה",Modifier.align(Alignment.TopCenter).padding(top=12.dp).background(p.paper).padding(8.dp),fontSize=11.sp)
        }
    }
}
@Composable private fun MapButton(text:String,description:String,rotation:Float=0f,click:()->Unit) {
    val p=LocalPalette.current
    Surface(onClick=click,color=p.paper,border=BorderStroke(1.dp,p.rule),modifier=Modifier.size(48.dp).semantics{contentDescription=description}) {
        Box(contentAlignment=Alignment.Center){Text(text,fontSize=25.sp,color=p.ink,modifier=Modifier.rotate(rotation))}
    }
}
@Composable private fun MapLabels(snapshot:MapSnapshot) {
    val density=LocalDensity.current.density
    val context=LocalContext.current
    val paint=remember{Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=ResourcesCompat.getFont(context,R.font.plex_hebrew_semibold)}}
    Canvas(Modifier.fillMaxSize()) {
        val kept=mutableListOf<RectF>()
        snapshot.labels.forEach { l ->
            paint.textSize=if(l.priority>=100)11*density else 12*density
            val w=paint.measureText(l.text);val h=paint.textSize;val x=l.x-w/2;val y=l.y-9*density
            val rect=RectF(x-3*density,y-h-3*density,x+w+3*density,y+3*density)
            if(kept.any { RectF.intersects(it,rect) })return@forEach
            kept.add(rect)
            drawIntoCanvas { canvas ->
                paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density;paint.color=android.graphics.Color.WHITE;canvas.nativeCanvas.drawText(l.text,x,y,paint)
                paint.style=Paint.Style.FILL;paint.color=l.color;canvas.nativeCanvas.drawText(l.text,x,y,paint)
            }
        }
    }
}

@Composable private fun FallbackMap(data:MountainData,hidden:Set<String>,selected:String?,camera:CameraState,onCamera:(CameraState)->Unit,choose:(String,Boolean)->Unit) {
    val p=LocalPalette.current
    val cx=camera.x;val cz=camera.z;val span=camera.distance
    val latestCamera by rememberUpdatedState(camera)
    Canvas(Modifier.fillMaxSize().background(p.snow).pointerInput(Unit){detectTransformGestures { _,pan,zoom,_->val c=latestCamera;onCamera(c.copy(x=c.x-pan.x*c.distance/size.width,z=c.z-pan.y*c.distance/size.width,distance=(c.distance/zoom).coerceIn(300f,24000f)))}}
        .pointerInput(hidden,selected,cx,cz,span){detectTapGestures { tap ->
            var key:String?=null;var best=24f*density
            fun screen(q:Point)=Offset((q.x-cx)/span*size.width+size.width/2,(q.z-cz)/span*size.width+size.height/2)
            data.pistes.filter{it.color !in hidden && (it.named || "unnamed" !in hidden)}.forEach { run ->run.segments.forEach{seg->seg.points.zipWithNext().forEach{(a,b)->
                val s=screen(a);val e=screen(b);val v=e-s;val l=v.x*v.x+v.y*v.y;val t=if(l==0f)0f else (((tap.x-s.x)*v.x+(tap.y-s.y)*v.y)/l).coerceIn(0f,1f)
                val d=(tap-s-v*t).getDistance();if(d<best){best=d;key=run.key}
            }} };key?.let{choose(it,false)}
        }}) {
        fun path(points:List<Point>,close:Boolean=false)=Path().apply { points.forEachIndexed{i,q->val x=(q.x-cx)/span*size.width+size.width/2;val y=(q.z-cz)/span*size.width+size.height/2;if(i==0)moveTo(x,y)else lineTo(x,y)};if(close)close() }
        data.terrain.contours.filter{it.height%100==0}.forEach{drawPath(path(it.points),p.rule,style=Stroke(if(it.height%500==0)1.5f else .6f))}
        data.pistes.filter{it.color !in hidden && (it.named || "unnamed" !in hidden)}.forEach { run ->run.segments.forEach{seg->
            if(seg.area)drawPath(path(seg.points,true),p.piste(run.color).copy(alpha=.15f))
            else { val alpha=if(selected==null||run.key==selected)1f else .28f;drawPath(path(seg.points),p.paper.copy(alpha=alpha),style=Stroke(6*density,cap=StrokeCap.Round));drawPath(path(seg.points),p.piste(run.color).copy(alpha=alpha),style=Stroke(3*density,cap=StrokeCap.Round)) }
        } }
        if("lifts" !in hidden)data.lifts.forEach{drawPath(path(it.points),p.muted,style=Stroke(1.5f*density))}
    }
}

fun length(m:Int)=if(m>=1000)"${"%.2f".format(java.util.Locale.US,m/1000f)} ק״מ" else "$m מ׳"
fun difficulty(color:String)=when(color){"green"->"מתחילים";"red"->"בינוני";"black"->"קשה";else->"קל"}
fun liftKind(kind:String)=when(kind){"chair_lift"->"רכבל כיסאות";"gondola"->"גונדולה";"platter"->"מעלית צלחת";"magic_carpet"->"מסוע";else->"מעלית גרירה"}
fun openUrl(context:Context,url:String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) } }
