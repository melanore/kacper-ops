package com.grebeniuk.babylog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First run: pick who uses this phone (or pair with the partner's phone first). */
@Composable
fun WelcomeScreen(c: AppController) {
    val s = LocalStrings.current
    val scan = rememberQrScanner(c)
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Kind.FEED, Kind.PEE, Kind.POOP).forEach { k ->
                val cat = LocalCats.current.of(k)
                Box(Modifier.size(52.dp).clip(CircleShape).background(cat.container), contentAlignment = Alignment.Center) {
                    KindIcon(k, cat.accent, 28.dp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("KacperOps", fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
        Text(s["tagline"], style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(40.dp))
        Text(s["whoTitle"], style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            c.db.liveProfiles().forEach { p ->
                val n = c.profileName(p, s)
                Pill(n, selected = false, onClick = { c.setMe(p.id) }, fontSize = 18,
                    modifier = Modifier.fillMaxWidth().height(64.dp), leading = { Avatar(p, n, 36.dp) })
            }
        }
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = scan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Outlined.QrCodeScanner, null)
            Spacer(Modifier.width(8.dp))
            Text(s["joinPartner"])
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Strings.LANGS.forEach { (code, name) ->
                TextButton(onClick = { c.setLang(code) }) {
                    Text(name, fontWeight = if (c.cfg.lang == code) FontWeight.Bold else FontWeight.Normal,
                        color = if (c.cfg.lang == code) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
