package com.wanderwildwood.fukuyaku.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.fukuyaku.R

const val SOURCE_URL = "https://github.com/wanderwildwood/fukuyaku"

/** One thing the app is built with: its name, its licence and where it lives. */
data class Component(val name: String, val licence: String, val link: String)

/**
 * Everything the APK contains that is not this project's own code, from its dependency tree
 * (androidx and Compose libraries all carry their licence in META-INF of the APK). Libraries come
 * in families: one line per family.
 */
val components: List<Component> = listOf(
    Component("Kotlin standard library 2.2", "Apache-2.0", "https://kotlinlang.org"),
    Component("kotlinx.coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    Component("AndroidX Core, Activity, Lifecycle, SavedState, Startup, Tracing, Emoji2, ProfileInstaller", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
    Component("Jetpack Compose (runtime, UI, foundation, animation, Material 3, Material icons core)", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
    Component("MMD 1.0.2, Mudita's E Ink component library", "Apache-2.0", "https://github.com/mudita/MMD"),
    Component("Lato typeface, inside MMD", "OFL-1.1", "https://www.latofonts.com"),
    Component("Icons drawn from Google's Material Symbols", "Apache-2.0", "https://fonts.google.com/icons"),
)

/** The full texts shown once each, after the components: the licence name and the raw file. */
data class LicenceText(val name: String, @RawRes val raw: Int)

val licenceTexts: List<LicenceText> = listOf(
    LicenceText("Apache-2.0", R.raw.apache_2_0),
    LicenceText("OFL-1.1 (Lato)", R.raw.ofl_1_1),
)

/** Hard-wrapped licence text as paragraphs: blank lines break, single line breaks are spaces. */
fun licenceParagraphs(text: String): List<String> =
    text.replace("\r", "").split(Regex("\n\\s*\n"))
        .map { p -> p.lines().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotEmpty() }

private fun rawText(context: Context, @RawRes id: Int): List<String> =
    licenceParagraphs(context.resources.openRawResource(id).bufferedReader().use { it.readText() })

/** Opens an address; with no browser installed it says so in words instead of failing. */
fun openLink(context: Context, address: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.about_no_browser), Toast.LENGTH_LONG).show()
    }
}

/**
 * Where the source is, which licence the app is under with its full text, and what it is built
 * with. A page of plain text that turns on whole rows, with no rules between its parts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicencesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val gpl = remember { rawText(context, R.raw.gpl_3_0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBarMMD(title = { TextMMD(stringResource(R.string.licences_title)) })
        Box(Modifier.weight(1f)) {
            PagedColumn(Modifier.padding(horizontal = 16.dp)) {
                item { Head(stringResource(R.string.licences_source)) }
                item {
                    TextMMD(
                        SOURCE_URL,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { openLink(context, SOURCE_URL) }
                            .padding(vertical = 12.dp),
                    )
                }
                item { Head(stringResource(R.string.licences_licence)) }
                item { Para(stringResource(R.string.licences_licence_body)) }
                items(gpl) { Para(it) }
                item { Head(stringResource(R.string.licences_built_with)) }
                items(components) { c ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { openLink(context, c.link) }
                            .padding(vertical = 6.dp),
                    ) {
                        TextMMD(c.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        TextMMD(c.licence, style = MaterialTheme.typography.bodySmall)
                    }
                }
                for (t in licenceTexts) {
                    item { Head(t.name) }
                    items(rawText(context, t.raw)) { Para(it) }
                }
                item {
                    OutlinedButtonMMD(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 16.dp, bottom = 24.dp),
                    ) { TextMMD(stringResource(R.string.about_close)) }
                }
            }
        }
    }
}

@Composable
private fun Head(text: String) {
    TextMMD(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun Para(text: String) {
    TextMMD(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 5.dp))
}
