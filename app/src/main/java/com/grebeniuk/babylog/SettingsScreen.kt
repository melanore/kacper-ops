package com.grebeniuk.babylog

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay

@Composable
fun rememberQrScanner(c: AppController): () -> Unit {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ScanContract()) { r ->
        val txt = r.contents ?: return@rememberLauncherForActivityResult
        val ok = c.applySetupCode(txt)
        Toast.makeText(ctx, if (ok) s["setupOk"] else s["setupBad"], Toast.LENGTH_SHORT).show()
    }
    return {
        launcher.launch(
            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(s["scanPrompt"])
                .setBeepEnabled(false).setOrientationLocked(true)
        )
    }
}

fun shareText(ctx: android.content.Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, null))
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    SectionTitle(title)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(c: AppController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val scan = rememberQrScanner(c)
    var showQr by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(s["settings"], style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp)) {
            item {
                Card(s["switchWho"]) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        c.db.liveProfiles().forEach { p ->
                            val n = c.profileName(p, s)
                            Pill(n, selected = c.cfg.me == p.id, onClick = { c.setMe(p.id) }, fontSize = 15,
                                modifier = Modifier.height(46.dp), leading = { Avatar(p, n, 28.dp) })
                        }
                    }
                }
            }
            item {
                Card(s["profiles"]) {
                    val live = c.db.liveProfiles()
                    live.forEach { p -> ProfileRow(c, p, canDelete = live.size > 1) }
                    TextButton(onClick = { c.addProfile() }) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(s["addProfile"])
                    }
                }
            }
            item { SyncCard(c, onShowQr = { showQr = true }, onScan = scan) }
            item {
                Card(s["language"]) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Strings.LANGS.forEachIndexed { i, (code, name) ->
                            SegmentedButton(
                                selected = c.cfg.lang == code, onClick = { c.setLang(code) },
                                shape = SegmentedButtonDefaults.itemShape(i, Strings.LANGS.size),
                            ) { Text(name, maxLines = 1) }
                        }
                    }
                }
            }
            item {
                Card(s["theme"]) {
                    val opts = listOf("auto", "light", "dark")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        opts.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = c.cfg.theme == t, onClick = { c.setTheme(t) },
                                shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                            ) { Text(s[t], maxLines = 1) }
                        }
                    }
                }
            }
            item {
                Card(s["data"]) {
                    FilledTonalButton(onClick = { shareText(ctx, c.csv(s)) }) { Text(s["export"]) }
                    Text("KacperOps ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }

    if (showQr) QrDialog(c) { showQr = false }
}

@Composable
private fun ProfileRow(c: AppController, p: Profile, canDelete: Boolean) {
    val s = LocalStrings.current
    var text by remember(p.id) { mutableStateOf(p.name) }
    LaunchedEffect(text) {
        if (text.trim() != p.name) { delay(700); c.renameProfile(p.id, text.trim()) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Avatar(p, text.ifBlank { s[p.def] }, 34.dp)
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            placeholder = { Text(s[p.def]) }, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { c.deleteProfile(p.id) }, enabled = canDelete) {
            Icon(Icons.Outlined.Delete, s["del"])
        }
    }
}

@Composable
private fun SyncCard(c: AppController, onShowQr: () -> Unit, onScan: () -> Unit) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var repo by remember(c.cfg.repo) { mutableStateOf(c.cfg.repo) }
    var branch by remember(c.cfg.branch) { mutableStateOf(c.cfg.branch) }
    var token by remember(c.cfg.token) { mutableStateOf(c.cfg.token) }
    var reveal by remember { mutableStateOf(false) }

    Card(s["sync"]) {
        val st = c.syncState
        val (icon, label) = when (st) {
            SyncState.OFF -> Icons.Outlined.CloudOff to s["syncOff"]
            SyncState.SYNCING -> Icons.Outlined.Sync to s["syncing"]
            SyncState.PENDING -> Icons.Outlined.Sync to s["syncPending"]
            SyncState.ERROR -> Icons.Outlined.ErrorOutline to (s["syncErr"] + ": " + (c.syncError?.let { s[it.key] + if (it.detail.isNotEmpty()) " (${it.detail})" else "" } ?: ""))
            SyncState.OK -> Icons.Outlined.CloudDone to s["synced"]
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (st == SyncState.ERROR) cs.error else cs.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, color = if (st == SyncState.ERROR) cs.error else cs.onSurface)
                if (c.cfg.lastSync > 0 && st != SyncState.OFF) {
                    Text("${s["lastSync"]}: ${dayLabel(c.cfg.lastSync, c.now, s)} ${hhmm(c.cfg.lastSync)}",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (c.cfg.syncOn) IconButton(onClick = { c.syncNow() }) { Icon(Icons.Outlined.Sync, s["syncNow"]) }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(repo, { repo = it }, label = { Text(s["repo"]) }, placeholder = { Text("melanore/kacper-ops") },
            singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(branch, { branch = it }, label = { Text(s["branch"]) },
            singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            token,
            { v ->
                // pasting a whole pairing message fills everything at once
                if (v.contains("kacperops://")) c.applySetupCode(v) else token = v
            },
            label = { Text(s["token"]) }, placeholder = { Text("github_pat_…") },
            singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                }
            },
        )
        Text(s["syncHelp"], style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        Button(onClick = { c.setSync(repo, branch, token) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(s["saveSync"], fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.QrCodeScanner, null); Spacer(Modifier.width(6.dp)); Text(s["scanQr"], maxLines = 1)
            }
            OutlinedButton(onClick = onShowQr, enabled = c.cfg.syncOn, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.QrCode2, null); Spacer(Modifier.width(6.dp)); Text("QR", maxLines = 1)
            }
        }
        TextButton(
            onClick = { shareText(ctx, "KacperOps — ${s["qrTitle"]}:\n${c.setupCode()}") },
            enabled = c.cfg.syncOn,
        ) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(6.dp)); Text(s["shareCode"]) }
    }
}

@Composable
private fun QrDialog(c: AppController, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val code = c.setupCode()
    val bmp = remember(code) { qrBitmap(code) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(s["done"]) } },
        title = { Text(s["qrTitle"]) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(bmp, contentDescription = null,
                    modifier = Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)))
                Text(s["qrHint"], style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
        },
    )
}

fun qrBitmap(text: String, size: Int = 640): androidx.compose.ui.graphics.ImageBitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(
        text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
    )
    val px = IntArray(size * size) { i -> if (m[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.setPixels(px, 0, size, 0, 0, size, size)
    return bmp.asImageBitmap()
}
