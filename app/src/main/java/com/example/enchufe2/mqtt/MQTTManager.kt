package com.example.enchufe2.mqtt

import android.util.Log
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject

class MQTTManager {

    private val brokerUrl = "tcp://io.adafruit.com:1883"
    private val username  = "nicohc"
    private val password  = "aio_KOQz9077t0pnepBgwcsibIrWSALK"

    private val topicControl   = "nicohc/feeds/enchufe-dot-control"   // publica: "ON"/"OFF"
    private val topicEstado    = "nicohc/feeds/enchufe-dot-status"    // subscribe: "ON"/"OFF"
    private val topicSensores  = "nicohc/feeds/enchufe-dot-sensores"  // subscribe: JSON
    private val topicConfig    = "nicohc/feeds/enchufe-dot-config"    // publica/suscribe: JSON

    private lateinit var mqttClient: MqttClient

    fun connect(
        estado: MutableState<String>,
        temperatura: MutableState<String>,
        humedad: MutableState<String>,
        luz: MutableState<String>,

        // Opcionales para precargar Settings desde el JSON retenido
        autoLuz: MutableState<Boolean>? = null,
        autoTemp: MutableState<Boolean>? = null,
        umbralLuz: MutableState<Int>? = null,
        tempMin: MutableState<Int>? = null,
        tempMax: MutableState<Int>? = null,
        timerMin: MutableState<Int>? = null
    ) {
        try {
            if (this::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.disconnect()
                Log.d("MQTT", "🔌 Cliente desconectado antes de reconectar")
            }

            val clientId = "AndroidClient-${System.currentTimeMillis()}"
            mqttClient = MqttClient(brokerUrl, clientId, null)

            val options = MqttConnectOptions().apply {
                userName = username
                password = this@MQTTManager.password.toCharArray()
                isAutomaticReconnect = true
                isCleanSession = true
            }

            mqttClient.connect(options)
            Log.d("MQTT", "✅ Conectado a MQTT")

            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.e("MQTT", "🔌 Conexión perdida: ${cause?.message}")
                }
                override fun messageArrived(topic: String?, message: MqttMessage?) { }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    try { Log.d("MQTT", "✅ deliveryComplete: ${token?.topics?.joinToString()}") } catch (_: Exception) {}
                }
            })

            subscribeToTopics(
                estado, temperatura, humedad, luz,
                autoLuz, autoTemp, umbralLuz, tempMin, tempMax, timerMin
            )

        } catch (e: Exception) {
            Log.e("MQTT", "❌ Error al conectar MQTT: ${e.message}", e)
        }
    }

    private fun subscribeToTopics(
        estado: MutableState<String>,
        temperatura: MutableState<String>,
        humedad: MutableState<String>,
        luz: MutableState<String>,

        autoLuz: MutableState<Boolean>? = null,
        autoTemp: MutableState<Boolean>? = null,
        umbralLuz: MutableState<Int>? = null,
        tempMin: MutableState<Int>? = null,
        tempMax: MutableState<Int>? = null,
        timerMin: MutableState<Int>? = null
    ) {
        // Estado del relé: "ON"/"OFF"
        mqttClient.subscribe(topicEstado) { _, message ->
            val v = message.toString().trim()
            estado.value = v
            Log.d("MQTT", "📥 Estado: $v")
        }

        // Sensores (JSON): {temperatura, humedad, luz}
        mqttClient.subscribe(topicSensores) { _, message ->
            val txt = message.toString()
            try {
                val obj = JSONObject(txt)
                obj.optDouble("temperatura", Double.NaN).let {
                    if (!it.isNaN()) temperatura.value = "${"%.1f".format(it)}°C"
                }
                obj.optDouble("humedad", Double.NaN).let {
                    if (!it.isNaN()) humedad.value = "${"%.0f".format(it)}%"
                }
                obj.optInt("luz", -1).let {
                    if (it >= 0) luz.value = "$it lx"
                }
                Log.d("MQTT", "📥 Sensores JSON: $txt")
            } catch (e: Exception) {
                Log.w("MQTT", "⚠ JSON inválido en sensores: $txt", e)
            }
        }

        // (Opcional) Config (JSON) para precargar Settings
        if (autoLuz != null || autoTemp != null || umbralLuz != null ||
            tempMin != null || tempMax != null || timerMin != null) {

            mqttClient.subscribe(topicConfig) { _, message ->
                val txt = message.toString()
                try {
                    val obj = JSONObject(txt)
                    autoLuz?.value   = obj.optBoolean("auto_luz",  autoLuz?.value ?: false)
                    autoTemp?.value  = obj.optBoolean("auto_temp", autoTemp?.value ?: false)
                    umbralLuz?.value = obj.optInt("luz_umbral",    umbralLuz?.value ?: 500)
                    tempMin?.value   = obj.optInt("temp_min",      tempMin?.value ?: 25)
                    tempMax?.value   = obj.optInt("temp_max",      tempMax?.value ?: 30)
                    timerMin?.value  = obj.optInt("timer",         timerMin?.value ?: 0)
                    Log.d("MQTT", "📥 Config JSON: $txt")
                } catch (e: Exception) {
                    Log.w("MQTT", "⚠ JSON inválido en config: $txt", e)
                }
            }
        }
    }

    /** Publica ON/OFF al feed de control (retenido) */
    fun sendControl(on: Boolean) {
        sendMessage(if (on) "ON" else "OFF", topicControl)
    }

    /** Publica el JSON de configuración (retenido) */
    fun sendConfigJson(
        autoLuz: Boolean,
        autoTemp: Boolean,
        luzUmbral: Int,
        tempMin: Int,
        tempMax: Int,
        timerMin: Int
    ) {
        val json = """
            {
              "auto_luz": $autoLuz,
              "auto_temp": $autoTemp,
              "luz_umbral": $luzUmbral,
              "temp_min": $tempMin,
              "temp_max": $tempMax,
              "timer": $timerMin
            }
        """.trimIndent()

        sendMessage(json, topicConfig)
    }

    /** Publicación genérica (retenida) */
    fun sendMessage(message: String, topic: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!this@MQTTManager::mqttClient.isInitialized || !mqttClient.isConnected) {
                    try { mqttClient.reconnect() } catch (_: Exception) {}
                    delay(1500)
                }
                if (mqttClient.isConnected) {
                    val mqttMessage = MqttMessage(message.toByteArray()).apply {
                        qos = 1
                        isRetained = true
                    }
                    mqttClient.publish(topic, mqttMessage)
                    Log.d("MQTT", "📤 Enviado a $topic: $message (retained=true)")
                } else {
                    Log.e("MQTT", "❌ No se pudo enviar, MQTT desconectado.")
                }
            } catch (e: Exception) {
                Log.e("MQTT", "❌ Error al enviar MQTT: ${e.message}", e)
            }
        }
    }

    fun disconnect() {
        try {
            if (this::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.disconnect()
                Log.d("MQTT", "🔌 Cliente desconectado correctamente")
            }
        } catch (e: Exception) {
            Log.e("MQTT", "❌ Error al desconectar: ${e.message}")
        }
    }

    fun isConnected(): Boolean {
        return this::mqttClient.isInitialized && mqttClient.isConnected
    }
}
