package com.wanderwildwood.fukuyaku.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.fukuyaku.R
import java.util.Locale

/*
 * A time typed as two plain number boxes, hour and minute, on the 24-hour clock. Mudita's own
 * time input kept the old figure and took a typed one as the tens ("4" then "5" came out as
 * 50), so this one selects the whole figure when the box is pressed: the first digit typed
 * replaces it, the second follows it, and a figure that would run past the top starts again.
 */

private fun two(n: Int) = String.format(Locale.ROOT, "%02d", n)

@Composable
private fun NumberBox(value: Int, max: Int, description: String, last: Boolean, onValue: (Int) -> Unit) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(two(value))) }
    LaunchedEffect(value, focused) {
        if (!focused) field = TextFieldValue(two(value))
    }
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(96.dp, 80.dp).border(BorderStroke(2.dp, ink), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = field,
            onValueChange = { v ->
                var digits = v.text.filter(Char::isDigit).takeLast(2)
                if ((digits.toIntOrNull() ?: 0) > max) digits = digits.takeLast(1)
                field = TextFieldValue(digits, TextRange(digits.length))
                digits.toIntOrNull()?.let(onValue)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 46.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = ink,
            ),
            cursorBrush = SolidColor(ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (last) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) field = field.copy(selection = TextRange(0, field.text.length))
                }
                .semantics { contentDescription = description },
        )
    }
}

/** The hour (0 to 23) and the minute, each in its box. */
@Composable
fun TimeBoxes(hour: Int, minute: Int, onChange: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        NumberBox(hour, 23, stringResource(R.string.cd_hour), last = false) { onChange(it, minute) }
        TextMMD(text = ":", fontSize = 40.sp, modifier = Modifier.padding(horizontal = 6.dp))
        NumberBox(minute, 59, stringResource(R.string.cd_minute), last = true) { onChange(hour, it) }
    }
}
