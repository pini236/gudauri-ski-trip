package io.github.pini236.skiapp.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.group.Muted
import io.github.pini236.skiapp.group.Sheet
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.meet.iso
import io.github.pini236.skiapp.ui.Ski

/**
 * The language, from the home page (3.10.2026, asked through the Google Play session; on the site the same list sits
 * in the settings, LP3, decision 41). A small tag in the head with the language's mark (עב, EN, RU, KA) opens the
 * list: each language in its own script and title font, so anyone finds theirs even in a language they do not read,
 * and "from the phone" goes back to the phone's language.
 */
@Composable
fun LangTag(halo: Shadow, onClick: () -> Unit) {
    val c = Ski.colors
    val l = Lang.current(LocalContext.current.resources)
    val label = stringResource(R.string.app_lang_button, l.name)
    Box(
        Modifier.widthIn(min = 44.dp).heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.border(1.5.dp, c.ink).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(l.code, style = Ski.type.label.copy(fontSize = 13.sp, shadow = halo, lineHeight = 1.3.em), color = c.ink)
        }
    }
}

/** The list (the site's .lang-sheet): a tap on a language saves it and the app comes back in it. */
@Composable
fun BoxScope.LangSheet(onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val c = Ski.colors
    val current = Lang.current(LocalContext.current.resources)
    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val title = stringResource(R.string.about_language) + if (current.tag == "en") "" else " · " + iso("Language")
            Text(title, Modifier.weight(1f).semantics { heading() }, style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (32f / 44f)), color = c.ink)
            val close = stringResource(R.string.common_close)
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onDismiss).semantics { contentDescription = close }, contentAlignment = Alignment.Center) {
                Text("✕", style = Ski.type.body.copy(fontSize = 20.sp), color = c.ink)
            }
        }
        Muted(stringResource(R.string.about_language_hint), Modifier.padding(top = 2.dp, bottom = 14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (l in Lang.ALL) {
                val on = l.tag == current.tag
                // each row in its own direction and font, whatever the app's language is now
                CompositionLocalProvider(LocalLayoutDirection provides l.direction) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).border(2.dp, if (on) c.ink else c.rule).background(c.paper)
                            .clickable(role = Role.RadioButton) { onPick(l.tag) }.semantics { selected = on }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val big = l.script == Lang.Script.HEBREW || l.script == Lang.Script.LATIN
                        Text(l.name, style = TextStyle(fontFamily = Lang.display(l), fontWeight = FontWeight.Bold, fontSize = if (big) 28.sp else 26.sp, lineHeight = 1.em), color = c.ink)
                        Box(Modifier.size(22.dp).border(2.dp, c.ink), contentAlignment = Alignment.Center) {
                            if (on) Box(Modifier.size(12.dp).background(c.ink))
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.app_lang_phone),
            Modifier.padding(top = 14.dp).heightIn(min = 44.dp).clickable(role = Role.Button) { onPick(null) }.padding(vertical = 12.dp),
            style = Ski.type.body.copy(fontSize = 14.sp, textDecoration = TextDecoration.Underline), color = c.glacier)
    }
}
