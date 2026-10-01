package com.pini.gudauri.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pini.gudauri.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

fun formatTripDate(iso: String) = LocalDate.parse(iso).let { "${it.dayOfMonth}.${it.monthValue}.${it.year}" }

@Composable fun FlightTickets(trip: Trip, modifier: Modifier = Modifier) {
    val p = LocalPalette.current; val light = LocalDaylight.current
    var inbound by rememberSaveable(trip.outbound.date, trip.inbound?.date) { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val sound = rememberTicketSounds()
    val haptic = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth()) {
        trip.inbound?.let { returnFlight ->
            val behind = if (inbound) trip.outbound else returnFlight
            Surface(color=p.paper,border=BorderStroke(1.dp,p.rule),modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp).rotate(1.5f)) {
                TextButton(enabled=!switching,onClick={
                    switching=true; sound("slide"); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        delay(250); inbound=!inbound
                        delay(170); sound("land")
                        delay(220); switching=false
                    }
                },modifier=Modifier.fillMaxWidth().testTag("switch-flight")) {
                    Text("${if(inbound) "הלוך" else "חזור"} · ${formatTripDate(behind.date)} · הצג כרטיס",fontSize=12.sp)
                }
            }
        }
        AnimatedContent(targetState=inbound && trip.inbound!=null,label="flight-pass",transitionSpec={
            fadeIn() + slideInHorizontally { it / 8 } togetherWith fadeOut()
        }) { showInbound ->
            val flight = if (showInbound) requireNotNull(trip.inbound) else trip.outbound
            FlightPass(flight,trip,showInbound,GudauriTime.daysToFlight(flight,light.actualTime.toInstant()),sound)
        }
        trip.skiDays?.let { ski ->
            Text("${ski.dates.size} ימי סקי · ${formatTripDate(ski.from.toString())}–${formatTripDate(ski.to.toString())}",Modifier.padding(top=12.dp),fontSize=12.sp,color=p.muted)
        }
    }
}

@Composable private fun FlightPass(flight: Flight, trip: Trip, inbound: Boolean, days: Int, sound: (String) -> Unit) {
    val p = LocalPalette.current; val haptic = LocalHapticFeedback.current
    var torn by rememberSaveable(flight.date, flight.flight) { mutableStateOf(false) }
    LaunchedEffect(torn) { if (torn) { delay(2600); torn=false } }
    Surface(Modifier.fillMaxWidth().rotate(-1.5f).shadow(8.dp).testTag(if(inbound) "flight-return" else "flight-outbound"),color=p.paper) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Column(Modifier.weight(1f).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("כרטיס עלייה למטוס · ${if(inbound) "חזור" else "הלוך"}",color=p.muted,fontSize=11.sp)
                Text(trip.airline.ifBlank { "חברת תעופה לא ידועה" },fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text(flight.fromCode.ifBlank { "—" },fontFamily=DisplayFont,fontSize=38.sp)
                        Text("→",fontSize=24.sp,color=p.muted)
                        Text(flight.toCode.ifBlank { "—" },fontFamily=DisplayFont,fontSize=38.sp)
                    }
                }
                Text("${flight.from} אל ${flight.to}",fontSize=15.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TicketField("תאריך",formatTripDate(flight.date),Modifier.weight(1f))
                    TicketField("טיסה",flight.flight,Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TicketField("המראה",flight.departs,Modifier.weight(1f))
                    TicketField("נחיתה",flight.arrives,Modifier.weight(1f))
                }
                Text("${trip.members.size} נוסעים · כבודה: ${trip.baggage.ifBlank { "לא ידועה" }}",fontSize=12.sp)
                if (flight.note.isNotBlank()) Text(flight.note,fontSize=12.sp,color=p.muted)
                Hint("תצוגת פרטי הטיסה בלבד, לא כרטיס סריק.")
            }
            Box(Modifier.width(76.dp).fillMaxHeight().background(p.snow),contentAlignment=Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(visible=!torn,enter=fadeIn(),exit=fadeOut()+slideOutVertically { it }) {
                    Column(Modifier.fillMaxHeight().background(p.accent).clickable {
                        torn=true; sound("tear"); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }.semantics { contentDescription="תלישת ספח כרטיס ${if(inbound) "חזור" else "הלוך"}" }
                        .testTag("ticket-stub").padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        val ink = if(p.dark) p.snow else Color.White
                        Text(days.toString(),fontFamily=DisplayFont,fontSize=64.sp,color=ink)
                        Text(GudauriTime.countdownLabel(flight,LocalDaylight.current.actualTime.toInstant(),p.dark),fontSize=12.sp,color=ink)
                        Spacer(Modifier.height(20.dp)); Text("תלשו כאן",fontSize=11.sp,color=ink)
                    }
                }
            }
        }
    }
}

@Composable private fun TicketField(label: String, value: String, modifier: Modifier) {
    Column(modifier) { Text(label,color=LocalPalette.current.muted,fontSize=11.sp);Text(value,fontSize=14.sp) }
}

/** Local samples only; system volume/ringer settings and foreground lifecycle are respected. */
@Composable private fun rememberTicketSounds(): (String) -> Unit {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current.lifecycle
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val sounds = remember { ConcurrentHashMap<String, Int>() }
    val ready = remember { ConcurrentHashMap.newKeySet<Int>() }
    val pool = remember { SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build() }
    DisposableEffect(pool, lifecycle) {
        pool.setOnLoadCompleteListener { _, id, status -> if(status==0) ready.add(id) }
        listOf("tear","slide","land").forEach { name ->
            runCatching { context.assets.openFd("audio/ticket-$name.wav").use { sounds[name]=pool.load(it,1) } }
        }
        val observer = LifecycleEventObserver { _, event -> if(event==Lifecycle.Event.ON_PAUSE) pool.autoPause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);sounds.clear();ready.clear();pool.release() }
    }
    return { name ->
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && audio.ringerMode==AudioManager.RINGER_MODE_NORMAL) {
            sounds[name]?.takeIf { it in ready }?.let { pool.play(it,.75f,.75f,1,0,1f) }
        }
    }
}
