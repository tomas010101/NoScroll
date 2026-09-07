package com.tomas.noscroll.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomas.noscroll.accessibility.NoScrollAccessibilityService
import com.tomas.noscroll.preferences.BlockPreferences

class MainActivity : ComponentActivity() {
    private lateinit var preferences: BlockPreferences
    private var serviceEnabled by mutableStateOf(false)
    private var instagramEnabled by mutableStateOf(true)
    private var youtubeEnabled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = BlockPreferences(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()
                        .verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("NoScroll", style = MaterialTheme.typography.headlineLarge)
                        HorizontalDivider()
                        Text(if (serviceEnabled) "✓ Protección activa" else "⚠ Protección desactivada",
                            style = MaterialTheme.typography.titleLarge)
                        Text(if (serviceEnabled) "Servicio de accesibilidad activo"
                            else "Servicio de accesibilidad desactivado")
                        if (!serviceEnabled) {
                            Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                                Text("Activar servicio de accesibilidad")
                            }
                        }
                        OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                            Text("Abrir configuración de accesibilidad")
                        }
                        PreferenceSwitch("Bloquear Instagram Reels", instagramEnabled) {
                            instagramEnabled = it
                            preferences.instagramReels = it
                        }
                        PreferenceSwitch("Bloquear YouTube Shorts", youtubeEnabled) {
                            youtubeEnabled = it
                            preferences.youtubeShorts = it
                        }
                        HorizontalDivider()
                        Text("NoScroll bloquea contenido de scroll infinito sin impedir usar el resto de las aplicaciones.")
                        Text("Funciona sin Internet. El servicio analiza la interfaz de Instagram y YouTube " +
                            "para salir de sus reproductores de Reels y Shorts mediante Atrás.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(AccessibilityManager::class.java)
        val expected = ComponentName(this, NoScrollAccessibilityService::class.java)
        // Consulta al volver de Ajustes, sin polling ni referencias estáticas al servicio.
        serviceEnabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo.serviceInfo
                ComponentName(service.packageName, service.name) == expected
            }
        instagramEnabled = preferences.instagramReels
        youtubeEnabled = preferences.youtubeShorts
    }
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
