#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <DHT.h>
#include <ArduinoJson.h>

// ---------- Pines / Sensores ----------
#define RELAY_PIN D1
#define LDR_PIN   A0
#define DHT_PIN   D2
#define DHTTYPE   DHT22
DHT dht(DHT_PIN, DHTTYPE);

// ---------- WiFi ----------
const char* ssid     = "";
const char* password = "";

// ---------- MQTT (Adafruit IO) ----------
const char* mqtt_server = "io.adafruit.com";
const int   mqtt_port   = 1883;
const char* mqtt_user   = "";
const char* mqtt_pass   = "";

WiFiClient espClient;
PubSubClient client(espClient);

// ---------- Feeds (exactos) ----------
const char* FEED_CONTROL  = "nicohc/feeds/enchufe-dot-control";   // ON/OFF manual
const char* FEED_STATUS   = "nicohc/feeds/enchufe-dot-status";    // estado del relé
const char* FEED_SENSORES = "nicohc/feeds/enchufe-dot-sensores";  // JSON sensores
const char* FEED_CONFIG   = "nicohc/feeds/enchufe-dot-config";    // JSON config

// ---------- Estado / Config ----------
bool  relayon       = false;
bool  auto_luz      = false;   // ← arrancan en falso: solo corren cuando llegue JSON
bool  auto_temp     = false;
int   luz_umbral    = 500;
float temp_min      = 25.0;
float temp_max      = 30.0;
int   timer_minutes = 0;
bool  timer_activo  = false;
unsigned long timer_start = 0;

// 🔒 No ejecutar automatizaciones hasta recibir JSON de la app
bool  config_recibida = false;

// ---------- Helpers ----------
void publicarEstado(const char* motivo) {
  bool ok = client.publish(FEED_STATUS, relayon ? "ON" : "OFF", true);  // retain
  Serial.printf("➡️  RELAY %s (%s) pubStatus.ok=%d\n", relayon ? "ON" : "OFF", motivo, ok);
}

void setRelay(bool on, const char* motivo) {
  if (relayon != on) {
    relayon = on;
    digitalWrite(RELAY_PIN, on ? HIGH : LOW);
    publicarEstado(motivo);
  }
}

void aplicarConfigDesdeJSON(const String& s) {
  StaticJsonDocument<256> doc;
  DeserializationError err = deserializeJson(doc, s);
  if (err) { Serial.printf("⚠ JSON inválido: %s\n", err.c_str()); return; }

  if (doc.containsKey("auto_luz"))    auto_luz    = doc["auto_luz"];
  if (doc.containsKey("auto_temp"))   auto_temp   = doc["auto_temp"];
  if (doc.containsKey("luz_umbral"))  luz_umbral  = doc["luz_umbral"];
  if (doc.containsKey("temp_min"))    temp_min    = doc["temp_min"];
  if (doc.containsKey("temp_max"))    temp_max    = doc["temp_max"];
  if (doc.containsKey("timer"))       timer_minutes = doc["timer"];

  // JSON recibido → habilitamos automatizaciones
  config_recibida = true;

  if (timer_minutes > 0) {
    timer_activo = true;
    timer_start = millis();
    setRelay(true, "Encendido por timer");
  }

  Serial.printf("✅ CFG: rec=%d auto_luz=%d auto_temp=%d umbral=%d Tmin=%.1f Tmax=%.1f timer=%d\n",
                config_recibida, auto_luz, auto_temp, luz_umbral, temp_min, temp_max, timer_minutes);
}

// ---------- MQTT Callback ----------
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String msg; msg.reserve(length);
  for (unsigned int i = 0; i < length; i++) msg += (char)payload[i];
  Serial.printf("📩 %s: %s\n", topic, msg.c_str());

  if (String(topic) == FEED_CONTROL) {
    String s = msg; s.trim(); s.toUpperCase();
    if (s == "ON" || s == "1" || s == "TRUE")  { timer_activo = false; setRelay(true,  "Encendido Manual"); }
    else if (s == "OFF" || s == "0" || s == "FALSE") { timer_activo = false; setRelay(false, "Apagado Manual"); }
  } else if (String(topic) == FEED_CONFIG) {
    aplicarConfigDesdeJSON(msg);
  }
}

