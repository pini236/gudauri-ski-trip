package com.pini.gudauri.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.pini.gudauri.R
import com.pini.gudauri.data.*
import java.io.File
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A local, square meeting card. No screenshot, network request or group member data. */
internal fun renderMeetingCard(context: Context, model: MeetingModel, choice: MeetingChoice): Bitmap {
    val station=requireNotNull(model.station(choice.stationId))
    val bitmap=Bitmap.createBitmap(1080,1080,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(238,242,245))
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
    val scale=1080f/2200;val ox=station.point.x-1100;val oz=station.point.z-1210
    fun line(points: List<Point>,color: Int,width: Float) {
        val path=Path();points.forEachIndexed { i,q -> if(i==0) path.moveTo((q.x-ox)*scale,(q.z-oz)*scale) else path.lineTo((q.x-ox)*scale,(q.z-oz)*scale) }
        paint.style=Paint.Style.STROKE;paint.color=color;paint.strokeWidth=width;canvas.drawPath(path,paint)
    }
    model.data.terrain.contours.forEach { line(it.points,Color.rgb(206,216,226),1.5f) }
    model.pistes.forEach { run -> run.segments.filterNot { it.area }.forEach { segment ->
        line(segment.points,Color.WHITE,11f)
        line(segment.points,when(run.color) { "green"->Color.rgb(27,138,76);"red"->Color.rgb(209,52,43);"black"->Color.rgb(19,35,58);else->Color.rgb(31,95,196) },6f)
    } }
    model.lifts.forEach { line(it.points,Color.WHITE,6f);line(it.points,Color.rgb(58,69,86),3f) }
    val x=(station.point.x-ox)*scale;val z=(station.point.z-oz)*scale
    paint.style=Paint.Style.STROKE;paint.strokeWidth=8f;paint.color=Color.rgb(19,35,58);canvas.drawLine(x,z,x,z-90,paint)
    paint.style=Paint.Style.FILL;paint.color=Color.rgb(244,185,66);canvas.drawCircle(x,z-90,48f,paint)
    paint.style=Paint.Style.STROKE;paint.color=Color.rgb(19,35,58);paint.strokeWidth=6f;canvas.drawCircle(x,z-90,48f,paint)
    paint.style=Paint.Style.FILL;paint.color=Color.WHITE;canvas.drawCircle(x,z-90,18f,paint)
    paint.color=Color.rgb(19,35,58);canvas.drawRect(0f,680f,1080f,1080f,paint)
    val text=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;typeface=ResourcesCompat.getFont(context,R.font.plex_hebrew_semibold);textSize=36f }
    fun paragraph(value: String,y: Float,height: Int) {
        val layout=StaticLayout.Builder.obtain(value,0,value.length,text,960).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL).setIncludePad(false).setMaxLines(height).build()
        canvas.save();canvas.translate(60f,y);layout.draw(canvas);canvas.restore()
    }
    paragraph("כרטיס מפגש · ${choice.day.format(DateTimeFormatter.ofPattern("d.M.yyyy"))} · ${station.elevation} מ׳",714f,2)
    paint.style=Paint.Style.FILL;paint.color=Color.WHITE;paint.typeface=ResourcesCompat.getFont(context,R.font.karantina_bold);paint.textSize=120f
    while(paint.measureText(station.name)>650 && paint.textSize>40) paint.textSize-=4
    canvas.drawText(station.name,60f,875f,paint)
    paint.color=Color.rgb(244,185,66);paint.textSize=135f;canvas.drawText(choice.time.toString(),780f,875f,paint)
    text.textSize=30f;paragraph(station.description,922f,3)
    paint.textSize=21f;paint.color=Color.rgb(203,213,223);paint.typeface=text.typeface
    canvas.drawText("© OpenStreetMap contributors · ODbL · SRTM",60f,1060f,paint)
    return bitmap
}

suspend fun shareMeetingCard(context: Context, model: MeetingModel, choice: MeetingChoice) {
    val uri=withContext(Dispatchers.IO) {
        val directory=File(context.cacheDir,"meeting-cards").apply { check(mkdirs() || isDirectory) }
        // Only expire files created by this feature, never another app/user directory.
        directory.listFiles()?.filter { it.name.matches(Regex("meet-[0-9]+\\.png")) && System.currentTimeMillis()-it.lastModified()>86_400_000 }
            ?.forEach { it.delete() }
        val file=File(directory,"meet-${System.nanoTime()}.png")
        val bitmap=renderMeetingCard(context,model,choice)
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } } finally { bitmap.recycle() }
        FileProvider.getUriForFile(context,"${context.packageName}.files",file)
    }
    val send=Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)
        .putExtra(Intent.EXTRA_TEXT,model.message(choice)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData=ClipData.newUri(context.contentResolver,"כרטיס מפגש",uri)
    context.startActivity(Intent.createChooser(send,"שיתוף נקודת מפגש"))
}
