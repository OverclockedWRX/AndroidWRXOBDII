package com.wrxhud.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wrxhud.hud.HudColors

/**
 * In-phone Navigation tab backed by Google Maps.
 * Turn-by-turn uses the Google Maps app / navigation intent.
 * Android Auto deliberately does not host this tab (Maps handles AA navigation).
 */
@Composable
fun NavigationScreen(
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var mapQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NAVIGATION",
                color = HudColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = HudColors.TextDim)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Destination or place") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = HudColors.TextDim)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = HudColors.TextPrimary,
                    unfocusedTextColor = HudColors.TextPrimary,
                    focusedBorderColor = HudColors.Accent,
                    unfocusedBorderColor = HudColors.Border,
                    cursorColor = HudColors.Accent,
                    focusedPlaceholderColor = HudColors.TextDim,
                    unfocusedPlaceholderColor = HudColors.TextDim
                )
            )
            Button(
                onClick = {
                    val q = query.trim()
                    if (q.isNotEmpty()) mapQuery = q
                },
                colors = ButtonDefaults.buttonColors(containerColor = HudColors.Panel)
            ) {
                Icon(Icons.Default.Map, contentDescription = null, tint = HudColors.TextPrimary)
                Spacer(Modifier.width(6.dp))
                Text("Show", color = HudColors.TextPrimary)
            }
            Button(
                onClick = {
                    val q = query.trim().ifEmpty { return@Button }
                    openGoogleMapsNavigation(context, q)
                },
                colors = ButtonDefaults.buttonColors(containerColor = HudColors.Accent)
            ) {
                Icon(Icons.Default.Directions, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Go")
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Powered by Google Maps  ·  “Go” opens turn-by-turn in the Maps app",
            color = HudColors.TextDim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(8.dp))

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    setBackgroundColor(0xFF050505.toInt())
                    loadUrl(mapsUrl(""))
                }
            },
            update = { webView ->
                val target = mapsUrl(mapQuery)
                if (webView.url != target) {
                    webView.loadUrl(target)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

private fun mapsUrl(query: String): String {
    return if (query.isBlank()) {
        "https://www.google.com/maps"
    } else {
        "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}"
    }
}

private fun openGoogleMapsNavigation(context: android.content.Context, destination: String) {
    val navUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
    val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(navIntent)
    } catch (_: ActivityNotFoundException) {
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}")
        )
        context.startActivity(web)
    }
}
