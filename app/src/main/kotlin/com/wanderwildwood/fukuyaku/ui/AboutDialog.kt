package com.wanderwildwood.fukuyaku.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.fukuyaku.BuildConfig
import com.wanderwildwood.fukuyaku.R

/**
 * What this is, and what it is not. The second line is the one a stranger most needs: that
 * nothing leaves the phone, and that a reminder is not advice.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    // The dialog cannot scroll, so the text pages inside a box of fixed height and Close stays
    // under it.
    val room = LocalConfiguration.current.screenHeightDp.dp * 0.62f
    EInkDialog(onDismiss = onDismiss) {
        PagedColumn(modifier = Modifier.fillMaxWidth().height(room)) {
            item(key = "title") {
                TextMMD(
                    text = stringResource(R.string.about_title, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            item(key = "what") {
                TextMMD(
                    text = stringResource(R.string.about_what),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            item(key = "privacy") { AboutPart(R.string.about_privacy_head, R.string.about_privacy) }
            item(key = "terms") { AboutPart(R.string.about_terms_head, R.string.about_terms) }
            item(key = "disclaimer") { AboutPart(R.string.about_disclaimer_head, R.string.about_disclaimer) }
            item(key = "licence") {
                Column(Modifier.padding(top = 14.dp)) {
                    TextMMD(text = stringResource(R.string.about_licence), style = MaterialTheme.typography.labelSmall)
                    TextMMD(text = stringResource(R.string.about_icons), style = MaterialTheme.typography.labelSmall)
                }
            }
            item(key = "source") {
                TextMMD(
                    text = "github.com/wanderwildwood/fukuyaku",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            item(key = "llama") { Box(Modifier.padding(top = 14.dp)) { Llama() } }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.about_close), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun AboutPart(head: Int, body: Int) {
    Column(Modifier.padding(top = 14.dp)) {
        TextMMD(text = stringResource(head), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        TextMMD(text = stringResource(body), style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to. The site's
 * address sits at the start of the same line and opens the site; the llama and its words
 * open the page.
 *
 * Straight to the checkout: the Donate button on the site only leads there anyway. The short
 * square.link form, which is what the site itself links to, so a regenerated checkout follows
 * it. A phone with nothing that opens a web address says so rather than doing nothing.
 */
@Composable
private fun Llama() {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextMMD(
            text = "wanderthe.dev",
            style = MaterialTheme.typography.labelSmall,
            // The site's address opens the site, the way the llama beside it opens its page.
            modifier = Modifier
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://wanderthe.dev")),
                        )
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.about_no_browser), Toast.LENGTH_SHORT).show()
                    }
                }
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        )
        Spacer(Modifier.width(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://square.link/u/AGu8oT10")),
                        )
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.about_no_browser), Toast.LENGTH_SHORT).show()
                    }
                }
                .heightIn(min = 48.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.llama),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(text = stringResource(R.string.about_feed_the_llamas), style = MaterialTheme.typography.labelSmall)
        }
    }
}