// ---------- WiFi / MQTT ----------
void setupWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  Serial.print("🔗 WiFi");
  int tries = 0;
  while (WiFi.status() != WL_CONNECTED && tries < 60) {
    delay(500); Serial.print(".");
    tries++;
  }
  Serial.println(WiFi.status() == WL_CONNECTED ? " ✅" : " ❌");
}

void reconnectMQTT() {
  if (!client.connected()) {
    Serial.print("🔗 MQTT...");
    if (client.connect("WemosJSON", mqtt_user, mqtt_pass)) {
      Serial.println("✅");
      client.subscribe(FEED_CONTROL);
      client.subscribe(FEED_CONFIG);
      Serial.println("✅ SUB: control + config");

      // Publica estado retenido
      bool okS = client.publish(FEED_STATUS, relayon ? "ON" : "OFF", true);
      Serial.printf("📤 PING status → %s (ok=%d)\n", relayon ? "ON" : "OFF", okS);

      // 🔔 PING de sensores para verificar feed y ruta
      StaticJsonDocument<128> ping;
      ping["temperatura"] = 23.5;
      ping["humedad"]     = 45;
      ping["luz"]         = 222;
      char buf[128];
      serializeJson(ping, buf);
      bool okP = client.publish(FEED_SENSORES, buf, true);
      Serial.printf("📤 PING sensores → %s (ok=%d)\n", buf, okP);

    } else {
      Serial.print("❌ state="); Serial.println(client.state());
    }
  }
}

// ---------- Setup ----------
void setup() {
  Serial.begin(115200);
  dht.begin();
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW);

  setupWifi();
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(mqttCallback);
  client.setKeepAlive(30);
  client.setBufferSize(512);
}

// ---------- Loop ----------
void loop() {
  reconnectMQTT();
  client.loop();

  // Diagnóstico breve
  static unsigned long lastDiag = 0;
  if (millis() - lastDiag > 5000) {
    lastDiag = millis();
    Serial.printf("NET wifi=%s mqtt=%d rssi=%d cfg=%d\n",
                  WiFi.status() == WL_CONNECTED ? "OK" : "DOWN",
                  client.connected(), WiFi.RSSI(), config_recibida);
  }

  // Lecturas
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  int   L = analogRead(LDR_PIN);

  // --------- Automatización (solo si llegó JSON de la app) ---------
  if (config_recibida) {
    // Luz
    if (auto_luz) {
      if (L < luz_umbral) setRelay(true,  "Encendido por luz");
      else                setRelay(false, "Apagado por luz");
    }
    // Temperatura
    if (auto_temp && !isnan(t)) {
      if (t >= temp_max)      setRelay(true,  "Encendido por temperatura");
      else if (t <= temp_min) setRelay(false, "Apagado por temperatura");
    }
    // Timer
    if (timer_activo && relayon) {
      unsigned long elapsedMin = (millis() - timer_start) / 60000UL;
      if ((int)elapsedMin >= timer_minutes) {
        timer_activo = false;
        setRelay(false, "Apagado por timer");
      }
    }
  }

  // --------- Publicación de sensores (JSON) con rate limit ---------
  static unsigned long lastPub = 0;
  if (millis() - lastPub > 10000) { // cada 10 s
    lastPub = millis();

    StaticJsonDocument<192> doc;
    if (!isnan(t)) doc["temperatura"] = t;
    if (!isnan(h)) doc["humedad"]     = h;
    doc["luz"] = L;

    char buf[192];
    serializeJson(doc, buf, sizeof(buf));
    bool ok = client.publish(FEED_SENSORES, buf, true);
    Serial.printf("📤 sensores → %s (ok=%d)\n", buf, ok);
    if (!ok) Serial.println("⚠ publish(sensores) falló (rate/conn)");
  }

  delay(2000);
}

