package com.example.enchufe2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.enchufe2.mqtt.MQTTManager
import com.example.enchufe2.screens.*
import com.example.enchufe2.ui.theme.Enchufe2Theme

class MainActivity : ComponentActivity() {

    private val ALERT_CHANNEL_ID = "sensor_alert_channel"
    private val ALERT_NOTIFICATION_ID = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ✅ Solicitar permiso de notificaciones (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        createNotificationChannel()

        setContent {
            Enchufe2Theme {
                val mqttManager = remember { MQTTManager() }

                var isUserLoggedIn by remember { mutableStateOf(false) }
                var isAdminLoggedIn by remember { mutableStateOf(false) }
                var isInSettings by remember { mutableStateOf(false) }

                val estado = remember { mutableStateOf("Desconectado") }
                val temperatura = remember { mutableStateOf("Cargando...") }
                val humedad = remember { mutableStateOf("Cargando...") }
                val luz = remember { mutableStateOf("Cargando...") }

                // Texto de timer para UI (se sincroniza con el Int real abajo)
                val timer = remember { mutableStateOf("0 min") }

                val controlLuz = remember { mutableStateOf(false) }
                val controlTemp = remember { mutableStateOf(false) }
                val alertasActivadas = remember { mutableStateOf(true) }

                // ⬇️ Estados "config" compartidos (precargables desde feed CONFIG)
                val umbralLuzState = remember { mutableStateOf(500) } // Int
                val tempMinState   = remember { mutableStateOf(25) }  // Int
                val tempMaxState   = remember { mutableStateOf(30) }  // Int
                val timerMinState  = remember { mutableStateOf(0) }   // Int

                // 🔌 Conexión MQTT
                LaunchedEffect(Unit) {
                    if (!mqttManager.isConnected()) {
                        mqttManager.connect(
                            estado = estado,
                            temperatura = temperatura,
                            humedad = humedad,
                            luz = luz,
                            // ↓↓↓ estos 6 son opcionales; los pasamos para precargar Settings desde CONFIG
                            autoLuz   = controlLuz,
                            autoTemp  = controlTemp,
                            umbralLuz = umbralLuzState,
                            tempMin   = tempMinState,
                            tempMax   = tempMaxState,
                            timerMin  = timerMinState
                        )
                    }
                }

                // Sincroniza el texto del timer con el Int real
                LaunchedEffect(timerMinState.value) {
                    timer.value = "${timerMinState.value} min"
                }

                // 🔔 Verificar alertas
                LaunchedEffect(temperatura.value, humedad.value, alertasActivadas.value) {
                    if (!alertasActivadas.value) return@LaunchedEffect

                    val tempValue = temperatura.value.replace("°C", "").toFloatOrNull()
                    val humedadValue = humedad.value.replace("%", "").toFloatOrNull()

                    if (tempValue != null && tempValue > 35f) {
                        showNotification("⚠️ Alerta de Temperatura", "La temperatura es muy alta: $tempValue°C")
                    }

                    if (humedadValue != null && humedadValue < 30f) {
                        showNotification("⚠️ Alerta de Humedad", "La humedad está muy baja: $humedadValue%")
                    }
                }

                // Navegación
                when {
                    isAdminLoggedIn -> {
                        SettingsScreen(
                            onBack = { isAdminLoggedIn = false },
                            mqttManager = mqttManager,
                            controlLuz = controlLuz,
                            controlTemp = controlTemp,
                            alertasActivadas = alertasActivadas,
                            umbralLuzState = umbralLuzState,
                            tempMinState = tempMinState,
                            tempMaxState = tempMaxState,
                            timerMinState = timerMinState
                        )
                    }

                    isUserLoggedIn -> {
                        if (isInSettings) {
                            SettingsScreen(
                                onBack = { isInSettings = false },
                                mqttManager = mqttManager,
                                controlLuz = controlLuz,
                                controlTemp = controlTemp,
                                alertasActivadas = alertasActivadas,
                                umbralLuzState = umbralLuzState,
                                tempMinState = tempMinState,
                                tempMaxState = tempMaxState,
                                timerMinState = timerMinState
                            )
                        } else {
                            EnchufeControlScreen(
                                onLogout = { isUserLoggedIn = false },
                                onSettingsClick = { isInSettings = true },
                                mqttManager = mqttManager,
                                estado = estado,
                                temperatura = temperatura,
                                humedad = humedad,
                                luz = luz,
                                timer = timer,
                                controlLuz = controlLuz,
                                controlTemp = controlTemp,
                                alertasActivadas = alertasActivadas
                            )
                        }
                    }

                    else -> {
                        LoginScreen(
                            onUserLoginSuccess = { isUserLoggedIn = true },
                            onAdminLoginSuccess = { isAdminLoggedIn = true }
                        )
                    }
                }
            }
        }
    }

    // 🔔 Crear canal de notificación
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sensor Alerts"
            val descriptionText = "Canal para alertas de temperatura y humedad"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(ALERT_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 🚨 Mostrar notificación
    private fun showNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        with(NotificationManagerCompat.from(this)) {
            notify(ALERT_NOTIFICATION_ID, builder.build())
        }
    }
}
