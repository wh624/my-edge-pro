package com.wuhuan.edgepro.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wuhuan.edgepro.App
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppIcon(packageName: String, size: Dp = 36.dp) {
    val app = LocalContext.current.applicationContext as App
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, packageName) { value = app.apps.iconOf(packageName, px) }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
        },
        actions = { actions() },
    )
}

/** 整数输入框，输入非法时保留文本但不回调。 */
@Composable
fun IntField(
    label: String,
    value: Int,
    onValue: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..Int.MAX_VALUE,
) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (text.toIntOrNull() != value) text = value.toString() }
    val parsed = text.toIntOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.filter(Char::isDigit).take(7)
            text.toIntOrNull()?.takeIf { v -> v in range }?.let(onValue)
        },
        label = { Text(label) },
        isError = parsed == null || parsed !in range,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** 以秒为单位输入、以毫秒保存的小数输入框。 */
@Composable
fun SecondsField(label: String, millis: Long, onMillis: (Long) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf((millis / 1000.0).toString().removeSuffix(".0")) }
    LaunchedEffect(millis) {
        if (text.toDoubleOrNull()?.let { (it * 1000).toLong() } != millis) {
            text = (millis / 1000.0).toString().removeSuffix(".0")
        }
    }
    val parsed = text.toDoubleOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.filter { c -> c.isDigit() || c == '.' }.take(8)
            text.toDoubleOrNull()?.takeIf { v -> v >= 0 }?.let { v -> onMillis((v * 1000).toLong()) }
        },
        label = { Text(label) },
        isError = parsed == null || parsed < 0,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private val fullFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
private val withWeekFormat = SimpleDateFormat("MM-dd EEE HH:mm:ss", Locale.CHINA)

fun formatTime(millis: Long): String = synchronized(fullFormat) { fullFormat.format(Date(millis)) }

fun formatTimeWithWeek(millis: Long): String = synchronized(withWeekFormat) { withWeekFormat.format(Date(millis)) }

fun formatRelative(target: Long, now: Long): String {
    val diff = (target - now) / 1000
    return when {
        diff < 60 -> "${diff.coerceAtLeast(0)} 秒后"
        diff < 3600 -> "${diff / 60} 分钟后"
        diff < 86400 -> "${diff / 3600} 小时 ${diff % 3600 / 60} 分后"
        else -> "${diff / 86400} 天后"
    }
}
