package com.example.enchufe2.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.enchufe2.mqtt.MQTTManager
import kotlinx.coroutines.delay

@Composable
fun EnchufeControlScreen(
    onLogout: () -> Unit,
    onSettingsClick: () -> Unit,
    mqttManager: MQTTManager,
    estado: MutableState<String>,
    temperatura: MutableState<String>,
    humedad: MutableState<String>,
    luz: MutableState<String>,
    timer: MutableState<String>,
    controlLuz: MutableState<Boolean>,
    controlTemp: MutableState<Boolean>,
    alertasActivadas: MutableState<Boolean>
) {
    var isSending by remember { mutableStateOf(false) }

    val displayEstado = if (isSending) "Enviando..." else estado.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Estado del Enchufe: $displayEstado", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("⏳ Tiempo Restante: ${timer.value}", fontSize = 18.sp)

        Button(
            onClick = {
                isSending = true
                mqttManager.sendMessage("ON", "nicohc/feeds/enchufe-dot-control")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("🔌 Encender", fontSize = 18.sp)
        }

        Button(
            onClick = {
                isSending = true
                mqttManager.sendMessage("OFF", "nicohc/feeds/enchufe-dot-control")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("⛔ Apagar", fontSize = 18.sp)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📊 Datos de Sensores", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("🌡 Temperatura: ${temperatura.value}", fontSize = 16.sp)
                Text("💧 Humedad: ${humedad.value}", fontSize = 16.sp)
                Text("💡 Luz: ${luz.value}", fontSize = 16.sp)

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text("⚙️ Estados del Sistema", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("🔆 Control por Luz: ${if (controlLuz.value) "Activado" else "Desactivado"}", fontSize = 16.sp)
                Text("🌡 Control por Temp: ${if (controlTemp.value) "Activado" else "Desactivado"}", fontSize = 16.sp)
                Text("🔔 Alertas: ${if (alertasActivadas.value) "Activas" else "Inactivas"}", fontSize = 16.sp)
            }
        }

        Column {
            Button(
                onClick = onSettingsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("⚙ Configuración", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Cerrar Sesión", fontSize = 18.sp, color = MaterialTheme.colorScheme.onError)
            }
        }
    }

    LaunchedEffect(isSending, estado.value) {
        if (isSending) {
            delay(1000)
            isSending = false
        }
    }
}
