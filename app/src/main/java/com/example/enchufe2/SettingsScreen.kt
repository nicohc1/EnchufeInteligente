package com.example.enchufe2.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.enchufe2.mqtt.MQTTManager

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    mqttManager: MQTTManager,
    controlLuz: MutableState<Boolean>,
    controlTemp: MutableState<Boolean>,
    alertasActivadas: MutableState<Boolean>,

    // Estados compartidos para precargar y modificar desde la UI
    umbralLuzState: MutableState<Int>,
    tempMinState: MutableState<Int>,
    tempMaxState: MutableState<Int>,
    timerMinState: MutableState<Int>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("⚙ Configuración de Sensores", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        // 🔔 Notificaciones
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("🔔 Notificaciones", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Switch(
                checked = alertasActivadas.value,
                onCheckedChange = { alertasActivadas.value = it }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 💡 Control por Luz
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("💡 Control por Luz", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Switch(
                checked = controlLuz.value,
                onCheckedChange = { controlLuz.value = it } // se aplica al guardar
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Slider(
            value = umbralLuzState.value.toFloat(),
            onValueChange = { umbralLuzState.value = it.toInt() },
            valueRange = 100f..1023f,
            steps = 9
        )
        Text("Umbral de Luz: ${umbralLuzState.value}")

        Spacer(modifier = Modifier.height(20.dp))

        // 🌡 Control de temperatura
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("🌡 Control por Temperatura", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Switch(
                checked = controlTemp.value,
                onCheckedChange = { controlTemp.value = it } // se aplica al guardar
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text("Temperatura Máxima: ${tempMaxState.value}°C")
        Slider(
            value = tempMaxState.value.toFloat(),
            onValueChange = { tempMaxState.value = it.toInt() },
            valueRange = 20f..45f
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text("Temperatura Mínima: ${tempMinState.value}°C")
        Slider(
            value = tempMinState.value.toFloat(),
            onValueChange = { tempMinState.value = it.toInt() },
            valueRange = 10f..40f
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ⏳ Temporizador
        val useTimerEnabled = timerMinState.value > 0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("⏳ Temporizador (min)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Switch(
                checked = useTimerEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        if (timerMinState.value == 0) timerMinState.value = 10
                    } else {
                        timerMinState.value = 0
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Slider(
            value = timerMinState.value.toFloat(),
            onValueChange = { timerMinState.value = it.toInt() },
            valueRange = 1f..120f,
            enabled = useTimerEnabled
        )
        Text("${if (useTimerEnabled) timerMinState.value else 0} min", modifier = Modifier.padding(start = 8.dp))

        Spacer(modifier = Modifier.height(24.dp))

        // 📝 Guardar (publica un SOLO JSON a enchufe-dot-config)
        Button(
            onClick = {
                val json = """
                    {
                      "auto_luz": ${controlLuz.value},
                      "auto_temp": ${controlTemp.value},
                      "luz_umbral": ${umbralLuzState.value},
                      "temp_min": ${tempMinState.value},
                      "temp_max": ${tempMaxState.value},
                      "timer": ${timerMinState.value}
                    }
                """.trimIndent()

                mqttManager.sendMessage(json, "nicohc/feeds/enchufe-dot-config")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar Configuración", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 🔌 Pruebas de control manual
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { mqttManager.sendMessage("ON",  "nicohc/feeds/enchufe-dot-control") },
                modifier = Modifier.weight(1f)
            ) { Text("Probar ON") }

            Button(
                onClick = { mqttManager.sendMessage("OFF", "nicohc/feeds/enchufe-dot-control") },
                modifier = Modifier.weight(1f)
            ) { Text("Probar OFF") }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver", fontSize = 18.sp)
        }
    }
}
