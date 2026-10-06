package io.github.pini236.skiapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The small pieces of the canvas (design/round10/build.py: btn, note, topbar, field), on the tokens.
 * Square corners everywhere, touch targets of at least 44.
 */

/**
 * The main action, as the site's .tf-save and .ac-btn.blue: the run blue with the text on it (white by day; by night the
 * light blue with dark text, round 18), 52 high. In a row, [full] = false keeps it to its text.
 */
@Composable
fun PrimaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, full: Boolean = true) {
    val c = Ski.colors
    Row(
        modifier.let { if (full) it.fillMaxWidth() else it }
            .shadow(if (full) 0.dp else 8.dp, RectangleShape, ambientColor = Color(0x591F5FC4), spotColor = Color(0x591F5FC4))
            .background(c.blue).clickable(role = Role.Button, onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = c.onBoard)
        Text(text, style = Ski.type.bodyBold.copy(fontSize = 16.5.sp), color = c.onBoard)
    }
}

/** A quiet action: just the words, in the link colour (red for a destructive one). */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    val c = Ski.colors
    Box(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Ski.type.bodyBold.copy(fontSize = 16.5.sp), color = if (danger) c.red else c.glacier)
    }
}

/** A short line with an icon: where something is kept, what happens next. */
@Composable
fun Note(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val c = Ski.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = c.muted)
        Text(text, style = Ski.type.small, color = c.muted)
    }
}

/** A page's head: its title in the sign voice at the start, and the way back at the end ("בית", "ביטול"). */
@Composable
fun TopBar(title: String, back: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ski.colors
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = Ski.type.title, color = c.ink)
        BackLink(back, onBack)
    }
}

@Composable
fun BackLink(text: String, onBack: () -> Unit, color: Color = Ski.colors.glacier) {
    Row(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.back, null, Modifier.size(20.dp), tint = color)
        Text(text, style = Ski.type.bodyBold, color = color)
    }
}

/**
 * A labelled text field (the canvas's .fld): 48 high, a 1.5 border, square. Latin-only values (codes, numbers) read left
 * to right. [showLabel] false puts the label inside, as the hint (a search field, as the site's); [focus] lets the
 * screen put the cursor in it.
 */
@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String = "", ltr: Boolean = false,
          keyboard: KeyboardType = KeyboardType.Text, error: String? = null, caps: Boolean = false, showLabel: Boolean = true,
          focus: FocusRequester? = null) {
    val c = Ski.colors
    var focused by remember { mutableStateOf(false) }
    val hint = if (showLabel) hint else hint.ifEmpty { label }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (showLabel) Text(label, style = Ski.type.label.copy(fontSize = 13.sp), color = c.muted)
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth().height(48.dp).then(if (focus != null) Modifier.focusRequester(focus) else Modifier).onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label }
                .background(c.paper).border(if (focused) 2.dp else 1.5.dp, if (error != null) c.red else if (focused) c.accent else c.rule),
            singleLine = true,
            textStyle = Ski.type.body.copy(fontSize = 16.sp, color = c.ink, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
            keyboardOptions = KeyboardOptions(capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.None, keyboardType = keyboard, imeAction = ImeAction.Next),
            cursorBrush = SolidColor(c.accent),
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && hint.isNotEmpty()) Text(hint, style = Ski.type.body.copy(fontSize = 16.sp), color = c.muted.copy(alpha = .75f), maxLines = 1)
                    inner()
                }
            },
        )
        if (error != null) Text(error, style = Ski.type.small.copy(fontSize = 12.5.sp), color = c.red)
    }
}
