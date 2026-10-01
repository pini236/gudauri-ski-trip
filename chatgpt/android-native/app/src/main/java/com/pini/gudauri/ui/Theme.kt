package com.pini.gudauri.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.pini.gudauri.R

val DisplayFont=FontFamily(Font(R.font.karantina_bold,FontWeight.Bold))
val BodyFont=FontFamily(Font(R.font.plex_hebrew_regular),Font(R.font.plex_hebrew_semibold,FontWeight.SemiBold))
data class Palette(val snow:Color,val paper:Color,val ink:Color,val muted:Color,val rule:Color,val accent:Color,
    val green:Color,val blue:Color,val red:Color,val black:Color,val sky:Color,val far:Color,val mid:Color,val dark:Boolean) {
    fun piste(color:String)=when(color) { "green"->green;"red"->red;"black"->black;else->blue }
}
val Light=Palette(Color(0xFFEEF2F5),Color.White,Color(0xFF13233A),Color(0xFF4B5A6F),Color(0xFFCBD5DF),Color(0xFF1F5FC4),Color(0xFF1B8A4C),Color(0xFF1F5FC4),Color(0xFFD1342B),Color(0xFF13233A),Color(0xFFDCE8F1),Color(0xFFC4D3E0),Color(0xFFDDE6EE),false)
val Dark=Palette(Color(0xFF0D1522),Color(0xFF16223A),Color(0xFFEAF0F7),Color(0xFFA3B3C8),Color(0xFF2A3B55),Color(0xFFF4B942),Color(0xFF3CC47C),Color(0xFF5B9BFF),Color(0xFFFF6A5F),Color(0xFFEAF0F7),Color(0xFF0B1320),Color(0xFF1B2B49),Color(0xFF15223C),true)
val LocalPalette=staticCompositionLocalOf { Light }
@Composable fun GudauriTheme(dark:Boolean=isSystemInDarkTheme(),content:@Composable ()->Unit) {
    val p=if(dark)Dark else Light
    val colors=if(dark) darkColorScheme(primary=p.accent,background=p.snow,surface=p.paper,onSurface=p.ink,onBackground=p.ink,outline=p.rule)
        else lightColorScheme(primary=p.accent,background=p.snow,surface=p.paper,onSurface=p.ink,onBackground=p.ink,outline=p.rule)
    val base=Typography()
    fun TextStyle.body()=copy(fontFamily=BodyFont)
    val typography=Typography(bodyLarge=base.bodyLarge.body(),bodyMedium=base.bodyMedium.body(),bodySmall=base.bodySmall.body(),labelLarge=base.labelLarge.body(),labelMedium=base.labelMedium.body(),titleMedium=base.titleMedium.body(),titleSmall=base.titleSmall.body(),headlineSmall=TextStyle(fontFamily=DisplayFont,fontWeight=FontWeight.Bold,fontSize=34.sp))
    CompositionLocalProvider(LocalPalette provides p) { MaterialTheme(colorScheme=colors,typography=typography,content=content) }
}

class SignShape:Shape {
    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
        val tip=with(density){18.dp.toPx()}.coerceAtMost(size.width/3)
        return Outline.Generic(Path().apply { moveTo(0f,size.height/2);lineTo(tip,0f);lineTo(size.width,0f);lineTo(size.width,size.height);lineTo(tip,size.height);close() })
    }
}
